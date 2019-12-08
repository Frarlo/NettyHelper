package me.ferlo.netty.multi;

import io.netty.channel.ChannelHandlerContext;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.datagram.DatagramPacketContext;
import me.ferlo.netty.stream.StreamPacketContext;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

public interface MultiPacketContext extends StreamPacketContext, DatagramPacketContext {

    MultiServerComponent getService();

    ChannelHandlerContext getChannelHandlerContext();

    SocketAddress getSender();

    SocketAddress getRecipient();

    CompletableFuture<Void> reply(Packet packet);

    @Override
    @Deprecated
    default CompletableFuture<Void> reply(Packet packet, boolean reliable) {
        return reply(packet);
    }
}
