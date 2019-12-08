package me.ferlo.netty.datagram;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.PacketParser;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DatagramToContextDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final DatagramPacketDecoder decoder;
    private final Function<DatagramPacketContext, CompletableFuture<Void>> replyConsumer;

    public DatagramToContextDecoder(Function<Byte, PacketParser> idToParser,
                                    Function<DatagramPacketContext, CompletableFuture<Void>> replyConsumer) {
        this.decoder = new DatagramPacketDecoder(idToParser);
        this.replyConsumer = replyConsumer;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx,
                          DatagramPacket msg,
                          List<Object> out) throws DecoderException {

        final Packet packet = decoder.decode(ctx, msg);
        if(packet != null)
            out.add(DefaultDatagramPacketContext.newInstance(
                    packet,
                    msg.sender(),
                    msg.recipient(),
                    replyConsumer
            ));
    }
}
