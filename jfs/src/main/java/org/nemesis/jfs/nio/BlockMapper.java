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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.nemesis.jfs.spi.JFSUtilities;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;

/**
 *
 * @author Tim Boudreau
 */
final class BlockMapper implements BlockStorage {

    private final BlockManager man;
    private final BlockToBytesConverter bytesConverter;
    private final Set<Blocks> liveBlocksInstances = Collections.synchronizedSet(JFSUtilities.newWeakSet());
    private final ByteBufferMapper bufferMapper;
    private final Lis lis = new Lis();
    private final Set<SnapshottableInputStream> liveStreams = Collections.synchronizedSet(JFSUtilities.newWeakSet());
    private final Ops ops;

    BlockMapper(int blockSize, int initialBlocks) throws IOException {
        this(new BlockToBytesConverter(blockSize), initialBlocks, Ops.defaultOps());
    }

    BlockMapper(BlockToBytesConverter mapper, int initialBlocks, Ops ops) throws IOException {
        this(mapper, new ByteBufferMapper(mapper, initialBlocks, true, ops), initialBlocks, ops);
    }

    BlockMapper(int blockSize, int initialBlocks, BlockStorageKind kind, Ops ops) throws IOException {
        this(new BlockToBytesConverter(blockSize), initialBlocks, kind, ops);
    }

    BlockMapper(BlockToBytesConverter mapper, int initialBlocks, BlockStorageKind kind, Ops ops) throws IOException {
        this(mapper, new ByteBufferMapper(mapper, initialBlocks, kind.createBufferAllocator(mapper, initialBlocks, ops), ops), initialBlocks, ops);
    }

    BlockMapper(BlockToBytesConverter bytes, ByteBufferMapper buffers, int initialBlocks, Ops ops) {
        this.man = new BlockManager(initialBlocks, lis);
        this.bytesConverter = bytes;
        this.bufferMapper = buffers;
        this.ops = ops;
    }

    @Override
    public void close() throws IOException {
        man.clear();
        bufferMapper.close();
    }

    BlockManager blockManager() {
        return man;
    }

    ByteBufferMapper bufferMapper() {
        return bufferMapper;
    }

    BlockToBytesConverter converter() {
        return bytesConverter;
    }

    private Blocks allocateBlocks(int blockCount) throws IOException {
        int start = man.allocate(blockCount);
        Blocks result = new Blocks(start, blockCount);
        ops.set("bm-allocate {0}", result);
        liveBlocksInstances.add(result);
        return result;
    }

    public MappedBytes allocate(int blockCount) throws IOException {
        Blocks newBlocks = allocateBlocks(blockCount);
        return new MappedBytes(newBlocks);
    }

    public MappedBytes allocate(byte[] data) throws IOException {
        int blockCount = bytesConverter.bytesToBlocks(data.length);
        assert bytesConverter.blocksToBytes(blockCount) >= data.length;
        Blocks newBlocks = allocateBlocks(blockCount);
        ByteBuffer buf = bufferMapper.slice(newBlocks);
        assert buf.capacity() >= data.length;
        assert buf.limit() >= data.length;
        buf.put(data);
        return new MappedBytes(newBlocks, data.length);
    }

    String recentOps() {
        return ops.toString();
    }

    @Override
    public String toString() {
        return recentOps();
    }

    volatile int deleteCount;

    class MappedBytes implements BlockStorage.StoredBytes {

        private final Blocks blocks;
        private volatile int physicalSize;

        MappedBytes(Blocks blocks) {
            this.blocks = blocks;
        }

        MappedBytes(Blocks blocks, int physicalSize) {
            this.blocks = blocks;
            this.physicalSize = physicalSize;
        }

        private Object lock() {
            return new Object();
        }

        @Override
        public String toString() {
            return physicalSize + "->" + blocks + " = " + blocks.toPhysicalRange(bytesConverter);
        }

        public int allocationSize() {
            return bytesConverter.blocksToBytes(blocks.size());
        }

