package me.ferlo.netty.core.spacket;

import me.ferlo.netty.CustomByteBuf;

public class PongPacket implements SPacket {

    public PongPacket() {
    }

    @Override
    public String toString() {
        return "PongPacket{}";
    }

    @Override
    public void writePacket(CustomByteBuf buf) {
    }

    public static final SPacketParser PARSER = (CustomByteBuf buf) -> new PongPacket();
}
