package me.ferlo.netty.multi;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.PacketParser;
import me.ferlo.netty.datagram.DatagramPacketContext;
import me.ferlo.netty.datagram.DatagramPacketDecoder;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DatagramToMultiContextDecoder extends DatagramPacketDecoder {

    private final MultiServerComponent netService;
    private final Function<DatagramPacketContext, CompletableFuture<Void>> replyConsumer;

    public DatagramToMultiContextDecoder(MultiServerComponent netService,
                                         Function<Byte, PacketParser> idToParser,
                                         Function<DatagramPacketContext, CompletableFuture<Void>> replyConsumer) {
        super(idToParser);

        this.netService = netService;
        this.replyConsumer = replyConsumer;
    }

    @Override
    public Object decode(ChannelHandlerContext ctx, DatagramPacket msg, boolean reliable) throws DecoderException {

        final Object res = super.decode(ctx, msg, reliable);
        if(!(res instanceof Packet))
            return res;

        final Packet packet = (Packet) res;
        return new DefaultMultiPacketContext(
                netService,
                packet,
                ctx,
                msg.sender(),
                msg.recipient(),
                replyConsumer
        );
    }
}
