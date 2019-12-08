package me.ferlo.netty.datagram;

import io.netty.channel.socket.DatagramPacket;
import io.netty.util.Recycler;

import java.util.Objects;

class SlidingWindowReceiveDatagram {

    private static final Recycler<SlidingWindowReceiveDatagram> RECYCLER = new Recycler<SlidingWindowReceiveDatagram>() {
        @Override
        protected SlidingWindowReceiveDatagram newObject(Handle<SlidingWindowReceiveDatagram> handle) {
            return new SlidingWindowReceiveDatagram(handle);
        }
    };

    private final Recycler.Handle<SlidingWindowReceiveDatagram> handle;

    private long id;
    private DatagramPacket packet;

    private SlidingWindowReceiveDatagram(Recycler.Handle<SlidingWindowReceiveDatagram> handle) {
        this.handle = handle;
    }

    static SlidingWindowReceiveDatagram newInstance(long id, DatagramPacket packet) {
        final SlidingWindowReceiveDatagram instance = RECYCLER.get();
        instance.id = id;
        instance.packet = packet;
        return instance;
    }

    public void recycle() {
        id = -1;
        packet = null;
        handle.recycle(this);
    }

    public long getId() {
        return id;
    }

    public DatagramPacket getPacket() {
        return packet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SlidingWindowReceiveDatagram)) return false;
        SlidingWindowReceiveDatagram that = (SlidingWindowReceiveDatagram) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SlidingWindowReceiveDatagram{" +
                "handle=" + handle +
                ", id=" + id +
                ", packet=" + packet +
                '}';
    }
}
