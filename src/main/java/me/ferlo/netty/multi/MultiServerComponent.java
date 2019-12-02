package me.ferlo.netty.multi;

import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.spacket.SPacket;

import java.util.concurrent.Future;

public interface MultiServerComponent {

    Future<Void> sendPacket(MultiServerPacketContext dst);

    default Future<Void> reply(Packet packet, MultiServerPacketContext rpc) {
        return sendPacket(rpc.makeReplyContext(packet));
    }

    Future<Void> sendPacketToAll(SPacket packet);
}
