package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.tools.JavaFileManager;

/**
 * Implementation of JFSBytesStorage which uses a giant direct bytebuffer as its
 * backing storage rather than the java heap, with some minimal, crude garbage
 * collection.
 *
 * @author Tim Boudreau
 */
final class OffHeapBytesStorageImpl implements JFSBytesStorage, Comparable<OffHeapBytesStorageImpl> {

    private final JFSStorage storage;
    private final Pool pool;
    private final Region region;
    volatile long lastModified;
    final Name name;

    OffHeapBytesStorageImpl(JFSStorage storage, Pool pool, Region region, Name name) {
        this.storage = storage;
        this.pool = pool;
        this.region = region;
        this.name = name;
    }

    public String toString() {
        return "Item-" + region;
    }

    @Override
    public InputStream openInputStream() {
        return pool.access(this, (Access<InputStream, RuntimeException>) buf -> {
            return new ByteBufferInputStream(buf);
        });
    }

    @Override
    public byte[] asBytes() {
        ByteBuffer buffer = asByteBuffer();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return pool.access(this, (Access<ByteBuffer, RuntimeException>) buf -> {
            return buf;
        });
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        return new PoolWriter();
    }

    final class PoolWriter extends ByteArrayOutputStream {

        @Override
        public void close() throws IOException {
            super.close();
            pool.putBytes(OffHeapBytesStorageImpl.this, toByteArray());
        }
    }

    @Override
    public long lastModified() {
        return lastModified;
    }

    @Override
    public JFSStorage storage() {
        return storage;
    }

    @Override
    public void discard() {
        pool.discard(this);
    }

    @Override
    public int length() {
        return region.size();
    }

    @Override
    public void setBytes(byte[] bytes, long lastModified) {
        pool.putBytes(this, bytes);
        lastModified = lastModified;
    }

    @Override
    public int compareTo(OffHeapBytesStorageImpl o) {
        return region.compareTo(o.region);
    }

    interface Access<T, R extends Exception> {

        T access(ByteBuffer buf) throws R;
    }

    public static JFSStorageAllocator<?> allocator() {
        return new Pool();
    }

    static final class Pool implements JFSStorageAllocator<OffHeapBytesStorageImpl> {

        private final List<OffHeapBytesStorageImpl> liveItems = new ArrayList<>();
        private static final int INITIAL_POOL_SIZE = 240_000;
        private static final int INCREMENT = 64000;
        private ByteBuffer buffer;
        private final int initialSize;
        private final int increment;

        Pool() {
            this(INITIAL_POOL_SIZE, INCREMENT);
        }

        Pool(int initialSize, int increment) {
            this.initialSize = initialSize;
            this.increment = increment;
        }

        void init() {
            buffer = ByteBuffer.allocateDirect(initialSize);
            buffer.position(0);
        }

        public synchronized void destroy() {
            synchronized (reclaimLock) {
                for (OffHeapBytesStorageImpl i : liveItems) {
                    i.region.clear();
                }
                this.reclaimed.clear();
                buffer = null;
            }
        }

        public <T, R extends Exception> T access(OffHeapBytesStorageImpl impl, Access<T, R> call) throws R {
            if (!liveItems.contains(impl)) {
                throw new IllegalStateException("Attempt to read a deleted file");
            }
            Region region = impl.region;
            if (region.start < 0) {
                call.access(ByteBuffer.allocate(0));
            }
            synchronized (this) {
                if (region.end > buffer.capacity()) {
                    throw new IllegalStateException("Requested region " + region
                            + " of " + buffer);
                }
                if (region.end > buffer.limit()) {
                    buffer.limit(buffer.limit() + region.size());
                }

                buffer.position(region.start);
                ByteBuffer slice = buffer.slice();
                slice.limit(region.end - region.start);
                return call.access(slice);
            }
        }

        private synchronized int rebuild(ByteBuffer old, ByteBuffer nue) {
            Collections.sort(liveItems);
            int newEnd = 0;
            synchronized (reclaimLock) {
                old.limit(old.capacity());
                for (OffHeapBytesStorageImpl i : liveItems) {
                    Region r = i.region;
                    if (r.isEmpty()) {
                        continue;
                    }
                    old.position(r.start);
                    ByteBuffer slice = old.slice();
                    slice.limit(r.end - r.start);
                    int newStart = nue.position();
                    if (nue.limit() < nue.position() + r.size()) {
                        nue.limit(nue.position() + r.size());
                    }
                    nue.put(slice);
                    newEnd = nue.position();
                    r.updateCoordinates(newStart, newEnd);
                }
                nue.flip();
                buffer = nue;
            }
            return newEnd;
        }

