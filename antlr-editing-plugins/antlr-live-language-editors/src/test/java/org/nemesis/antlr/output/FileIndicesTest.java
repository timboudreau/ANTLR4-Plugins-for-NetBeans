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

import com.mastfrog.util.collections.ArrayUtils;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class FileIndicesTest {

    private static final int MAX_AGE = 100;

    @Test
    public void testStatesInOrder() {
        int ct = 5;
        FileIndices in = new FileIndices(MAX_AGE);
        long[] touches = new long[ct];
        long maxTouch = 0;
        for (int i = 0; i < ct; i++) {
            touches[i] = maxTouch = System.currentTimeMillis();
            int ix = in.writerOpened();
            assertEquals(i, ix, "Wrong index returned for " + i + ": " + ix);
            assertTrue(in.isOpenForWriting(ix), "Should be open for writing after taking " + ix);
            assertFalse(in.isReadyForReading(ix), "Should not yet be open for reading: " + ix);
            assertFalse(in.isOpenForReading(ix), "Should not yet be open for reading: " + ix);
            assertEquals(ix, in.lastOpened(), "Last opened should be " + ix);
        }
        System.out.println("AFTER OPEN WRITER: " + in);
        for (int i = 0; i < ct; i++) {
            touches[i] = maxTouch = System.currentTimeMillis();
            in.readyForRead(i);
            assertFalse(in.isOpenForWriting(i), "Should be open for writing after closing writer " + i);
            assertTrue(in.isReadyForReading(i), "Should be ready for reading after closing writer " + i);
            assertFalse(in.isOpenForReading(i), "Should not yet be open for reading after closing writer " + i);
        }
        System.out.println("AFTER CLOSE WRITER: " + in);
        for (int i = 0; i < ct; i++) {
            touches[i] = maxTouch = System.currentTimeMillis();
            int ix = in.readerOpened(i);
            assertEquals(i, ix, "Wrong reader index opened");
            assertTrue(in.isOpenForReading(i), "Should be open for reading ofter opening " + i + " for reading");
            assertFalse(in.isOpenForWriting(i), "Should not be open for writing after opening " + i + " for reading");
            assertFalse(in.isReadyForReading(i), "Should not be ready for reading: " + i + " for reading");
        }
        System.out.println("AFTER OPEN READER: " + in);
        for (int i = 0; i < ct; i++) {
            in.readerDisposed(i);
            assertFalse(in.isOpenForReading(i), "Should not be open for reading after disposing reader " + i);
            assertFalse(in.isOpenForWriting(i), "Should not be open for writing after disposing reader " + i);
            assertFalse(in.isReadyForReading(i), "Should not be ready for after disposing reader" + i);
        }
        System.out.println("AFTER CLOSE READER: " + in);
    }

    @Test
    public void testGc() throws InterruptedException {
        int ct = 5;
        int total = (ct * 2) + 1;
        FileIndices in = new FileIndices(MAX_AGE);
        for (int i = 0; i < ct; i++) {
            int ix = in.writerOpened();
            in.readyForRead(ix);
        }
        int next = in.writerOpened();
        assertEquals(next, ct, "Should have opened writer " + ct + " but opened " + next);
        for (int i = 0; i < ct; i++) {
            int ix = in.writerOpened();
            in.readyForRead(ix);
        }
        Thread.sleep(MAX_AGE + (MAX_AGE / 2));
        BitSet got = in.gc(MAX_AGE);
        BitSet expect = new BitSet(total);
        expect.set(0, total);
        expect.clear(ct);
        assertEquals(expect, got, bs2s("Unread but ready to read indices should have been be cleared", expect, got));
        BitSet realloc = new BitSet((ct * 2) + 1);
        for (int i = 0; i < (ct * 2) + 1; i++) {
            int ix = in.writerOpened();
            realloc.set(ix);
            assertNotEquals(ct, ix, "Should not have reused item " + ct + " which still has an open writer");
        }
        assertEquals(expect, got, bs2s("Should have reused the recycled items", expect, realloc));
    }

    @Test
    public void testConcurrent() throws InterruptedException {
        int threadCount = 7;
        int iterations = 500;
        FileIndices indices = new FileIndices(100000);
        Phaser phase1 = new Phaser(threadCount + 1);
        CountDownLatch phase1complete = new CountDownLatch(1);
        Phaser phase2 = new Phaser(threadCount + 1);
        CountDownLatch phase2complete = new CountDownLatch(1);
        Phaser phase3 = new Phaser(threadCount + 1);
        CountDownLatch phase3complete = new CountDownLatch(1);
        Phaser phase4 = new Phaser(threadCount + 1);
        CountDownLatch phase4complete = new CountDownLatch(1);
        Phaser phase5 = new Phaser(threadCount + 1);
        CountDownLatch phase5complete = new CountDownLatch(1);
        R[] all = new R[threadCount];
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < threadCount; i++) {
            R r = new R(indices, iterations,
                    phase1, phase1complete,
                    phase2, phase2complete,
                    phase3, phase3complete,
                    phase4, phase4complete,
                    phase5, phase5complete
            );
            all[i] = r;
            Thread t = new Thread(r, "t-" + i);
            t.setDaemon(true);
            t.start();
        }

        BitSet[] writers1 = new BitSet[threadCount];
        BitSet[] writers2 = new BitSet[threadCount];
        BitSet[] readers1 = new BitSet[threadCount];
        BitSet[] readers2 = new BitSet[threadCount];
        phase1.arriveAndDeregister();
        phase1complete.await(30, SECONDS);

        for (int i = 0; i < threadCount; i++) {
            writers1[i] = (BitSet) all[i].writers1.clone();
        }

        phase2.arriveAndDeregister();
        phase2complete.await(30, SECONDS);

        phase3.arriveAndDeregister();
        phase3complete.await(30, SECONDS);

        for (int i = 0; i < threadCount; i++) {
            writers2[i] = (BitSet) all[i].writers2.clone();
        }

        phase4.arriveAndDeregister();
        phase4complete.await(30, SECONDS);

        for (int i = 0; i < threadCount; i++) {
            readers1[i] = (BitSet) all[i].readers1.clone();
        }

        phase5.arriveAndDeregister();
        phase5complete.await(30, SECONDS);

        for (int i = 0; i < threadCount; i++) {
            readers2[i] = (BitSet) all[i].readers2.clone();
        }

        assertNoIntersections("Writers 1", iterations, writers1);
        assertNoIntersections("Writers 2", iterations, writers2);
        BitSet[] allWriters = ArrayUtils.concatenate(writers1, writers2);
        assertNoIntersections("Combined writers", iterations, allWriters);

        assertNoIntersections("Readers 1", iterations, readers1);
        assertNoIntersections("Readers 2", iterations, readers2);
        BitSet[] allReaders = ArrayUtils.concatenate(readers1, readers2);
        assertNoIntersections("Combined readers", iterations, readers2);
    }

    private void assertNoIntersections(String name, int sz, BitSet[] bits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bits.length; i++) {
            for (int j = 0; j < bits.length; j++) {
                if (i != j) {
                    if (bits[i].intersects(bits[j])) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        BitSet isect = (BitSet) bits[i].clone();
                        isect.and(bits[j]);
                        sb.append(name).append(" set ")
                                .append(i).append(" intersects with set ")
                                .append(j).append(". Intersection: ")
                                .append(isect)
                                .append(" ISET ").append(bits[i])
                                .append(" JSET ").append(bits[j]);
                    }
                }
            }
        }
        if (sb.length() > 0) {
            fail(sb.toString());
        }
    }

    private static final class R implements Runnable {

        final Phaser phase1;
        final CountDownLatch phase1complete;
        final Phaser phase2;
        final CountDownLatch phase2complete;
        final Phaser phase3;
        final CountDownLatch phase3complete;
        final Phaser phase4;
        final CountDownLatch phase4complete;
        final Phaser phase5;
        final CountDownLatch phase5complete;
        private final FileIndices indices;
        private final int iterations;
        final BitSet writers1;
        final BitSet writers2;
        final BitSet readers1;
        final BitSet readers2;

        public R(FileIndices indices, int iterations,
                Phaser phase1, CountDownLatch phase1complete,
                Phaser phase2, CountDownLatch phase2complete,
                Phaser phase3, CountDownLatch phase3complete,
                Phaser phase4, CountDownLatch phase4complete,
                Phaser phase5, CountDownLatch phase5complete
        ) {
            writers1 = new BitSet(iterations);
            writers2 = new BitSet(iterations);
            readers1 = new BitSet(iterations);
            readers2 = new BitSet(iterations);
            this.iterations = iterations;
            this.indices = indices;
            this.phase1 = phase1;
            this.phase1complete = phase1complete;
            this.phase2 = phase2;
            this.phase2complete = phase2complete;
            this.phase3 = phase3;
            this.phase3complete = phase3complete;
            this.phase4 = phase4;
            this.phase4complete = phase4complete;
            this.phase5 = phase5;
            this.phase5complete = phase5complete;
        }

        @Override
        public void run() {
            phase1.arriveAndAwaitAdvance();
            for (int i = 0; i < iterations; i++) {
                Thread.yield();
                int w = indices.writerOpened();
                writers1.set(w);
            }
            phase1complete.countDown();
            phase2.arriveAndAwaitAdvance();
            for (int bit = writers1.nextSetBit(0); bit >= 0; bit = writers1.nextSetBit(bit + 1)) {
                Thread.yield();
                indices.readyForRead(bit);
            }
            phase2complete.countDown();
            phase3.arriveAndAwaitAdvance();
            for (int i = 0; i < iterations; i++) {
                Thread.yield();
                int w = indices.writerOpened();
                writers2.set(w);
            }
            phase3complete.countDown();
            phase4.arriveAndAwaitAdvance();
            for (int bit = writers2.nextSetBit(0); bit >= 0; bit = writers2.nextSetBit(bit + 1)) {
                indices.readyForRead(bit);
                Thread.yield();
            }
            for (int bit = writers1.nextSetBit(0); bit >= 0; bit = writers1.nextSetBit(bit + 1)) {
                int opened = indices.readerOpened(bit);
                readers1.set(opened);
                Thread.yield();
                indices.readerDisposed(opened);
            }
            phase4complete.countDown();
            phase5.arriveAndAwaitAdvance();
            for (int bit = writers2.nextSetBit(0); bit >= 0; bit = writers2.nextSetBit(bit + 1)) {
                int opened = indices.readerOpened(bit);
                readers2.set(opened);
                Thread.yield();
                indices.readerDisposed(opened);
            }
            phase5complete.countDown();
        }
    }

    private Supplier<String> bs2s(String msg, BitSet expect, BitSet got) {
        return () -> {
            StringBuilder sb = new StringBuilder();
            for (int bit = expect.nextSetBit(0); bit >= 0; bit = expect.nextSetBit(bit + 1)) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(bit);
            }
            sb.insert(0, '[').append(']')
                    .insert(0, msg);
            sb.append(" but got [");
            boolean first = true;
            for (int bit = got.nextSetBit(0); bit >= 0; bit = got.nextSetBit(bit + 1)) {
                if (!first) {
                    sb.append(',');
                }
                sb.append(bit);
                first = false;
            }
            sb.append(']');
            int card = Math.max(expect.cardinality(), got.cardinality());
            BitSet unexpected = new BitSet(card);
            unexpected.or(got);
            unexpected.andNot(expect);

            if (unexpected.cardinality() > 0) {
                sb.append(" unexpected: [");
                first = true;
                for (int bit = unexpected.nextSetBit(0); bit >= 0; bit = unexpected.nextSetBit(bit + 1)) {
                    if (!first) {
                        sb.append(',');
                    }
                    sb.append(bit);
                    first = false;
                }
                sb.append("]");
            }

            BitSet absent = new BitSet(card);
            absent.or(got);
            absent.andNot(expect);
            if (absent.cardinality() > 0) {
                sb.append(" absent: [");
                first = true;
                for (int bit = absent.nextSetBit(0); bit >= 0; bit = absent.nextSetBit(bit + 1)) {
                    if (!first) {
                        sb.append(',');
                    }
                    sb.append(bit);
                    first = false;
                }
                sb.append("]");
            }
            return sb.toString();
        };
    }

    @Test
    public void testStatesInterleaved() {
        int ct = 5;
        FileIndices in = new FileIndices(MAX_AGE);
        for (int i = 0; i < ct; i++) {
            int open = in.writerOpened();
            assertEquals(i, open, "Wrong writer opened for " + i);
            in.readyForRead(i);
            in.readerOpened(i);
            for (int j = 0; j < 10; j++) {
                int curr = in.writerOpened();
                assertEquals(j + i + 1, curr, "Wrong index " + i + " / " + j + " opened");
                in.readyForRead(curr);
                in.readerOpened(curr);
//                in.readerDisposed(curr);
            }
            System.out.println("PRE-CLOSE " + i);
            for (int j = 0; j < 10; j++) {
                in.readerDisposed(j + i + 1);
            }
            System.out.println("POST " + i + ": " + in);
        }
    }
}
