package me.ferlo.netty.datagram;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.Recycler;

import java.net.InetSocketAddress;

class ReliableDatagramPacket implements ByteBufHolder {

    private static final Recycler<ReliableDatagramPacket> RECYCLER = new Recycler<ReliableDatagramPacket>() {
        @Override
        protected ReliableDatagramPacket newObject(Handle<ReliableDatagramPacket> handle) {
            return new ReliableDatagramPacket(handle);
        }
    };

    private final Recycler.Handle<ReliableDatagramPacket> handle;

    private DatagramPacket datagramPacket;

    private ReliableDatagramPacket(Recycler.Handle<ReliableDatagramPacket> handle) {
        this.handle = handle;
    }

    public static ReliableDatagramPacket newInstance(DatagramPacket datagramPacket) {
        final ReliableDatagramPacket inst = RECYCLER.get();
        inst.datagramPacket = datagramPacket;
        return inst;
    }

    public void recycle() {
        datagramPacket = null;
        handle.recycle(this);
    }

    public DatagramPacket getActualDatagram() {
        return datagramPacket;
    }

    @Override
    public String toString() {
        return "ReliableDatagramPacket{" +
                "handle=" + handle +
                ", datagramPacket=" + datagramPacket +
                '}';
    }

    // DatagramPacket delegate

    @Override
    public ReliableDatagramPacket copy() {
        return replace(content().copy());
    }

    @Override
    public ReliableDatagramPacket duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public ReliableDatagramPacket retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public ReliableDatagramPacket replace(ByteBuf content) {
        return ReliableDatagramPacket.newInstance(datagramPacket.replace(content));
    }

    @Override
    public ReliableDatagramPacket retain() {
        datagramPacket.retain();
        return this;
    }

    @Override
    public ReliableDatagramPacket retain(int increment) {
        datagramPacket.retain(increment);
        return this;
    }

    @Override
    public ReliableDatagramPacket touch() {
        datagramPacket.touch();
        return this;
    }

    @Override
    public ReliableDatagramPacket touch(Object hint) {
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
