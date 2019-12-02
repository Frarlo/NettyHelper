package me.ferlo.netty.bytestuffing;

import io.netty.handler.codec.DecoderException;

/**
 * Exception raised by a {@link ByteStuffingDecoder} when a frame gets discarded
 * because it's not correctly encoded
 *
 * @author Ferlo
 *
 * @see ByteStuffingDecoder
 */
public class DelimiterDecoderException extends DecoderException {

    public DelimiterDecoderException() {
    }

    public DelimiterDecoderException(String message, Throwable cause) {
        super(message, cause);
    }

    public DelimiterDecoderException(String message) {
        super(message);
    }

    public DelimiterDecoderException(Throwable cause) {
        super(cause);
    }
}
