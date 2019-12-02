package me.ferlo.netty.stream;

import me.ferlo.netty.core.Packet;

public class DefaultStreamPacketContext implements StreamPacketContext {

    private Packet packet;

    public DefaultStreamPacketContext(Packet packet) {
        this.packet = packet;
    }

    public void setPacket(Packet packet) {
        this.packet = packet;
    }

    @Override
    public Packet getPacket() {
        return packet;
    }

    @Override
    public String toString() {
        return "DefaultStreamPacketContext{" +
                "packet=" + packet +
                '}';
    }
}
