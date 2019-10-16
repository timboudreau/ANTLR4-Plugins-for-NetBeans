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
import java.util.BitSet;
import java.util.List;
import java.util.function.IntConsumer;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;

/**
 * A poor man's memory manager, which manages a set of blocks, each consisting
 * of some number of bytes, and can allocate and deallocate them. This class
 * simply maintains the set of active blocks and notifies when that changes. It
 * is expected that the caller will provide locking to ensure consistency under
 * concurrency.
 *
 * @author Tim Boudreau
 */
final class BlockManager {

    private int blockCount;
    private final BitSet used;
    private final Listener listener;
    private ExpansionAlgorithm expansionAlgorithm;
    private final int initialBlockCount;

    BlockManager(int initialBlockCount, Listener phys) {
        this.blockCount = initialBlockCount;
        this.initialBlockCount = initialBlockCount;
        expansionAlgorithm = new DefaultExpansionAlgorithm();
        used = new BitSet(blockCount);
        this.listener = phys;
    }

    public int blockCount() {
        return blockCount;
    }

    public int usedBlocks() {
        return used.cardinality();
    }

    public int availableBlocks() {
        return blockCount - usedBlocks();
    }

    static final class DefaultExpansionAlgorithm implements ExpansionAlgorithm {

        @Override
        public int computeNewBlockCount(int initialBlockCount, int currentBlockCount, int requestedBlockCount) {
            int amountNew = requestedBlockCount - currentBlockCount;
            if (amountNew < initialBlockCount) {
                amountNew = initialBlockCount;
            } else if (amountNew > initialBlockCount) {
                amountNew = ((amountNew / initialBlockCount) + 1) * initialBlockCount;
            }
            return currentBlockCount + amountNew;
        }

    }

    interface ExpansionAlgorithm {

        int computeNewBlockCount(int initialBlockCount, int currentBlockCount, int requestedBlockCount);
    }

    interface Listener {

        default void onBeforeExpand(int oldBlockCount, int minimumBlocks, int lastUsedBlock) throws IOException {

        }

        default void onDeallocate(int start, int blocks) throws IOException {

        }

        default void onAllocate(int start, int blocks) throws IOException {

        }

        default void onMigrate(int firstBlock, int blockCount, int dest, int newBlockCount) throws IOException {
        }

        default void onResized(int start, int oldSize, int newSize) throws IOException {

        }
    }

    private int _expand(int minimumBlocks) throws IOException {
        int newSize = expansionAlgorithm.computeNewBlockCount(initialBlockCount, blockCount, minimumBlocks);
        int lastUsedBlock = used.previousSetBit(blockCount);
        listener.onBeforeExpand(blockCount, newSize, lastUsedBlock);
        int result = blockCount = newSize;
        return result;
    }

    int expand(int minimumBlocks) throws IOException {
        return _expand(minimumBlocks);
    }

    public void shrink(int start, int oldSize, int newSize) throws IOException {
        shrink(start, oldSize, newSize, null);
    }

    public void shrink(int start, int oldSize, int newSize, Runnable onDone) throws IOException {
        if (newSize <= 0) {
            throw new IllegalArgumentException("Zero new size " + newSize);
        }
        int diff = (oldSize - newSize) + 1;
//        System.out.println("SHRINK DIFF " + diff + " shrink " + start + ":" + (start + oldSize - 1) + " -> " + start + ":" + (start + newSize - 1));
        if (diff != 0) {
            listener.onResized(start, oldSize, newSize);
//            System.out.println("SHRINK DEALLOCATING " + diff + "@ " + (start + newSize));
            if (onDone != null) {
                onDone.run();
            }
            deallocate(start + newSize, oldSize - newSize);
        } else if (onDone != null) {
            onDone.run();
        }
    }

    public int grow(int start, int oldSize, int newSize, boolean copy) throws IOException {
        return grow(start, oldSize, newSize, copy, null);
    }