        private int lastUsedPosition() {
            assert Thread.holdsLock(this);
            int end = 0;
            for (OffHeapBytesStorageImpl i : liveItems) {
                end = Math.max(i.region.end, end);
            }
            return end;
        }

        private int growToSize(int size) {
            if (size % increment != 0) {
                size += (size % increment) + increment;
            }
            ByteBuffer nue = ByteBuffer.allocateDirect(size);
            return rebuild(buffer, nue);
        }

        private int bytesRequired() {
            int total = 0;
            for (OffHeapBytesStorageImpl i : liveItems) {
                total += i.region.size();
            }
            return total;
        }

        private int growIfNecessary(int additionalBytes) {
            assert Thread.holdsLock(this);
            int currEnd = lastUsedPosition();
            int newEnd = currEnd + additionalBytes;
            if (newEnd >= buffer.capacity()) {
                currEnd = growToSize(buffer.capacity() + bytesRequired() + additionalBytes);
                if (currEnd + additionalBytes > buffer.capacity()) {
                    throw new IllegalStateException("Did not grow enough - "
                            + " need room for " + additionalBytes + " at " + currEnd
                            + " but have " + (buffer.capacity() - buffer.limit()));
                }
            }
            return currEnd;
        }

        public synchronized void putBytes(OffHeapBytesStorageImpl item, byte[] bytes) {
            if (!liveItems.contains(item)) {
                throw new IllegalArgumentException("Write to a deleted item " + item);
            }
            if (buffer == null) {
                init();
            }
            if (bytes.length == 0) {
                Region oldCoords = item.region.snapshot();
                if (oldCoords.isEmpty()) {
                    return;
                }
                reclaim(new int[]{oldCoords.start, oldCoords.end});
                item.region.updateCoordinates(-1, -1);
                return;
            }
            Region oldCoords = item.region.snapshot();
            if (bytes.length <= oldCoords.size()) {
                if (oldCoords.start + bytes.length > buffer.limit()) {
                    buffer.limit(buffer.limit() + bytes.length);
                }
                buffer.position(oldCoords.start);
                buffer.put(bytes);
                if (buffer.position() != oldCoords.end) {
                    item.region.updateCoordinates(oldCoords.start, oldCoords.start + bytes.length);
                    reclaim(new int[]{buffer.position(), oldCoords.end});
                }
                return;
            }

            Region reuse = underReclaimLock(() -> {
                ReclaimableRegion found = findReclaimableRegionToFit(bytes.length);
                return found == null ? null : found.use(bytes.length);
            });
            if (reuse != null) {
                item.region.updateCoordinates(reuse.start, reuse.end);
                if (reuse.end > buffer.limit()) {
                    buffer.limit(reuse.end);
                }
                buffer.position(reuse.start);
                buffer.put(bytes);
                return;
            }

            int start = growIfNecessary(bytes.length);
            underReclaimLock(() -> {
                for (ReclaimableRegion rr : new ArrayList<>(reclaimed)) {
                    if (rr.contains(start) || rr.contains(start + bytes.length)) {
                        reclaimed.remove(rr);
                    }
                }
                return null;
            });
            try {
                if (start + bytes.length > buffer.limit()) {
                    buffer.limit(start + bytes.length);
                }
                buffer.position(start);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Position at " + start + " buffer "
                        + " pos " + buffer.position() + " limit " + buffer.limit() + " cap "
                        + buffer.capacity() + " required end " + (start + bytes.length), ex);
            }
            if (start + bytes.length > buffer.limit()) {
                buffer.limit(buffer.limit() + bytes.length);
            }
            buffer.put(bytes);
            int[] oc = item.region.updateCoordinates(start, start + bytes.length);
            if (!oldCoords.isEmpty()) {
                reclaim(oc);
            }
        }

        public synchronized void discard(OffHeapBytesStorageImpl item) {
            if (buffer == null) {
                return; //already destroyed
            }
            reclaim(item.region.clear());
            liveItems.remove(item);
        }

