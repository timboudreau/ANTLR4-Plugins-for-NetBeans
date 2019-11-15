/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.common.cachefile;

import com.mastfrog.function.throwing.io.IOConsumer;
import com.mastfrog.function.throwing.io.IOFunction;
import static com.mastfrog.util.preconditions.Checks.nonNegative;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.BitSet;

/**
 *
 * @author Tim Boudreau
 */
public class CacheFileUtils {

    private final ByteBuffer numBuf = ByteBuffer.allocate(Long.BYTES);
    private final int magicNumber;

    CacheFileUtils() {
        this(0);
    }

    CacheFileUtils(int magicNumber) {
        this.magicNumber = magicNumber;
    }

    public <T, C extends WritableByteChannel & SeekableByteChannel> T read(C channel, IOFunction<CacheFileReader, T> reader) throws BadMagicNumberException, IOException {
        return read(channel, Integer.MAX_VALUE, reader);
    }

    public static CacheFileUtils create(int magic) {
        return new CacheFileUtils(magic);
    }

    public static CacheFileUtils create() {
        return new CacheFileUtils();
    }

    public <T, C extends WritableByteChannel & SeekableByteChannel> T read(C channel, int limit, IOFunction<CacheFileReader, T> reader) throws BadMagicNumberException, IOException {
        long origPos = channel.position();
        if (magicNumber != 0) {
            int magic = readInt(channel, numBuf);
            if (magic != this.magicNumber) {
                channel.position(origPos);
                throw new BadMagicNumberException(magicNumber, magic);
            }
        }
        int size = readInt(channel, numBuf);
        if (size < 0 || size > limit) {
            throw new IOException("Size out of range: " + size);
        }
        ByteBuffer buf = ByteBuffer.allocate(size);
        int readBytes = channel.read(buf);
        if (readBytes != size) {
            throw new IOException("Failed to read " + size + " bytes - got " + readBytes);
        }
        buf.flip();
        CacheFileReaderImpl impl = new CacheFileReaderImpl(buf);
        return reader.apply(impl);
    }

    private static String b2s(ByteBuffer buf) {
        int rem = buf.remaining();
        buf = buf.duplicate();
        byte[] b = new byte[rem];
        buf.get(b);
        return new String(b, UTF_8);
    }

    private class CacheFileReaderImpl implements CacheFileReader {

        final ByteBuffer buf;

        public CacheFileReaderImpl(ByteBuffer buf) {
            this.buf = buf;
        }

        @Override
        public byte[] readBytes(int length) {
            byte[] b = new byte[nonNegative("length", length)];
            buf.get(b);
            return b;
        }

        @Override
        public int readInt() {
            return buf.getInt();
        }

        @Override
        public long readLong() {
            return buf.getLong();
        }

        @Override
        public String readString() throws IOException {
            return CacheFileUtils.readString(buf, Integer.MAX_VALUE);
        }

        @Override
        public Path readPath() throws IOException {
            return CacheFileUtils.readPath(buf);
        }

        @Override
        public boolean[] readBooleans(int count) throws IOException {
            return readBooleanArray(count, buf);
        }

        @Override
        public BitSet readBitSet() throws IOException {
            return CacheFileUtils.readBitSet(buf, Integer.MAX_VALUE);
        }

        @Override
        public String readString(int maxBytes) throws IOException {
            return CacheFileUtils.readString(buf, maxBytes);
        }

        @Override
        public BitSet readBitSet(int maxBytes) throws IOException {
            return CacheFileUtils.readBitSet(buf, maxBytes);
        }
    }

    public <C extends WritableByteChannel & SeekableByteChannel> int write(C channel, IOConsumer<CacheFileWriter> w) throws IOException {
        long initialPosition = channel.position();
        if (magicNumber != 0) {
            writeNumber(magicNumber, channel, numBuf);
        }
        long lengthPos = channel.position();
        writeNumber(0, channel, numBuf);
        // reposition if exception
        int written = 0;
        try {
            CacheFileWriterImpl ww = new CacheFileWriterImpl(channel);
            w.accept(ww);
            written += ww.written;
        } catch (Exception | Error err) {
            channel.truncate(initialPosition);
            channel.position(initialPosition);
            return Exceptions.chuck(err);
        }
        long currentPos = channel.position();
        try {
            channel.position(lengthPos);
            writeNumber(written, channel, numBuf);
        } finally {
            channel.position(currentPos);
        }
        return written + (Integer.BYTES * 2);
    }

    class CacheFileWriterImpl<C extends WritableByteChannel & SeekableByteChannel> implements CacheFileWriter {

        int written = 0;
        final C channel;

        public CacheFileWriterImpl(C channel) {
            this.channel = channel;
        }

        @Override
        public CacheFileWriter writeString(String str) throws IOException {
            written += CacheFileUtils.writeString(str, channel);
            return this;
        }

        @Override
        public CacheFileWriter writePath(Path path) throws IOException {
            written += CacheFileUtils.writePath(path, channel);
            return this;
        }

        @Override
        public CacheFileWriter writeNumber(int number) throws IOException {
            written += CacheFileUtils.writeNumber(number, channel, numBuf);
            return this;
        }

        @Override
        public CacheFileWriter writeNumber(long number) throws IOException {
            written += CacheFileUtils.writeNumber(number, channel, numBuf);
            return this;
        }

        @Override
        public CacheFileWriter writeBooleanArray(boolean[] arr) throws IOException {
            written += CacheFileUtils.writeBooleanArray(arr, channel, numBuf);
            return this;
        }

