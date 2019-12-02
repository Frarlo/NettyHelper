package me.ferlo.netty.multicast;

import java.net.InetAddress;
import java.net.NetworkInterface;

public interface DiscoveredServer {

    NetworkInterface getNetworkInterface();

    InetAddress getAddress();

    default boolean hasStreamPort() {
        return getStreamPort() != -1;
    }

    int getStreamPort();

    default boolean hasDatagramPort() {
        return getDatagramPort() != -1;
    }

    int getDatagramPort();
}
