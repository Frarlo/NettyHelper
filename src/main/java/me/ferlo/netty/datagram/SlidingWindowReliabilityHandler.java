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

    // Handlers

    private final InboundHandler inboundHandler;
    private final OutboundHandler outboundHandler;

    // Send stuff

    private final ConcurrentMap<Long, SlidingWindowSendDatagram> sentDatagrams;
    private final AtomicInteger sentDatagramsSize = new AtomicInteger();

    private final Queue<SlidingWindowSendDatagram> toSendDatagrams;
    private final AtomicLong datagramId;

    // Receive Stuff

    private final AtomicLong receiveWindowStart;
    private final SortedSet<SlidingWindowReceiveDatagram> receivedUnorderedPackets;

    public SlidingWindowReliabilityHandler(long startId, int windowSize) {

        this.inboundHandler = new InboundHandler();
        this.outboundHandler = new OutboundHandler();

        this.sentDatagrams = new ConcurrentHashMap<>();
        this.toSendDatagrams = new ConcurrentLinkedQueue<>();
        this.datagramId = new AtomicLong(startId);

        this.receivedUnorderedPackets = new ConcurrentSkipListSet<>(Comparator.comparingLong(SlidingWindowReceiveDatagram::getId));

        this.windowSize = windowSize;
        this.receiveWindowStart = new AtomicLong(startId);
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

            if((flags & ACK_FLAG) != 0) { // if(isAck)
                final long id = buf.readLong();
                handleAck(ctx, id);
            } else {
                if((flags & RELIABLE_FLAG) == 0) { // if(!reliable)
                    msg.retain();
                    out.add(msg);
                    return;
                }

                final long id = buf.readLong();
                handleReliablePacket(ctx, id, msg, out);
            }
        }

        private void handleAck(ChannelHandlerContext ctx, long id) {

            final SlidingWindowSendDatagram acked = sentDatagrams.remove(id);
            if(acked == null)
                throw new DecoderException(String.format(
                        "Received an ack with id %s, but the sent datagram buffer does not contain it (buff: %s)",
                        id, sentDatagrams));
            acked.getPromise().setSuccess();
            acked.recycle();

            final SlidingWindowSendDatagram head = toSendDatagrams.poll();
            if(head == null)
                return;

            sentDatagramsSize.incrementAndGet();
            sentDatagrams.put(head.getId(), head);
            ctx.writeAndFlush(head.getPacket().getActualDatagram());
        }

        private void handleReliablePacket(ChannelHandlerContext ctx,
                                          long id,
                                          DatagramPacket msg,
                                          List<Object> decoded) {

            final int delta = (int) (id - receiveWindowStart.getAndUpdate(i -> (i == id) ? i + 1 : i));
            final SlidingWindowReceiveDatagram info = SlidingWindowReceiveDatagram.newInstance(id, msg);

            // If a packet was already received, do nothing
            if(delta < 0 || receivedUnorderedPackets.contains(info)) {
                info.recycle();
                return;
            }

            final ByteBuf ackBuf = ctx.alloc().buffer(9);
            ackBuf.writeByte(ACK_FLAG);
            ackBuf.writeLong(id);

            ctx.writeAndFlush(new DatagramPacket(ackBuf, msg.sender(), msg.recipient()));

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
    }

    class OutboundHandler extends MessageToMessageEncoder<ReliabilityDatagramPacket> {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            try {
                if (!acceptOutboundMessage(msg)) {
                    super.write(ctx, msg, promise);
                    return;
                }

                final ReliabilityDatagramPacket cast = (ReliabilityDatagramPacket) msg;
                if(!cast.isReliable()) {
                    super.write(ctx, handleUnreliable(ctx, cast, promise), promise);
                    return;
                }

                final DatagramPacket toWrite = handleReliable(ctx, cast, promise);
                if(toWrite != null)
                    super.write(ctx, toWrite, ctx.voidPromise());

            } catch (EncoderException e) {
                throw e;
            } catch (Throwable t) {
                throw new EncoderException(t);
            }
        }

        private DatagramPacket handleUnreliable(ChannelHandlerContext ctx, ReliabilityDatagramPacket msg, ChannelPromise promise) {
            final ByteBuf header = ctx.alloc().buffer(1, 1);
            header.writeByte(0);

            final ByteBuf datagramBuf = Unpooled.wrappedBuffer(header, msg.content());
            msg = msg.replace(datagramBuf);
            msg.retain();

            return msg.getActualDatagram();
        }

        private DatagramPacket handleReliable(ChannelHandlerContext ctx, ReliabilityDatagramPacket msg, ChannelPromise promise) {
            final long datagramId = SlidingWindowReliabilityHandler.this.datagramId.getAndIncrement();

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

        @Override
        protected void encode(ChannelHandlerContext ctx, ReliabilityDatagramPacket msg, List<Object> out) {
            out.add(msg);
        }
    }

}
