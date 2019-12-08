package me.ferlo.netty.datagram;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.PacketParser;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DatagramToContextDecoder extends DatagramPacketDecoder {

    private final Function<DatagramPacketContext, CompletableFuture<Void>> replyConsumer;

    public DatagramToContextDecoder(Function<Byte, PacketParser> idToParser,
                                    Function<DatagramPacketContext, CompletableFuture<Void>> replyConsumer) {
        super(idToParser);
        this.replyConsumer = replyConsumer;
    }

    @Override
    public Object decode(ChannelHandlerContext ctx, DatagramPacket msg, boolean reliable) throws DecoderException {

        final Object res = super.decode(ctx, msg, reliable);
        if(!(res instanceof Packet))
            return res;

        final Packet packet = (Packet) res;
        return DefaultDatagramPacketContext.newInstance(
                packet,
                msg.sender(),
                msg.recipient(),
                replyConsumer
        );
    }
}