    public int grow(int start, int oldSize, int newSize, boolean copy, IntConsumer onDone) throws IOException {
        int diff = newSize - oldSize;
        if (diff == 0) {
            if (onDone != null) {
                onDone.accept(start);
            }
            return start;
        }
//        System.out.println("test grow in place for " + start + ":" + (start + oldSize - 1) + " -> " + start + ":" + (start + newSize - 1));
        int result = -1;
        if (start + newSize > blockCount) {
            expand(start + newSize);
        }

        try {
            boolean growInPlace = start + newSize < blockCount;
            if (growInPlace) {
                int nextSetBit = used.nextSetBit(start + oldSize);
                growInPlace = nextSetBit > start + newSize;
            }
            if (growInPlace) {
                markAllocated(newSize - oldSize, start + oldSize);
                listener.onResized(start, oldSize, newSize);
                result = start;
            } else {
                int nextSetBit = used.nextSetBit(start + oldSize);
                growInPlace = nextSetBit > start + newSize;
                if (growInPlace) {
                    markAllocated(newSize - oldSize, start + oldSize);
                    listener.onResized(start, oldSize, newSize);
                    result = start;
                } else {
                    int newStart = allocate(newSize);
                    if (copy) {
                        migrate(start, oldSize, newStart, newSize);
                    } else {
                        markUnallocated(oldSize, start);
                        // XXX FIRE MIGRATE TO LISTENER?  WHAT HERE SO BLOCKS INSTANCES ARE UPDATED?
                    }
                    result = newStart;
                }
            }
        } finally {
            if (onDone != null) {
                onDone.accept(result);
            }
        }

//        }
        return result;
    }

    public int allocate(int blocks) throws IOException {
        if (blocks == 0) {
            throw new IllegalArgumentException("Allocating 0 is silly");
        }
        if (used.isEmpty()) {
            if (blocks > blockCount) {
                _expand(blocks);
            }
            markAllocated(blocks, 0);
            listener.onAllocate(0, blocks);
            return 0;
        }
        int start = findContigiuousUnallocated(blocks);
        if (start >= blockCount) {
            start = _expand(blockCount + blocks);
        } else if (start < 0) {
            int oldCount = blockCount;
            int last = lastUsedBlock();
            last = last < 0 ? 0 : last;
            _expand(blockCount + blocks);
//            start = findContigiuousUnallocated(last);
            start = last + 1;
            assert start >= 0 : "After expand " + oldCount + " to " + blockCount + ", still cannot find " + blocks + " in " + blockCount
                    + " last used block " + last + " in " + this;
//            System.out.println("  START IS " + start + " NEW BLOCK COUNT " + blockCount);
        }
        int end = used.nextSetBit(start + 1);
        if (end > 0 && start == 0 && end - start > blocks) {
//            System.out.println("shift start to " + (end - blocks) + " from " + start);
            start = end - blocks;
        }
        markAllocated(blocks, start);
        listener.onAllocate(start, blocks);
        return start;
    }

    public int lastUsedBlock() {
        return used.previousSetBit(blockCount);
    }

    public void deallocate(int start, int blocks) throws IOException {
        listener.onDeallocate(start, blocks);
        markUnallocated(blocks, start);
    }

    public void clear() throws IOException {
        deallocate(0, blockCount);
    }

    void set(int ix) {
        assert ix >= 0 && ix < blockCount : ix;
        used.set(ix);
    }

    void unset(int ix) {
        assert ix >= 0 && ix < blockCount : ix;
        used.clear(ix);
    }

    private void markAllocated(int blocks, int at) {
        assert at >= 0 && at + (blocks - 1) < blockCount : at + ":" + blocks;
        used.set(at, at + blocks);
    }

    private void markUnallocated(int blocks, int at) {
        assert at >= 0 && at + (blocks - 1) < blockCount : at + ":" + blocks;
        used.clear(at, at + blocks);
    }

    int findContigiuousUnallocated(int blocks) {
        return findContigiuousUnallocated(0, blocks);
    }

    private int findContigiuousUnallocated(int from, int blocks) {
        return findContigiuous(from, blocks, false);
    }

    private int findContigiuous(int from, int blocks, final boolean allocated) {
        return findContigiuous(from, blocks, allocated, false);
    }