        @Override
        public int size() {
            // We use the blocks lock to protect the physicalSize field:
            return physicalSize;
//            }
        }

        @Override
        public ByteBuffer readBuffer() {
//            synchronized (lock()) {
            ByteBuffer result = bufferMapper.slice(blocks);
            result.limit(Math.min(physicalSize, result.capacity()));
            return result;
//            }
        }

        public ByteBuffer readBufferSnapshot() {
//            synchronized (lock()) {
            ByteBuffer result = bufferMapper.sliceAndSnapshot(blocks);
            result.limit(Math.min(physicalSize, result.capacity()));
            return result;
//            }
        }

        @Override
        public void delete() throws IOException {
            ops.set("delete {0} id {1}", blocks, blocks.hashCode());
//            synchronized (lock()) {
            Blocks b = blocks;
            if (b != null) {
                try {
                    man.deallocate(b.start(), b.size());
                } finally {
                    b.discard();
                }
                liveBlocksInstances.remove(b);
                if (++deleteCount % 5 == 0 && man.fragmentation() > 0.6) {
                    man.fullDefrag();
                }
            }
//            }
        }

        @Override
        public void setBytes(byte[] bytes) throws IOException {
            ops.set("bm-getBytes {0} phys {1} newSize {2}", blocks, physicalSize, bytes.length);
//            blocks.writeLock();
            try {
//                BlockMapper.this.bufferMapper.underWriteLock(() -> {
                onBeforeChange(blocks.start(), blocks.size());
                int lim = -1;
                try {
                    ByteBuffer buf = writeBuffer(bytes.length, true);
                    lim = buf.limit();
                    buf.put(bytes);
                } catch (Exception e) {
                    throw new IOException("Exception getting write buffer for " + blocks + " with phys size " + physicalSize
                            + " and blocks mapped to phys buffer " + blocks.toPhysicalRange(bytesConverter)
                            + " over available " + bufferMapper.size() + " in buffer with limit " + lim + " for byte array of "
                            + bytes.length, e);
                }
//                });
            } finally {
//                blocks.writeUnlock();
            }
        }

        public ByteBuffer writeBuffer(int size, boolean copyExistingData) throws IOException {
            int targetBlocks = bytesConverter.bytesToBlocks(size);
            ops.set("bm-writebuffer {0} id {1} newSize {2} currSize {3}", blocks, blocks.hashCode(), size, physicalSize);
//            bufferMapper.underWriteLock(() -> {
            try {
                int currBlocks = blocks.size();
                if (targetBlocks < currBlocks) {
                    man.shrink(blocks.start(), currBlocks, targetBlocks, () -> {
                        physicalSize = size;
                    });
                } else if (targetBlocks > currBlocks) {
                    man.grow(blocks.start(), blocks.size(), targetBlocks, copyExistingData, ns -> {
                        physicalSize = size;
                    });
                } else {
                    physicalSize = size;
                }
            } catch (Exception e) {
                throw new IOException("Exception getting write buffer for " + blocks + " with phys size " + physicalSize
                        + " and blocks mapped to phys buffer " + blocks.toPhysicalRange(bytesConverter), e);
            }
//            });
            return readBuffer();
        }

        @Override
        public byte[] getBytes() throws IOException {
//            synchronized (lock()) {
            ops.set("bm-getBytes {0} phys {1}", blocks, physicalSize);
            byte[] b = new byte[physicalSize];
            if (physicalSize == 0) {
                return b;
            }
            ByteBuffer buf = null;
            try {
                buf = readBuffer();
                buf.get(b);
                return b;
            } catch (Exception e) {
                throw new IOException("Exception reading buffer for " + blocks + " with phys size " + physicalSize
                        + " and blocks mapped to phys buffer " + blocks.toPhysicalRange(bytesConverter)
                        + " over allocated " + bufferMapper.size() + " buffer limit " + (buf == null ? "null" : buf.limit()
                        + " position " + (buf == null ? "null" : buf.position())), e);
            }
        }

        Blocks blocks() {
            return blocks;
        }

