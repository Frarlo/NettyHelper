package me.ferlo.netty.datagram;

import me.ferlo.netty.core.Packet;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

public interface DatagramPacketContext {

    Packet getPacket();

    boolean isReliable();

    SocketAddress getSender();

    SocketAddress getRecipient();

    default CompletableFuture<Void> reply(Packet packet) {
        return reply(packet, false);
    }

    CompletableFuture<Void> reply(Packet packet, boolean reliable);
}