    void _fullDefrag() throws IOException {
        int oldCardinality = used.cardinality();
        int[] scratch = new int[2];
        int[] lastEnd = new int[]{-1};

        int[] res;
        // Scan regions forward from 0, shifting each one to abut the previous
        // and notifying the listener to move byte buffer contents
        while ((res = nextRegion(lastEnd[0] + 1, true, false, scratch)) != null) {
            if (res[0] > lastEnd[0]) {
                int len = (res[1] - res[0]) + 1;
//                System.out.println("defrag migrate " + Blocks.blockStringWithCoords(res[0], res[1]) + " to " + (lastEnd[0] + 1));
                lastEnd[0] = migrate(res[0], len, lastEnd[0] + 1, len) - 1;
//                System.out.println("  next start now " + lastEnd[0]);
            } else {
//                System.out.println("defrag NO migrate for " + Blocks.blockStringWithCoords(res[0], res[1]));
                lastEnd[0]++;
            }
        }
        System.out.println("\n AFTER FULL DEFRAG " + this + "\n");
        assert this.regionCount(true) == 1 : "After full defrag, there are still unallocated interstitial regions: " + this;
//        System.out.println("AFTER FULL DEFRAG, " + this.regionCount(true) + " regions");
        int newCardinality = used.cardinality();
        assert oldCardinality == newCardinality : "Defrag changed cardinality - blocks dropped or phantom blocks added. Was "
                + oldCardinality + " but is now " + newCardinality;
    }

    private int migrate(int firstBlock, int blockCount, int newStart, int newBlockCount) throws IOException {
        if (firstBlock == newStart && blockCount == newBlockCount) {
            return newStart + newBlockCount;
        }

        // Special case the condition where we are just unsetting
        // and setting a single bit
        if (blockCount == 1 && newBlockCount == 1) {
            listener.onMigrate(firstBlock, blockCount, newStart, newBlockCount);
            unset(firstBlock);
            listener.onDeallocate(firstBlock, 1);
            set(newStart);
            listener.onAllocate(newStart, 1);
            return newStart + 1;
        }
        // Notify the listener that the region is in use, so it can grow the
        // buffer if need be before we try to write to it
        listener.onAllocate(newStart, newBlockCount);
        // Notify the listener of the entire migration, so it can shift around
        // buffer contents
        this.listener.onMigrate(firstBlock, blockCount, newStart, newBlockCount);
        // Create some temporary instances to compute the overlap

        IntRange<? extends IntRange<?>> old = Range.of(firstBlock, blockCount);
        IntRange<? extends IntRange<?>> nue = Range.of(newStart, newBlockCount);
        IntRange<? extends IntRange<?>> overlap = old.overlapWith(nue);
        // If there is an overlap, we need to deallocate only those regions that
        // do not overlap
        if (!overlap.isEmpty()) {
            List<? extends IntRange<?>> l = old.nonOverlap(nue);
            for (IntRange<?> ir : l) {
                if (!nue.overlaps(ir)) {
                    used.clear(ir.start(), ir.end());
                    // should subtract sub regions?
                    listener.onDeallocate(ir.start(), ir.size());
                } else {
                    List<? extends IntRange<?>> subregions = ir.subtracting(nue);
                    for (IntRange<?> sub : subregions) {
                        used.clear(sub.start(), sub.end());
                        listener.onDeallocate(sub.start(), sub.size());
                    }
                }
            }

            used.set(newStart, newStart + newBlockCount);
            return newStart + newBlockCount;
        } else {
            // Simple migration of non-overlapping content
            used.clear(firstBlock, firstBlock + blockCount);
            used.set(newStart, newStart + newBlockCount);
            listener.onDeallocate(firstBlock, blockCount);
            return newStart + newBlockCount;
        }
    }

    boolean defrag = true;

    volatile boolean inDefrag;

