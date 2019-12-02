package me.ferlo.netty.core;

import me.ferlo.netty.CustomByteBuf;

public interface Packet {

    void writePacket(CustomByteBuf buf) throws Exception;
}
