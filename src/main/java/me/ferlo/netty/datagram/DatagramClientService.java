package me.ferlo.netty.datagram;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import me.ferlo.netty.NetService;
import me.ferlo.netty.NetworkException;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.PacketParser;
import me.ferlo.netty.core.cpacket.CPacket;
import me.ferlo.utils.FutureUtils;
import me.ferlo.utils.SneakyThrow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class DatagramClientService implements NetService {

    // Constants

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramClientService.class);
    private static final int DEFAULT_SHUTDOWN_TIMEOUT = 5000;

    // Attributes

    private final AtomicBoolean hasStarted = new AtomicBoolean(false);
    private final AtomicBoolean hasStopped = new AtomicBoolean(false);
    private final CompletableFuture<CompletableFuture<Void>> stopFutureFuture = new CompletableFuture<>();
    private final int shutdownTimeout;

    private final Bootstrap bootstrap;
    private final InetSocketAddress remoteAddress;

    private EventLoopGroup group;
    private ChannelFuture channelFuture;

    public DatagramClientService(InetSocketAddress remoteAddress,
                                 Function<Class<? extends Packet>, Byte> packetToId,
                                 Function<Byte, PacketParser> idToParser,
                                 ChannelInboundHandler handler) {
        this(remoteAddress, DEFAULT_SHUTDOWN_TIMEOUT, packetToId, idToParser, handler);
    }

    public DatagramClientService(InetSocketAddress remoteAddress,
                                 int shutdownTimeout,
                                 Function<Class<? extends Packet>, Byte> packetToId,
                                 Function<Byte, PacketParser> idToParser,
                                 ChannelInboundHandler handler) {

        this.remoteAddress = remoteAddress;
        this.shutdownTimeout = shutdownTimeout;

        this.group = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap()
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_RCVBUF, 16384)
                .option(ChannelOption.SO_SNDBUF, 16384)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) {
                        final int sendBuffSize = ch.config().getSendBufferSize();
                        final int packetSize = DatagramPacketEncoder.SAFE_MAX_PACKET_SIZE;

                        final SlidingWindowReliabilityHandler slidingWindow = new SlidingWindowReliabilityHandler(
                                0,
                                (int) Math.ceil(sendBuffSize / 4D / packetSize));
                        ch.pipeline().addLast(
                                // ↑
                                slidingWindow.getOutboundHandler(),
                                new DatagramPacketEncoder(packetToId),
                                // ↓
                                slidingWindow.getInboundHandler(),
                                new DatagramPacketDecoder(idToParser),
                                handler
                        );
                    }
                })
                .remoteAddress(remoteAddress);
    }

    @Override
    public CompletableFuture<Void> startAsync() throws NetworkException {

        if(hasStarted.getAndSet(true))
            throw new NetworkException("DatagramClientService cannot be started twice, make a new one");

        return CompletableFuture
                .runAsync(() -> {
                    LOGGER.info("Connecting DatagramClientService to {}...", remoteAddress);
                    this.group = new NioEventLoopGroup();
                })
                .thenCompose(v -> {
                    this.channelFuture = bootstrap
                            .group(group)
                            .connect();
                    return FutureUtils.nettyToJava(channelFuture);
                });
    }

    @Override
    public CompletableFuture<Void> closeAsync() throws NetworkException {

        if(!hasStarted.get())
            throw new NetworkException("DatagramClientService hasn't been started");
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

    public CompletableFuture<Void> sendPacket(CPacket packet) {
        return sendPacket(packet, false);
    }

    public CompletableFuture<Void> sendPacket(CPacket packet, boolean reliable) {
        final DefaultDatagramPacketContext packetCtx = DefaultDatagramPacketContext.newInstance(
                packet,
                channelFuture.channel().localAddress(),
                channelFuture.channel().remoteAddress(),
                reliable,
                (ctx) -> sendPacket((CPacket) ctx.getPacket(), ctx.isReliable()));

        final ChannelFuture future = channelFuture.channel().writeAndFlush(packetCtx);
        future.addListener(f -> packetCtx.recycle());
        return FutureUtils.nettyToJava(future);
    }
}
