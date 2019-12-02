package me.ferlo.netty.multi;

import io.netty.channel.ChannelHandlerContext;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.datagram.DatagramPacketContext;
import me.ferlo.netty.stream.StreamPacketContext;

import java.net.SocketAddress;
import java.util.concurrent.Future;

public interface MultiServerPacketContext extends StreamPacketContext, DatagramPacketContext {

    MultiServerComponent getService();

    ChannelHandlerContext getChannelHandlerContext();

    SocketAddress getSender();

    SocketAddress getRecipient();

    Future<Void> reply(Packet packet);

    MultiServerPacketContext makeReplyContext(Packet packet);
}
