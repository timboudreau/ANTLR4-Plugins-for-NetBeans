package org.nemesis.jfs.nio;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A set of blocks which may be used by a virtual file. Note that each set of
 * blocks has its own lock; when doing wholesale modifications of large sets of
 * blocks, all affected block locks should be acquired first.
 *
 * @author Tim Boudreau
 */
final class Blocks implements Comparable<Blocks>, Range {

    static final AtomicInteger ids = new AtomicInteger();
    private int size;
    private int start;
    private final int id;
    private volatile boolean discarded;
    private final FunctionalLock lock;
    private static final int[] EMPTY = new int[0];

    public Blocks(int start, int size) {
        this(start, size, ids.getAndIncrement());
    }

    private Blocks(int start, int size, int id) {
        this.start = start;
        this.size = size;
        this.id = id;
        lock = id == -1 ? null :
                new DysfunctionalLock();
//                new FunctionalLockImpl(true, blockStringWithSize(start, size) + "{" + id + "}");
    }

    public static Blocks createTemp(int start, int size) {
        return new Blocks(start, size, -1);
    }

    public static Blocks tempFromCoordinates(int start, int stop) {
        assert stop >= start : "Zero or negative size for " + start + ":" + stop;
        return new Blocks(start, (stop - start) + 1, -1);
    }

    public static Blocks fromCoordinates(int start, int stop) {
        assert stop >= start : "Negative size for " + start + ":" + stop;
        return new Blocks(start, (stop - start) + 1);
    }

    boolean discard() {
        boolean old = discarded;
        discarded = true;
        return old;
    }

    void writeLock() {
        if (id == -1) {
            return;
        }
//        System.out.println("writeLock " + this);
        lock.writeLock();
    }

    void writeUnlock() {
        if (id == -1) {
            return;
        }
//        System.out.println("writeUnlock " + this);
        lock.writeUnlock();
    }

    void readLock() {
        if (id == -1) {
            return;
        }
//        System.out.println("readLock " + this);
        lock.readLock();
    }

    void readUnlock() {
        if (id == -1) {
            return;
        }
        lock.readUnlock();
//        System.out.println("readUnlock " + this);
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
        readLock();
        try {
            return new Blocks(start, size, -1);
        } finally {
            readUnlock();
        }
    }

    boolean isDiscarded() {
        return discarded;
    }

    void locked(Consumer<Blocks> c) {
        if (discarded) {
            c.accept(this);
            return;
        }
        readLock();
        try {
            c.accept(this);
        } finally {
            readUnlock();
        }
    }

    void clear() {
        setStartAndSize(-1, 0);
    }

    boolean matches(Blocks other) {
        readLock();
        try {
            other.readLock();
            try {
                return _matches(other);
            } finally {
                other.readUnlock();
            }
        } finally {
            readUnlock();
        }
    }

    private boolean _matches(Blocks other) {
        return other.size == size && other.start == start;
    }

    private boolean _containsBoundary(Blocks other) {
        return _contains(other._start()) || _contains(other._stop());
    }

    private boolean containsBoundary(Blocks other) {
        readLock();
        try {
            other.readLock();
            try {
                return _containsBoundary(other);
            } finally {
                other.readUnlock();
            }
        } finally {
            readUnlock();
        }
    }

    private boolean _contains(Blocks other) {
        if (other._stop() <= this._stop() && other.start >= this.start) {
            return true;
        }
        return other.start >= start && other._end() < _end();
    }

    boolean contains(Blocks other) {
        readLock();
        try {
            other.readLock();
            try {
                return _contains(other);
            } finally {
                other.readUnlock();
            }
        } finally {
            readUnlock();
        }
    }

    private int[] _startAndSize() {
        return new int[]{start, size};
    }

    int[] startAndSize() {
        readLock();
        try {
            return _startAndSize();
        } finally {
            readUnlock();
        }
    }

    static String blockStringWithCoords(int start, int stop) {
        int len = (stop - start) + 1;
        return "[" + start + ":" + stop + "(" + len + ")]";
    }

    static String blockStringWithSize(int start, int blocks) {
        return "[" + start + ":" + (start + blocks - 1) + "(" + blocks + ")]";
    }

    private int[] _getOverlap(Blocks other) {
        if (_matches(other)) {
            return new int[]{start, _stop()};
        }
        if (_contains(other)) {
            return new int[]{other.start, other._stop()};
        } else if (other.contains(this)) {
            return new int[]{start, _stop()};
        } else if (_containsBoundary(other)) {
            return new int[]{Math.max(start, other.start), Math.min(_stop(), other._stop())};
        }
        return EMPTY;
    }

    int[] getOverlap(Blocks other) {
        readLock();
        try {
            other.readLock();
            try {
                return _getOverlap(other);
            } finally {
                other.readUnlock();
            }
        } finally {
            readUnlock();
        }
    }

    int[] _getNonOverlap(Blocks other) {
        if (!_matches(other)) {
            if (_contains(other)) {
                if (start == other.start) {
                    return new int[]{other._end(), _stop()};
                } else if (_stop() == other._stop()) {
                    return new int[]{start, other.start - 1};
                } else {
                    return new int[]{start, other.start - 1, other._end(), _stop()};
                }
            } else if (other.contains(this)) {
                if (start == other.start) {
                    return new int[]{_end(), other._stop()};
                } else if (_stop() == other._stop()) {
                    return new int[]{other.start, start - 1};
                } else {
                    return new int[]{other.start, start - 1, _end(), other._stop()};
                }
            } else if (containsBoundary(other)) {
                if (_contains(other._stop())) {
                    if (other._stop() == _stop()) {
                        return new int[]{other.start, start - 1};
                    } else {
                        return new int[]{other.start, start - 1, other._stop() + 1, _stop()};
                    }
                } else if (_contains(other.start)) {
                    if (start == other.start) {
                        return new int[]{start, other.start - 1};
                    } else {
                        return new int[]{start, other.start - 1, _end(), other._stop()};
                    }
                }
            }
        }
        return EMPTY;
    }

