package me.ferlo.netty.core.cpacket;

import me.ferlo.netty.CustomByteBuf;

public class UdpConnectionPacket implements CPacket {

    public UdpConnectionPacket() {
    }

    @Override
    public void writePacket(CustomByteBuf buf) {
    }

    @Override
    public String toString() {
        return "UdpConnectionPacket{}";
    }

    public static final CPacketParser PARSER = (CustomByteBuf buf) -> new UdpConnectionPacket();
}
