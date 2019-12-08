package me.ferlo.netty.multi;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.PacketParser;
import me.ferlo.netty.stream.StreamPacketContext;
import me.ferlo.netty.stream.StreamPacketDecoder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class StreamToMultiContextDecoder extends StreamPacketDecoder {

    private final MultiServerComponent netComponent;
    private final Function<StreamPacketContext, CompletableFuture<Void>> replyConsumer;

    public StreamToMultiContextDecoder(MultiServerComponent netComponent,
                                       Function<Byte, PacketParser> idToParser,
                                       Function<StreamPacketContext, CompletableFuture<Void>> replyConsumer) {
        super(idToParser);
        this.netComponent = netComponent;
        this.replyConsumer = replyConsumer;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx,
                          ByteBuf in,
                          List<Object> out) throws DecoderException {

        final Packet packet = decode(ctx, in);
        if(packet != null)
            out.add(new DefaultMultiPacketContext(
                    netComponent,
                    packet,
                    ctx,
                    ctx.channel().localAddress(),
                    ctx.channel().remoteAddress(),
                    replyConsumer
            ));
    }

}
