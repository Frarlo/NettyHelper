package me.ferlo.netty.datagram;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.Recycler;

import java.net.InetSocketAddress;

class ReliabilityDatagramPacket implements ByteBufHolder {

    private static final Recycler<ReliabilityDatagramPacket> RECYCLER = new Recycler<ReliabilityDatagramPacket>() {
        @Override
        protected ReliabilityDatagramPacket newObject(Handle<ReliabilityDatagramPacket> handle) {
            return new ReliabilityDatagramPacket(handle);
        }
    };

    private final Recycler.Handle<ReliabilityDatagramPacket> handle;

    private DatagramPacket datagramPacket;
    private boolean isReliable;

    private ReliabilityDatagramPacket(Recycler.Handle<ReliabilityDatagramPacket> handle) {
        this.handle = handle;
    }

    public static ReliabilityDatagramPacket newInstance(DatagramPacket datagramPacket, boolean isReliable) {
        final ReliabilityDatagramPacket inst = RECYCLER.get();
        inst.datagramPacket = datagramPacket;
        inst.isReliable = isReliable;
        return inst;
    }

    public void recycle() {
        datagramPacket = null;
        handle.recycle(this);
    }

    public DatagramPacket getActualDatagram() {
        return datagramPacket;
    }

    public boolean isReliable() {
        return isReliable;
    }

    @Override
    public String toString() {
        return "ReliableDatagramPacket{" +
                "handle=" + handle +
                ", datagramPacket=" + datagramPacket +
                ", isReliable=" + isReliable +
                '}';
    }

    // DatagramPacket delegate

    @Override
    public ReliabilityDatagramPacket copy() {
        return replace(content().copy());
    }

    @Override
    public ReliabilityDatagramPacket duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public ReliabilityDatagramPacket retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public ReliabilityDatagramPacket replace(ByteBuf content) {
        return ReliabilityDatagramPacket.newInstance(datagramPacket.replace(content), isReliable);
    }

    @Override
    public ReliabilityDatagramPacket retain() {
        datagramPacket.retain();
        return this;
    }

    @Override
    public ReliabilityDatagramPacket retain(int increment) {
        datagramPacket.retain(increment);
        return this;
    }

    @Override
    public ReliabilityDatagramPacket touch() {
        datagramPacket.touch();
        return this;
    }

    @Override
    public ReliabilityDatagramPacket touch(Object hint) {
        datagramPacket.touch(hint);
        return this;
    }

    @Override
    public ByteBuf content() {
        return datagramPacket.content();
    }

    public InetSocketAddress sender() {
        return datagramPacket.sender();
    }

    public InetSocketAddress recipient() {
        return datagramPacket.recipient();
    }

    @Override
    public int refCnt() {
        return datagramPacket.refCnt();
    }

    @Override
    public boolean release() {
        final boolean res = datagramPacket.release();
        if(res)
            recycle();
        return res;
    }

    @Override
    public boolean release(int decrement) {
        final boolean res = datagramPacket.release(decrement);
        if(res)
            recycle();
        return res;
    }
}