    void simpleDefrag() throws IOException {
        if (!defrag || inDefrag || used.isEmpty()) { // for tests
            return;
        }
        inDefrag = true;
        try {
            for (;;) {
                int x;
                int[] last = lastBlock();
                if (last == null) {
                    x = 0;
                } else {
                    int blockLength = (last[1] - last[0]) + 1;
                    int start = findContigiuous(0, blockLength, false, false);
                    if (start == -1 || start > last[1]) {
                        x = 0;
                    } else {
                        int len = (last[1] - last[0]) + 1;
                        migrate(last[0], len, start, len);
                        x = 1;
                    }
                }
                if (x == 0) {
                    break;
                }
            }
            System.out.println("unalloc region count after: " + regionCount(false) + " frag " + fragmentation());
        } finally {
            inDefrag = false;
        }
        if (fragmentation() > 0.4) {
            fullDefrag();
        }
//        System.out.println("DEFRAG AFTER: " + this);
    }

    int regionCount(boolean allocated) throws IOException {
        int[] result = new int[1];
        this.regions(allocated, false, (a, b) -> {
            result[0]++;
            return true;
        });
        return result[0];
    }

    void fullDefrag() throws IOException {
        if (inDefrag) {
            return;
        }
        inDefrag = true;
        try {
            this._fullDefrag();
        } finally {
            inDefrag = false;
        }
    }

    int[] lastBlock() {
        int[] scratch = new int[2];
        scratch = nextRegion(blockCount, true, true, scratch);
        return scratch;
    }

    public float fragmentation() throws IOException {
        float frag = fragmentedBlocks();
        float ct = blockCount;
        return (frag / ct);
    }

    public int fragmentedBlocks() throws IOException {
        int[] count = new int[1];
        regions(false, false, (a, b) -> {
//            System.out.println("test " + a + "," + b);
            if (a > 0 && b < blockCount - 1) {
//                System.out.println("  included " + a + "," + b);
                count[0] += (b - a) + 1;
            }
            return true;
        });
        return count[0];
    }

    int findContigiuous(int from, int blocks, final boolean allocated, final boolean backwards) {
        int localFrom = from;
        if (blocks > blockCount) {
            return -1;
        }
        if (used.isEmpty() && blocks <= blockCount) {
            return 0;
        }
        int[] scratch = new int[2];
        for (;;) {
            int[] res = nextRegion(localFrom, allocated, backwards, scratch);
            if (res != null) {
                if ((res[1] - res[0]) + 1 >= blocks) {
                    return res[0];
//                    } else {
//                        tooSmallRegions++;
//                        skippedBlocks += (res[1] - res[0]) + 1;
                }
                localFrom = backwards ? res[0] - 1 : res[1] + 1;
            } else {
                return -1;
            }
        }
    }

