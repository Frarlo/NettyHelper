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
import static org.junit.jupiter.api.Assertions.*;

class ByteStuffingDecoderTest {

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
    void testDecode() {
        final byte escape = 'x';
        final byte start = 'y';
        final byte end = 'z';

        final EmbeddedChannel ch = new EmbeddedChannel(new ByteStuffingDecoder(escape, start, end));

        ch.writeInbound(Unpooled.copiedBuffer("" +
                        (char)escape + "" + (char)start + "first" + (char)escape + "" + (char)end +
                        (char)escape + "" + (char)start + "second" + (char)escape + "" + (char)end +
                        (char)escape + "" + (char)start + "third",
                CharsetUtil.US_ASCII));

        assertEquals("first", releaseLater((ByteBuf) ch.readInbound()).toString(CharsetUtil.US_ASCII));
        assertEquals("second", releaseLater((ByteBuf) ch.readInbound()).toString(CharsetUtil.US_ASCII));
        assertNull(ch.readInbound());

        ch.finish();
    }

    @Test
    void testMissingStartDecode() {
        final byte escape = 'x';
        final byte start = 'y';
        final byte end = 'z';

        final EmbeddedChannel ch = new EmbeddedChannel(new ByteStuffingDecoder(escape, start, end));

        assertThrows(DelimiterDecoderException.class, () -> ch.writeInbound(Unpooled.copiedBuffer("" +
                        "first" + (char)escape + "" + (char)end +
                        (char)escape + "" + (char)start + "second" + (char)escape + "" + (char)end,
                CharsetUtil.US_ASCII)));
        assertThrows(DelimiterDecoderException.class, () -> ch.writeInbound(Unpooled.copiedBuffer("" +
                        (char)escape + "" + (char)start + "first" + (char)escape + "" + (char)end +
                        "second" + (char)escape + "" + (char)end,
                CharsetUtil.US_ASCII)));

        ch.finish();

        ByteBuf read;
        while((read = ch.readInbound()) != null)
            ReferenceCountUtil.release(read);
    }

    @Test
    void testMissingEndDecode() {
        final byte escape = 'x';
        final byte start = 'y';
        final byte end = 'z';

        final EmbeddedChannel ch = new EmbeddedChannel(new ByteStuffingDecoder(escape, start, end));

        assertThrows(DelimiterDecoderException.class, () -> ch.writeInbound(Unpooled.copiedBuffer("" +
                        (char)escape + "" + (char)start + "first" +
                        (char)escape + "" + (char)start + "second" + (char)escape + "" + (char)end,
                CharsetUtil.US_ASCII)));

        ch.finish();

        ByteBuf read;
        while((read = ch.readInbound()) != null)
            ReferenceCountUtil.release(read);
    }
}