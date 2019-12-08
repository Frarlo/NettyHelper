package me.ferlo.netty.datagram;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.Recycler;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SlidingWindowReliabilityHandler {

    private static final byte ACK_FLAG = 0x1;
    private static final byte RELIABLE_FLAG = 0x2;

    private final int windowSize;
    private final long startId;

    private final InboundHandler inboundHandler;
    private final OutboundHandler outboundHandler;

    private final ConcurrentMap<InetSocketAddress, SlidingWindow> senderToWindow;

    public SlidingWindowReliabilityHandler(long startId, int windowSize) {

        this.inboundHandler = new InboundHandler();
        this.outboundHandler = new OutboundHandler();

        this.windowSize = windowSize;
        this.startId = startId;

        this.senderToWindow = new ConcurrentHashMap<>();
    }

    public InboundHandler getInboundHandler() {
        return inboundHandler;
    }

    public OutboundHandler getOutboundHandler() {
        return outboundHandler;
    }

    private SlidingWindow getWindow(InetSocketAddress sender) {
        return senderToWindow.computeIfAbsent(sender, s -> new SlidingWindow(startId, windowSize));
    }

    class InboundHandler extends MessageToMessageDecoder<DatagramPacket> {

        @Override
        protected void decode(ChannelHandlerContext ctx,
                              DatagramPacket msg,
                              List<Object> out) {

            final SlidingWindow window = getWindow(msg.sender());

            final ByteBuf buf = msg.content();
            final byte flags = buf.readByte();

            if((flags & ACK_FLAG) != 0) { // if(isAck)
                final long id = buf.readLong();
                window.handleAck(ctx, id);
            } else {
                if((flags & RELIABLE_FLAG) == 0) { // if(!reliable)
                    msg.retain();
                    out.add(msg);
                    return;
                }

                final long id = buf.readLong();
                window.handleReceiveReliable(ctx, id, msg, out);
            }
        }
    }

    class OutboundHandler extends MessageToMessageEncoder<Object> {

        @Override
        public boolean acceptOutboundMessage(Object msg) {
            return msg instanceof DatagramPacket ||
                    msg instanceof ReliableDatagramPacket ||
                    msg instanceof WrappedDatagram;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            try {
                if (!acceptOutboundMessage(msg)) {
                    super.write(ctx, msg, promise);
                    return;
                }

                if(msg instanceof WrappedDatagram) {
                    final DatagramPacket unwrapped = ((WrappedDatagram) msg).getDatagramPacket();
                    unwrapped.retain();

                    super.write(ctx, unwrapped, promise);
                    return;
                }

                if(msg instanceof DatagramPacket) {
                    super.write(ctx, handleUnreliable(ctx, (DatagramPacket) msg), promise);
                    return;
                }

                final ReliableDatagramPacket cast = (ReliableDatagramPacket) msg;
                final DatagramPacket toWrite = getWindow(cast.recipient()).handleSendReliable(ctx, cast, promise);
                if(toWrite != null)
                    super.write(ctx, toWrite, ctx.voidPromise());
            } catch (EncoderException e) {
                throw e;
            } catch (Throwable t) {
                throw new EncoderException(t);
            }
        }

        private DatagramPacket handleUnreliable(ChannelHandlerContext ctx, DatagramPacket msg) {
            final ByteBuf header = ctx.alloc().buffer(1, 1);
            header.writeByte(0);

            final ByteBuf datagramBuf = Unpooled.wrappedBuffer(header, msg.content());
            msg = msg.replace(datagramBuf);
            msg.retain();

            return msg;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) {
            out.add(msg);
        }
    }

    static class SlidingWindow {

        private final int windowSize;

        // Send stuff

        private final ConcurrentMap<Long, SlidingWindowSendDatagram> sentDatagrams;
        private final AtomicInteger sentDatagramsSize = new AtomicInteger();

        private final Queue<SlidingWindowSendDatagram> toSendDatagrams;
        private final AtomicLong datagramId;

        // Receive Stuff

        private final AtomicLong receiveWindowStart;
        private final SortedSet<SlidingWindowReceiveDatagram> receivedUnorderedPackets;


        public SlidingWindow(long startId, int windowSize) {

            this.sentDatagrams = new ConcurrentHashMap<>();
            this.toSendDatagrams = new ConcurrentLinkedQueue<>();
            this.datagramId = new AtomicLong(startId);

            this.receivedUnorderedPackets = new ConcurrentSkipListSet<>(Comparator.comparingLong(SlidingWindowReceiveDatagram::getId));

            this.windowSize = windowSize;
            this.receiveWindowStart = new AtomicLong(startId);
        }

        private DatagramPacket handleSendReliable(ChannelHandlerContext ctx,
                                                  ReliableDatagramPacket msg,
                                                  ChannelPromise promise) {

            final long datagramId = this.datagramId.getAndIncrement();

            final ByteBuf header = ctx.alloc().buffer(9, 9);
            header.writeByte(RELIABLE_FLAG);
            header.writeLong(datagramId);

            final ByteBuf datagramBuf = Unpooled.wrappedBuffer(header, msg.content());
            msg = msg.replace(datagramBuf);
            msg.retain();

            if(sentDatagramsSize.getAndUpdate(i -> Math.min(i + 1, windowSize)) < windowSize) {
                sentDatagrams.put(datagramId, SlidingWindowSendDatagram.newInstance(datagramId, msg, promise));
                return msg.getActualDatagram();
            }

            toSendDatagrams.add(SlidingWindowSendDatagram.newInstance(datagramId, msg, promise));
            return null;
        }

        private void handleAck(ChannelHandlerContext ctx, long id) {

            final SlidingWindowSendDatagram acked = sentDatagrams.remove(id);
            // Already received an ack for that packet, probably duplicated somehow
            // Do nothing
            if(acked == null)
                return;
            acked.getPromise().setSuccess();
            acked.recycle();

            final SlidingWindowSendDatagram head = toSendDatagrams.poll();
            if(head == null) {
                sentDatagramsSize.decrementAndGet();
                return;
            }

            sentDatagrams.put(head.getId(), head);
            writeDatagram(ctx, head.getPacket().getActualDatagram());
        }

        private void handleReceiveReliable(ChannelHandlerContext ctx,
                                           long id,
                                           DatagramPacket msg,
                                           List<Object> decoded) {

            final int delta = (int) (id - receiveWindowStart.getAndUpdate(i -> (i == id) ? i + 1 : i));
            final SlidingWindowReceiveDatagram info = SlidingWindowReceiveDatagram.newInstance(id, msg);

            final ByteBuf ackBuf = ctx.alloc().buffer(9);
            ackBuf.writeByte(ACK_FLAG);
            ackBuf.writeLong(id);

            writeDatagram(ctx, new DatagramPacket(ackBuf, msg.sender(), msg.recipient()));

            // Even if a packet was already received, resend the ack
            // just in case it got lost
            if(delta < 0 || receivedUnorderedPackets.contains(info)) {
                info.recycle();
                return;
            }

            // If it's not the start of the window, add it to the waiting packets
            if(delta > 0) {
                msg.retain();
                receivedUnorderedPackets.add(info);
                return;
            }

            info.recycle();

            // Start of the window, receive it
            msg.retain();
            decoded.add(msg);
            // Receive all the ones immediately subsequent to the received id
            final Iterator<SlidingWindowReceiveDatagram> iter = receivedUnorderedPackets.iterator();
            while(iter.hasNext()) {
                final SlidingWindowReceiveDatagram currInfo = iter.next();
                final long currId = currInfo.getId();
                final DatagramPacket currPacket = currInfo.getPacket();

                if(currId != receiveWindowStart.getAndUpdate(i -> (i == currId) ? i + 1 : i))
                    break;

                decoded.add(currPacket);
                iter.remove();
                currInfo.recycle();
            }
        }

        private ChannelFuture writeDatagram(ChannelHandlerContext ctx, DatagramPacket datagramPacket) {
            // Wrap it so we are sure ti goes through all the pipeline without getting modified
            return ctx.writeAndFlush(WrappedDatagram.newInstance(datagramPacket));
        }
    }

    static class WrappedDatagram {

        private static final Recycler<WrappedDatagram> RECYCLER = new Recycler<WrappedDatagram>() {
            @Override
            protected WrappedDatagram newObject(Handle<WrappedDatagram> handle) {
                return new WrappedDatagram(handle);
            }
        };

        private final Recycler.Handle<WrappedDatagram> handle;
        private DatagramPacket datagramPacket;

        private WrappedDatagram(Recycler.Handle<WrappedDatagram> handle) {
            this.handle = handle;
        }

        public static WrappedDatagram newInstance(DatagramPacket datagramPacket) {
            final WrappedDatagram inst = RECYCLER.get();
            inst.datagramPacket = datagramPacket;
            return inst;
        }

        public void recycle() {
            datagramPacket = null;
            handle.recycle(this);
        }

        public DatagramPacket getDatagramPacket() {
            return datagramPacket;
        }
    }
}
