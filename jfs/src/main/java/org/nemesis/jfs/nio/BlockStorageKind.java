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
package org.nemesis.jfs.nio;

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
    private static final int DEFAULT_INITIAL_BLOCKS = 128;

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