    int[] getNonOverlap(Blocks other) {
        readLock();
        try {
            other.readLock();
            try {
                return _getNonOverlap(other);
            } finally {
                other.readUnlock();
            }
        } finally {
            readUnlock();
        }
    }

    public boolean setSize(int newSize) {
        writeLock();
        try {
            boolean result = newSize != size;
            this.size = newSize;
            return result;
        } finally {
            writeUnlock();
        }
    }

    public boolean setStartAndSize(int newStart, int newSize) {
        writeLock();
        try {
            boolean result = newStart != start || newSize != size;
            this.start = newStart;
            this.size = newSize;
            return result;
        } finally {
            writeUnlock();
        }
    }

    public boolean setStart(int newStart) {
        writeLock();
        try {
            boolean change = start != newStart;
            start = newStart;
            return change;
        } finally {
            writeUnlock();
        }
    }

    private int _size() {
        return size;
    }

    public int size() {
        readLock();
        try {
            return _size();
        } finally {
            readUnlock();
        }
    }

    private int _start() {
        return start;
    }

    public int start() {
        readLock();
        try {
            return _start();
        } finally {
            readUnlock();
        }
    }

    private int _stop() {
        return start + (size - 1);
    }

    public int stop() {
        readLock();
        try {
            return _stop();
        } finally {
            readUnlock();
        }
    }

    public boolean contains(int block) {
        readLock();
        try {
            return _contains(block);
        } finally {
            readUnlock();
        }
    }

    private boolean _contains(int block) {
        return block >= start && block < start + size;
    }

    private int _end() {
        return start + size;
    }

    @Override
    public int end() {
        readLock();
        try {
            return _end();
        } finally {
            readUnlock();
        }
    }

    boolean _overlaps(int rangeStart, int rangeEnd) {
        if (size <= 0) {
            return false;
        }
        if (_contains(rangeStart) || _contains(rangeEnd)) {
            return true;
        }
        if (start == rangeStart) {
            return true;
        }
        if (rangeEnd <= start) {
            return false;
        }
        if (rangeStart > _end()) {
            return false;
        }
        if (rangeEnd < _end()) {
            return false;
        }
        return rangeStart <= start && rangeEnd >= start;
    }

    boolean overlaps(int rangeStart, int rangeEnd) {
        readLock();
        try {
            return _overlaps(rangeStart, rangeEnd);
        } finally {
            readUnlock();
        }
    }

    boolean _resizeIfExact(int firstBlock, int oldSize, int newSize) {
        if (start == firstBlock && oldSize == size) {
            size = newSize;
            return true;
        }
        return false;
    }

    private boolean _isExactMatch(int firstBlock, int oldSize) {
        return firstBlock == start && oldSize == size;
    }

    boolean resizeIfExact(int firstBlock, int oldSize, int newSize) {
        readLock();
        try {
            if (!_isExactMatch(firstBlock, oldSize)) {
                return false;
            }
        } finally {
            readUnlock();
        }
        writeLock();
        try {
            if (!_isExactMatch(firstBlock, oldSize)) {
                return false;
            }
            return _resizeIfExact(firstBlock, oldSize, newSize);
        } finally {
            writeUnlock();
        }
    }

    public boolean maybeMigrate(int firstBlock, int blockCount, int newFirstBlock, int newBlockCount) {
        writeLock();
        try {
            return _maybeMigrate(firstBlock, blockCount, newFirstBlock, newBlockCount);
        } finally {
            writeUnlock();
        }
    }

    boolean _maybeMigrate(int firstBlock, int blockCount, int newFirstBlock, int newBlockCount) {
        if (firstBlock == start) {
//            System.out.println("MOVE START IN " + this + " to " + newFirstBlock);
            start = newFirstBlock;
            if (newBlockCount != blockCount) {
                size = newBlockCount;
            }
            return true;
        } else if (_overlaps(firstBlock, firstBlock + blockCount - 1)) {
            Blocks origRange = new Blocks(firstBlock, blockCount);
            int[] origOverlap = origRange.getOverlap(this);
            int newStart, newSize;
            if (origRange._end() == _end()) {
                newStart = newFirstBlock + (origOverlap[0] - firstBlock);
                newSize = newBlockCount - (origOverlap[0] - firstBlock);
            } else {
                int offset = start - firstBlock;
                newStart = newFirstBlock + offset;
                newSize = Math.min(size, newBlockCount - offset);
            }
            if (newStart < 0 || newSize < 0) {
                System.out.println("Suspicious offsets " + newStart + ":" + newSize
                        + " for " + this + " when migrating " + firstBlock + ":" + blockCount + " -> "
                        + newFirstBlock + ":" + newBlockCount + " with overlap " + Arrays.toString(origOverlap));
                return false;
            }
            size = newSize;
            start = newStart;
            return true;
        }
        return false;
    }

    @Override
    public int compareTo(Blocks o) {
        int sta = start;
        int stb = o.start;
        int result = sta > stb ? 1 : sta == stb ? 0 : -1;
        if (result == 0) {
            int sza = size;
            int szb = size;
            result = sza > szb ? 1 : sza == szb ? 0 : -1;
        }
        return result;
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

    public Range toPhysicalRange(BlockToBytesConverter mapper) {
        return new PhysRange(this, mapper);

    }

    private static final class PhysRange implements Range {

        private final Blocks blocks;
        private final BlockToBytesConverter mapper;

        public PhysRange(Blocks blocks, BlockToBytesConverter mapper) {
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
    }
}
