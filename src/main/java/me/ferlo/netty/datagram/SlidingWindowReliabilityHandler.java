package me.ferlo.netty.datagram;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.Recycler;
import me.ferlo.utils.ThreadFactoryBuilder;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SlidingWindowReliabilityHandler {

    // Constants

    private static final byte ACK_FLAG = 0x1;
    private static final byte RELIABLE_FLAG = 0x2;

    private static final int DEFAULT_RESEND_DELAY = 250;
    private static ScheduledExecutorService DEFAULT_EXECUTOR_SERVICE;

    private static ScheduledExecutorService getDefaultExecutorService() {
        if(DEFAULT_EXECUTOR_SERVICE == null)
            DEFAULT_EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor(
                    new ThreadFactoryBuilder()
                            .setNameFormat(c -> "DatagramSlidingWindowResend")
                            .setDaemon(true)
                            .setPriority(Thread.MIN_PRIORITY)
                            .build());
        return DEFAULT_EXECUTOR_SERVICE;
    }

    // Attributes

    private final int windowSize;
    private final int resendDelay;

    private final InboundHandler inboundHandler;
    private final OutboundHandler outboundHandler;

    private final ConcurrentMap<InetSocketAddress, SlidingWindow> senderToWindow;

    public SlidingWindowReliabilityHandler(int windowSize) {
        this(windowSize, DEFAULT_RESEND_DELAY);
    }

    public SlidingWindowReliabilityHandler(int windowSize, int resendDelay) {
        this(windowSize, resendDelay, getDefaultExecutorService());
    }

    public SlidingWindowReliabilityHandler(int windowSize,
                                           int resendDelay,
                                           ScheduledExecutorService executorService) {

        this.windowSize = windowSize;
        this.resendDelay = resendDelay;

        this.senderToWindow = new ConcurrentHashMap<>();

        this.inboundHandler = new InboundHandler();
        this.outboundHandler = new OutboundHandler();

        executorService.scheduleAtFixedRate(() ->
                senderToWindow.values().forEach(SlidingWindow::resend),
                resendDelay / 2, resendDelay / 2, TimeUnit.MILLISECONDS);
    }

    public InboundHandler getInboundHandler() {
        return inboundHandler;
    }

    public OutboundHandler getOutboundHandler() {
        return outboundHandler;
    }

    private SlidingWindow getWindow(ChannelHandlerContext ctx, InetSocketAddress sender) {
        return senderToWindow.computeIfAbsent(sender, s -> new SlidingWindow(ctx.channel(), windowSize, resendDelay));
    }

    class InboundHandler extends MessageToMessageDecoder<DatagramPacket> {

        @Override
        protected void decode(ChannelHandlerContext ctx,
                              DatagramPacket msg,
                              List<Object> out) {

            final SlidingWindow window = getWindow(ctx, msg.sender());

            final ByteBuf buf = msg.content();
            final byte flags = buf.readByte();

            if((flags & ACK_FLAG) != 0) { // if(isAck)
                final long id = buf.readLong();
                window.handleAck(id);
            } else {
                if((flags & RELIABLE_FLAG) == 0) { // if(!reliable)
                    msg.retain();
                    out.add(msg);
                    return;
                }

                final long id = buf.readLong();
                window.handleReceiveReliable(id, msg, out);
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
                final SlidingWindow window = getWindow(ctx, cast.recipient());

                final DatagramPacket toWrite = window.handleSendReliable(cast, promise);
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

        private final Channel channel;
        private final int windowSize;
        private final int resendDelay;

        // Send stuff

        private final ConcurrentMap<Long, SlidingWindowSendDatagram> sentDatagrams;
        private final AtomicInteger sentDatagramsSize;

        private final Queue<SlidingWindowSendDatagram> toSendDatagrams;
        private final AtomicLong datagramId;

        // Receive Stuff

        private final AtomicLong receiveWindowStart;
        private final SortedSet<SlidingWindowReceiveDatagram> receivedUnorderedPackets;

        SlidingWindow(Channel channel, int windowSize, int resendDelay) {
            this.channel = channel;
            this.windowSize = windowSize;
            this.resendDelay = resendDelay;

            this.sentDatagrams = new ConcurrentHashMap<>();
            this.sentDatagramsSize = new AtomicInteger(0);
            this.toSendDatagrams = new ConcurrentLinkedQueue<>();
            this.datagramId = new AtomicLong(0);

            this.receiveWindowStart = new AtomicLong(0);
            this.receivedUnorderedPackets = new ConcurrentSkipListSet<>(Comparator.comparingLong(SlidingWindowReceiveDatagram::getId));
        }

        private DatagramPacket handleSendReliable(ReliableDatagramPacket msg, ChannelPromise promise) {
            final long datagramId = this.datagramId.getAndIncrement();

            final ByteBuf header = channel.alloc().buffer(9, 9);
            header.writeByte(RELIABLE_FLAG);
            header.writeLong(datagramId);

            final ByteBuf datagramBuf = Unpooled.wrappedBuffer(header, msg.content());
            msg = msg.replace(datagramBuf);
            // Retain to store
            msg.retain();

            if(sentDatagramsSize.getAndUpdate(i -> Math.min(i + 1, windowSize)) < windowSize) {
                sentDatagrams.put(datagramId, SlidingWindowSendDatagram.newInstance(
                        datagramId,
                        msg,
                        System.currentTimeMillis(),
                        promise));
                // Retain to send
                msg.retain();
                return msg.getActualDatagram();
            }

            toSendDatagrams.add(SlidingWindowSendDatagram.newInstance(datagramId, msg, promise));
            return null;
        }

        private void handleAck(long id) {

            final SlidingWindowSendDatagram acked = sentDatagrams.remove(id);
            // Already received an ack for that packet, probably duplicated somehow
            // Do nothing
            if(acked == null)
                return;
            acked.getPromise().setSuccess();
            acked.getPacket().release();
            acked.recycle();

            final SlidingWindowSendDatagram head = toSendDatagrams.poll();
            if(head == null) {
                sentDatagramsSize.decrementAndGet();
                return;
            }

            sentDatagrams.put(head.getId(), head);
            head.setTimestamp(System.currentTimeMillis());
            writeDatagram(head.getPacket().getActualDatagram());
        }

        private void handleReceiveReliable(long id,
                                           DatagramPacket msg,
                                           List<Object> decoded) {

            final int delta = (int) (id - receiveWindowStart.getAndUpdate(i -> (i == id) ? i + 1 : i));
            final SlidingWindowReceiveDatagram info = SlidingWindowReceiveDatagram.newInstance(id, msg);

            final ByteBuf ackBuf = channel.alloc().buffer(9);
            ackBuf.writeByte(ACK_FLAG);
            ackBuf.writeLong(id);

            writeDatagram(new DatagramPacket(ackBuf, msg.sender(), msg.recipient()));

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
            decoded.add(ReliableDatagramPacket.newInstance(msg));
            // Receive all the ones immediately subsequent to the received id
            final Iterator<SlidingWindowReceiveDatagram> iter = receivedUnorderedPackets.iterator();
            while(iter.hasNext()) {
                final SlidingWindowReceiveDatagram currInfo = iter.next();
                final long currId = currInfo.getId();
                final DatagramPacket currPacket = currInfo.getPacket();

                if(currId != receiveWindowStart.getAndUpdate(i -> (i == currId) ? i + 1 : i))
                    break;

                decoded.add(ReliableDatagramPacket.newInstance(currPacket));
                iter.remove();
                currInfo.recycle();
            }
        }

        void resend() {
            final long currentMillis = System.currentTimeMillis();

            sentDatagrams.values().forEach(datagramInfo -> {
                if(currentMillis - datagramInfo.getTimestamp() < resendDelay)
                    return;

                datagramInfo.setTimestamp(currentMillis);

                try {
                    final DatagramPacket datagramPacket = datagramInfo.getPacket().getActualDatagram();
                    datagramPacket.retain();
                    writeDatagram(datagramPacket);
                } catch (Throwable t) {
                    channel.pipeline().fireExceptionCaught(t);
                }
            });
        }

        private ChannelFuture writeDatagram(DatagramPacket datagramPacket) {
            // Wrap it so we are sure ti goes through all the pipeline without getting modified
            return channel.writeAndFlush(WrappedDatagram.newInstance(datagramPacket));
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
