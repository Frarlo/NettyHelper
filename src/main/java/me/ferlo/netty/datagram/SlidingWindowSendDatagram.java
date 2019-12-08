package me.ferlo.netty.datagram;

import io.netty.channel.ChannelPromise;
import io.netty.util.Recycler;

import java.util.Objects;

class SlidingWindowSendDatagram {

    private static final Recycler<SlidingWindowSendDatagram> RECYCLER = new Recycler<SlidingWindowSendDatagram>() {
        @Override
        protected SlidingWindowSendDatagram newObject(Handle<SlidingWindowSendDatagram> handle) {
            return new SlidingWindowSendDatagram(handle);
        }
    };

    private final Recycler.Handle<SlidingWindowSendDatagram> handle;

    private long id;
    private ReliableDatagramPacket packet;
    private ChannelPromise promise;

    private SlidingWindowSendDatagram(Recycler.Handle<SlidingWindowSendDatagram> handle) {
        this.handle = handle;
    }

    static SlidingWindowSendDatagram newInstance(long id, ReliableDatagramPacket packet, ChannelPromise promise) {
        final SlidingWindowSendDatagram instance = RECYCLER.get();
        instance.id = id;
        instance.packet = packet;
        instance.promise = promise;
        return instance;
    }

    public void recycle() {
        id = -1;
        packet = null;
        promise = null;
        handle.recycle(this);
    }

    public long getId() {
        return id;
    }

    public ReliableDatagramPacket getPacket() {
        return packet;
    }

    public ChannelPromise getPromise() {
        return promise;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SlidingWindowSendDatagram)) return false;
        SlidingWindowSendDatagram that = (SlidingWindowSendDatagram) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SlidingWindowSendDatagram{" +
                "handle=" + handle +
                ", id=" + id +
                ", packet=" + packet +
                ", promise=" + promise +
                '}';
    }
}
