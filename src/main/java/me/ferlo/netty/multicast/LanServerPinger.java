package me.ferlo.netty.multicast;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import me.ferlo.netty.CustomByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LanServerPinger implements Closeable {

    // Constants

    private static final Logger LOGGER = LoggerFactory.getLogger(LanServerPinger.class);

    private static final int DEFAULT_SHUTDOWN_TIMEOUT = 5000;

    // Attributes

    private final AtomicBoolean hasStarted = new AtomicBoolean(false);
    private final AtomicBoolean hasStopped = new AtomicBoolean(false);

    private final int shutdownTimeout;

    private final String id;

    private final ScheduledExecutorService scheduler;

    private final InetSocketAddress address;
    private final Bootstrap bootstrap;

    private final int tcpPortToSend;
    private final int udpPortToSend;
    private final InetSocketAddress multicastAddress;

    private EventLoopGroup group;
    private ChannelFuture channelFuture;
    private Future<?> pingFuture;

    public LanServerPinger(String id,
                           InetSocketAddress multicastAddress,
                           int tcpPortToSend,
                           int udpPortToSend,
                           ScheduledExecutorService scheduler) {
        this(id, DEFAULT_SHUTDOWN_TIMEOUT, multicastAddress, tcpPortToSend, udpPortToSend, scheduler);
    }

    public LanServerPinger(String id,
                           int shutdownTimeout,
                           InetSocketAddress multicastAddress,
                           int tcpPortToSend,
                           int udpPortToSend,
                           ScheduledExecutorService scheduler) {

        this.id = id;

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

    public void start() throws Exception {

        if(hasStarted.getAndSet(true))
            throw new Exception("LanServerPinger cannot be started twice, make a new one");

        try {
            LOGGER.info("Binding LanServerPinger on {} to ping multicast address {}...", address, multicastAddress);

            this.group = new NioEventLoopGroup();
            this.channelFuture = bootstrap
                    .group(group)
                    .bind()
                    .sync();

            // Schedule ping future

            final CustomByteBuf payload = CustomByteBuf.get(Unpooled.buffer());
            payload.writeString(id);
            payload.writeInt(tcpPortToSend);
            payload.writeInt(udpPortToSend);

            this.pingFuture = scheduler.scheduleAtFixedRate(() -> {
                if(!hasStarted.get())
                    return;

                final DatagramPacket packet = new DatagramPacket(payload.retain(), multicastAddress);
                LOGGER.trace("Sending datagram packet {}", packet);
                channelFuture.channel().writeAndFlush(packet);

            }, 0, 1500, TimeUnit.MILLISECONDS);

        } catch (Throwable t) {
            hasStarted.set(false);
            throw new Exception("Couldn't open " + this, t);
        }
    }

    @Override
    public void close() throws IOException {

        if(!hasStarted.get())
            throw new IOException("LanServerPinger hasn't been started");
        if(hasStopped.getAndSet(true))
            return;

        try {
            if(pingFuture != null)
                pingFuture.cancel(false);
            pingFuture = null;

            group.shutdownGracefully().await(shutdownTimeout, TimeUnit.MILLISECONDS);

        } catch (Throwable t) {
            throw new IOException("Couldn't close " + this, t);
        }
    }

    @Override
    public String toString() {
        return "LanServerPinger{" +
                "hasStarted=" + hasStarted +
                ", hasStopped=" + hasStopped +
                ", bootstrap=" + bootstrap +
                ", tcpPortToSend=" + tcpPortToSend +
                ", udpPortToSend=" + udpPortToSend +
                ", multicastAddress=" + multicastAddress +
                '}';
    }
}
