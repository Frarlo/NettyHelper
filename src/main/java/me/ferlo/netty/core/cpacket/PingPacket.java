package me.ferlo.netty.core.cpacket;

import me.ferlo.netty.CustomByteBuf;

public class PingPacket implements CPacket {

    public PingPacket() {
    }

    @Override
    public String toString() {
        return "PingPacket{}";
    }

    @Override
    public void writePacket(CustomByteBuf buf) {
    }

    public static final CPacketParser PARSER = (CustomByteBuf buf) -> new PingPacket();
}
