package me.ferlo.netty.datagram;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageEncoder;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.stream.StreamPacketEncoder;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class DatagramPacketEncoder extends MessageToMessageEncoder<DatagramPacketContext> {

    // Constants

    // Source: https://stackoverflow.com/a/35697810
    // Not sure how correct it is
    private static final int SAFE_MAX_PACKET_SIZE = 508;

    private static final int FRAGMENTED_FLAG = 0x1;
    private static final int LAST_FRAGMENT_FLAG = 0x2;

    // Attributes

    private final StreamPacketEncoder encoder;
    private final int packetSize;

    private final AtomicInteger packedIds = new AtomicInteger(0);

    public DatagramPacketEncoder(Function<Class<? extends Packet>, Byte> packetToId) {
        this(packetToId, SAFE_MAX_PACKET_SIZE);
    }

    public DatagramPacketEncoder(Function<Class<? extends Packet>, Byte> packetToId, int packetSize) {
        this.encoder = new StreamPacketEncoder(packetToId);
        this.packetSize = packetSize;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx,
                          DatagramPacketContext msg,
                          List<Object> out) throws EncoderException {

        final ByteBuf bytes = Unpooled.buffer();

        bytes.writeByte(0x0); // Set it as not fragmented
        encoder.writePacket(msg.getPacket(), bytes);

        // The max single packet size is going to be
        // packetSize - 1 byte for flags

        final int currentPacketSize = bytes.readableBytes();
        if(currentPacketSize <= packetSize - 1) {
            out.add(new DatagramPacket(bytes, (InetSocketAddress) msg.getRecipient()));

        } else {

            // Discard the first byte as there is the flags set to not fragmented
            bytes.readByte();

            final InetSocketAddress recipient = (InetSocketAddress) msg.getRecipient();

            // It's packet size minus:
            // - 1 byte for flags (fragmented flag and last fragment flag)
            // - 1 byte to indicate the packet id
            // - 1 byte to indicate the fragment id

            final int actualPacketSize = packetSize - 3;

            byte flags = FRAGMENTED_FLAG; // set it as fragmented but not last fragment
            byte packedId = (byte) (packedIds.getAndIncrement() % Byte.MAX_VALUE);
            byte fragmentId = 0;

            int remainingPacketSize = currentPacketSize;
            while(remainingPacketSize > actualPacketSize) {

                final ByteBuf flagsBuf = Unpooled.buffer(3, 3);
                flagsBuf.writeByte(flags);
                flagsBuf.writeByte(packedId);
                flagsBuf.writeByte(fragmentId++);

                final ByteBuf slice = bytes.retainedSlice(bytes.readerIndex(), actualPacketSize);
                final ByteBuf compositeBuf = Unpooled.wrappedBuffer(flagsBuf, slice);

                out.add(new DatagramPacket(compositeBuf, recipient));

                bytes.readerIndex(bytes.readerIndex() + actualPacketSize);
                remainingPacketSize -= actualPacketSize;
            }

            flags |= LAST_FRAGMENT_FLAG; // Set the packet as last fragment

            final ByteBuf flagsBuf = Unpooled.buffer(3, 3);
            flagsBuf.writeByte(flags);
            flagsBuf.writeByte(packedId);
            flagsBuf.writeByte(fragmentId);

            final ByteBuf compositeBuf = Unpooled.wrappedBuffer(flagsBuf, bytes);
            out.add(new DatagramPacket(compositeBuf, recipient));
        }

    }
}
