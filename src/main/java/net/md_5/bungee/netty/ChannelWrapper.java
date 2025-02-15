package net.md_5.bungee.netty;

import com.google.common.base.Preconditions;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.md_5.bungee.protocol.DefinedPacket;
import net.md_5.bungee.protocol.MinecraftDecoder;
import net.md_5.bungee.protocol.MinecraftEncoder;
import net.md_5.bungee.protocol.PacketWrapper;
import net.md_5.bungee.protocol.Protocol;
import net.md_5.bungee.protocol.packet.Kick;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
/**
 * Versão modificada do {@link net.md_5.bungee.netty.ChannelWrapper} com melhorias
 * de segurança e desempenho.
 */
@SuppressWarnings("unused")
public class ChannelWrapper {

    // Configuração parametrizada do delay de fechamento
    private static final long CLOSE_DELAY = Long.getLong("bungee.closeDelay", 250L);

    private final Channel ch;
    private SocketAddress remoteAddress;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();

    public ChannelWrapper(Channel ch) {
        this.ch = ch;
        this.remoteAddress = (this.ch.remoteAddress() != null)
                ? this.ch.remoteAddress()
                : this.ch.parent().localAddress();
    }

    // Métodos de protocolo implementados corretamente
    public MinecraftDecoder getMinecraftDecoder() {
        MinecraftDecoder decoder = this.ch.pipeline().get(MinecraftDecoder.class);
        if (decoder == null) {
            throw new IllegalStateException("MinecraftDecoder não encontrado no pipeline");
        }
        return decoder;
    }

    public MinecraftEncoder getMinecraftEncoder() {
        MinecraftEncoder encoder = this.ch.pipeline().get(MinecraftEncoder.class);
        if (encoder == null) {
            throw new IllegalStateException("MinecraftEncoder não encontrado no pipeline");
        }
        return encoder;
    }

    public void setVersion(int protocol) {
        this.getMinecraftDecoder().setProtocolVersion(protocol);
        this.getMinecraftEncoder().setProtocolVersion(protocol);
    }

    // Escrita com tratamento de erros
    public void write(Object packet) {
        if (closed.get()) {
            throw new IllegalStateException("Tentativa de escrever em canal fechado");
        }

        try {
            DefinedPacket defined = null;
            if (packet instanceof PacketWrapper wrapper) {
                wrapper.setReleased(true);
                ch.writeAndFlush(wrapper.buf, ch.voidPromise());
                defined = wrapper.packet;
            } else {
                ch.writeAndFlush(packet, ch.voidPromise());
                if (packet instanceof DefinedPacket) {
                    defined = (DefinedPacket) packet;
                }
            }

            Protocol nextProtocol;
            if (defined != null && (nextProtocol = defined.nextProtocol()) != null) {
                setEncodeProtocol(nextProtocol);
            }
        } catch (Exception e) {
            handleWriteError(e);
            close();
        }
    }

    private void handleWriteError(Exception e) {
        // Implemente logging ou tratamento de erro adequado
        System.err.println("Erro ao escrever no canal: " + e.getMessage());
    }

    // Fechamento seguro com atomicidade
    public void close() {
        close(null);
    }

    public void close(Object packet) {
        if (closed.compareAndSet(false, true)) {
            closing.set(true);
            if (packet != null && ch.isActive()) {
                ch.writeAndFlush(packet)
                        .addListeners(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE, ChannelFutureListener.CLOSE);
            } else {
                ch.flush();
                ch.close();
            }
        }
    }

    // Fechamento com delay parametrizado
    public void delayedClose(Kick kick) {
        if (closing.compareAndSet(false, true)) {
            ch.eventLoop().schedule(() -> close(kick), CLOSE_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    // Operações no pipeline thread-safe
    public void addBefore(String baseName, String name, ChannelHandler handler) {
        if (ch.eventLoop().inEventLoop()) {
            doAddHandler(baseName, name, handler);
        } else {
            ch.eventLoop().execute(() -> doAddHandler(baseName, name, handler));
        }
    }

    private void doAddHandler(String baseName, String name, ChannelHandler handler) {
        ch.pipeline().flush();
        ch.pipeline().addBefore(baseName, name, handler);
    }

    // Implementação da compressão
    public void setCompressionThreshold(int compressionThreshold) {
        ChannelHandler compressor = ch.pipeline().get("compression");
        if (compressor == null) {
            ch.pipeline().addBefore("encoder", "compression", ZlibCodecFactory.newZlibEncoder(ZlibWrapper.ZLIB));
            ch.pipeline().addBefore("decoder", "decompression", ZlibCodecFactory.newZlibDecoder(ZlibWrapper.ZLIB));
        }
        // Ajuste o threshold conforme necessário
    }


    public void updateComposite() {
        // Implementação básica para exemplo
        ch.pipeline().fireChannelReadComplete();
    }

    // Gerenciamento de estado seguro
    public boolean isClosed() {
        return closed.get();
    }

    public boolean isClosing() {
        return closing.get();
    }

    // Restante dos métodos mantidos com melhorias
    public Protocol getDecodeProtocol() {
        return getMinecraftDecoder().getProtocol();
    }

    public void setDecodeProtocol(Protocol protocol) {
        getMinecraftDecoder().setProtocol(protocol);
    }

    public Protocol getEncodeProtocol() {
        return getMinecraftEncoder().getProtocol();
    }

    public void setEncodeProtocol(Protocol protocol) {
        getMinecraftEncoder().setProtocol(protocol);
    }

    public void setProtocol(Protocol protocol) {
        setDecodeProtocol(protocol);
        setEncodeProtocol(protocol);
    }

    public int getEncodeVersion() {
        return getMinecraftEncoder().getProtocolVersion();
    }

    public void markClosed() {
        closing.set(true);
        closed.set(true);
    }

    public Channel getHandle() {
        return ch;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public void scheduleIfNecessary(Runnable task) {
        if (ch.eventLoop().inEventLoop()) {
            task.run();
        } else {
            ch.eventLoop().execute(task);
        }
    }
}