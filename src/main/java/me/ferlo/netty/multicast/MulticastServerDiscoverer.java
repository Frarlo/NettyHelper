package me.ferlo.netty.multicast;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import me.ferlo.netty.CustomByteBuf;
import me.ferlo.utils.MulticastUtils;
import me.ferlo.utils.SneakyThrow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MulticastServerDiscoverer implements Closeable {

    // Constants

    private static final Logger LOGGER = LoggerFactory.getLogger(MulticastServerDiscoverer.class);

    private static final int DEFAULT_SHUTDOWN_TIMEOUT = 5000;

    // Attributes

    private final AtomicBoolean hasStarted = new AtomicBoolean(false);
    private final AtomicBoolean hasStopped = new AtomicBoolean(false);

    private final String id;
    private final int shutdownTimeout;

    private final InetSocketAddress mcastSocketAddr;

    private final Set<Consumer<DiscoveredServer>> listeners;
    private final List<MulticastDiscoverer> discoverers;

    public MulticastServerDiscoverer(String id, InetSocketAddress mcastSocketAddr) {
        this(id, DEFAULT_SHUTDOWN_TIMEOUT, mcastSocketAddr);
    }

    public MulticastServerDiscoverer(String id,
                                     int shutdownTimeout,
                                     InetSocketAddress mcastSocketAddr) {
        this.id = id;
        this.shutdownTimeout = shutdownTimeout;

        this.mcastSocketAddr = mcastSocketAddr;

        this.listeners = ConcurrentHashMap.newKeySet();
        this.discoverers = new ArrayList<>();

        // Bind on all the IPv4 NICs that support multicast

        SneakyThrow.callUnchecked(MulticastUtils::getIPv4NetworkInterfaces)
                .forEach(networkInterface -> discoverers.add(new MulticastDiscoverer(networkInterface)));
    }

    public void start() throws Exception {
        if(hasStarted.getAndSet(true))
            throw new Exception("MulticastServerDiscoverer cannot be started twice, make a new one");

        for(MulticastDiscoverer discoverer : discoverers)
            discoverer.open();
    }

    @Override
    public void close() throws IOException {
        if(!hasStarted.get())
            throw new IOException("MulticastServerDiscoverer hasn't been started");
        if(hasStopped.getAndSet(true))
            return;

        for(MulticastDiscoverer discoverer : discoverers)
            discoverer.close();
    }

    public void addListener(Consumer<DiscoveredServer> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<DiscoveredServer> listener) {
        listeners.remove(listener);
    }

    private class MulticastDiscoverer implements Closeable {

        private final NetworkInterface interf;
        private final Bootstrap bootstrap;

        private EventLoopGroup group;
        private ChannelFuture channelFuture;

        private MulticastDiscoverer(NetworkInterface interf) {
            this.interf = interf;

            this.bootstrap = new Bootstrap()
                    .channelFactory(()-> new NioDatagramChannel(InternetProtocolFamily.IPv4))
                    .option(ChannelOption.IP_MULTICAST_IF, interf)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.IP_MULTICAST_LOOP_DISABLED, true)
                    .handler(new MulticastDiscovererHandler())
                    .localAddress(mcastSocketAddr.getPort());
        }

        public void open() throws Exception {

            LOGGER.trace("Binding MulticastDiscoverer for group {} on NIC {}",
                    mcastSocketAddr, interf);

            try {
                this.group = new NioEventLoopGroup();
                this.channelFuture = bootstrap
                        .group(group)
                        .bind()
                        .sync();
                ((DatagramChannel)channelFuture.channel()).joinGroup(mcastSocketAddr, interf).sync();

            } catch (Throwable t) {
                throw new Exception("Couldn't bind " + this, t);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                group.shutdownGracefully().await(shutdownTimeout, TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                throw new IOException("Couldn't close " + this, t);
            }
        }

        @Override
        public String toString() {
            return "MulticastDiscoverer{" +
                    "hasStarted=" + hasStarted +
                    ", hasStopped=" + hasStopped +
                    ", mcastGroup=" + mcastSocketAddr +
                    ", interf=" + interf +
                    '}';
        }

        private class MulticastDiscovererHandler extends SimpleChannelInboundHandler<DatagramPacket> {

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                LOGGER.trace(
                        "Received datagram packet {} (NIC: {}, Multicast Group: {})",
                        msg, interf, mcastSocketAddr);

                final CustomByteBuf buff = CustomByteBuf.get(msg.content());

                if(buff.readString(6).equals(id)) {

                    final InetAddress addr = msg.sender().getAddress();
                    final int newTcpPort = buff.readInt();
                    final int newUdpPort = buff.readInt();

                    LOGGER.trace(
                            "Found valid server {} using tcp port {} and udp port {}" +
                                    " (NIC: {}, Multicast Group: {})",
                            addr, newTcpPort, newUdpPort, interf, mcastSocketAddr);

                    final Result res = new Result(interf, addr, newTcpPort, newUdpPort);
                    listeners.forEach(c -> c.accept(res));
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                cause.printStackTrace();

                discoverers.remove(MulticastDiscoverer.this);
                ctx.close();
            }
        }
    }

    static class Result implements DiscoveredServer {

        private final NetworkInterface networkInterface;
        private final InetAddress address;
        private final int streamPort;
        private final int datagramPort;

        Result(NetworkInterface networkInterface,
               InetAddress address,
               int streamPort,
               int datagramPort) {

            this.networkInterface = networkInterface;
            this.address = address;
            this.streamPort = streamPort;
            this.datagramPort = datagramPort;
        }

        @Override
        public NetworkInterface getNetworkInterface() {
            return networkInterface;
        }

        @Override
        public InetAddress getAddress() {
            return address;
        }

        @Override
        public int getStreamPort() {
            return streamPort;
        }

        @Override
        public int getDatagramPort() {
            return datagramPort;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Result)) return false;
            Result result = (Result) o;
            return getStreamPort() == result.getStreamPort() &&
                    getDatagramPort() == result.getDatagramPort() &&
                    getNetworkInterface().equals(result.getNetworkInterface()) &&
                    getAddress().equals(result.getAddress());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getNetworkInterface(), getAddress(), getStreamPort(), getDatagramPort());
        }

        @Override
        public String toString() {
            return "Result{" +
                    "networkInterface=" + networkInterface +
                    ", address=" + address +
                    ", streamPort=" + streamPort +
                    ", datagramPort=" + datagramPort +
                    '}';
        }
    }
}
