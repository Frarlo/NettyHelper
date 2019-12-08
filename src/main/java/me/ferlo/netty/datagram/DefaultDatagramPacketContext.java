package me.ferlo.netty.datagram;

import io.netty.util.Recycler;
import me.ferlo.netty.core.Packet;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DefaultDatagramPacketContext implements DatagramPacketContext {

    private static final Recycler<DefaultDatagramPacketContext> RECYCLER = new Recycler<DefaultDatagramPacketContext>() {
        @Override
        protected DefaultDatagramPacketContext newObject(Handle<DefaultDatagramPacketContext> handle) {
            return new DefaultDatagramPacketContext(handle);
        }
    };

    private final Recycler.Handle<DefaultDatagramPacketContext> handle;

    private Packet packet;
    private SocketAddress sender;
    private SocketAddress recipient;
    private boolean isReliable;

    private Function<DatagramPacketContext, CompletableFuture<Void>> replyConsumer;

    private DefaultDatagramPacketContext(Recycler.Handle<DefaultDatagramPacketContext> handle) {
        this.handle = handle;
    }

    public static DefaultDatagramPacketContext newInstance(Packet packet,
                                                           SocketAddress sender,
                                                           SocketAddress recipient,
                                                           Function<DatagramPacketContext, CompletableFuture<Void>> replyConsumer) {
        return newInstance(packet, sender, recipient, false, replyConsumer);
    }

    public static DefaultDatagramPacketContext newInstance(Packet packet,
                                                           SocketAddress sender,
                                                           SocketAddress recipient,
                                                           boolean isReliable,
                                                           Function<DatagramPacketContext, CompletableFuture<Void>> replyConsumer) {
        final DefaultDatagramPacketContext inst = RECYCLER.get();
        inst.packet = packet;
        inst.sender = sender;
        inst.recipient = recipient;
        inst.isReliable = isReliable;
        inst.replyConsumer = replyConsumer;
        return inst;
    }

    public void recycle() {
        packet = null;
        sender = null;
        recipient = null;
        isReliable = false;
        replyConsumer = null;
        handle.recycle(this);
    }

    @Override
    public Packet getPacket() {
        return packet;
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
    public boolean isReliable() {
        return isReliable;
    }

    @Override
    public CompletableFuture<Void> reply(Packet packet, boolean reliable) {
        return replyConsumer.apply(newInstance(
                packet,
                recipient,
                sender,
                reliable,
                replyConsumer
        ));
    }

    @Override
    public String toString() {
        return "DefaultDatagramPacketContext{" +
                "handle=" + handle +
                ", packet=" + packet +
                ", sender=" + sender +
                ", recipient=" + recipient +
                ", isReliable=" + isReliable +
                ", replyConsumer=" + replyConsumer +
                '}';
    }
}
