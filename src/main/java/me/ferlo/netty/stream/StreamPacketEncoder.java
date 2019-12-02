package me.ferlo.netty.stream;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import me.ferlo.netty.CustomByteBuf;
import me.ferlo.netty.core.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Encodes packets to {@link ByteBuf}s
 *
 * @author Ferlo
 */
@ChannelHandler.Sharable
public class StreamPacketEncoder extends MessageToByteEncoder<StreamPacketContext> {


    // Constants

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamPacketEncoder.class);

    // Attributes

    /**
     * Function returning the id for the given packet class
     */
    private final Function<Class<? extends Packet>, Byte> packetToId;

    /**
     * Constructs a packet encoder
     *
     * @param packetToId function returning the id for the given packet class
     */
    public StreamPacketEncoder(Function<Class<? extends Packet>, Byte> packetToId) {
        this.packetToId = packetToId;
    }

    @Override
    public void encode(ChannelHandlerContext ctx,
                       StreamPacketContext packetCtx,
                       ByteBuf out) throws EncoderException {

        final Packet packet = packetCtx.getPacket();

        LOGGER.trace("Sending packet {}", packet);
        writePacket(packet, out);
    }

    public void writePacket(Packet packet, ByteBuf out) throws EncoderException {
        final CustomByteBuf out0 = CustomByteBuf.get(out);

        try {
            out0.writeByte(packetToId.apply(packet.getClass()));
            packet.writePacket(out0);
        } catch (Exception ex) {
            throw new EncoderException(ex);
        } finally {
            out0.recycle();
        }
    }
}
