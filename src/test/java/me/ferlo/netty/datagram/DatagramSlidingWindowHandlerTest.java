package me.ferlo.netty.datagram;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class DatagramSlidingWindowHandlerTest {

    @Test
    void testOrder() {

        final DatagramSlidingWindowHandler handler = new DatagramSlidingWindowHandler(0, 4);
        final EmbeddedChannel ch = new EmbeddedChannel(
                handler.getInboundHandler(),
                handler.getOutboundHandler());

        final InetSocketAddress address = new InetSocketAddress(12);
        final ByteBuf buf0 = Unpooled.copyBoolean(true);
        final ByteBuf buf1 = Unpooled.copyBoolean(false);

        ch.writeOutbound(
                new DatagramPacket(buf0.copy().retain(), address),
                new DatagramPacket(buf1.copy().retain(), address),
                new DatagramPacket(buf0.copy().retain(), address),
                new DatagramPacket(buf1.copy().retain(), address));

        final DatagramPacket read0 = ch.readOutbound();
        assertArrayEquals(
                toBytes(getContent(read0.content(), buf0)),
                toBytes(buf0),
                "First sent packet content is wrong");
        final DatagramPacket read1 = ch.readOutbound();
        assertArrayEquals(
                toBytes(getContent(read1.content(), buf1)),
                toBytes(buf1),
                "Second sent packet content is wrong");
        final DatagramPacket read2 = ch.readOutbound();
        assertArrayEquals(
                toBytes(getContent(read1.content(), buf1)),
                toBytes(buf1),
                "Third sent packet content is wrong");
        final DatagramPacket read3 = ch.readOutbound();
        assertArrayEquals(
                toBytes(getContent(read1.content(), buf1)),
                toBytes(buf1),
                "Fourth sent packet content is wrong");
        assertNull(ch.readOutbound(), "Sending packet which is not supposed to exist");

        // Send just 1 and 4, inbound should only be getting 1
        ch.writeInbound(read0.retain(), read3.retain());

        assertArrayEquals(
                toBytes(((DatagramPacket)ch.readInbound()).content()),
                toBytes(buf0),
                "First received packet content is wrong");
        assertNull(ch.readInbound(), "Received packet out of order");

        // Send 2 and 3, should be getting 2, 3, 4
        ch.writeInbound(read1.retain(), read2.retain());
        assertArrayEquals(
                toBytes(((DatagramPacket)ch.readInbound()).content()),
                toBytes(buf1),
                "Second received packet content is wrong");
        assertArrayEquals(
                toBytes(((DatagramPacket)ch.readInbound()).content()),
                toBytes(buf0),
                "Third received packet content is wrong");
        assertArrayEquals(
                toBytes(((DatagramPacket)ch.readInbound()).content()),
                toBytes(buf1),
                "Fourth received packet content is wrong");
    }

    @Test
    void testFlow() {

        final DatagramSlidingWindowHandler handler = new DatagramSlidingWindowHandler(0, 2);
        final EmbeddedChannel ch = new EmbeddedChannel(
                handler.getInboundHandler(),
                handler.getOutboundHandler());

        final InetSocketAddress address = new InetSocketAddress(12);
        final ByteBuf buf0 = Unpooled.copyBoolean(true);
        final ByteBuf buf1 = Unpooled.copyBoolean(false);

        ch.writeOutbound(
                new DatagramPacket(buf0.copy().retain(), address),
                new DatagramPacket(buf1.copy().retain(), address),
                new DatagramPacket(buf0.copy().retain(), address),
                new DatagramPacket(buf1.copy().retain(), address),
                new DatagramPacket(buf0.copy().retain(), address));

        // Read the first 2 packets
        final DatagramPacket read0 = ch.readOutbound();
        assertArrayEquals(
                toBytes(getContent(read0.content(), buf0)),
                toBytes(buf0),
                "First sent packet content is wrong");
        final DatagramPacket read1 = ch.readOutbound();
        assertArrayEquals(
                toBytes(getContent(read1.content(), buf1)),
                toBytes(buf1),
                "Second sent packet content is wrong");
        assertNull(ch.readOutbound(), "Sending too many packets, window size is not being respected");

        ch.writeInbound(read0.retain(), read1.retain());

        final DatagramPacket ack0 = ch.readOutbound();
        assertNotNull(ack0,  "First packet ack is null");
        assertArrayEquals(
                toBytes(((DatagramPacket)ch.readInbound()).content()),
                toBytes(buf0),
                "First received packet content is wrong");
        final DatagramPacket ack1 = ch.readOutbound();
        assertNotNull(ack1, "Second packet ack is null");
        assertArrayEquals(
                toBytes(((DatagramPacket)ch.readInbound()).content()),
                toBytes(buf1),
                "Second received packet content is wrong");

        ch.writeInbound(ack0, ack1);

        // Read the second 2 packets
        final DatagramPacket read2 = ch.readOutbound();
        assertArrayEquals(
                toBytes(getContent(read2.content(), buf0)),
                toBytes(buf0),
                "Third sent packet content is wrong");
        final DatagramPacket read3 = ch.readOutbound();
        assertArrayEquals(
                toBytes(getContent(read3.content(), buf1)),
                toBytes(buf1),
                "Fourth sent packet content is wrong");
        assertNull(ch.readOutbound(), "Sending too many packets, window size is not being respected");

        ch.writeInbound(read2.retain(), read3.retain());

        final DatagramPacket ack2 = ch.readOutbound();
        assertNotNull(ack2,  "Third packet ack is null");
        assertArrayEquals(
                toBytes(((DatagramPacket)ch.readInbound()).content()),
                toBytes(buf0),
                "Third received packet content is wrong");
        final DatagramPacket ack3 = ch.readOutbound();
        assertNotNull(ack3, "Fourth packet ack is null");
        assertArrayEquals(
                toBytes(((DatagramPacket)ch.readInbound()).content()),
                toBytes(buf1),
                "Fourth received packet content is wrong");

        ch.writeInbound(ack2, ack3);

        // Read the last packet
        final DatagramPacket read4 = ch.readOutbound();
        assertArrayEquals(
                toBytes(getContent(read4.content(), buf0)),
                toBytes(buf0),
                "Fifth sent packet content is wrong");
        assertNull(ch.readOutbound(), "Sending too many packets, window size is not being respected");

        ch.writeInbound(read4.retain());

        final DatagramPacket ack4 = ch.readOutbound();
        assertNotNull(ack0,  "Fifth packet ack is null");
        assertArrayEquals(
                toBytes(((DatagramPacket)ch.readInbound()).content()),
                toBytes(buf0),
                "Third received packet content is wrong");

        ch.writeInbound(ack4);
        assertNull(ch.readOutbound(), "Sending packet which is not supposed to exist");

        ch.finish();
    }

    private ByteBuf getContent(ByteBuf packet, ByteBuf actualContent) {
        return packet.slice(packet.writerIndex() - actualContent.readableBytes(), actualContent.readableBytes());
    }

    private byte[] toBytes(ByteBuf byteBuf) {
        final byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.getBytes(byteBuf.readerIndex(), bytes);
        return bytes;
    }
}