package me.ferlo.netty.multicast;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import me.ferlo.netty.CustomByteBuf;
import me.ferlo.netty.NetService;
import me.ferlo.netty.NetworkException;
import me.ferlo.utils.FutureUtils;
import me.ferlo.utils.SneakyThrow;
import me.ferlo.utils.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MulticastServerPinger implements NetService {

    // Constants

    private static final Logger LOGGER = LoggerFactory.getLogger(MulticastServerPinger.class);

    private static final int DEFAULT_PACKET_DELAY = 1500;

    private static final int DEFAULT_SHUTDOWN_TIMEOUT = 5000;
    private static ScheduledExecutorService DEFAULT_EXECUTOR_SERVICE;

    private static ScheduledExecutorService getDefaultExecutorService() {
        if(DEFAULT_EXECUTOR_SERVICE == null)
            DEFAULT_EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor(
                    new ThreadFactoryBuilder()
                            .setNameFormat(c -> "LanServerPinger")
                            .setDaemon(true)
                            .build());
        return DEFAULT_EXECUTOR_SERVICE;
    }

    // Attributes

    private final AtomicBoolean hasStarted = new AtomicBoolean(false);
    private final AtomicBoolean hasStopped = new AtomicBoolean(false);
    private final CompletableFuture<CompletableFuture<Void>> stopFutureFuture = new CompletableFuture<>();
    private final int shutdownTimeout;

    private final String id;
    private final int packetDelay;

    private final InetSocketAddress address;
    private final Bootstrap bootstrap;

    private final InetSocketAddress multicastAddress;
    private final int tcpPortToSend;
    private final int udpPortToSend;

    private final ScheduledExecutorService scheduler;

    private EventLoopGroup group;
    private ChannelFuture channelFuture;
    private Future<?> pingFuture;

    public MulticastServerPinger(String id,
                                 InetSocketAddress multicastAddress,
                                 int tcpPortToSend,
                                 int udpPortToSend) {
        this(id, DEFAULT_PACKET_DELAY, multicastAddress, tcpPortToSend, udpPortToSend);
    }

    public MulticastServerPinger(String id,
                                 int packetDelay,
                                 InetSocketAddress multicastAddress,
                                 int tcpPortToSend,
                                 int udpPortToSend) {
        this(id, packetDelay, multicastAddress, tcpPortToSend, udpPortToSend, getDefaultExecutorService());
    }

    public MulticastServerPinger(String id,
                                 InetSocketAddress multicastAddress,
                                 int tcpPortToSend,
                                 int udpPortToSend,
                                 ScheduledExecutorService scheduler) {
        this(id, DEFAULT_PACKET_DELAY, multicastAddress, tcpPortToSend, udpPortToSend, scheduler);
    }

    public MulticastServerPinger(String id,
                                 InetSocketAddress multicastAddress,
                                 int tcpPortToSend,
                                 int udpPortToSend,
                                 ScheduledExecutorService scheduler,
                                 int shutdownTimeout) {
        this(id, DEFAULT_PACKET_DELAY, multicastAddress, tcpPortToSend, udpPortToSend, scheduler, shutdownTimeout);
    }

    public MulticastServerPinger(String id,
                                 int packetDelay,
                                 InetSocketAddress multicastAddress,
                                 int tcpPortToSend,
                                 int udpPortToSend,
                                 ScheduledExecutorService scheduler) {
        this(id, packetDelay, multicastAddress, tcpPortToSend, udpPortToSend, scheduler, DEFAULT_SHUTDOWN_TIMEOUT);
    }

    public MulticastServerPinger(String id,
                                 int packetDelay,
                                 InetSocketAddress multicastAddress,
                                 int tcpPortToSend,
                                 int udpPortToSend,
                                 ScheduledExecutorService scheduler,
                                 int shutdownTimeout) {

        this.id = id;
        this.packetDelay = packetDelay;

        this.scheduler = scheduler;
        this.shutdownTimeout = shutdownTimeout;

        this.tcpPortToSend = tcpPortToSend;
        this.udpPortToSend = udpPortToSend;
        this.multicastAddress = multicastAddress;
        this.address = new InetSocketAddress(0);

        this.group = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap()
                .channelFactory(() -> new NioDatagramChannel(InternetProtocolFamily.IPv4))
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.IP_MULTICAST_TTL, 9)
                .handler(new SimpleChannelInboundHandler<Object>() {
                    @Override
                    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
                    }
                })
                .localAddress(address);
    }

    @Override
    public CompletableFuture<Void> startAsync() throws NetworkException {

        if(hasStarted.getAndSet(true))
            throw new NetworkException("LanServerPinger cannot be started twice, make a new one");

        return CompletableFuture
                .runAsync(() -> {
                    LOGGER.info("Binding LanServerPinger on {} to ping multicast address {}...", address, multicastAddress);
                    this.group = new NioEventLoopGroup();
                })
                .thenCompose(v -> {
                    this.channelFuture = bootstrap
                            .group(group)
                            .bind();
                    return FutureUtils.nettyToJava(channelFuture);
                })
                .thenRun(() -> {
                    // Schedule ping future

                    final CustomByteBuf payload = CustomByteBuf.get(channelFuture.channel().alloc().buffer());
                    payload.writeString(id);
                    payload.writeInt(tcpPortToSend);
                    payload.writeInt(udpPortToSend);

                    this.pingFuture = scheduler.scheduleAtFixedRate(() -> {
                        if(!hasStarted.get())
                            return;

                        final DatagramPacket packet = new DatagramPacket(payload.retain(), multicastAddress);
                        LOGGER.trace("Sending datagram packet {}", packet);
                        channelFuture.channel().writeAndFlush(packet);

                    }, 0, packetDelay, TimeUnit.MILLISECONDS);
                });
    }

    @Override
    public void close() throws IOException, NetworkException {
        try {
            closeAsync().get(shutdownTimeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException t) {
            LOGGER.warn("Closing took too long");
        } catch (InterruptedException | ExecutionException t) {
            throw new IOException("Couldn't close " + this, t);
        }
    }

    @Override
    public CompletableFuture<Void> closeAsync() throws NetworkException {

        if(!hasStarted.get())
            throw new NetworkException("LanServerPinger hasn't been started");
        if(hasStopped.getAndSet(true))
            return SneakyThrow.callUnchecked(stopFutureFuture::get);

        final CompletableFuture<Void> stopFuture = CompletableFuture
                .runAsync(() -> {
                    if(pingFuture != null)
                        pingFuture.cancel(false);
                    pingFuture = null;
                })
                .thenCompose(v -> FutureUtils.nettyToJava(group.shutdownGracefully()))
                .thenApply(v -> null);
        stopFutureFuture.complete(stopFuture);
        return stopFuture;
    }

    @Override
    public String toString() {
        return "LanServerPinger{" +
                "hasStarted=" + hasStarted +
                ", hasStopped=" + hasStopped +
                ", stopFutureFuture=" + stopFutureFuture +
                ", bootstrap=" + bootstrap +
                ", tcpPortToSend=" + tcpPortToSend +
                ", udpPortToSend=" + udpPortToSend +
                ", multicastAddress=" + multicastAddress +
                '}';
    }
}
