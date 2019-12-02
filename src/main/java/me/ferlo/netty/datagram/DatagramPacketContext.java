package me.ferlo.netty.datagram;

import me.ferlo.netty.core.Packet;

import java.net.SocketAddress;

public interface DatagramPacketContext {

    Packet getPacket();

    SocketAddress getRecipient();
}
