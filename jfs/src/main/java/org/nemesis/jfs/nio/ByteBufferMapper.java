package org.nemesis.jfs.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Function;
import org.nemesis.range.IntRange;

/**
 *
 * @author Tim Boudreau
 */
class ByteBufferMapper {

    final BlockToBytesConverter bytesMapper;
    private ByteBufferAllocator alloc;
    private final Ops ops;

    ByteBufferMapper(BlockToBytesConverter bytesMapper, int initialBlockCount, boolean direct, Ops ops) throws IOException {
        this(bytesMapper, initialBlockCount, new ByteBufferAllocator.DefaultByteBufferAllocator(direct, ops), ops);
    }

    ByteBufferMapper(BlockToBytesConverter bytesMapper, int initialBlockCount, ByteBufferAllocator alloc, Ops ops) throws IOException {
        this.ops = ops;
        this.bytesMapper = bytesMapper;
        this.alloc = alloc;
        alloc.allocate(bytesMapper.blocksToBytes(initialBlockCount));
    }

    void expand(int oldSizeInBytes, int newSizeInBytes, int copyThru) throws IOException {
        ops.set("bbm-expand {0} to {1} copying {2}", oldSizeInBytes, newSizeInBytes, copyThru);
        alloc.grow(newSizeInBytes, copyThru);
    }

    void ensureBuffer(int bufferSize, int copyBytesCount) throws IOException {
        alloc.ensureSize(bufferSize, copyBytesCount);
    }

    public void close() throws IOException {
        alloc.close();
    }

    ByteBuffer sliceAndSnapshot(Blocks blocks) {
        IntRange<?> phys = blocks.toPhysicalRange(bytesMapper);
        return sliceAndSnapshot(phys.start(), phys.end());
    }

    ByteBuffer slice(Blocks blocks) {
        IntRange<?> phys = blocks.toPhysicalRange(bytesMapper).snapshot();
        return getSlice(phys.start(), phys.end());
    }

    ByteBuffer getSlice(int pos, int limit) {
        return withBufferReadState(pos, limit, (buf) -> {
            assert pos == buf.position();
            assert limit == buf.limit();
            return buf.slice();
        });
    }

    ByteBuffer sliceAndSnapshot(int pos, int limit) {
        return withBufferReadState(pos, limit, (buf) -> {
            ByteBuffer nue = ByteBuffer.allocate(buf.capacity());
            nue.put(buf);
            nue.flip();
            return nue;
        });
    }

    public int size() {
        return alloc.currentBufferSize();
    }

    private <T> T withBufferReadState(int pos, int limit, Function<ByteBuffer, T> run) {
        return alloc.withBuffer(pos, limit, buf -> {
            if (limit > buf.capacity()) {
                throw new IllegalArgumentException("Limit " + limit + " > " + buf.capacity());
            }
            if (pos < 0) {
                throw new IllegalArgumentException("Pos < 0 for pos " + pos + " limit " + limit);
            }
            return run.apply(buf);
        });
    }

    void migrate(int startByte, int byteCount, int destByte) throws IOException {
        if (startByte == destByte) {
            return;
        }
        ops.set("bbm-move from {0} moving {1} bytes to {2}", startByte, byteCount, destByte);
        alloc.move(startByte, byteCount, destByte);
    }
}
