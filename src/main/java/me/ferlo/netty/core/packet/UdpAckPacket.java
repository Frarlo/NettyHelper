package me.ferlo.netty.core.packet;

import me.ferlo.netty.CustomByteBuf;
import me.ferlo.netty.core.cpacket.CPacket;
import me.ferlo.netty.core.cpacket.CPacketParser;
import me.ferlo.netty.core.spacket.SPacket;
import me.ferlo.netty.core.spacket.SPacketParser;

public class UdpAckPacket implements SPacket, CPacket {

    public UdpAckPacket() {
    }

    @Override
    public void writePacket(CustomByteBuf buf) {
    }

    @Override
    public String toString() {
        return "AckPacket{}";
    }

    public static final CPacketParser CPARSER = (CustomByteBuf buf) -> new UdpAckPacket();
    public static final SPacketParser SPARSER = (CustomByteBuf buf) -> new UdpAckPacket();
}
