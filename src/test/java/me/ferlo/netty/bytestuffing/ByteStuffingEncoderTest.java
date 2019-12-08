package me.ferlo.netty.bytestuffing;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.netty.util.ReferenceCountUtil.releaseLater;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ByteStuffingEncoderTest {

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
    @SuppressWarnings("deprecation")
    void testEncode() {
        final byte escape = 'x';
        final byte start = 'y';
        final byte end = 'z';

        final EmbeddedChannel ch = new EmbeddedChannel(new ByteStuffingEncoder(escape, start, end));

        ch.writeOutbound(Unpooled.copiedBuffer("first", CharsetUtil.US_ASCII));
        ch.writeOutbound(Unpooled.copiedBuffer("second", CharsetUtil.US_ASCII));
        ch.writeOutbound(Unpooled.copiedBuffer("third", CharsetUtil.US_ASCII));

        assertEquals((char) escape + "" + (char) start + "first" + (char) escape + "" + (char) end,
                releaseLater((ByteBuf) ch.readOutbound()).toString(CharsetUtil.US_ASCII));
        assertEquals((char) escape + "" + (char) start + "second" + (char) escape + "" + (char) end,
                releaseLater((ByteBuf) ch.readOutbound()).toString(CharsetUtil.US_ASCII));
        assertEquals((char) escape + "" + (char) start + "third" + (char) escape + "" + (char) end,
                releaseLater((ByteBuf) ch.readOutbound()).toString(CharsetUtil.US_ASCII));
        ch.finish();

        ReferenceCountUtil.release(ch.readInbound());
    }

    @Test
    @SuppressWarnings("deprecation")
    void testStuffing() {
        final byte escape = 'i';
        final byte start = 'y';
        final byte end = 'z';

        final EmbeddedChannel ch = new EmbeddedChannel(new ByteStuffingEncoder(escape, start, end));

        ch.writeOutbound(Unpooled.copiedBuffer("first", CharsetUtil.US_ASCII));
        ch.writeOutbound(Unpooled.copiedBuffer("second", CharsetUtil.US_ASCII));
        ch.writeOutbound(Unpooled.copiedBuffer("third", CharsetUtil.US_ASCII));

        assertEquals((char) escape + "" + (char) start + "fiirst" + (char) escape + "" + (char) end,
                releaseLater((ByteBuf) ch.readOutbound()).toString(CharsetUtil.US_ASCII));
        assertEquals((char) escape + "" + (char) start + "second" + (char) escape + "" + (char) end,
                releaseLater((ByteBuf) ch.readOutbound()).toString(CharsetUtil.US_ASCII));
        assertEquals((char) escape + "" + (char) start + "thiird" + (char) escape + "" + (char) end,
                releaseLater((ByteBuf) ch.readOutbound()).toString(CharsetUtil.US_ASCII));
        ch.finish();

        ReferenceCountUtil.release(ch.readInbound());
    }
}