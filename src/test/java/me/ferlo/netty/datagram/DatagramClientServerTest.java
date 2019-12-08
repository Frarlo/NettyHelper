package me.ferlo.netty.datagram;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ResourceLeakDetector;
import me.ferlo.netty.CustomByteBuf;
import me.ferlo.netty.core.Packet;
import me.ferlo.netty.core.PacketIdService;
import me.ferlo.netty.core.PacketIdServiceMap;
import me.ferlo.netty.core.cpacket.CPacket;
import me.ferlo.netty.core.cpacket.CPacketParser;
import me.ferlo.netty.core.spacket.SPacket;
import me.ferlo.netty.core.spacket.SPacketParser;
import me.ferlo.utils.FutureUtils;
import me.ferlo.utils.NetUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class DatagramClientServerTest {

    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int LENGTH = 10000;

    private ResourceLeakDetector.Level previous;

    @BeforeEach
    void setUp() {
        previous = ResourceLeakDetector.getLevel();
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }

    @AfterEach
    void tearDown() {
        ResourceLeakDetector.setLevel(previous);
    }

    @Test
    void testClientServer() throws IOException {

        final InetSocketAddress address = new InetSocketAddress("localhost", NetUtils.getUnboundUdpPort());

        final CompletableFuture<String> receivedString0 = new CompletableFuture<>();
        final CompletableFuture<String> receivedString1 = new CompletableFuture<>();
        final String generatedString = generateRandomString();

        Map<Class<? extends Packet>, Byte> packetIds = new HashMap<>();
        packetIds.put(BigFatPacket.class, (byte) 1);
        packetIds.put(BigFatReply.class, (byte) 1);

        final PacketIdService serverIdService = new PacketIdServiceMap(
                packetIds,
                Collections.singletonMap(BigFatPacket.class, BigFatPacket.PARSER));
        final PacketIdService clientIdService = new PacketIdServiceMap(
                packetIds,
                Collections.singletonMap(BigFatReply.class, BigFatReply.PARSER));

        DatagramServerService datagramServerService = null;
        DatagramClientService datagramClientService = null;
        try {
            datagramServerService = new DatagramServerService(
                    address.getPort(),
                    serverIdService::getPacketId,
                    serverIdService::getParserById,
                    new SimpleChannelInboundHandler<DatagramPacketContext>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacketContext msg) {
                            BigFatPacket packet = (BigFatPacket) msg.getPacket();
                            receivedString0.complete(packet.s);
                            msg.reply(new BigFatReply(packet), true);
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            receivedString0.completeExceptionally(cause);
                        }
                    });
            FutureUtils.getUnchecked(datagramServerService.startAsync());

            datagramClientService = new DatagramClientService(
                    address,
                    clientIdService::getPacketId,
                    clientIdService::getParserById,
                    new SimpleChannelInboundHandler<SPacket>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, SPacket msg) {
                            BigFatReply packet = (BigFatReply) msg;
                            receivedString1.complete(packet.s);
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            receivedString1.completeExceptionally(cause);
                        }
                    });
            FutureUtils.getUnchecked(datagramClientService.startAsync());

            datagramClientService.sendPacket(new BigFatPacket(generatedString), true);

            final String s0 = FutureUtils.getUnchecked(receivedString0, 5000, TimeUnit.MILLISECONDS);
            assertEquals(generatedString, s0, "Sent string and received differs (Client -> Server | reliable)");

            final String s1 = FutureUtils.getUnchecked(receivedString1, 5000, TimeUnit.MILLISECONDS);
            assertEquals(generatedString, s1, "Sent string and received differs (Server -> Client | reliable)");

        } catch (TimeoutException e) {
            fail("Execution took too long and timed out", e);
        } finally {
            if(datagramServerService != null)
                datagramServerService.close();
            if(datagramClientService != null)
                datagramClientService.close();
        }
    }

    private String generateRandomString() {
        final Random rn = new Random();
        final StringBuilder builder = new StringBuilder();

        for (int count = 0; count < LENGTH; count++) {
            int character = rn.nextInt(ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }

        return builder.toString();
    }

    static class BigFatPacket implements CPacket {

        private final String s;

        public BigFatPacket(String s) {
            this.s = s;
        }

        @Override
        public void writePacket(CustomByteBuf buf) {
            buf.writeString(s);
        }

        public static final CPacketParser PARSER = buf -> new BigFatPacket(buf.readString(LENGTH));

        @Override
        public String toString() {
            return "BigFatPacket{" +
                    "s='" + s + '\'' +
                    '}';
        }
    }


    static class BigFatReply implements SPacket {

        private final String s;

        public BigFatReply(BigFatPacket packet) {
            this.s = packet.s;
        }

        private BigFatReply(String s) {
            this.s = s;
        }

        @Override
        public void writePacket(CustomByteBuf buf) {
            buf.writeString(s);
        }

        public static final SPacketParser PARSER = buf -> new BigFatReply(buf.readString());

        @Override
        public String toString() {
            return "BigFatReply{" +
                    "s='" + s + '\'' +
                    '}';
        }
    }
}
