package me.ferlo.netty.datagram;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.Recycler;
import io.netty.util.ReferenceCountUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DatagramSlidingWindowHandler {

    private static final byte ACK_FLAG = 0x1;
    private static final byte RELIABLE_FLAG = 0x2;

    private final int windowSize;

    // Handlers

    private final InboundHandler inboundHandler;
    private final OutboundHandler outboundHandler;

    // Send stuff

    private final ConcurrentMap<Long, DatagramInfo> sentDatagrams;
    private final AtomicInteger sentDatagramsSize = new AtomicInteger();

    private final Queue<DatagramInfo> toSendDatagrams;
    private final AtomicLong datagramId;

    // Receive Stuff

    private final AtomicLong currWindow;
    private final SortedSet<DatagramInfo> waitingPackets;

    public DatagramSlidingWindowHandler(long startId, int windowSize) {

        this.inboundHandler = new InboundHandler();
        this.outboundHandler = new OutboundHandler();

        this.sentDatagrams = new ConcurrentHashMap<>();
        this.toSendDatagrams = new ConcurrentLinkedQueue<>();
        this.datagramId = new AtomicLong(startId);

        this.waitingPackets = new ConcurrentSkipListSet<>(Comparator.comparingLong(DatagramInfo::getId));

        this.windowSize = windowSize;
        this.currWindow = new AtomicLong(startId);
    }

    public InboundHandler getInboundHandler() {
        return inboundHandler;
    }

    public OutboundHandler getOutboundHandler() {
        return outboundHandler;
    }

    class InboundHandler extends MessageToMessageDecoder<DatagramPacket> {

        @Override
        protected void decode(ChannelHandlerContext ctx,
                              DatagramPacket msg,
                              List<Object> out) {

            final ByteBuf buf = msg.content();

            final byte flags = buf.readByte();
            final long id = buf.readLong();

            if((flags & ACK_FLAG) != 0) { // if(isAck)
                handleAck(ctx, id, out);
            } else {

                if((flags & RELIABLE_FLAG) == 0) { // if(!reliable)
                    out.add(msg);
                    return;
                }

                handleReliablePacket(ctx, id, msg, out);
            }
        }

        private void handleAck(ChannelHandlerContext ctx, long id, List<Object> out) {

            final DatagramInfo acked = sentDatagrams.remove(id);
            if(acked == null)
                throw new DecoderException(String.format(
                        "Received an ack with id %s, but the sent datagram buffer does not contain it (buff: %s)",
                        id, sentDatagrams));

            acked.getPromise().setSuccess();

            final DatagramInfo head = toSendDatagrams.poll();
            if(head == null)
                return;

            sentDatagramsSize.incrementAndGet();
            sentDatagrams.put(head.getId(), DatagramInfo.newInstance(head.getId(), head.getPacket(), head.getPromise()));
            ctx.writeAndFlush(head.getPacket().retain());

            head.recycle();
        }

        private void handleReliablePacket(ChannelHandlerContext ctx,
                                          long id,
                                          DatagramPacket msg,
                                          List<Object> out) {

            final ByteBuf ackBuf = Unpooled.buffer(9);
            ackBuf.writeByte(ACK_FLAG);
            ackBuf.writeLong(id);

            ctx.writeAndFlush(new DatagramPacket(ackBuf, msg.sender(), msg.recipient()));

            if(id != currWindow.getAndUpdate(i -> (i == id) ? i + 1 : i)) {
                waitingPackets.add(DatagramInfo.newInstance(id, msg));
                return;
            }

            out.add(msg);

            final Iterator<DatagramInfo> iter = waitingPackets.iterator();
            while(iter.hasNext()) {
                final DatagramInfo info = iter.next();
                final long currId = info.getId();

                if(currId != currWindow.getAndUpdate(i -> (i == currId) ? i + 1 : i))
                    break;

                out.add(info.getPacket());
                iter.remove();
                info.recycle();
            }
        }
    }

    class OutboundHandler extends MessageToMessageEncoder<DatagramPacket> {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            try {
                if (!acceptOutboundMessage(msg)) {
                    super.write(ctx, msg, promise);
                    return;
                }

                final DatagramPacket cast = (DatagramPacket) msg;
                try {
                    // TODO: handle non reliable stuff
                    final DatagramPacket toWrite = handleReliable(ctx, cast, promise);
                    if(toWrite != null)
                        super.write(ctx, toWrite, ctx.voidPromise());
                } finally {
                    ReferenceCountUtil.release(cast);
                }
            } catch (EncoderException e) {
                throw e;
            } catch (Throwable t) {
                throw new EncoderException(t);
            }
        }

        private DatagramPacket handleReliable(ChannelHandlerContext ctx, DatagramPacket msg, ChannelPromise promise) {
            final long datagramId = DatagramSlidingWindowHandler.this.datagramId.getAndIncrement();

            final ByteBuf header = Unpooled.buffer(9);
            header.writeByte(RELIABLE_FLAG);
            header.writeLong(datagramId);

            final ByteBuf datagramBuf = Unpooled.wrappedBuffer(header, msg.content());
            msg = msg.replace(datagramBuf);

            if(sentDatagramsSize.getAndUpdate(i -> Math.min(i + 1, windowSize)) < windowSize) {
                sentDatagrams.put(datagramId, DatagramInfo.newInstance(datagramId, msg, promise));
                return msg;
            }

            toSendDatagrams.add(DatagramInfo.newInstance(datagramId, msg, promise));
            return null;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) {
            out.add(msg);
            msg.retain();
        }
    }

    static class DatagramInfo {

        private static final Recycler<DatagramInfo> RECYCLER = new Recycler<DatagramInfo>() {
            @Override
            protected DatagramInfo newObject(Handle<DatagramInfo> handle) {
                return new DatagramInfo(handle);
            }
        };

        private final Recycler.Handle<DatagramInfo> handle;

        private long id;
        private DatagramPacket packet;
        private ChannelPromise promise;

        private DatagramInfo(Recycler.Handle<DatagramInfo> handle) {
            this.handle = handle;
        }

        static DatagramInfo newInstance(long id, DatagramPacket packet) {
            final DatagramInfo instance = RECYCLER.get();
            instance.id = id;
            instance.packet = packet;
            return instance;
        }

        static DatagramInfo newInstance(long id, DatagramPacket packet, ChannelPromise promise) {
            final DatagramInfo instance = RECYCLER.get();
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

        public DatagramPacket getPacket() {
            return packet;
        }

        public ChannelPromise getPromise() {
            return promise;
        }
    }
}
