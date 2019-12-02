package me.ferlo.netty.multi;

import io.netty.channel.ChannelHandlerContext;
import me.ferlo.netty.core.Packet;

import java.net.SocketAddress;
import java.util.concurrent.Future;

public class DefaultMultiServerPacketContext implements MultiServerPacketContext {

    private MultiServerComponent service;
    private Packet packet;
    private ChannelHandlerContext channelHandlerContext;
    private SocketAddress sender;
    private SocketAddress recipient;

    public DefaultMultiServerPacketContext(MultiServerComponent netComponent,
                                           Packet packet,
                                           ChannelHandlerContext channelHandlerContext,
                                           SocketAddress sender,
                                           SocketAddress recipient) {

        this.service = netComponent;
        this.packet = packet;
        this.channelHandlerContext = channelHandlerContext;
        this.sender = sender;
        this.recipient = recipient;
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

    public void setService(MultiServerComponent service) {
        this.service = service;
    }

    public void setPacket(Packet packet) {
        this.packet = packet;
    }

    public void setChannelHandlerContext(ChannelHandlerContext channelHandlerContext) {
        this.channelHandlerContext = channelHandlerContext;
    }

    public void setSender(SocketAddress sender) {
        this.sender = sender;
    }

    public void setRecipient(SocketAddress recipient) {
        this.recipient = recipient;
    }

    @Override
    public Future<Void> reply(Packet packet) {
        return service.reply(packet, makeReplyContext(packet));
    }

    @Override
    public MultiServerPacketContext makeReplyContext(Packet packet) {
        return new DefaultMultiServerPacketContext(
                service,
                packet,
                channelHandlerContext,
                recipient,
                sender
        );
    }

    @Override
    public String toString() {
        return "DefaultMultiPacketContext{" +
                "manager=" + service +
                ", packet=" + packet +
                ", channelHandlerContext=" + channelHandlerContext +
                ", sender=" + sender +
                ", recipient=" + recipient +
                '}';
    }
}
