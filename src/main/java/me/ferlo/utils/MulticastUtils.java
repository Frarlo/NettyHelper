package me.ferlo.utils;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;

public class MulticastUtils {

    private MulticastUtils() {}

    public static NetworkInterface getAnyAddr() throws IOException {
        try(MulticastSocket socket = new MulticastSocket()) {
            return socket.getNetworkInterface();
        }
    }

    public static Collection<NetworkInterface> getIPv4NetworkInterfaces() throws SocketException {
        final Collection<NetworkInterface> validInterfaces = new ArrayList<>();
        final Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

        while(en.hasMoreElements()) {
            final NetworkInterface interf = en.nextElement();

            if(interf.supportsMulticast() &&
                    !interf.isVirtual() &&
                    !interf.isLoopback()) {
                boolean isIPV4Enabled = false;
                final Enumeration<InetAddress> addrs = interf.getInetAddresses();

                while(!isIPV4Enabled && addrs.hasMoreElements())
                    isIPV4Enabled = (addrs.nextElement() instanceof Inet4Address);

                if(isIPV4Enabled)
                    validInterfaces.add(interf);
            }
        }

        return validInterfaces;
    }
}
