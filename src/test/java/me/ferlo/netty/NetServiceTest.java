package me.ferlo.netty;

import me.ferlo.netty.datagram.DatagramClientService;
import me.ferlo.netty.datagram.DatagramServerService;
import me.ferlo.netty.multicast.MulticastDiscoveringTest;
import me.ferlo.netty.multicast.MulticastServerDiscoverer;
import me.ferlo.netty.multicast.MulticastServerPinger;
import me.ferlo.utils.FutureUtils;
import me.ferlo.utils.NetUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class NetServiceTest {

    @ParameterizedTest(name = "[{index}] => testStarting '' {0} ''")
    @MethodSource("provideNetServices")
    void testStarting(@SuppressWarnings("unused") String name, NetService netService) throws ExecutionException {
        final CompletableFuture<Void> startPromise = netService.startAsync();
        // Subsequent calls to start should explode
        assertThrows(NetworkException.class, netService::startAsync);

        FutureUtils.getUninterruptibly(startPromise);
        netService.closeAsync();
    }

    @ParameterizedTest(name = "[{index}] => testClosing '' {0} ''")
    @MethodSource("provideNetServices")
    void testClosing(@SuppressWarnings("unused") String name, NetService netService) throws ExecutionException {
        FutureUtils.getUninterruptibly(netService.startAsync());
        // Subsequent calls to close should have no effect
        assertDoesNotThrow(() -> {
            netService.close();
            netService.close();
        });
    }

    @ParameterizedTest(name = "[{index}] => testClosingAsync '' {0} ''")
    @MethodSource("provideNetServices")
    void testClosingAsync(@SuppressWarnings("unused") String name, NetService netService) throws ExecutionException {
        // Subsequent calls to close async should return the same completion promise
        FutureUtils.getUninterruptibly(netService.startAsync());
        assertSame(netService.closeAsync(), netService.closeAsync());
    }

    private static Stream<Arguments> provideNetServices() throws IOException {
        final int shutdownTimeout = Integer.MAX_VALUE;
        final InetSocketAddress multicastAddress = MulticastDiscoveringTest.MULTICAST_LOCAL_ADDRESS;
        final InetSocketAddress datagramAddress = new InetSocketAddress("localhost", NetUtils.getUnboundUdpPort());

        return Stream.of(
                Arguments.of(MulticastServerPinger.class.getSimpleName(),
                        new MulticastServerPinger("Test",
                                shutdownTimeout,
                                multicastAddress,
                                NetUtils.getUnboundTcpPort(), NetUtils.getUnboundUdpPort())),
                Arguments.of(MulticastServerDiscoverer.class.getSimpleName(),
                        new MulticastServerDiscoverer("Test",
                                shutdownTimeout,
                                multicastAddress)),
                Arguments.of(DatagramClientService.class.getSimpleName(),
                        new DatagramClientService(datagramAddress,
                                shutdownTimeout,
                                c -> (byte) -1,
                                b -> null,
                                null)),
                Arguments.of(DatagramServerService.class.getSimpleName(),
                        new DatagramServerService(datagramAddress.getPort(),
                                shutdownTimeout,
                                c -> (byte) -1,
                                b -> null,
                                null))
        );
    }
}