    private int[] nextRegion(int from, boolean allocated, boolean backwards, int[] scratch) {
        if (scratch == null) {
            scratch = new int[2];
        }
//        System.out.println("NEXT REGION from " + from + (allocated ? " allocated " : " unallocated ")
//                + (backwards ? "backwards " : "forwards")
//                + "from " + from);
        boolean isSet;
        if (!backwards) {
            if (from >= blockCount) {
                return null;
            } else if (from < 0) {
                from = 0;
                isSet = used.get(from);
            } else {
                isSet = used.get(from);
            }
        } else {
            if (from > blockCount) {
                if (allocated) {
                    from = blockCount - 1;
//                    System.out.println("  CHANGE FROM TO " + from);
                    isSet = used.get(from);
                } else {
                    from = blockCount;
//                    System.out.println("  CHANGE FROM TO " + from);
                    isSet = !allocated;
                }
            } else if (from < 0) {
                return null;
            } else {
                isSet = used.get(from);
            }
        }

//        System.out.println("  next region from " + from + " allocated? " + allocated + " isSet? " + isSet);
        if (isSet != allocated) {
//            System.out.println("   scan to next " + (allocated ? "allocated " : "unallocated ") + " bit from " + from);
            if (!backwards) {
                if (allocated) {
                    from = used.nextSetBit(from + 1);
//                    System.out.println("  shift from to next set bit " + from);
                } else {
                    if (from >= blockCount) {
//                        System.out.println("  from < 0: " + from + " > block count " + blockCount + " - bail");
                        return null;
                    }
//                    int of = from;
                    from = used.nextClearBit(from + 1);
//                    System.out.println("next clear bit " + of + " gets " + from);
                }
            } else {
                if (allocated) {
                    if (from < 0) {
                        return null;
                    }
//                    System.out.println("find previous set bit from " + from);
                    from = used.previousSetBit(from - 1);
                } else {
                    if (from <= 0) {
//                        System.out.println("  from < 0: " + from + " > block count " + blockCount + " - bail");
                        return null;
                    }
//                    int of = from;
                    from = used.previousClearBit(from - 1);
//                    System.out.println("prev clear bit " + of + " gets " + from + " will start from " + from);
                }
            }
        }
        if (!backwards) {
            if (from == blockCount - 1) {
//                System.out.println("  from out of range: " + from + " w/ block count " + blockCount);
                return null;
            }
        } else {
            if (from < 0) {
//                System.out.println("  from out of range < 0 for reverse from : " + from + " w/ block count " + blockCount);
                return null;
            }
        }
        int to;
        if (!backwards) {
            if (allocated) {
                int nextClear = used.nextClearBit(from + 1);
                if (nextClear == -1) {
                    nextClear = blockCount - 1;
//                    System.out.println("use block count because next set bit from " + (from + 1) + " out of range");
                }
                to = nextClear - 1;
//                System.out.println("  to is " + to);
            } else {
                if (from + 1 >= blockCount) {
                    return null;
                }
                int nextSet = used.nextSetBit(from + 1);
                if (nextSet == -1) {
                    scratch[0] = from;
                    scratch[1] = blockCount - 1;
//                    System.out.println("use block count because next set bit from " + (from + 1) + " out of range - " + (blockCount - 2));
                    return scratch;
//                    nextSet = blockCount - 2;
                } else {
                    to = nextSet - 1;
                }
//                System.out.println("find next set bit from " + (from + 1) + " is " + to);
            }
        } else {
            if (allocated) {
                to = used.previousClearBit(from - 1) + 1;
                if (to < 0) {
                    to = 0;
                }
//                System.out.println("  to is " + to);
            } else {
                to = used.previousSetBit(from - 1) + 1;
//                System.out.println("find prev set bit from " + (from - 1) + " is " + to);
                if (to < 0) {
                    to = 0;
                }
            }
        }

        boolean viable = from >= 0 && to >= 0;
//        System.out.println("  viable? " + viable + " for " + from + ", " + to);
        if (viable) {
            scratch[0] = Math.min(from, Math.min(blockCount, to));
            scratch[1] = Math.max(from, Math.min(blockCount, to));
            return scratch;
        } else {
            scratch[0] = -1;
            scratch[1] = -1;
        }
        return null;
    }

    public boolean regions(boolean allocated, boolean backwards, RegionReceiver recv) throws IOException {
        int start = backwards ? blockCount + 1 : 0;
        return regions(start, allocated, backwards, recv);
    }

    public boolean regions(int from, boolean allocated, boolean backwards, RegionReceiver recv) throws IOException {
        boolean result = false;
        int[] scratch = new int[]{-1, -1};
        for (;;) {
            int[] res = nextRegion(from, allocated, backwards, scratch);
            if (res != null) {
                result |= recv.receive(res[0], res[1]);
                if (!backwards) {
                    from = scratch[1] + 1;
                } else {
                    from = scratch[0] - 1;
                }
            } else {
                break;
            }
        }
        return result;
    }

    interface RegionReceiver {

        boolean receive(int start, int end) throws IOException;

        default RegionReceiver filter(int minLength) {
            return (int start, int end) -> {
                if ((end - start) + 1 >= minLength) {
                    return RegionReceiver.this.receive(start, end);
                }
                return false;
            };
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean last = false;
        int start = -1;
        for (int i = 0; i < blockCount; i++) {
            boolean bit = used.get(i);
            boolean write = false;
            if (bit != last) {
                if (bit) {
                    start = i;
                } else {
                    write = true;
                }
            } else if (bit && i == blockCount - 1) {
                write = true;
            }
            if (write) {
                sb.append('[').append(start).append(':').append(i - 1).append(']');
            }
            last = bit;
        }
//        regions(true, false, (a, b) -> {
//            if (sb.length() > 0) {
//                sb.append(", ");
//            }
//            sb.append(a + "->" + b);
//            return true;
//        });
//        return sb.toString();
        return sb.toString();
    }
}
