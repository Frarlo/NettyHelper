package me.ferlo.netty.multi;

import io.netty.channel.ChannelHandlerContext;
import me.ferlo.netty.core.Packet;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DefaultMultiPacketContext implements MultiPacketContext {

    private MultiServerComponent service;
    private Packet packet;
    private ChannelHandlerContext channelHandlerContext;
    private SocketAddress sender;
    private SocketAddress recipient;
    private Function<? super MultiPacketContext, CompletableFuture<Void>> replyConsumer;

    public DefaultMultiPacketContext(MultiServerComponent netComponent,
                                     Packet packet,
                                     ChannelHandlerContext channelHandlerContext,
                                     SocketAddress sender,
                                     SocketAddress recipient,
                                     Function<? super MultiPacketContext, CompletableFuture<Void>> replyConsumer) {

        this.service = netComponent;
        this.packet = packet;
        this.channelHandlerContext = channelHandlerContext;
        this.sender = sender;
        this.recipient = recipient;
        this.replyConsumer = replyConsumer;
    }

    @Override
    public MultiServerComponent getService() {
        return service;
    }

    @Override
    public Packet getPacket() {
        return packet;
    }

    @Override
    public boolean isReliable() {
        // Reliability is automatically guaranteed by the service in use
        return false;
    }

    @Override
    public ChannelHandlerContext getChannelHandlerContext() {
        return channelHandlerContext;
    }

    @Override
    public SocketAddress getSender() {
        return sender;
    }

    @Override
    public SocketAddress getRecipient() {
        return recipient;
    }

    @Override
    public CompletableFuture<Void> reply(Packet packet) {
        return replyConsumer.apply(new DefaultMultiPacketContext(
                service,
                packet,
                channelHandlerContext,
                recipient,
                sender,
                replyConsumer
        ));
    }

    @Override
    public String toString() {
        return "DefaultMultiServerPacketContext{" +
                "service=" + service +
                ", packet=" + packet +
                ", channelHandlerContext=" + channelHandlerContext +
                ", sender=" + sender +
                ", recipient=" + recipient +
                ", replyConsumer=" + replyConsumer +
                '}';
    }
}
