package me.ferlo.netty.core;

import me.ferlo.netty.CustomByteBuf;

public interface PacketParser {
    Packet parse(CustomByteBuf buf) throws Exception;
}
