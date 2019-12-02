package me.ferlo.netty.multicast;

import me.ferlo.utils.FutureUtils;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.fail;

public class MulticastDiscoveringTest {

    // Should be a multicast local address
    public static final InetSocketAddress MULTICAST_LOCAL_ADDRESS = new InetSocketAddress("239.255.43.42", 44337);

    @Test
    void testDiscovering() throws ExecutionException, InterruptedException {

        final String id = "Hello world";
        final InetSocketAddress multicastAddress = MULTICAST_LOCAL_ADDRESS;

        final int streamPort = 10;
        final int datagramPort = 20;

        final int testTime = 100;
        final int packetDelay = 10;
        final int expectedPackets = Math.max(1, (int) Math.ceil((float) testTime / packetDelay) - 1);

        final MulticastServerPinger pinger = new MulticastServerPinger(id, packetDelay, multicastAddress, streamPort, datagramPort);
        final MulticastServerDiscoverer discoverer = new MulticastServerDiscoverer(id, multicastAddress);

        final CountDownLatch countDownLatch = new CountDownLatch(expectedPackets);
        discoverer.addListener(server -> {
            if(server.getStreamPort() != streamPort)
                return;
            if(server.getDatagramPort() != datagramPort)
                return;
            countDownLatch.countDown();
        });

        FutureUtils.getUninterruptibly(discoverer.startAsync());
        FutureUtils.getUninterruptibly(pinger.startAsync());

        if(!countDownLatch.await(testTime, TimeUnit.MILLISECONDS))
            fail(String.format(
                    "Not enough packets have been received (expected: %s, received: %s)",
                    expectedPackets,
                    expectedPackets - countDownLatch.getCount()
            ));

        pinger.closeAsync();
        discoverer.closeAsync();
    }

    @Test
    void testNotDiscovering() throws ExecutionException, InterruptedException {

        final String id0 = "Hello world";
        final String id1 = "Yo bro";

        final InetSocketAddress multicastAddress = MULTICAST_LOCAL_ADDRESS;

        final int streamPort = 10;
        final int datagramPort = 20;

        final int testTime = 100;
        final int packetDelay = 10;

        final MulticastServerPinger pinger = new MulticastServerPinger(id0, packetDelay, multicastAddress, streamPort, datagramPort);
        final MulticastServerDiscoverer discoverer = new MulticastServerDiscoverer(id1, multicastAddress);

        final CompletableFuture<DiscoveredServer> serverFuture = new CompletableFuture<>();
        discoverer.addListener(server -> {
            if(server.getStreamPort() != streamPort)
                return;
            if(server.getDatagramPort() != datagramPort)
                return;
            serverFuture.complete(server);
        });

        FutureUtils.getUninterruptibly(discoverer.startAsync());
        FutureUtils.getUninterruptibly(pinger.startAsync());

        try {
            fail(String.format(
                    "Discovered server when none should have been found (server: %s)",
                    serverFuture.get(testTime, TimeUnit.MILLISECONDS)
            ));
        } catch (TimeoutException e) {
            // Ignored
        }

        pinger.closeAsync();
        discoverer.closeAsync();
    }
}