        @Override
        public OffHeapBytesStorageImpl allocate(JFSStorage storage, Name name, JavaFileManager.Location location) {
            OffHeapBytesStorageImpl result = new OffHeapBytesStorageImpl(storage, this, new Region(), name);
            synchronized (this) {
                liveItems.add(result);
            }
            return result;
        }

        @Override
        public void onDiscard(JFSBytesStorage obj) {
            if (obj instanceof OffHeapBytesStorageImpl) {
                int[] oldCoords = ((OffHeapBytesStorageImpl) obj).region.clear();
                reclaim(oldCoords);
            }
        }

        <T> T underReclaimLock(Supplier<T> supp) {
            synchronized (reclaimLock) {
                return supp.get();
            }
        }

        ReclaimableRegion findReclaimableRegionToFit(int size) {
            synchronized (reclaimLock) {
                List<ReclaimableRegion> all = new LinkedList<>(reclaimed);
                // Sort largest to smallest
                Collections.sort(all);
                // Now reverse that, so we first check the smallest
                // ones
                Collections.reverse(all);
                // Prune any that are too small
                for (Iterator<ReclaimableRegion> iter = all.iterator(); iter.hasNext();) {
                    ReclaimableRegion r = iter.next();
                    if (r.size() < size) {
                        iter.remove();
                    }
                }
                // Now sort the ones that can fit this item from smallest
                // to largest
                Collections.sort(all, (a, b) -> {
                    int aDiff = a.size() - size;
                    int bDiff = b.size() - size;
                    return aDiff > bDiff ? 1 : aDiff == bDiff ? 0 : -1;
                });

                // Now filter that list down to the smallest ones
                // that can possibly fit this item
                List<ReclaimableRegion> winnowed = new LinkedList<>();
                int bestSize = -1;
                for (ReclaimableRegion r : all) {
                    if (bestSize == -1) {
                        winnowed.add(r);
                        bestSize = r.size();
                    } else {
                        if (r.size() == bestSize) {
                            winnowed.add(r);
                        } else {
                            break;
                        }
                    }
                }
                // Now, if we have multiple candidates of the same
                // size, choose the one with the highest end, so that
                // we prefer opening up a bigger window to reclaim
                // towards the beginning
                if (winnowed.size() == 1) {
                    return winnowed.get(0);
                } else {
                    Collections.sort(winnowed, (a, b) -> {
                        int ea = a.end;
                        int eb = b.end;
                        return ea > eb ? -1 : ea == eb ? 0 : -1;
                    });
                }

                for (ReclaimableRegion r : winnowed) {
                    if (r.size() >= size) {
                        return r;
                    }
                }
                return null;
            }
        }

        final Set<ReclaimableRegion> reclaimed = new HashSet<>();
        private final Object reclaimLock = new Object();

        private void reclaim(int[] oldCoords) {
            synchronized (this) {
                if (oldCoords[0] == -1) {
                    return;
                }
                if (oldCoords[1] == buffer.limit()) {
//                    buffer.limit(oldCoords[0]);
                    return;
                }
                if (oldCoords[0] == 0 /* && oldCoords[1] > 512 */) {
                    synchronized (reclaimLock) {
                        buffer.position(oldCoords[1]);
                        buffer.compact();
                        for (OffHeapBytesStorageImpl item : liveItems) {
                            item.region.shift(oldCoords[1]);
                        }
                        Set<ReclaimableRegion> newReclaims = new HashSet<>();
                        for (ReclaimableRegion r : reclaimed) {
                            ReclaimableRegion revised = r.shiftLeft(oldCoords[1]);
                            newReclaims.add(revised);
                        }
                        reclaimed.clear();
                        reclaimed.addAll(newReclaims);
                        checkOverlaps();
                    }
                    return;
                }
            }
            Set<ReclaimableRegion> newReclaimed = new HashSet<>();
            ReclaimableRegion toReclaim = new ReclaimableRegion(oldCoords);
            int reclaimableBytes = toReclaim.size();
            boolean changed = false;
            synchronized (reclaimLock) {
                for (ReclaimableRegion r : reclaimed) {
                    ReclaimableRegion replacement = r.combineIfOverlapping(toReclaim);
                    if (replacement != null) {
                        System.out.println("combine " + r + " and " + toReclaim + " to " + replacement);
                        newReclaimed.add(replacement);
                        toReclaim = replacement;
                        changed = true;
                        reclaimableBytes -= replacement.size() - toReclaim.size();
                        reclaimableBytes += replacement.size();
                        continue;
                    }
                    if (!toReclaim.contains(r)) {
                        newReclaimed.add(r);
                        reclaimableBytes += r.size();
                    }
                }
                if (changed) {
                    reclaimed.clear();
                    reclaimed.addAll(newReclaimed);
                } else {
                    reclaimed.add(toReclaim);
                }
                System.out.println("RECLAIMABLE: " + reclaimableBytes + " - " + reclaimed);
                checkOverlaps();
                if (reclaimed.size() < 4) {
                    return;
                }
            }
            if (reclaimableBytes > initialSize / 2) {
                gc(reclaimableBytes);
            }
        }

