package me.ferlo.netty.datagram;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.Recycler;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.PacketParser;
import me.ferlo.netty.stream.StreamPacketDecoder;
import me.ferlo.utils.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.*;
import java.util.function.Function;

public class DatagramPacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

    // Constants

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramPacketDecoder.class);

    private static final int FRAGMENTED_FLAG = 0x1;
    private static final int LAST_FRAGMENT_FLAG = 0x2;

    private static final int DEFAULT_PACKET_MAX_DELAY = 100;
    private static ScheduledExecutorService DEFAULT_EXECUTOR_SERVICE;

    private static ScheduledExecutorService getDefaultExecutorService() {
        if(DEFAULT_EXECUTOR_SERVICE == null)
            DEFAULT_EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor(
                    new ThreadFactoryBuilder()
                            .setNameFormat(c -> "DatagramPacketDiscard")
                            .setDaemon(true)
                            .setPriority(Thread.MIN_PRIORITY)
                            .build());
        return DEFAULT_EXECUTOR_SERVICE;
    }

    // Attributes

    private final StreamPacketDecoder decoder;
    private final int maxPacketDelay;
    private final Map<Byte, FragmentedPacketInfo> packetsMap;

    private final ScheduledExecutorService executorService;

    public DatagramPacketDecoder(Function<Byte, PacketParser> idToParser) {
        this(idToParser, getDefaultExecutorService());
    }

    public DatagramPacketDecoder(Function<Byte, PacketParser> idToParser, ScheduledExecutorService executorService) {
        this(idToParser, DEFAULT_PACKET_MAX_DELAY, executorService);
    }

    public DatagramPacketDecoder(Function<Byte, PacketParser> idToParser, int maxPacketDelay) {
        this(idToParser, maxPacketDelay, getDefaultExecutorService());
    }

    public DatagramPacketDecoder(Function<Byte, PacketParser> idToParser,
                                 int maxPacketDelay,
                                 ScheduledExecutorService executorService) {

        this.decoder = new StreamPacketDecoder(idToParser);
        this.maxPacketDelay = maxPacketDelay;
        this.packetsMap = new ConcurrentHashMap<>();

        this.executorService = executorService;
        this.executorService.scheduleAtFixedRate(() -> {

            final long currentMillis = System.currentTimeMillis();
            packetsMap.values().removeIf(info -> {

                final long currDelay = currentMillis - info.timestamp;
                final boolean shouldRemove = !info.isParsing && currDelay > this.maxPacketDelay;

                // TODO: what about reliable packets?
                if(shouldRemove) {
                    LOGGER.debug("Discarded Datagram packet: currDelay {}, maxDelay: {}", currDelay, this.maxPacketDelay);

                    for(ByteBuf buf : info.fragments.values())
                        buf.release();
                    info.recycle();
                }
                return shouldRemove;
            });
        }, maxPacketDelay, maxPacketDelay, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx,
                          DatagramPacket msg,
                          List<Object> out) throws DecoderException {
        final Packet packet = decode(ctx, msg);

        if(packet != null)
            out.add(packet);
    }

    public Packet decode(ChannelHandlerContext ctx, DatagramPacket in) throws DecoderException {

        final ByteBuf byteBuf = in.content();

        final byte flags = byteBuf.readByte();
        if((flags & FRAGMENTED_FLAG) == 0x0) { // if(!isFragmented)
            LOGGER.debug("Received non fragmented packet");
            return decoder.decode(ctx, byteBuf);
        }

        // Fragmented packet

        final boolean isLastFragment = (flags & LAST_FRAGMENT_FLAG) != 0x0;
        final byte packetId = byteBuf.readByte();
        final byte fragmentId = byteBuf.readByte();

        LOGGER.trace("Received fragment\n" +
                        "isLastFragment: {}\n" +
                        "packetId: {}\n" +
                        "fragmentId: {}",
                isLastFragment, packetId, fragmentId);

        final FragmentedPacketInfo info = packetsMap.computeIfAbsent(packetId, FragmentedPacketInfo::newInstance);
        info.timestamp = System.currentTimeMillis();

        final ByteBuf copyBuf = byteBuf.copy();

        if(isLastFragment) {
            info.lastFragment = copyBuf;
            info.lastFragmentId = fragmentId;
        }

        info.fragments.put(fragmentId, copyBuf);

        if(info.lastFragmentId != -1 &&
                (info.lastFragmentId + 1) == info.fragments.size() &&
                // If it can possibly being discarded, do not parse it
                // as it could lead to concurrency issues
                (System.currentTimeMillis() - info.timestamp) <= maxPacketDelay) {
            info.isParsing = true;

            try {
                final ByteBuf[] fragments = info.fragments.values().toArray(new ByteBuf[0]);
                final ByteBuf compositeBuf = Unpooled.wrappedBuffer(fragments);

                try {
                    LOGGER.debug("Received fragmented packet");
                    return decoder.decode(ctx, compositeBuf);
                } finally {
                    compositeBuf.release();
                }
            } finally {
                final FragmentedPacketInfo removed = packetsMap.remove(packetId);
                if(removed != null)
                    removed.recycle();
            }
        }

        return null;
    }

    static final class FragmentedPacketInfo {

        private static final Recycler<FragmentedPacketInfo> RECYCLER = new Recycler<FragmentedPacketInfo>() {
            @Override
            protected FragmentedPacketInfo newObject(Handle<FragmentedPacketInfo> handle) {
                return new FragmentedPacketInfo(handle);
            }
        };

        private final Recycler.Handle<FragmentedPacketInfo> handle;

        byte packetId = -1;;
        SortedMap<Byte, ByteBuf> fragments = new ConcurrentSkipListMap<>();
        long timestamp = -1;;

        ByteBuf lastFragment;
        byte lastFragmentId = -1;;

        boolean isParsing;

        private FragmentedPacketInfo(Recycler.Handle<FragmentedPacketInfo> handle) {
            this.handle = handle;
        }

        public static FragmentedPacketInfo newInstance(byte packetId) {
            final FragmentedPacketInfo inst = RECYCLER.get();
            inst.packetId = packetId;
            return inst;
        }

        public void recycle() {
            packetId = -1;
            fragments.clear();
            timestamp = -1;
            lastFragment = null;
            lastFragmentId = -1;
            isParsing = false;
            handle.recycle(this);
        }
    }
}
