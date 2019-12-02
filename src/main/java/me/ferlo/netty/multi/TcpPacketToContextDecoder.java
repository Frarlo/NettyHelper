package me.ferlo.netty.multi;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.PacketParser;
import me.ferlo.netty.stream.StreamPacketDecoder;

import java.util.List;
import java.util.function.Function;

public class TcpPacketToContextDecoder extends StreamPacketDecoder {

    private final MultiServerComponent netComponent;

    public TcpPacketToContextDecoder(MultiServerComponent netComponent,
                                     Function<Byte, PacketParser> idToParser) {
        super(idToParser);
        this.netComponent = netComponent;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx,
                          ByteBuf in,
                          List<Object> out) throws DecoderException {

        final Packet packet = decode(ctx, in);
        if(packet != null)
            out.add(new DefaultMultiServerPacketContext(
                    netComponent,
                    packet,
                    ctx,
                    ctx.channel().localAddress(),
                    ctx.channel().remoteAddress()
            ));
    }

}
