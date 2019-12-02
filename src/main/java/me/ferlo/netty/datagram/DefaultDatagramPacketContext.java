package me.ferlo.netty.datagram;

import me.ferlo.netty.core.Packet;

import java.net.SocketAddress;

public class DefaultDatagramPacketContext implements DatagramPacketContext {

    private Packet packet;
    private SocketAddress recipient;

    public DefaultDatagramPacketContext() {
        this(null, null);
    }

    public DefaultDatagramPacketContext(Packet packet) {
        this(packet, null);
    }

    public DefaultDatagramPacketContext(SocketAddress recipient) {
        this(null, recipient);
    }

    public DefaultDatagramPacketContext(Packet packet,
                                        SocketAddress recipient) {

        this.packet = packet;
        this.recipient = recipient;
    }

    @Override
    public Packet getPacket() {
        return packet;
    }

    @Override
    public SocketAddress getRecipient() {
        return recipient;
    }
}
