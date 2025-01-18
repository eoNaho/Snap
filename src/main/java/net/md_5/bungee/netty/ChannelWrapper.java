package net.md_5.bungee.netty;

import com.google.common.base.Preconditions;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import net.md_5.bungee.protocol.DefinedPacket;
import net.md_5.bungee.protocol.MinecraftDecoder;
import net.md_5.bungee.protocol.MinecraftEncoder;
import net.md_5.bungee.protocol.PacketWrapper;
import net.md_5.bungee.protocol.Protocol;
import net.md_5.bungee.protocol.packet.Kick;

/**
 * Slightly modified version of {@link net.md_5.bungee.netty.ChannelWrapper} for compatibility.
 */
@SuppressWarnings("unused")
public class ChannelWrapper {
    private final Channel ch;
    private SocketAddress remoteAddress;
    private volatile boolean closed;
    private volatile boolean closing;

    public ChannelWrapper(Channel ch) {
        this.ch = ch;
        this.remoteAddress = this.ch.remoteAddress() == null ? this.ch.parent().localAddress() : this.ch.remoteAddress();
    }

    public Protocol getDecodeProtocol() {
        return this.getMinecraftDecoder().getProtocol();
    }

    public void setDecodeProtocol(Protocol protocol) {
        this.getMinecraftDecoder().setProtocol(protocol);
    }

    public Protocol getEncodeProtocol() {
        return this.getMinecraftEncoder().getProtocol();
    }

    public void setEncodeProtocol(Protocol protocol) {
        this.getMinecraftEncoder().setProtocol(protocol);
    }

    public void setProtocol(Protocol protocol) {
        this.setDecodeProtocol(protocol);
        this.setEncodeProtocol(protocol);
    }

    public void setVersion(int protocol) {
        this.getMinecraftDecoder().setProtocolVersion(protocol);
        this.getMinecraftEncoder().setProtocolVersion(protocol);
    }

    public MinecraftDecoder getMinecraftDecoder() {
        if (true) {
            throw new UnsupportedOperationException("Not implemented yet"); // TODO: Implement
        }
        return (MinecraftDecoder)this.ch.pipeline().get(MinecraftDecoder.class);
    }

    public MinecraftEncoder getMinecraftEncoder() {
        if (true) {
            throw new UnsupportedOperationException("Not implemented yet"); // TODO: Implement
        }
        return (MinecraftEncoder)this.ch.pipeline().get(MinecraftEncoder.class);
    }

    public int getEncodeVersion() {
        return this.getMinecraftEncoder().getProtocolVersion();
    }

    public void write(Object packet) {
        if (!this.closed) {
            Protocol nextProtocol;
            DefinedPacket defined = null;
            if (packet instanceof PacketWrapper wrapper) {
                wrapper.setReleased(true);
                this.ch.writeAndFlush(wrapper.buf, this.ch.voidPromise());
                defined = wrapper.packet;
            } else {
                this.ch.writeAndFlush(packet, this.ch.voidPromise());
                if (packet instanceof DefinedPacket) {
                    defined = (DefinedPacket)packet;
                }
            }
            if (defined != null && (nextProtocol = defined.nextProtocol()) != null) {
                this.setEncodeProtocol(nextProtocol);
            }
        }
    }

    public void markClosed() {
        this.closing = true;
        this.closed = true;
    }

    public void close() {
        this.close(null);
    }

    public void close(Object packet) {
        if (!this.closed) {
            this.closing = true;
            this.closed = true;
            if (packet != null && this.ch.isActive()) {
                this.ch.writeAndFlush(packet).addListeners(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE, ChannelFutureListener.CLOSE);
            } else {
                this.ch.flush();
                this.ch.close();
            }
        }
    }

    public void delayedClose(Kick kick) {
        if (!this.closing) {
            this.closing = true;
            this.ch.eventLoop().schedule(() -> this.close(kick), 250L, TimeUnit.MILLISECONDS);
        }
    }

    public void addBefore(String baseName, String name, ChannelHandler handler) {
        Preconditions.checkState(this.ch.eventLoop().inEventLoop(), "cannot add handler outside of event loop");
        this.ch.pipeline().flush();
        this.ch.pipeline().addBefore(baseName, name, handler);
    }

    public Channel getHandle() {
        return this.ch;
    }

    public void setCompressionThreshold(int compressionThreshold) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void updateComposite() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void scheduleIfNecessary(Runnable task) {
        if (this.ch.eventLoop().inEventLoop()) {
            task.run();
            return;
        }
        this.ch.eventLoop().execute(task);
    }

    public SocketAddress getRemoteAddress() {
        return this.remoteAddress;
    }

    public void setRemoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public boolean isClosed() {
        return this.closed;
    }

    public boolean isClosing() {
        return this.closing;
    }
}
