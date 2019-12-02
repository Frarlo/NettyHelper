package me.ferlo.netty;

import me.ferlo.netty.multicast.LanServerPinger;
import me.ferlo.netty.multicast.MulticastServerDiscoverer;
import me.ferlo.utils.FutureUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class NetServiceTest {

    @ParameterizedTest(name = "[{index}] => testStarting '' {0} ''")
    @MethodSource("provideNetServices")
    void testStarting(@SuppressWarnings("unused") String name, NetService netService) {
        // Subsequent calls to start should explode
        assertThrows(NetworkException.class, () -> {
            netService.startAsync();
            netService.startAsync();
        });
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

    private static Stream<Arguments> provideNetServices() {
        final InetSocketAddress multicastAddress = new InetSocketAddress("239.255.43.42", 44337);

        return Stream.of(
                Arguments.of(LanServerPinger.class.getSimpleName(),
                        new LanServerPinger("", multicastAddress, 10, 10)),
                Arguments.of(MulticastServerDiscoverer.class.getSimpleName(),
                        new MulticastServerDiscoverer("", multicastAddress))
        );
    }
}
