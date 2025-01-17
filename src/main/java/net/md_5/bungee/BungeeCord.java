package net.md_5.bungee;

import de.themoep.snap.Snap;
import de.themoep.snap.forwarding.SnapProxyServer;
import net.md_5.bungee.api.*;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.config.ConfigurationAdapter;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.api.scheduler.TaskScheduler;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.logging.Logger;

/**
 * Class for redirecting method calls from dumb plugins.
 */
@SuppressWarnings({"unused", "deprecation"})
public class BungeeCord extends ProxyServer {
    private static final boolean DISABLE_AUTHOR_NAG = Boolean.getBoolean("snap.noMoreAuthorNag");
    private static final BungeeCord instance = new BungeeCord();
    private static final Set<Class<?>> authorNagSet = new HashSet<>();
    private final ProxyServer snapProxy = SnapProxyServer.getInstance();

    public static BungeeCord getInstance() {
        if (!DISABLE_AUTHOR_NAG) {
            Optional<? extends Class<?>> callerOptional = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), 1).walk(s -> s.map(StackWalker.StackFrame::getDeclaringClass).skip(1).findFirst());
            if (callerOptional.isPresent()) {
                Class<?> caller = callerOptional.get();
                if (!authorNagSet.contains(caller)) {
                    authorNagSet.add(caller);
                    if (Plugin.class.isAssignableFrom(caller)) {
                        var plugins = SnapProxyServer.getInstance().getPluginManager().getPlugins();
                        for (Plugin plugin : plugins) {
                            if (plugin.getClass() == caller) {
                                Snap.logger().warn("Plugin {} is calling deprecated BungeeCord internals, report this to the author.", plugin.getDescription().getName());
                            }
                        }
                    } else {
                        Snap.logger().warn("Plugin class {} is calling deprecated BungeeCord internals, report this to the author.", caller.getName());
                    }
                }
            }
        }
        return instance;
    }

    @Override
    public String getName() {
        return snapProxy.getName();
    }

    @Override
    public String getVersion() {
        return snapProxy.getVersion();
    }

    @Override
    public String getTranslation(String s, Object... objects) {
        return snapProxy.getTranslation(s, objects);
    }

    @Override
    public Logger getLogger() {
        return snapProxy.getLogger();
    }

    @Override
    public Collection<ProxiedPlayer> getPlayers() {
        return snapProxy.getPlayers();
    }

    @Override
    public ProxiedPlayer getPlayer(String s) {
        return snapProxy.getPlayer(s);
    }

    @Override
    public ProxiedPlayer getPlayer(UUID uuid) {
        return snapProxy.getPlayer(uuid);
    }

    @Override
    public Map<String, ServerInfo> getServers() {
        return snapProxy.getServers();
    }

    @Override
    public Map<String, ServerInfo> getServersCopy() {
        return snapProxy.getServersCopy();
    }

    @Override
    public ServerInfo getServerInfo(String s) {
        return snapProxy.getServerInfo(s);
    }

    @Override
    public PluginManager getPluginManager() {
        return snapProxy.getPluginManager();
    }

    @Override
    public ConfigurationAdapter getConfigurationAdapter() {
        return snapProxy.getConfigurationAdapter();
    }

    @Override
    public void setConfigurationAdapter(ConfigurationAdapter configurationAdapter) {
        snapProxy.setConfigurationAdapter(configurationAdapter);
    }

    @Override
    public ReconnectHandler getReconnectHandler() {
        return snapProxy.getReconnectHandler();
    }

    @Override
    public void setReconnectHandler(ReconnectHandler reconnectHandler) {
        snapProxy.setReconnectHandler(reconnectHandler);
    }

    @Override
    public void stop() {
        snapProxy.stop();
    }

    @Override
    public void stop(String s) {
        snapProxy.stop(s);
    }

    @Override
    public void registerChannel(String s) {
        snapProxy.registerChannel(s);
    }

    @Override
    public void unregisterChannel(String s) {
        snapProxy.unregisterChannel(s);
    }

    @Override
    public Collection<String> getChannels() {
        return snapProxy.getChannels();
    }

    @Override
    public String getGameVersion() {
        return snapProxy.getGameVersion();
    }

    @Override
    public int getProtocolVersion() {
        return snapProxy.getProtocolVersion();
    }

    @Override
    public ServerInfo constructServerInfo(String s, InetSocketAddress inetSocketAddress, String s1, boolean b) {
        return snapProxy.constructServerInfo(s, inetSocketAddress, s1, b);
    }

    @Override
    public ServerInfo constructServerInfo(String s, SocketAddress socketAddress, String s1, boolean b) {
        return snapProxy.constructServerInfo(s, socketAddress, s1, b);
    }

    @Override
    public CommandSender getConsole() {
        return snapProxy.getConsole();
    }

    @Override
    public File getPluginsFolder() {
        return snapProxy.getPluginsFolder();
    }

    @Override
    public TaskScheduler getScheduler() {
        return snapProxy.getScheduler();
    }

    @Override
    public int getOnlineCount() {
        return snapProxy.getOnlineCount();
    }

    @Override
    public void broadcast(String s) {
        snapProxy.broadcast(s);
    }

    @Override
    public void broadcast(BaseComponent... baseComponents) {
        snapProxy.broadcast(baseComponents);
    }

    @Override
    public void broadcast(BaseComponent baseComponent) {
        snapProxy.broadcast(baseComponent);
    }

    @Override
    public Collection<String> getDisabledCommands() {
        return snapProxy.getDisabledCommands();
    }

    @Override
    public ProxyConfig getConfig() {
        return snapProxy.getConfig();
    }

    @Override
    public Collection<ProxiedPlayer> matchPlayer(String s) {
        return snapProxy.matchPlayer(s);
    }

    @Override
    public Title createTitle() {
        return snapProxy.createTitle();
    }
}