        @Override
        public CacheFileWriter writeBitSet(BitSet bitSet) throws IOException {
            written += CacheFileUtils.writeBitSet(bitSet, channel, numBuf);
            return this;
        }
    }

    public static <C extends ReadableByteChannel & SeekableByteChannel> Path readPath(C channel, ByteBuffer numBuf) throws IOException {
        int len = readInt(channel, numBuf);
        byte[] bytes = new byte[len];
        channel.read(ByteBuffer.wrap(bytes));
        return Paths.get(new String(bytes, UTF_8));
    }

    public static Path readPath(ByteBuffer buf) throws IOException {
        int len = buf.getInt();
        if (len < 0 || len > 2048) {
            throw new IOException("Absurdly long path entry: " + len);
        }
        byte[] bytes = new byte[len];
        buf.get(bytes);
        Path p = Paths.get(new String(bytes, UTF_8));
        return p;
    }

    public static <C extends WritableByteChannel & SeekableByteChannel> int writePath(Path path, C channel) throws IOException {
        return writeString(path == null ? null : path.toString(), channel);
    }

    public static String readString(ByteBuffer buf) throws IOException {
        return readString(buf, 1536);
    }

    public static String readString(ByteBuffer buf, int limit) throws IOException {
        if (buf.remaining() < Integer.BYTES) {
            throw new IOException("Attempt to read string length from a buffer "
                    + "with only " + buf.remaining() + " bytes remaining");
        }
        int length = buf.getInt();
        if (length == -1) {
            return null;
        }
        if (length < 0 || length > limit) {
            throw new IOException("Absurd path length " + length);
        }
        if (buf.remaining() < length) {
            throw new IOException("Attempting to get a string of length " + length
                    + " bytes but only " + buf.remaining() + " bytes remain.");
        }
        byte[] bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, UTF_8);
    }

    public static <T extends WritableByteChannel & SeekableByteChannel> int readInt(T channel, ByteBuffer numberBuf) throws IOException {
        numberBuf.rewind();
        boolean limit = numberBuf.capacity() > Integer.BYTES;
        int oldLimit = numberBuf.limit();
        if (limit) {
            numberBuf.limit(Integer.BYTES);
        }
        int readBytes = channel.read(numberBuf);
        if (readBytes != Integer.BYTES) {
            throw new IOException("Read wrong number of bytes for int: " + readBytes);
        }
        numberBuf.flip();
        int result = numberBuf.getInt();
        numberBuf.rewind();
        if (limit) {
            numberBuf.limit(oldLimit);
        }
        return result;
    }

    public static <T extends WritableByteChannel & SeekableByteChannel> int writeNumber(int num, T channel, ByteBuffer numberBuf) throws IOException {
        numberBuf.putInt(num);
        numberBuf.flip();
        int result = channel.write(numberBuf);
        numberBuf.rewind();
        return result;
    }

    public static <T extends WritableByteChannel & SeekableByteChannel> int writeNumber(long num, T channel, ByteBuffer numberBuf) throws IOException {
        numberBuf.rewind();
        numberBuf.limit(Long.BYTES);
        numberBuf.putLong(num);
        numberBuf.flip();
        int result = channel.write(numberBuf);
        numberBuf.rewind();
        return result;
    }

    public static BitSet readBitSet(ByteBuffer buffer, int limit) throws IOException {
        int outCount = buffer.getInt();
        if (outCount < 0 || outCount > limit) {
            throw new IOException("Absurd byte count for bitset: "
                    + outCount + " at " + (buffer.position() - Integer.BYTES)
                    + " remaining: " + b2s(buffer));
        }
        byte[] bts = new byte[outCount];
        buffer.get(bts);
        BitSet outs = BitSet.valueOf(bts);
        return outs;
    }

    public static <C extends WritableByteChannel & SeekableByteChannel> int writeBitSet(BitSet set, C channel, ByteBuffer numBuffer) throws IOException {
        byte[] bts = set.toByteArray();
        int written = writeNumber(bts.length, channel, numBuffer);
        if (bts.length > 0) {
            written += channel.write(ByteBuffer.wrap(bts));
        }
        return written;
    }

    public static boolean[] readBooleanArray(int size, ByteBuffer buf) throws IOException {
        BitSet bits = readBitSet(buf, Math.max(1, size / Long.SIZE));
        boolean[] result = new boolean[size];
        for (int i = 0; i < size; i++) {
            if (bits.get(i)) {
                result[i] = true;
            }
        }
        return result;
    }

    public static <T extends WritableByteChannel & SeekableByteChannel> int writeBooleanArray(boolean[] val, T channel, ByteBuffer numberBuf) throws IOException {
        BitSet bits = new BitSet(val.length);
        for (int i = 0; i < val.length; i++) {
            if (val[i]) {
                bits.set(i);
            }
        }
        return writeBitSet(bits, channel, numberBuf);
    }

    public static <T extends WritableByteChannel & SeekableByteChannel> int writeString(String val, T channel) throws IOException {
        if (val == null) {
            return writeNumber(-1, channel, ByteBuffer.allocate(Integer.BYTES));
        } else {
            byte[] bytes = val.getBytes(UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(bytes.length + Integer.BYTES);
            buf.putInt(bytes.length);
            buf.put(bytes);
            buf.flip();
            channel.write(buf);
            return buf.capacity();
        }
    }
}