        @Override
        public OutputStream openOutputStream() {
            return new Out();
        }

        @Override
        public InputStream openInputStream() {
            SnapshottableInputStream result;
            result = new SnapshottableInputStream(physicalSize, this, liveStreams::remove);
            liveStreams.add(result);
            return result;
        }

        private final class Out extends ByteArrayOutputStream {

            @Override
            public void close() throws IOException {
                super.close();
                byte[] bytes = toByteArray();
                setBytes(bytes);
            }
        }
    }

    void onBeforeChange(int firstBlock, int blockCount) {
//        System.out.println("on before change " + firstBlock + " " + (blockCount + firstBlock - 1));
        // If any input streams are open, cause the bytes they are currently
        // reading to be moved into a copied buffer, so they are consistent
        Set<SnapshottableInputStream> ins;
        synchronized (liveStreams) {
            ins = new HashSet<>(liveStreams);
        }
        for (SnapshottableInputStream in : liveStreams) {
            Blocks blocks = in.blocks();
//            System.out.println("FOUND LIVE INPUT STREAM " + blocks + " change in " + firstBlock + ":" + (firstBlock + blockCount - 1)
//                    + " overlapsStartAndEnd? " + blocks.overlapsStartAndEnd(firstBlock, firstBlock + blockCount - 1));
            if (blocks.overlaps(Range.of(firstBlock, blockCount))) {
                in.acquireSnapshot();
            }
        }
    }

    class Lis implements BlockManager.Listener {

        public void onBeforeExpand(int oldBlockCount, int newBlocks, int lastUsedBlock) throws IOException {
            int oldSize = bytesConverter.blocksToBytes(oldBlockCount);
            int length = bytesConverter.blocksToBytes(newBlocks);
            int copyUpTo = bytesConverter.blocksToBytes(lastUsedBlock + 1);
            bufferMapper.expand(oldSize, length, copyUpTo);
        }

        public void onDeallocate(int start, int blockCount) throws IOException {
            onBeforeChange(start, blockCount);
            ops.set("bm-evt-deallocate {0}:{1}", start, blockCount);
//            System.out.println("deallocate " + new Blocks(start, blockCount));
//            Set<Blocks> live;
//            synchronized(liveBlocksInstances) {
//                live = new HashSet<>(liveBlocksInstances);
//            }
//            for (Blocks block : live) {
//                synchronized (block) {
//                    if (block.overlapsStartAndEnd(start, start + blockCount)) {
//                        if (start > block.start()) {
//                            int newSize = (start - block.start()) + 1;
//                            if (start + blockCount < block.end()) {
//                                System.out.println("questionable deallocate in middle of block: " + start + ":" + (start + blockCount) + " in " + block);
//                                continue;
//                            }
//                            block.setSize(newSize);
//                        } else if (start < block.start()) {
//                            if (start + blockCount >= block.end()) {
//                                block.setSize(0);
//                            }
//                        }
//                    }
//                }
//            }
        }

        @Override
        public void onResized(int start, int oldSize, int newSize) throws IOException {
//            System.out.println("  ON RESIZED!!! " + start + " old " + oldSize + " nu " + newSize);
            onBeforeChange(start, oldSize);
            ops.set("bm-evt-resized {0}:{1} -> {0}:{2}", start, oldSize, newSize);
//            bufferMapper.underReadLock(() -> {
            Set<Blocks> liveBlocks;
            synchronized (liveBlocksInstances) { // protect against a CME under concurrency
                liveBlocks = new HashSet<>(liveBlocksInstances);
            }
            for (Blocks block : liveBlocks) {
                if (block.resizeIfExact(start, oldSize, newSize)) {
                    ops.set("bm-evt-resize-live {0} for {1}:{2} -> {1}:{3}", block, start, oldSize, newSize);
//                        System.out.println("resized " + old + " to " + block);
                }
            }
        }

