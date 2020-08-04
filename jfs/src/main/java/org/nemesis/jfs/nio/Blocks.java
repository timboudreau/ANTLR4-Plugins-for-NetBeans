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

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;

/**
 * A set of blocks which may be used by a virtual file. Note that each set of
 * blocks has its own lock; when doing wholesale modifications of large sets of
 * blocks, all affected block locks should be acquired first.
 *
 * @author Tim Boudreau
 */
final class Blocks implements  com.mastfrog.range.MutableIntRange<Blocks> {

    static final AtomicInteger ids = new AtomicInteger();
    private int size;
    private int start;
    private final int id;
    private volatile boolean discarded;

    Blocks(int start, int size) {
        this(start, size, ids.getAndIncrement());
    }

    private Blocks(int start, int size, int id) {
        this.start = start;
        this.size = size;
        this.id = id;
    }

    boolean discard() {
        boolean old = discarded;
        discarded = true;
        setStartAndSize(0, 0);
        return old;
    }

    @Override
    public String toString() {
        int st = start;
        StringBuilder sb = new StringBuilder().append('[').append(st).append(':').append(st + (size - 1))
                .append(" (").append(size).append(")]");
        if (id != -1) {
            sb.append('{').append(id).append('}');
        }
        return sb.toString();
    }

    int id() {
        return id;
    }

    Blocks copy() {
        return new Blocks(start, size, -1);
    }

    boolean isDiscarded() {
        return discarded;
    }

    static String blockStringWithCoords(int start, int stop) {
        int len = (stop - start) + 1;
        return "[" + start + ":" + stop + "(" + len + ")]";
    }

    static String blockStringWithSize(int start, int blocks) {
        return "[" + start + ":" + (start + blocks - 1) + "(" + blocks + ")]";
    }

    @Override
    public boolean setSize(int newSize) {
        boolean result = newSize != size;
        this.size = newSize;
        return result;
    }

    @Override
    public boolean setStartAndSize(int newStart, int newSize) {
        boolean result = newStart != start || newSize != size;
        this.start = newStart;
        this.size = newSize;
        return result;
    }

    @Override
    public boolean setStart(int newStart) {
        boolean change = start != newStart;
        start = newStart;
        return change;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int start() {
        return start;
    }

    @Override
    public int stop() {
        return start + (size - 1);
    }

    public boolean maybeMigrate(int firstBlock, int blockCount, int newFirstBlock, int newBlockCount) {
        if (firstBlock == start && blockCount == size && firstBlock == newFirstBlock && newBlockCount == blockCount) {
            return true;
        }
        if (firstBlock == start) {
            boolean changed = start != newFirstBlock;
            start = newFirstBlock;
            if (newBlockCount != blockCount) {
                size = newBlockCount;
                changed = true;
            }
            return changed;
        } else if (overlaps(Range.of(firstBlock, blockCount))) {
            Blocks origRange = new Blocks(firstBlock, blockCount);
            Blocks overlap = origRange.overlapWith(this);
            int newStart, newSize;
            if (origRange.end() == end()) {
                newStart = newFirstBlock + (overlap.start() - firstBlock);
                newSize = newBlockCount - (overlap.start() - firstBlock);
            } else {
                int offset = start - firstBlock;
                newStart = newFirstBlock + offset;
                newSize = Math.min(size, newBlockCount - offset);
            }
            if (newStart < 0 || newSize < 0) {
                System.out.println("Suspicious offsets " + newStart + ":" + newSize // println ok
                        + " for " + this + " when migrating " + firstBlock + ":" + blockCount + " -> "
                        + newFirstBlock + ":" + newBlockCount + " with overlap " + overlap);
                return false;
            }
            boolean changed = size != newSize || start != newStart;
            size = newSize;
            start = newStart;
            return changed;
        }
        return false;
    }

    static Comparator<Blocks> REVERSE_COMPARATOR = (Blocks o1, Blocks o2) -> {
        int sta = o1.start;
        int stb = o2.start;
        int result = sta > stb ? -1 : sta == stb ? 0 : 1;
        if (result == 0) {
            int sza = o1.size;
            int szb = o2.size;
            result = sza > szb ? -1 : sza == szb ? 0 : 1;
        }
        return result;
    };

    /**
     * Returns an instanceof IntRange which will reflect the size and position
     * of this Blocks in bytes at the time its methods are called.
     *
     * @param mapper A block mapper
     * @return A range which dynamically returns the physical byte range
     * used by this one
     */
    public IntRange<? extends IntRange<?>> toPhysicalRange(BlockToBytesConverter mapper) {
        return new PhysRange(this, mapper);
    }

    @Override
    public Blocks newRange(int start, int size) {
        return new Blocks(start, size, -1);
    }

    @Override
    public Blocks newRange(long start, long size) {
        return new Blocks((int) start, (int) size, -1);
    }

    private static final class PhysRange implements IntRange<PhysRange> {

        private final Blocks blocks;
        private final BlockToBytesConverter mapper;

        PhysRange(Blocks blocks, BlockToBytesConverter mapper) {
            this.blocks = blocks;
            this.mapper = mapper;
        }

        @Override
        public int start() {
            return mapper.blocksToBytes(blocks.start());
        }

        @Override
        public int stop() {
            return mapper.blocksToBytes(blocks.stop());
        }

        public int end() {
            return mapper.blocksToBytes(blocks.end());
        }

        @Override
        public int size() {
            return mapper.blocksToBytes(blocks.size());
        }

        @Override
        public String toString() {
            return "[" + start() + ":" + end() + " (" + size() + ")]";
        }

        @Override
        public PhysRange newRange(int start, int size) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public PhysRange newRange(long start, long size) {
            throw new UnsupportedOperationException("Not supported.");
        }
    }
}
