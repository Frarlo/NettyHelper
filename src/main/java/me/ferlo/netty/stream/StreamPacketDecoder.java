package me.ferlo.netty.stream;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import me.ferlo.netty.CustomByteBuf;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.PacketParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

/**
 * Decodes {@link ByteBuf}s frames into {@link Packet}
 *
 * @author Ferlo
 */
@ChannelHandler.Sharable
public class StreamPacketDecoder extends ByteToMessageDecoder {

    // Constants

    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamPacketDecoder.class);

    // Attributes

    /**
     * Function returning the parser for the given packet id
     */
    private final Function<Byte, PacketParser> idToParser;

    /**
     * Constructs a packet decoder
     *
     * @param idToParser function returning the parser for the given packet id
     */
    public StreamPacketDecoder(Function<Byte, PacketParser> idToParser) {
        this.idToParser = idToParser;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx,
                          ByteBuf in,
                          List<Object> out) throws DecoderException {

        final Packet packet = decode(ctx, in);

        LOGGER.trace("Received packet {}", packet);
        if(packet != null)
            out.add(packet);
    }

    /**
     * Decodes the given frame
     *
     * @param frame buffer containing the packet data
     * @return decoded packet
     * @throws DecoderException if the packet couldn't be parsed
     */
    public Packet decode(ChannelHandlerContext ctx, ByteBuf frame) throws DecoderException {

        byte[] bytes = new byte[frame.readableBytes()];
        frame.getBytes(frame.readerIndex(), bytes, 0, frame.readableBytes());

        final CustomByteBuf msg0 = CustomByteBuf.get(frame);
        final byte packetId = msg0.readByte();

        try {
            final PacketParser packetParser = idToParser.apply(packetId);
            if(packetParser == null)
                throw new DecoderException("There is no parser for the given ID (" + packetId + ')');

            try {
                return packetParser.parse(msg0);
            } finally {
                msg0.setIndex(msg0.writerIndex(), msg0.writerIndex());
            }
        } catch(Exception e) {
            LOGGER.error("Couldn't parse packet (id: {})", packetId, e);
        } finally {
            msg0.recycle();
        }

        return null;
    }
}
