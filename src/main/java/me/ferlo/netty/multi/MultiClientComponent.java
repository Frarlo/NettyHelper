package me.ferlo.netty.multi;

import me.ferlo.netty.core.cpacket.CPacket;

import java.net.SocketAddress;
import java.util.concurrent.Future;

public interface MultiClientComponent {

    Future<Void> sendPacket(CPacket packet);

    SocketAddress getLocalAddress();
}
