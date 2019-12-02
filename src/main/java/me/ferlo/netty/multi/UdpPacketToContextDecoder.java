package me.ferlo.netty.multi;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.PacketParser;
import me.ferlo.netty.datagram.DatagramPacketDecoder;

import java.util.List;
import java.util.function.Function;

public class UdpPacketToContextDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final MultiServerComponent netService;
    private final DatagramPacketDecoder decoder;

    public UdpPacketToContextDecoder(MultiServerComponent netService,
                                     Function<Byte, PacketParser> idToParser) {

        this.netService = netService;
        this.decoder = new DatagramPacketDecoder(idToParser);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx,
                          DatagramPacket msg,
                          List<Object> out) throws DecoderException {

        final Packet packet = decoder.decode(ctx, msg);
        if(packet != null)
            out.add(new DefaultMultiServerPacketContext(
                    netService,
                    packet,
                    ctx,
                    msg.sender(),
                    msg.recipient()
            ));
    }
}
