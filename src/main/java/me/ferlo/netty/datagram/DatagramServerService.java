package me.ferlo.netty.datagram;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import me.ferlo.netty.NetService;
import me.ferlo.netty.NetworkException;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.PacketParser;
import me.ferlo.utils.FutureUtils;
import me.ferlo.utils.SneakyThrow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class DatagramServerService implements NetService {

    // Constants

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramClientService.class);
    private static final int DEFAULT_SHUTDOWN_TIMEOUT = 5000;

    // Attributes

    private final AtomicBoolean hasStarted = new AtomicBoolean(false);
    private final AtomicBoolean hasStopped = new AtomicBoolean(false);
    private final CompletableFuture<CompletableFuture<Void>> stopFutureFuture = new CompletableFuture<>();
    private final int shutdownTimeout;

    private final Bootstrap bootstrap;
    private final int port;

    private EventLoopGroup group;
    private ChannelFuture future;

    public DatagramServerService(int port,
                                 Function<Class<? extends Packet>, Byte> packetToId,
                                 Function<Byte, PacketParser> idToParser,
                                 ChannelInboundHandler handler) {
        this(port, DEFAULT_SHUTDOWN_TIMEOUT, packetToId, idToParser, handler);
    }

    public DatagramServerService(int port,
                                 int shutdownTimeout,
                                 Function<Class<? extends Packet>, Byte> packetToId,
                                 Function<Byte, PacketParser> idToParser,
                                 ChannelInboundHandler handler) {

        this.port = port;
        this.shutdownTimeout = shutdownTimeout;
        this.bootstrap = new Bootstrap()
                .channel(NioDatagramChannel.class)
                .localAddress(port)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) throws Exception {
                        final SlidingWindowReliabilityHandler slidingWindow =
                                new SlidingWindowReliabilityHandler(0, 10);
                        ch.pipeline().addLast(
                                // ↑
                                slidingWindow.getOutboundHandler(),
                                new DatagramPacketEncoder(packetToId),
                                // ↓
                                slidingWindow.getInboundHandler(),
                                new DatagramToContextDecoder(idToParser, (ctx) -> sendPacket(ctx)),
                                handler
                        );
                    }
                });
    }

    @Override
    public CompletableFuture<Void> startAsync() throws NetworkException {

        if(hasStarted.getAndSet(true))
            throw new NetworkException("DatagramServerService cannot be started twice, make a new one");

        return CompletableFuture
                .runAsync(() -> {
                    LOGGER.info("Connecting DatagramServerService on {}...", port);
                    this.group = new NioEventLoopGroup();
                })
                .thenCompose(v -> {
                    this.future = bootstrap
                            .group(group)
                            .bind();
                    return FutureUtils.nettyToJava(future);
                });
    }

    @Override
    public CompletableFuture<Void> closeAsync() throws NetworkException {

        if(!hasStarted.get())
            throw new NetworkException("DatagramServerService hasn't been started");
        if(hasStopped.getAndSet(true))
            return SneakyThrow.callUnchecked(stopFutureFuture::get);

        final CompletableFuture<Void> stopFuture = FutureUtils.nettyToJava(group.shutdownGracefully())
                .thenApply(v -> null);
        stopFutureFuture.complete(stopFuture);
        return stopFuture;
    }

    @Override
    public void close() throws IOException {
        try {
            closeAsync().get(shutdownTimeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException t) {
            LOGGER.warn("Closing took too long");
        } catch (InterruptedException | ExecutionException t) {
            throw new IOException("Couldn't close " + this, t);
        }
    }

    private CompletableFuture<Void> sendPacket(DatagramPacketContext ctx) {
        return FutureUtils.nettyToJava(future.channel().writeAndFlush(ctx));
    }
}
