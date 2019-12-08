package me.ferlo.netty.multi;

import me.ferlo.netty.core.spacket.SPacket;

import java.util.concurrent.Future;

public interface MultiServerComponent {

    Future<Void> sendPacket(MultiPacketContext dst);

    Future<Void> sendPacketToAll(SPacket packet);
}