        private void checkOverlaps() {
            for (ReclaimableRegion r : reclaimed) {
                for (ReclaimableRegion r1 : reclaimed) {
                    if (r != r1) {
                        if (r.overlaps(r1)) {
                            throw new IllegalStateException("Overlapping regions " + r + " and " + r1);
                        }
                    }
                }
            }
        }

        private void gc(int reclaimableBytes) {
            int requiredSize = 0;
            synchronized (this) {
                for (OffHeapBytesStorageImpl item : liveItems) {
                    requiredSize += item.length();
                }
                int rsize = requiredSize - initialSize;
                int target;
                if (rsize > initialSize) {
                    target = initialSize + (((rsize / increment) + 1) * increment);
                } else {
                    target = initialSize;
                }
                System.out.println("gc with reclaimable " + reclaimableBytes + " liveItems " + liveItems.size()
                        + " target " + target + " req size " + requiredSize);
                synchronized (reclaimLock) {
                    clearReclaimed();
                    ByteBuffer nue = ByteBuffer.allocateDirect(target);
                    rebuild(buffer, nue);
                }
            }
        }

        private void clearReclaimed() {
            synchronized (reclaimLock) {
                reclaimed.clear();
            }
        }

        final class ReclaimableRegion implements Comparable<ReclaimableRegion> {

            private final int start;
            private final int end;

            public ReclaimableRegion(int start, int end) {
                // Like region, but implements a different equality contract
                this.start = start;
                this.end = end;
            }

            public ReclaimableRegion(int[] coords) {
                this.start = coords[0];
                this.end = coords[1];
            }

            public ReclaimableRegion(Region region) {
                this(region.start, region.end);
            }

            public ReclaimableRegion shiftLeft(int amt) {
                if (start - amt < 0) {
                    throw new IllegalArgumentException("Shifting " + this + " left by " + amt
                            + " gets a negative start offset " + (start - amt) + ":" + (end - amt));
                }
                ReclaimableRegion reg = new ReclaimableRegion(start - amt, end - amt);
                return reg;
            }

            public Region use(int length) {
                int sz = this.size();
                if (length > sz) {
                    throw new IllegalArgumentException("Too large: " + length + " vs. " + this);
                }
                Region region = new Region(end - length, end);
                synchronized (reclaimLock) {
                    boolean removed = reclaimed.remove(this);
                    if (!removed) {
                        throw new IllegalStateException("Used twice: " + this);
                    }
                    if (length < sz) {
                        reclaim(new int[]{start, region.start});
                    }
                    checkOverlaps();
                }
                return region;
            }

            public String toString() {
                return start + ":" + end + "(" + size() + ")";
            }

            ReclaimableRegion combineIfOverlapping(ReclaimableRegion other) {
                if (contains(other)) {
                    return this;
                } else if (other.contains(this)) {
                    return other;
                } else if (other == this || other.equals(this)) {
                    return this;
                }
                if (contains(other.start)) {
                    return new ReclaimableRegion(start, other.end);
                } else if (contains(other.end)) {
                    return new ReclaimableRegion(other.start, end);
                }
                if (precedes(other)) {
                    return new ReclaimableRegion(start, other.end);
                } else if (succeeds(other)) {
                    return new ReclaimableRegion(other.start, end);
                }
                return null;
            }

            private boolean precedes(ReclaimableRegion r) {
                return end == r.start;
            }

            private boolean succeeds(ReclaimableRegion r) {
                return start == r.end;
            }

            boolean overlaps(ReclaimableRegion r1) {
                return contains(r1.start) || contains(r1.end)
                        || r1.contains(start) || r1.contains(end);
            }

            boolean contains(int position) {
                return position >= start && position < end;
            }

