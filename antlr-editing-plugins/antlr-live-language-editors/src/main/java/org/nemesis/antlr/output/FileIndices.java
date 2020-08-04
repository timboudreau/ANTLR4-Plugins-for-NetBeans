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
package org.nemesis.antlr.output;

import java.util.Arrays;
import java.util.BitSet;

/**
 *
 * @author Tim Boudreau
 */
final class FileIndices {

    /*
    What this all does;

    We need to round-robin through as small a set of files as possible,
    so the JFS memory manager can keep reusing space rather than allocating
    more and more.

    There may be several "open" files for a given path/task combo at a time,
    either because references to Supplier<String> instances are held, or
    due to in-process writes, or due to written files which nothing has asked
    for (and which will be reclaimed after a timeout).

    So a given "file" goes through four phases of its lifecycle:

    1.  Opened for writing - there is an unclosed print stream or writer which
    may write to it - at this point, it is not visible to the rest of the world.

    2.  The output stream has been closed, and it is ready for reading, but
    nothing has asked for it yet.  At this point, subsequent calls may reclaim
    it if it is oder than the maxAge timeout and nothing has asked for it.

    3.  Opened for reading - A Supplier exists which can provide the file's
    text content.  Until that Supplier is garbage collected, the bytes remain
    allocated;  the TextSupplierRef will delete the file once the garbage
    collector has collected the reference.

    Files are deleted from the JFS once unused (for the back ends with more
    complicated memory management, all this means is that a few bits were flipped
    in a BitSet to mark the space it formerly occupied as unused).

    For each path + task combo, a FileIndices instance exists, which can return
    the index of the most recently modified file that is ready for reading.

    */
    private final BitSet openForReading = new BitSet(32);
    private final BitSet readyForReading = new BitSet(32);
    private final BitSet openForWriting = new BitSet(32);
    private final BitSet merge = new BitSet(32);
    private long[] timestamps = new long[32];
    private int max = -1;
    private int last = -1;
    private final int maxAge;
    private int lastReady = -1;

    FileIndices(int maxAge) {
        this.maxAge = maxAge;
        Arrays.fill(timestamps, -1);
    }

    private BitSet merged() {
        merge.clear();
        merge.or(openForReading);
        merge.or(openForWriting);
        merge.or(readyForReading);
        return merge;
    }

    synchronized int nextAvailableIndex() {
        BitSet m = merged();
        return Math.max(0, m.nextClearBit(0));
    }

    synchronized int historicMaximum() {
        return max;
    }

    synchronized int lastOpened() {
        return last;
    }

    synchronized boolean isOpenForWriting(int ix) {
        return openForWriting.get(ix);
    }

    synchronized boolean isOpenForReading(int ix) {
        return openForReading.get(ix);
    }

    synchronized boolean isReadyForReading(int ix) {
        return readyForReading.get(ix);
    }

    synchronized boolean isOpen(int ix) {
        return ix < max && isOpenForWriting(ix) || isOpenForReading(ix) || isReadyForReading(ix);
    }

    synchronized long age(int ix) {
        if (ix >= timestamps.length) {
            return -1;
        }
        return System.currentTimeMillis() - timestamps[ix];
    }

    public String toString() {
        int maxcard = maxCardinality();
        StringBuilder sb = new StringBuilder("FileIndices(");
        if (maxcard <= 0) {
            sb.append("empty)");
            return sb.toString();
        } else {
            sb.append("current-max=").append(maxOpened()).append(" last=").append(last).append(" historical-max=").append(max);
        }
        BitSet avail = new BitSet(maxcard);
        long now = System.currentTimeMillis();
        for (int i = 0; i < maxcard; i++) {
            boolean reading = openForReading.get(i);
            boolean writing = openForWriting.get(i);
            boolean ready = readyForReading.get(i);
            if (reading | writing | ready) {
                sb.append("\n\t").append(i).append(". ");
                if (reading) {
                    sb.append("read ");
                }
                if (writing) {
                    sb.append("write ");
                }
                if (ready) {
                    sb.append("ready ");
                }
                long age = now - timestamps[i];
                sb.append(age).append(age).append("ms old");
            }
        }
        return sb.append(')').toString();
    }

    public synchronized int writerOpened() {
        BitSet merged = merged();
        int top = max + 1;
        for (int bit = merged.nextClearBit(0); bit >= 0; bit = merged.nextClearBit(bit + 1)) {
            top = bit;
            break;
        }
        openForWriting.set(top);
        max = Math.max(top, max);
        return touch(last = top);
    }

    private int touch(int index) {
        if (index < 0) {
            return index;
        }
        if (index >= timestamps.length) {
            timestamps = Arrays.copyOf(timestamps, timestamps.length * 2);
        }
        timestamps[index] = System.currentTimeMillis();
        return index;
    }

    int maxOpened() {
        return Math.max(openForReading.cardinality(), Math.max(readyForReading.cardinality(), openForWriting.cardinality()));
    }

    int maxCardinality() {
        return Math.max(32, maxOpened());
    }

    BitSet gc() {
        return gc(maxAge);
    }

    synchronized BitSet gc(long maxAge) {
        int card = maxCardinality();
        BitSet waitingForReaders = new BitSet(card);
        waitingForReaders.or(readyForReading);
        waitingForReaders.andNot(openForReading);
        waitingForReaders.andNot(openForWriting);
        long now = System.currentTimeMillis();
        BitSet result = new BitSet(card);
        for (int bit = waitingForReaders.nextSetBit(0); bit >= 0; bit = waitingForReaders.nextSetBit(bit + 1)) {
            if (now - timestamps[bit] > maxAge) {
                readyForReading.clear(bit);
                timestamps[bit] = -1;
                result.set(bit);
            }
        }
        return result;
    }

    public synchronized int readerOpened(int ix) {
        if (ix < 0) {
            return ix;
        }
        openForReading.set(ix);
        readyForReading.clear(ix);
        int result = touch(ix);
        gc();
        return result;
    }

    private boolean isReadable(int ix) {
        return ix >= 0 && !openForWriting.get(ix)
                && (readyForReading.get(ix) || openForReading.get(ix));
    }

    public synchronized int readerOpened() {
        if (last < 0) {
            return last;
        }
        if (isReadable(lastReady)) {
            return readerOpened(lastReady);
        }
        int result = readyForReading.previousSetBit(Integer.MAX_VALUE);
        if (!isReadable(result)) {
            result = openForReading.previousSetBit(Integer.MAX_VALUE);
        }
        return readerOpened(result);
    }

    synchronized void readyForRead(int bit) {
        touch(bit);
        readyForReading.set(bit);
        openForWriting.clear(bit);
        lastReady = bit;
    }

    synchronized void readerDisposed(int bit) {
        openForReading.clear(bit);
        readyForReading.clear(bit);
        gc(maxAge);
    }
}
