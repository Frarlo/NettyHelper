package me.ferlo.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ByteProcessor;
import io.netty.util.Recycler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CustomByteBuf extends ByteBuf {

    private static final Recycler<CustomByteBuf> RECYCLER = new Recycler<CustomByteBuf>() {
        protected CustomByteBuf newObject(Recycler.Handle<CustomByteBuf> handle) {
            return new CustomByteBuf(handle);
        }
    };

    private CustomByteBuf(Recycler.Handle<CustomByteBuf> handle) {
        this.handle = handle;
    }

    public static CustomByteBuf get(ByteBuf wrapped) {
        final CustomByteBuf buff = RECYCLER.get();
        buff.wrapped = wrapped;
        return buff;
    }

    private final Recycler.Handle<CustomByteBuf> handle;
    private ByteBuf wrapped;

    public void recycle() {
        wrapped = null;
        handle.recycle(this);
    }

    /**
     * Write the given UTF-8 string in the buffer
     *
     * @param string string to write
     */
    public CustomByteBuf writeString(String string) {
        final byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeInt(bytes.length);
        writeBytes(bytes);
        return this;
    }

    /**
     * Read a UTF-8 string from the buffer
     *
     * @param maxLength max length to read (or -1 to read any length)
     * @return string read from the buffer
     * @throws DecoderException if the string length exceeded maxLength
     */
    public String tryReadString(int maxLength) {
        final int length = readInt();

        if(maxLength != -1 && maxLength < length)
            throw new DecoderException(String.format(
                    "String is too long! (length: %s, maxLength: %s)",
                    length, maxLength
            ));

        return readSlice(length).toString(StandardCharsets.UTF_8);
    }

    /**
     * Read a UTF-8 string from the buffer
     *
     * @param maxLength max length to read (or -1 to read any length)
     * @return string read from the buffer or an empty if the string length exceeded maxLength
     */
    public String readString(int maxLength) {
        try {
            return tryReadString(maxLength);
        } catch (DecoderException ex) {
            return "";
        }
    }

    /**
     * Read a UTF-8 string from the buffer
     *
     * @return string read from the buffer
     */
    public String readString() {
        return readString(-1);
    }

    public CustomByteBuf writeCInt(int integer) {
        if(integer < -32768 || integer > 32767)
            throw new RuntimeException("Integer is too large");

        writeByte((byte) integer);
        writeByte((byte) (integer >> 8));
        return this;
    }

    // Delegate

    @Override
    public int capacity() {
        return wrapped.capacity();
    }

    @Override
    public CustomByteBuf capacity(int newCapacity) {
        wrapped.capacity(newCapacity);
        return this;
    }

    @Override
    public int maxCapacity() {
        return wrapped.maxCapacity();
    }

    @Override
    public ByteBufAllocator alloc() {
        return wrapped.alloc();
    }

    @Override
    @Deprecated
    public ByteOrder order() {
        return wrapped.order();
    }

    @Override
    @Deprecated
    public CustomByteBuf order(ByteOrder endianness) {
        return CustomByteBuf.get(wrapped.order(endianness));
    }

    @Override
    public ByteBuf unwrap() {
        return wrapped.unwrap();
    }

    @Override
    public boolean isDirect() {
        return wrapped.isDirect();
    }

    @Override
    public boolean isReadOnly() {
        return wrapped.isReadOnly();
    }

    @Override
    public CustomByteBuf asReadOnly() {
        return CustomByteBuf.get(wrapped.asReadOnly());
    }

    @Override
    public int readerIndex() {
        return wrapped.readerIndex();
    }

    @Override
    public CustomByteBuf readerIndex(int readerIndex) {
        wrapped.readerIndex(readerIndex);
        return this;
    }

    @Override
    public int writerIndex() {
        return wrapped.writerIndex();
    }

    @Override
    public CustomByteBuf writerIndex(int writerIndex) {
        wrapped.writerIndex(writerIndex);
        return this;
    }

    @Override
    public CustomByteBuf setIndex(int readerIndex, int writerIndex) {
        wrapped.setIndex(readerIndex, writerIndex);
        return this;
    }

    @Override
    public int readableBytes() {
        return wrapped.readableBytes();
    }

    @Override
    public int writableBytes() {
        return wrapped.writableBytes();
    }

    @Override
    public int maxWritableBytes() {
        return wrapped.maxWritableBytes();
    }

    @Override
    public boolean isReadable() {
        return wrapped.isReadable();
    }

    @Override
    public boolean isReadable(int size) {
        return wrapped.isReadable(size);
    }

    @Override
    public boolean isWritable() {
        return wrapped.isWritable();
    }

    @Override
    public boolean isWritable(int size) {
        return wrapped.isWritable(size);
    }

    @Override
    public CustomByteBuf clear() {
        wrapped.clear();
        return this;
    }

    @Override
    public CustomByteBuf markReaderIndex() {
        wrapped.markReaderIndex();
        return this;
    }

    @Override
    public CustomByteBuf resetReaderIndex() {
        wrapped.resetReaderIndex();
        return this;
    }

    @Override
    public CustomByteBuf markWriterIndex() {
        wrapped.markWriterIndex();
        return this;
    }

    @Override
    public CustomByteBuf resetWriterIndex() {
        wrapped.resetWriterIndex();
        return this;
    }

    @Override
    public CustomByteBuf discardReadBytes() {
        wrapped.discardReadBytes();
        return this;
    }

    @Override
    public CustomByteBuf discardSomeReadBytes() {
        wrapped.discardSomeReadBytes();
        return this;
    }

    @Override
    public CustomByteBuf ensureWritable(int minWritableBytes) {
        wrapped.ensureWritable(minWritableBytes);
        return this;
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        return wrapped.ensureWritable(minWritableBytes, force);
    }

    @Override
    public boolean getBoolean(int index) {
        return wrapped.getBoolean(index);
    }

    @Override
    public byte getByte(int index) {
        return wrapped.getByte(index);
    }

    @Override
    public short getUnsignedByte(int index) {
        return wrapped.getUnsignedByte(index);
    }

    @Override
    public short getShort(int index) {
        return wrapped.getShort(index);
    }

    @Override
    public short getShortLE(int index) {
        return wrapped.getShortLE(index);
    }

    @Override
    public int getUnsignedShort(int index) {
        return wrapped.getUnsignedShort(index);
    }

    @Override
    public int getUnsignedShortLE(int index) {
        return wrapped.getUnsignedShortLE(index);
    }

    @Override
    public int getMedium(int index) {
        return wrapped.getMedium(index);
    }

    @Override
    public int getMediumLE(int index) {
        return wrapped.getMediumLE(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return wrapped.getUnsignedMedium(index);
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        return wrapped.getUnsignedMediumLE(index);
    }

    @Override
    public int getInt(int index) {
        return wrapped.getInt(index);
    }

    @Override
    public int getIntLE(int index) {
        return wrapped.getIntLE(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        return wrapped.getUnsignedInt(index);
    }

    @Override
    public long getUnsignedIntLE(int index) {
        return wrapped.getUnsignedIntLE(index);
    }

    @Override
    public long getLong(int index) {
        return wrapped.getLong(index);
    }

    @Override
    public long getLongLE(int index) {
        return wrapped.getLongLE(index);
    }

    @Override
    public char getChar(int index) {
        return wrapped.getChar(index);
    }

    @Override
    public float getFloat(int index) {
        return wrapped.getFloat(index);
    }

    @Override
    public float getFloatLE(int index) {
        return wrapped.getFloatLE(index);
    }

    @Override
    public double getDouble(int index) {
        return wrapped.getDouble(index);
    }

    @Override
    public double getDoubleLE(int index) {
        return wrapped.getDoubleLE(index);
    }

    @Override
    public CustomByteBuf getBytes(int index, ByteBuf dst) {
        final ByteBuf res = wrapped.getBytes(index, dst);
        if(res instanceof CustomByteBuf)
            return (CustomByteBuf) res;
        return CustomByteBuf.get(res);
    }

    @Override
    public CustomByteBuf getBytes(int index, ByteBuf dst, int length) {
        final ByteBuf res = wrapped.getBytes(index, dst, length);
        if(res instanceof CustomByteBuf)
            return (CustomByteBuf) res;
        return CustomByteBuf.get(res);
    }

    @Override
    public CustomByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        final ByteBuf res = wrapped.getBytes(index, dst, dstIndex, length);
        if(res instanceof CustomByteBuf)
            return (CustomByteBuf) res;
        return CustomByteBuf.get(res);
    }

    @Override
    public CustomByteBuf getBytes(int index, byte[] dst) {
        return CustomByteBuf.get(wrapped.getBytes(index, dst));
    }

    @Override
    public CustomByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        return CustomByteBuf.get(wrapped.getBytes(index, dst, dstIndex, length));
    }

    @Override
    public CustomByteBuf getBytes(int index, ByteBuffer dst) {
        return CustomByteBuf.get(wrapped.getBytes(index, dst));
    }

    @Override
    public CustomByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        return CustomByteBuf.get(wrapped.getBytes(index, out, length));
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return wrapped.getBytes(index, out, length);
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return wrapped.getBytes(index, out, position, length);
    }

    @Override
    public CharSequence getCharSequence(int index, int length, Charset charset) {
        return wrapped.getCharSequence(index, length, charset);
    }

    @Override
    public CustomByteBuf setBoolean(int index, boolean value) {
        wrapped.setBoolean(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setByte(int index, int value) {
        wrapped.setByte(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setShort(int index, int value) {
        wrapped.setShort(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setShortLE(int index, int value) {
        wrapped.setShortLE(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setMedium(int index, int value) {
        wrapped.setMedium(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setMediumLE(int index, int value) {
        wrapped.setMediumLE(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setInt(int index, int value) {
        wrapped.setInt(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setIntLE(int index, int value) {
        wrapped.setIntLE(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setLong(int index, long value) {
        wrapped.setLong(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setLongLE(int index, long value) {
        wrapped.setLongLE(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setChar(int index, int value) {
        wrapped.setChar(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setFloat(int index, float value) {
        wrapped.setFloat(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setFloatLE(int index, float value) {
        wrapped.setFloatLE(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setDouble(int index, double value) {
        wrapped.setDouble(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setDoubleLE(int index, double value) {
        wrapped.setDoubleLE(index, value);
        return this;
    }

    @Override
    public CustomByteBuf setBytes(int index, ByteBuf src) {
        wrapped.setBytes(index, src);
        return this;
    }

    @Override
    public CustomByteBuf setBytes(int index, ByteBuf src, int length) {
        wrapped.setBytes(index, src, length);
        return this;
    }

    @Override
    public CustomByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        wrapped.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public CustomByteBuf setBytes(int index, byte[] src) {
        wrapped.setBytes(index, src);
        return this;
    }

    @Override
    public CustomByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        wrapped.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public CustomByteBuf setBytes(int index, ByteBuffer src) {
        wrapped.setBytes(index, src);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return wrapped.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        return wrapped.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
        return wrapped.setBytes(index, in, position, length);
    }

    @Override
    public CustomByteBuf setZero(int index, int length) {
        wrapped.setZero(index, length);
        return this;
    }

    @Override
    public int setCharSequence(int index, CharSequence sequence, Charset charset) {
        return wrapped.setCharSequence(index, sequence, charset);
    }

    @Override
    public boolean readBoolean() {
        return wrapped.readBoolean();
    }

    @Override
    public byte readByte() {
        return wrapped.readByte();
    }

    @Override
    public short readUnsignedByte() {
        return wrapped.readUnsignedByte();
    }

    @Override
    public short readShort() {
        return wrapped.readShort();
    }

    @Override
    public short readShortLE() {
        return wrapped.readShortLE();
    }

    @Override
    public int readUnsignedShort() {
        return wrapped.readUnsignedShort();
    }

    @Override
    public int readUnsignedShortLE() {
        return wrapped.readUnsignedShortLE();
    }

    @Override
    public int readMedium() {
        return wrapped.readMedium();
    }

    @Override
    public int readMediumLE() {
        return wrapped.readMediumLE();
    }

    @Override
    public int readUnsignedMedium() {
        return wrapped.readUnsignedMedium();
    }

    @Override
    public int readUnsignedMediumLE() {
        return wrapped.readUnsignedMediumLE();
    }

    @Override
    public int readInt() {
        return wrapped.readInt();
    }

    @Override
    public int readIntLE() {
        return wrapped.readIntLE();
    }

    @Override
    public long readUnsignedInt() {
        return wrapped.readUnsignedInt();
    }

    @Override
    public long readUnsignedIntLE() {
        return wrapped.readUnsignedIntLE();
    }

    @Override
    public long readLong() {
        return wrapped.readLong();
    }

    @Override
    public long readLongLE() {
        return wrapped.readLongLE();
    }

    @Override
    public char readChar() {
        return wrapped.readChar();
    }

    @Override
    public float readFloat() {
        return wrapped.readFloat();
    }

    @Override
    public float readFloatLE() {
        return wrapped.readFloatLE();
    }

    @Override
    public double readDouble() {
        return wrapped.readDouble();
    }

    @Override
    public double readDoubleLE() {
        return wrapped.readDoubleLE();
    }

    @Override
    public CustomByteBuf readBytes(int length) {
        return CustomByteBuf.get(wrapped.readBytes(length));
    }

    @Override
    public CustomByteBuf readSlice(int length) {
        return CustomByteBuf.get(wrapped.readSlice(length));
    }

    @Override
    public CustomByteBuf readRetainedSlice(int length) {
        return CustomByteBuf.get(wrapped.readRetainedSlice(length));
    }

    @Override
    public CustomByteBuf readBytes(ByteBuf dst) {
        wrapped.readBytes(dst);
        return this;
    }

    @Override
    public CustomByteBuf readBytes(ByteBuf dst, int length) {
        wrapped.readBytes(dst, length);
        return this;
    }

    @Override
    public CustomByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        wrapped.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public CustomByteBuf readBytes(byte[] dst) {
        wrapped.readBytes(dst);
        return this;
    }

    @Override
    public CustomByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        wrapped.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public CustomByteBuf readBytes(ByteBuffer dst) {
        wrapped.readBytes(dst);
        return this;
    }

    @Override
    public CustomByteBuf readBytes(OutputStream out, int length) throws IOException {
        wrapped.readBytes(out, length);
        return this;
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        return wrapped.readBytes(out, length);
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        return wrapped.readCharSequence(length, charset);
    }

    @Override
    public int readBytes(FileChannel out, long position, int length) throws IOException {
        return wrapped.readBytes(out, position, length);
    }

    @Override
    public CustomByteBuf skipBytes(int length) {
        wrapped.skipBytes(length);
        return this;
    }

    @Override
    public CustomByteBuf writeBoolean(boolean value) {
        wrapped.writeBoolean(value);
        return this;
    }

    @Override
    public CustomByteBuf writeByte(int value) {
        wrapped.writeByte(value);
        return this;
    }

    @Override
    public CustomByteBuf writeShort(int value) {
        wrapped.writeShort(value);
        return this;
    }

    @Override
    public CustomByteBuf writeShortLE(int value) {
        wrapped.writeShortLE(value);
        return this;
    }

    @Override
    public CustomByteBuf writeMedium(int value) {
        wrapped.writeMedium(value);
        return this;
    }

    @Override
    public CustomByteBuf writeMediumLE(int value) {
        wrapped.writeMediumLE(value);
        return this;
    }

    @Override
    public CustomByteBuf writeInt(int value) {
        wrapped.writeInt(value);
        return this;
    }

    @Override
    public CustomByteBuf writeIntLE(int value) {
        wrapped.writeIntLE(value);
        return this;
    }

    @Override
    public CustomByteBuf writeLong(long value) {
        wrapped.writeLong(value);
        return this;
    }

    @Override
    public CustomByteBuf writeLongLE(long value) {
        wrapped.writeLongLE(value);
        return this;
    }

    @Override
    public CustomByteBuf writeChar(int value) {
        wrapped.writeChar(value);
        return this;
    }

    @Override
    public CustomByteBuf writeFloat(float value) {
        wrapped.writeFloat(value);
        return this;
    }

    @Override
    public CustomByteBuf writeFloatLE(float value) {
        wrapped.writeFloatLE(value);
        return this;
    }

    @Override
    public CustomByteBuf writeDouble(double value) {
        wrapped.writeDouble(value);
        return this;
    }

    @Override
    public CustomByteBuf writeDoubleLE(double value) {
        wrapped.writeDoubleLE(value);
        return this;
    }

    @Override
    public CustomByteBuf writeBytes(ByteBuf src) {
        wrapped.writeBytes(src);
        return this;
    }

    @Override
    public CustomByteBuf writeBytes(ByteBuf src, int length) {
        wrapped.writeBytes(src, length);
        return this;
    }

    @Override
    public CustomByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        wrapped.writeBytes(src, srcIndex, length);
        return this;
    }

    @Override
    public CustomByteBuf writeBytes(byte[] src) {
        wrapped.writeBytes(src);
        return this;
    }

    @Override
    public CustomByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        wrapped.writeBytes(src, srcIndex, length);
        return this;
    }

    @Override
    public CustomByteBuf writeBytes(ByteBuffer src) {
        wrapped.writeBytes(src);
        return this;
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        return wrapped.writeBytes(in, length);
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        return wrapped.writeBytes(in, length);
    }

    @Override
    public int writeBytes(FileChannel in, long position, int length) throws IOException {
        return wrapped.writeBytes(in, position, length);
    }

    @Override
    public CustomByteBuf writeZero(int length) {
        wrapped.writeZero(length);
        return this;
    }

    @Override
    public int writeCharSequence(CharSequence sequence, Charset charset) {
        return wrapped.writeCharSequence(sequence, charset);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        return wrapped.indexOf(fromIndex, toIndex, value);
    }

    @Override
    public int bytesBefore(byte value) {
        return wrapped.bytesBefore(value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        return wrapped.bytesBefore(length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        return wrapped.bytesBefore(index, length, value);
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        return wrapped.forEachByte(processor);
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        return wrapped.forEachByte(index, length, processor);
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        return wrapped.forEachByteDesc(processor);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        return wrapped.forEachByteDesc(index, length, processor);
    }

    @Override
    public CustomByteBuf copy() {
        return CustomByteBuf.get(wrapped.copy());
    }

    @Override
    public CustomByteBuf copy(int index, int length) {
        return CustomByteBuf.get(wrapped.copy(index, length));
    }

    @Override
    public CustomByteBuf slice() {
        return CustomByteBuf.get(wrapped.slice());
    }

    @Override
    public CustomByteBuf retainedSlice() {
        return CustomByteBuf.get(wrapped.retainedSlice());
    }

    @Override
    public CustomByteBuf slice(int index, int length) {
        return CustomByteBuf.get(wrapped.slice(index, length));
    }

    @Override
    public CustomByteBuf retainedSlice(int index, int length) {
        return CustomByteBuf.get(wrapped.retainedSlice(index, length));
    }

    @Override
    public CustomByteBuf duplicate() {
        return CustomByteBuf.get(wrapped.duplicate());
    }

    @Override
    public CustomByteBuf retainedDuplicate() {
        return CustomByteBuf.get(wrapped.retainedDuplicate());
    }

    @Override
    public int nioBufferCount() {
        return wrapped.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer() {
        return wrapped.nioBuffer();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return wrapped.nioBuffer(index, length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return wrapped.internalNioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        return wrapped.nioBuffers();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return wrapped.nioBuffers(index, length);
    }

    @Override
    public boolean hasArray() {
        return wrapped.hasArray();
    }

    @Override
    public byte[] array() {
        return wrapped.array();
    }

    @Override
    public int arrayOffset() {
        return wrapped.arrayOffset();
    }

    @Override
    public boolean hasMemoryAddress() {
        return wrapped.hasMemoryAddress();
    }

    @Override
    public long memoryAddress() {
        return wrapped.memoryAddress();
    }

    @Override
    public String toString(Charset charset) {
        return wrapped.toString(charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        return wrapped.toString(index, length, charset);
    }

    @Override
    public int hashCode() {
        return wrapped.hashCode();
    }

    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object obj) {
        return wrapped.equals(obj);
    }

    @Override
    public int compareTo(ByteBuf buffer) {
        return wrapped.compareTo(buffer);
    }

    @Override
    public String toString() {
        return wrapped.toString();
    }

    @Override
    public CustomByteBuf retain(int increment) {
        wrapped.retain(increment);
        return this;
    }

    @Override
    public CustomByteBuf retain() {
        wrapped.retain();
        return this;
    }

    @Override
    public CustomByteBuf touch() {
        wrapped.touch();
        return this;
    }

    @Override
    public CustomByteBuf touch(Object hint) {
        wrapped.touch(hint);
        return this;
    }

    @Override
    public int refCnt() {
        return wrapped.refCnt();
    }

    @Override
    public boolean release() {
        final boolean res = wrapped.release();
        if(res)
            recycle();
        return res;
    }

    @Override
    public boolean release(int decrement) {
        final boolean res = wrapped.release(decrement);
        if(res)
            recycle();
        return res;
    }
}