        public void onAllocate(int start, int blocks) throws IOException {
            int lastUsedBlock = man.lastUsedBlock();
            int copyThru = lastUsedBlock < 0 ? 0 : bytesConverter.blocksToBytes(lastUsedBlock + 1);
            bufferMapper.ensureBuffer(bytesConverter.blocksToBytes(start + blocks), copyThru);
        }

        boolean asserts = false;

        {
            assert asserts = true;
        }

        private String compareBytes(byte[] a, byte[] b) {
            StringBuilder sb = new StringBuilder();
            if (a.length != b.length) {
                sb.append("length mismatch: ").append(a.length).append(" vs ").append(b.length);
            }
            int max = Math.min(a.length, b.length);
            int mismatchCount = 0;
            for (int i = 0; i < max; i++) {
                if (a[i] != b[i]) {
                    sb.append(", mismatch at ").append(i).append(" expected ").append(a[i]).append(" was ").append(b[i]);
                    mismatchCount++;
                }
                if (mismatchCount > 4) {
                    break;
                }
            }
            return sb.toString();
        }

        private void assertBytes(byte[] expected, int firstBlock, int blockCount) {
            if (!asserts) {
                return;
            }
            byte[] test = bytes(firstBlock, blockCount);
            assert Arrays.equals(expected, test) : "Bytes non-match after move to " + firstBlock + ":" + (firstBlock + blockCount - 1)
                    + " " + compareBytes(expected, test);
        }

        private byte[] bytes(int start, int count) {
            if (!asserts) {
                return new byte[0];
            }
            ByteBuffer buf = bufferMapper.slice(new Blocks(start, count));
            byte[] bytes = new byte[buf.limit()];
            buf.get(bytes);
            return bytes;
        }

        private Set<Blocks> currentLiveBlocks(boolean reverseSort) {
            Set<Blocks> result;
            if (reverseSort) {
                result = new TreeSet<>(Blocks.REVERSE_COMPARATOR);
            } else {
                result = new TreeSet<>();
            }
            synchronized (liveBlocksInstances) {
                result.addAll(liveBlocksInstances);
            }
            return result;
        }

        public void onMigrate(int firstBlock, int blockCount, int dest, int newBlockCount) throws IOException {
//            System.out.println("  ON MIGRATE!! " + firstBlock + " blocks " + blockCount + " to " + dest);
            onBeforeChange(firstBlock, blockCount);
            onBeforeChange(dest, blockCount);
//            bufferMapper.underWriteLock(() -> {
            int startByte = bytesConverter.blocksToBytes(firstBlock);
            int length = bytesConverter.blocksToBytes(blockCount);
            int destByte = bytesConverter.blocksToBytes(dest);

            byte[] check = bytes(firstBlock, blockCount);

//                System.out.println("  migrate buffer from " + startByte + ":" + (startByte + length - 1) + " -> "
//                        + destByte + ":" + (destByte + length - 1) + " for blocks " + firstBlock + ":" + (firstBlock + blockCount - 1) + " -> " + dest);
            if (newBlockCount > blockCount) {
                bufferMapper.ensureBuffer(newBlockCount, bytesConverter.blocksToBytes(man.lastUsedBlock() + 1));
            }
            bufferMapper.migrate(startByte, length, destByte);
//                System.out.println("  MIGRATE BUFFERS " + startByte + " bytes " + length + " to " + destByte + " for migrate "
//                        + Blocks.blockStringWithSize(firstBlock, blockCount) + " -> "
//                        + Blocks.blockStringWithSize(dest, newBlockCount));

            assertBytes(check, dest, blockCount);

            List<Blocks> liveBlocks = new ArrayList<>(currentLiveBlocks(dest > firstBlock));
            IntRange<?> old = Range.of(firstBlock, blockCount);
            for (Blocks block : liveBlocks) {
                if (block.isDiscarded()) {
                    continue;
                }
                if (!old.contains(block) && !old.matches(block)) { // partial overlap - we did this one on a previous pass in defrag
                    continue;
                }
//                    if (block.overlapsStartAndEnd(firstBlock, firstBlock + blockCount - 1)) {
//                        System.out.println("  migrate block " + block + " in " + Blocks.blockStringWithSize(firstBlock, blockCount)
//                                + " -> " + Blocks.blockStringWithSize(dest, newBlockCount));
//                    }
                if (block.maybeMigrate(firstBlock, blockCount, dest, newBlockCount)) {
//                        System.out.println("    migrated to " + block + " in " + Blocks.blockStringWithSize(dest, newBlockCount));
                    ops.set("bm-evt-migrate-live {0} for {1}:{2} -> {3}:{4}", block, firstBlock, blockCount, dest, newBlockCount);
//                        System.out.println("ON MIGRATE MIGRATED BLOCKS " + old + " -> " + block);
                }
            }
//            });
        }
    }

