package de.themoep.snap;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.themoep.snap.forwarding.SnapPlayer;
import de.themoep.snap.forwarding.SnapServerInfo;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;

public class Snap {
    private final ProxyServer proxy;
    private static Logger logger;
    private final Path dataFolder;
    private SnapBungeeAdapter bungeeAdapter;

    private PluginConfig config;

    private boolean throwUnsupportedException = true;
    private boolean registerAllForwardingListeners = false;

    private final Map<UUID, SnapPlayer> players = new ConcurrentHashMap<>();
    private final Map<String, SnapPlayer> playerNames = new ConcurrentHashMap<>();
    private final Map<String, SnapServerInfo> servers = new ConcurrentHashMap<>();
    private final Set<UUID> transferred = ConcurrentHashMap.newKeySet();

    private final Table<UUID, Key, CompletableFuture<byte[]>> cookieRequests = HashBasedTable.create();

    private final Cache<String, UUID> gameprofileUuidCache = CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.SECONDS).build();

    static {
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
    }

    @Inject
    public Snap(ProxyServer proxy, Logger logger, @DataDirectory Path dataFolder) {
        this.proxy = proxy;
        Snap.logger = logger;
        this.dataFolder = dataFolder;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException, IllegalAccessException, InvocationTargetException, IOException {
        if (loadConfig()) {
            if (!config.has("stats-id") || config.getString("stats-id").isEmpty()) {
                config.set("stats-id", UUID.randomUUID().toString());
                config.save();
            }
            bungeeAdapter = new SnapBungeeAdapter(this);
            bungeeAdapter.loadPlugins();
            getProxy().getEventManager().register(this, new SnapListener(this));
        } else {
            getLogger().error("Unable to load config! Plugin will not enable.");
        }
    }

    private boolean loadConfig() {
        config = new PluginConfig(this, dataFolder.resolve("snap.conf"));
        try {
            config.createDefaultConfig();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (config.load()) {
            throwUnsupportedException = config.getBoolean("throw-unsupported-exception", throwUnsupportedException);
            registerAllForwardingListeners = config.getBoolean("register-all-listeners", registerAllForwardingListeners);
            return true;
        }
        return false;
    }

    public PluginConfig getConfig() {
        return config;
    }

    public boolean shouldRegisterAllForwardingListeners() {
        return registerAllForwardingListeners;
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataFolder() {
        return dataFolder;
    }

    public SnapBungeeAdapter getBungeeAdapter() {
        return bungeeAdapter;
    }

    public Map<UUID, SnapPlayer> getPlayers() {
        return players;
    }

    public Map<String, SnapPlayer> getPlayerNames() {
        return playerNames;
    }

    public SnapPlayer getPlayer(Player player) {
        SnapPlayer p = players.computeIfAbsent(player.getUniqueId(), u -> new SnapPlayer(this, player));
        playerNames.putIfAbsent(p.getName(), p);
        return p;
    }

    public SnapServerInfo getServerInfo(RegisteredServer server) {
        if (server == null) {
            return null;
        }
        return servers.computeIfAbsent(server.getServerInfo().getName(), u -> new SnapServerInfo(this, server));
    }

    public Map<String, SnapServerInfo> getServers() {
        return servers;
    }

    public Object unsupported(String... message) {
        if (throwUnsupportedException) {
            throw new UnsupportedOperationException(message.length > 0 ? String.join("\n", message): "Not implemented (yet)!");
        }
        return null;
    }

    public void cacheUuidForGameprofile(String username, UUID uuid) {
        gameprofileUuidCache.put(username, uuid);
    }

    public UUID pullCachedUuidForUsername(String username) {
        UUID playerId = gameprofileUuidCache.getIfPresent(username);
        if (playerId != null) {
            gameprofileUuidCache.invalidate(username);
        }
        return playerId;
    }

    void invalidate(Player player) {
        players.remove(player.getUniqueId());
        playerNames.remove(player.getUsername());
        cookieRequests.row(player.getUniqueId()).clear();
        transferred.remove(player.getUniqueId());
    }

    public CompletableFuture<byte[]> retrieveCookie(InboundConnection connection, String key) {
        try {
            if (connection instanceof Player player) {
                return retrieveCookie(player, Key.key(key));
            }
            unsupported("Retrieving cookies from a non-Player connection is not supported in Velocity's API! (Was " + connection.getClass().getName() + ")");
        } catch (InvalidKeyException e) {
            unsupported("Tried to retrieve cookie at key '" + key + "' but the provided key was invalid!");
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<byte[]> retrieveCookie(Player player, Key key) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        cookieRequests.put(player.getUniqueId(), key, future);
        player.requestCookie(key);
        return future;
    }

    boolean completeCookieRequest(Player player, Key key, byte[] data) {
        CompletableFuture<byte[]> future = cookieRequests.get(player.getUniqueId(), key);
        if (future != null) {
            cookieRequests.remove(player.getUniqueId(), key);
            future.complete(data);
            return true;
        }
        return false;
    }

    public void markTransferred(Player player) {
        transferred.add(player.getUniqueId());
    }

    public boolean isTransferred(UUID playerId) {
        return transferred.contains(playerId);
    }

    public static Logger logger() {
        return logger;
    }
}
