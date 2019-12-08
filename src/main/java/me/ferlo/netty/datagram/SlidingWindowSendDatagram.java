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
    private long timestamp;
    private ChannelPromise promise;

    private SlidingWindowSendDatagram(Recycler.Handle<SlidingWindowSendDatagram> handle) {
        this.handle = handle;
    }

    static SlidingWindowSendDatagram newInstance(long id,
                                                 ReliableDatagramPacket packet,
                                                 ChannelPromise promise) {
        return newInstance(id, packet, -1, promise);
    }

    static SlidingWindowSendDatagram newInstance(long id,
                                                 ReliableDatagramPacket packet,
                                                 long timestamp,
                                                 ChannelPromise promise) {

        final SlidingWindowSendDatagram instance = RECYCLER.get();
        instance.id = id;
        instance.packet = packet;
        instance.timestamp = timestamp;
        instance.promise = promise;
        return instance;
    }

    public void recycle() {
        id = -1;
        packet = null;
        timestamp = -1;
        promise = null;
        handle.recycle(this);
    }

    public long getId() {
        return id;
    }

    public ReliableDatagramPacket getPacket() {
        return packet;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
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
