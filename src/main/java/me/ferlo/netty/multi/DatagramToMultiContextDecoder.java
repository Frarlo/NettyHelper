package me.ferlo.netty.multi;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.PacketParser;
import me.ferlo.netty.datagram.DatagramPacketContext;
import me.ferlo.netty.datagram.DatagramPacketDecoder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DatagramToMultiContextDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final MultiServerComponent netService;
    private final DatagramPacketDecoder decoder;
    private final Function<DatagramPacketContext, CompletableFuture<Void>> replyConsumer;

    public DatagramToMultiContextDecoder(MultiServerComponent netService,
                                         Function<Byte, PacketParser> idToParser,
                                         Function<DatagramPacketContext, CompletableFuture<Void>> replyConsumer) {
        this.netService = netService;
        this.decoder = new DatagramPacketDecoder(idToParser);
        this.replyConsumer = replyConsumer;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx,
                          DatagramPacket msg,
                          List<Object> out) throws DecoderException {

        final Packet packet = decoder.decode(ctx, msg);
        if(packet != null)
            out.add(new DefaultMultiPacketContext(
                    netService,
                    packet,
                    ctx,
                    msg.sender(),
                    msg.recipient(),
                    replyConsumer
            ));
    }
}
