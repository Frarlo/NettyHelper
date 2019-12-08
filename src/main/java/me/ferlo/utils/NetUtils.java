package me.ferlo.utils;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;

public final class NetUtils {

    private NetUtils() {}

    public static int getUnboundTcpPort() throws IOException {
        try(ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public static int getUnboundUdpPort() throws IOException {
        try(DatagramSocket socket = new DatagramSocket()) {
            return socket.getLocalPort();
        }
    }
}
