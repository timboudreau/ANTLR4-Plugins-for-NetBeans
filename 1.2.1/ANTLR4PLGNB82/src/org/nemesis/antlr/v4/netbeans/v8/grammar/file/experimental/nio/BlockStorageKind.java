package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.nio;

import java.io.IOException;

/**
 * Types of backing storage for a block storage.
 *
 * @author Tim Boudreau
 */
public enum BlockStorageKind {

    /**
     * Use a memory mapped temporary file.
     */
    MAPPED_TEMP_FILE,
    /**
     * Use heap-based ByteBuffers.
     */
    HEAP,
    /**
     * Use direct ByteBuffers.
     */
    OFF_HEAP;

    private static final int DEFAULT_BLOCK_SIZE = 256;
    private static final int DEFAULT_INITIAL_BLOCKS = 4;

    public BlockStorage create() throws IOException {
        return create(DEFAULT_BLOCK_SIZE, DEFAULT_INITIAL_BLOCKS);
    }

    public BlockStorage create(int blockSize, int initialBlocks) throws IOException {
        return new BlockMapper(blockSize, initialBlocks, this, new Ops(20));
    }

    ByteBufferAllocator createBufferAllocator(BlockToBytesConverter bytesMapper, int initialBlockCount, Ops ops) throws IOException {
        switch (this) {
            case HEAP:
                return new ByteBufferAllocator.DefaultByteBufferAllocator(false, ops);
            case OFF_HEAP:
                return new ByteBufferAllocator.DefaultByteBufferAllocator(true, ops);
            case MAPPED_TEMP_FILE:
                return new ByteBufferAllocator.MappedBufferAllocator(bytesMapper.blocksToBytes(initialBlockCount), ops);
            default:
                throw new AssertionError(this);
        }
    }
}