    /**
     * An input stream which, in case of a pending write or relocation, can
     * switch in-flight to using a snapshotted copy of the bytes it is streaming
     * so changes do not affect reads in progress.
     */
    private static final class SnapshottableInputStream extends InputStream {

        private final int size;
        private int lastPosition = 0;
        private ByteBuffer snapshot;
        private final MappedBytes virtualFile;
        private final Consumer<SnapshottableInputStream> remove;

        SnapshottableInputStream(int size, MappedBytes virtualFile, Consumer<SnapshottableInputStream> remove) {
            this.size = size;
            this.virtualFile = virtualFile;
            this.remove = remove;
        }

        Blocks blocks() {
            return virtualFile.blocks();
        }

        @Override
        public void close() throws IOException {
            super.close();
            remove.accept(this);
        }

        synchronized void acquireSnapshot() {
            if (snapshot == null) {
                System.out.println("To snapshot mode for " + blocks());
                snapshot = virtualFile.readBufferSnapshot();
            }
        }

        private ByteBuffer buffer() {
            ByteBuffer result;
            synchronized (this) {
                result = snapshot == null ? virtualFile.readBuffer() : snapshot;
                if (lastPosition < result.capacity()) {
                    result.position(lastPosition);
                } else {
                    result.position(size);
                }
            }
//            System.out.println("GOT BUFFER " + result.capacity() + " last position " + lastPosition + " limit " + result.limit() + " size " + size);
            result.limit(size);
            return result;
        }

        @Override
        public int read() throws IOException {
            ByteBuffer b = buffer();
            if (lastPosition >= b.capacity()) {
                return -1;
            }
            int result = b.remaining() == 0 ? -1 : b.get();
            synchronized (this) {
                lastPosition = b.position();
            }
            return result;
        }

        @Override
        public synchronized int available() throws IOException {
            ByteBuffer b = buffer();
            return b.limit() - b.position();
        }

        @Override
        public synchronized void reset() throws IOException {
            lastPosition = 0;
        }

        @Override
        public int read(byte[] b) throws IOException {
            ByteBuffer bb = buffer();
            if (lastPosition >= bb.capacity()) {
                return -1;
            }
            int rem = bb.limit() - bb.position();
            if (rem == 0) {
                return -1;
            }
            int result = Math.min(rem, b.length);
//            System.out.println("READ " + result + " bytes from " + bb.position() + " into array of " + b.length + " remaining " + rem  );
            bb.get(b, 0, result);
            synchronized (this) {
                lastPosition = bb.position();
            }
            return result;
        }

        @Override
        public int read(byte[] bytes, int off, int len) throws IOException {
            ByteBuffer b = buffer();
            if (off + len > bytes.length) {
                throw new IOException("offset + length=" + (off + len) + " but array length is " + bytes.length);
            }
            int rem = b.remaining();
            if (rem == 0) {
                return -1;
            }
            int result = Math.min(len, rem);
            b.get(bytes, off, result);
            synchronized (this) {
                lastPosition = b.position();
            }
            return result;
        }

        @Override
        public long skip(long n) throws IOException {
            lastPosition += (int) n;
            if (lastPosition > size) {
                lastPosition = size;
            }
            return lastPosition;
        }
    }

}
