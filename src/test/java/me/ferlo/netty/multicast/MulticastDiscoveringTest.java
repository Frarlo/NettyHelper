package me.ferlo.netty.multicast;

import me.ferlo.utils.FutureUtils;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.fail;

class MulticastDiscoveringTest {

    @Test
    void testDiscovering() throws ExecutionException, InterruptedException {

        final String id = "Hello world";
        final InetSocketAddress multicastAddress = new InetSocketAddress("239.255.43.42", 44337);

        final int streamPort = 10;
        final int datagramPort = 20;

        final int testTime = 100;
        final int packetDelay = 10;
        final int expectedPackets = Math.max(1, (int) Math.ceil((float) testTime / packetDelay) - 1);

        final LanServerPinger pinger = new LanServerPinger(id, packetDelay, multicastAddress, streamPort, datagramPort);
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

        final InetSocketAddress multicastAddress = new InetSocketAddress("239.255.43.42", 44337);

        final int streamPort = 10;
        final int datagramPort = 20;

        final int testTime = 100;
        final int packetDelay = 10;

        final LanServerPinger pinger = new LanServerPinger(id0, packetDelay, multicastAddress, streamPort, datagramPort);
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