            boolean contains(ReclaimableRegion r) {
                return contains(r.start) && contains(r.end);
            }

            @Override
            public int compareTo(ReclaimableRegion o) {
                int sz = size();
                int osz = o.size();
                return sz > osz ? -1 : sz == osz ? 0 : -1;
            }

            synchronized int size() {
                return end - start;
            }

            boolean isEmpty() {
                return size() == 0;
            }

            public int hashCode() {
                return ((start + 1) * 16417)
                        + end;
            }

            public boolean equals(Object o) {
                return o == this
                        ? true
                        : o == null
                                ? false
                                : o instanceof ReclaimableRegion
                                        ? ((ReclaimableRegion) o).start == start
                                        && ((ReclaimableRegion) o).end == end : false;
            }
        }
    }

    private static final class Region implements Comparable<Region> {

        private int start = -1;
        private int end = -1;

        Region() {

        }

        Region(int start, int end) {
            this.start = Math.max(-1, start);
            this.end = Math.max(-1, end);
        }

        Region(int[] coords) {
            this(coords[0], coords[1]);
        }

        public String toString() {
            return start + "," + end;
        }

        synchronized void shift(int amount) {
            if (start >= 0) {
                start -= amount;
            }
            if (end >= 0) {
                end -= amount;
            }
        }

        synchronized Region snapshot() {
            return new Region(start, end);
        }

        private synchronized int[] updateCoordinates(int start, int end) {
            int oldStart = start;
            int oldEnd = end;
            this.start = start;
            this.end = end;
            return new int[]{oldStart, oldEnd};
        }

        private int[] clear() {
            return updateCoordinates(-1, 1);
        }

        synchronized int size() {
            return end - start;
        }

        boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public int compareTo(Region o) {
            int sz = size();
            int osz = o.size();
            return sz > osz ? -1 : sz == osz ? 0 : -1;
        }

//        @Override
//        public int hashCode() {
//            int hash = 7;
//            hash = 67 * hash + this.start;
//            hash = 67 * hash + this.end;
//            return hash;
//        }
//
//        @Override
//        public boolean equals(Object obj) {
//            if (this == obj) {
//                return true;
//            }
//            if (obj == null) {
//                return false;
//            }
//            if (getClass() != obj.getClass()) {
//                return false;
//            }
//            final Region other = (Region) obj;
//            int[] mine = this.coordinates();
//            int[] theirs = other.coordinates();
//            return mine[0] == theirs[0] && mine[1] == theirs[1];
//        }
    }

    private static final class ByteBufferInputStream extends InputStream {

        private final ByteBuffer buf;

        ByteBufferInputStream(ByteBuffer buf) {
            this.buf = buf;
        }

        @Override
        public int read() throws IOException {
            return buf.remaining() == 0 ? -1 : buf.get();
        }

        @Override
        public int available() throws IOException {
            return buf.limit() - buf.position();
        }

        @Override
        public void reset() throws IOException {
            buf.position(0);
        }

        @Override
        public int read(byte[] b) throws IOException {
            int rem = buf.limit() - buf.position();
            int result = Math.min(rem, b.length);
            buf.get(b, 0, result);
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (off + len > b.length) {
                throw new IOException("offset + length=" + (off + len) + " but array length is " + b.length);
            }
            int rem = buf.remaining();
            if (rem == 0) {
                return -1;
            }
            int result = Math.min(len, rem);
            buf.get(b, off, result);
            return result;
        }
    }

    /*
    static class BlockManager {
        private final int blockSize;
        private int allocated;
        private final BitSet bits;

        BlockManager(int blockSize, int allocated) {
            bits = new BitSet(allocated);
            this.blockSize = blockSize;
        }

        public int blocksToBytes(int blocks) {
            return blockSize * blocks;
        }

        public int bytesToBlocks(int bytes) {
            int result = bytes / blockSize;
            if (result % blockSize != 0) {
                result++;
            }
            return result;
        }

        private int availableBlocks() {
            return allocated - bits.cardinality();
        }

        public int findContiguousBlock(int count) {
            int contiguous = -1;
            int currCount = 0;
            int last = -1;
            for (int bit = bits.nextSetBit(0); bit >= 0; last = bit, bit = bits.nextSetBit(bit + 1)) {
                if (bit == last+1) {
                    currCount = 1;
                }
            }
        }
    }
*/

}
