package me.ferlo.netty.datagram;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
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

    private static final int DEFAULT_PACKET_MAX_DELAY = 50;

    // Attributes

    private final StreamPacketDecoder decoder;
    private final int maxPacketDelay;
    private final Map<Byte, FragmentedPacketInfo> packetsMap;

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                    .setNameFormat(c -> "DatagramPacketDecoder-discard")
                    .setDaemon(true)
                    .setPriority(Thread.MIN_PRIORITY)
                    .build());

    public DatagramPacketDecoder(Function<Byte, PacketParser> idToParser) {
        this(idToParser, DEFAULT_PACKET_MAX_DELAY);
    }

    public DatagramPacketDecoder(Function<Byte, PacketParser> idToParser,
                                 int maxPacketDelay) {

        this.decoder = new StreamPacketDecoder(idToParser);
        this.maxPacketDelay = maxPacketDelay;
        this.packetsMap = new ConcurrentHashMap<>();

        this.executorService.scheduleAtFixedRate(() -> {

            final long currentMillis = System.currentTimeMillis();
            packetsMap.values().removeIf(info -> {

                final long currDelay = currentMillis - info.timestamp;
                final boolean shouldRemove = !info.isParsing && currDelay > this.maxPacketDelay;

                if(shouldRemove) {
                    LOGGER.debug("Discarded Datagram packet: currDelay {}, maxDelay: {}", currDelay, this.maxPacketDelay);

                    for(ByteBuf buf : info.fragments.values())
                        buf.release();
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

        final FragmentedPacketInfo info = packetsMap.computeIfAbsent(packetId,
                key -> new FragmentedPacketInfo(key, System.currentTimeMillis()));
        final ByteBuf copyBuf = byteBuf.copy();

        if(isLastFragment) {
            info.lastFragment = copyBuf;
            info.lastFragmentId = fragmentId;
        }

        info.fragments.put(fragmentId, copyBuf);

        if((System.currentTimeMillis() - info.timestamp) <= maxPacketDelay&&
                info.lastFragmentId != 0 &&
                (info.lastFragmentId + 1) == info.fragments.size()) {

            info.isParsing = true;

            try {
                final ByteBuf compositeBuf = Unpooled.wrappedBuffer(
                        info.fragments.values().toArray(new ByteBuf[0]));

                try {
                    LOGGER.debug("Received fragmented packet");
                    return decoder.decode(ctx, compositeBuf);

                } finally {
                    compositeBuf.release();
                }
            } finally {
                packetsMap.remove(packetId);
            }
        }

        return null;
    }

    private static final class FragmentedPacketInfo {

        final byte packetId;
        final SortedMap<Byte, ByteBuf> fragments;
        final long timestamp;

        ByteBuf lastFragment;
        byte lastFragmentId;

        boolean isParsing;

        FragmentedPacketInfo(byte packetId,
                                    long timestamp) {

            this.packetId = packetId;
            this.timestamp = timestamp;
            this.fragments = new ConcurrentSkipListMap<>();
        }
    }
}
