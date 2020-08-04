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

import com.mastfrog.function.state.Int;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;

/**
 *
 * @author Tim Boudreau
 */
public class OutputTest {

    private static final Random RND = new Random(50192019204284L);
    private static final String TASK_ONE = "one";
    private static final String TASK_TWO = "two";
    private static final int MAX_AGE = 250;
    private JFS jfs;
    private Listener listener;
    private Output output;
    private static final UnixPath PATH_ONE = UnixPath.get("com/foo/SomeGrammar.g4");
    private static final UnixPath PATH_TWO = UnixPath.get("murg/wurble/OtherLanguage.g4");

    @Test
    public void testSimpleSynchronousWrites() throws Exception {
        RecyclingPrintStream p1t1a = (RecyclingPrintStream) output.printStream(PATH_ONE, TASK_ONE);

        assertEquals(1, p1t1a.indices().nextAvailableIndex());
        String text1 = writeToStream(12, 20, p1t1a);
        p1t1a.close();

        listener.assertAllocated(UnixPath.get("0/com/foo/SomeGrammar.g4-one"));

        Supplier<? extends CharSequence> supp = output.outputFor(PATH_ONE, TASK_ONE);
        assertNotNull(supp, "No supplier");
        assertEquals(text1, supp.get().toString());

        RecyclingPrintStream p1t1b = (RecyclingPrintStream) output.printStream(PATH_ONE, TASK_ONE);
        assertSame(p1t1a.indices(), p1t1b.indices(), "Same path and task should share indices");
        assertNotSame(p1t1a.index(), p1t1b.index(), "Two outputs open at the same time for "
                + "the same path and task should have different file indices");

        listener.assertAllocated(UnixPath.get("1/com/foo/SomeGrammar.g4-one"));

        String text2 = writeToStream(10, 15, p1t1b);
        p1t1b.close();
        Supplier<? extends CharSequence> supp2 = output.outputFor(PATH_ONE, TASK_ONE);
        assertNotNull(supp, "No supplier");
        assertEquals(text2, supp2.get().toString());

        assertSame(supp2, output.outputFor(PATH_ONE, TASK_ONE));
        assertEquals(text1, supp.get().toString());

        assertEquals(2, p1t1a.indices().nextAvailableIndex());

        TextSupplierRef ref = ((TextSupplier) supp).ref();

        supp = null;
        forceGC();
        assertNull(ref.get(), "Text supplier is still referenced");
        assertEquals(0, p1t1a.indices().nextAvailableIndex(), "0th index should be available");

        RecyclingPrintStream p1t1c = (RecyclingPrintStream) output.printStream(PATH_ONE, TASK_ONE);
        String text3 = writeToStream(10, 15, p1t1c);

        Supplier<? extends CharSequence> supp3 = output.outputFor(PATH_ONE, TASK_ONE);
        assertNotNull(supp3);
        assertNotEquals(text1, supp3.get(), "Should not get other text");
        assertNotEquals(text3, supp3.get(), "Should not get other text");
        assertEquals(text2, supp3.get().toString(), "Should not get text from an unclosed printstream");
        p1t1c.close();
        assertEquals(text2, supp3.get().toString(), "Text from a supplier should be consistent");
        listJFS();

        Supplier<? extends CharSequence> supp4 = output.outputFor(PATH_ONE, TASK_ONE);
        assertEquals(text3, supp4.get().toString());
        supp4 = null;
        supp3 = null;
        forceGC();
        Supplier<? extends CharSequence> supp5 = output.outputFor(PATH_ONE, TASK_ONE);
        assertSame(supp2, supp5);
        assertNotNull(supp5);
        assertEquals(text2, supp5.get().toString(), "Should have reverted to text "
                + "2 with a supplier still alive for that");

        supp2 = supp3 = supp4 = supp5 = supp = null;
        forceGC();
        assertNull(output.outputFor(PATH_ONE, TASK_ONE), "Should be no open files");

        assertTrue(ref.disposed);
        assertEquals(0, ref.indices.nextAvailableIndex());
        int count = listJFS();
        assertEquals(0, count, "JFS should be empty after all closed");

        assertEquals(0, ref.fo.getCharContent(true).length());
    }

//    @Test
    public void testConcurrent() throws Exception {
        int threadCount = 13;
        int iterations = 150;
        Phaser phaser = new Phaser(threadCount + 1);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<R> all = new ArrayList<>(threadCount);
        List<Thread> threads = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            R r = new R(output, i % 2 == 0 ? PATH_ONE : PATH_TWO, latch, iterations, phaser);
            Thread t = new Thread(r);
            all.add(r);
            t.setDaemon(true);
            t.start();
        }
        phaser.arriveAndDeregister();
        latch.await(30, SECONDS);
        Throwable t = null;
        R first = null;
        for (R r : all) {
            if (t == null) {
                t = r.thrown;
                if (t != null) {
                    first = r;
                }
            } else if (r.thrown != null) {
                t.addSuppressed(r.thrown);
            }
        }
        if (first != null) {
            first.rethrow();
        }
    }

    static class R implements Runnable {

        private final Output output;
        private final UnixPath path;
        private final CountDownLatch latch;
        private final int iterations;
        private final Phaser phaser;
        static int ix;
        private Throwable thrown;

        public R(Output output, UnixPath path, CountDownLatch latch, int iterations, Phaser phaser) {
            this.output = output;
            this.path = path;
            this.latch = latch;
            this.iterations = iterations;
            this.phaser = phaser;
        }

        void rethrow() {
            if (thrown != null) {
                Exceptions.chuck(thrown);
            }
        }

        @Override
        public void run() {
            int tix = ++ix;
            Thread.currentThread().setName("R-" + tix);
            phaser.arriveAndAwaitAdvance();
            int i = 0;
            try {
                for (; i < iterations; i++) {
                    String text;
                    int txtIndex;
                    try (RecyclingPrintStream ps = (RecyclingPrintStream) output.printStream(path, "concurrent")) {
                        text = writeToStream(15, 11 + i, ps);
                        txtIndex = ps.index();
                        ps.close();
                    }
                    TextSupplier seq = (TextSupplier) output.outputFor(txtIndex, path, "concurrent");
                    assertNotNull(seq);
                    int seqIndex = seq.ref().index;
                    if (txtIndex == seqIndex) {
                        assertEquals(text, seq.get().toString());
                    } else {
                        System.out.println("mismatch: " + seqIndex + " and "
                                + txtIndex + " on " + Thread.currentThread());
                    }
                    Thread.yield();
                    if (tix == 1 && i == iterations / 2) {
                        long then = System.currentTimeMillis();
                        output.jfs().whileWriteLocked(() -> {
                            long wait = System.currentTimeMillis() - then;
                            System.out.println("contended " + wait);
                            try {
                                Thread.sleep(MAX_AGE * 2);
                            } catch (InterruptedException ex) {
                                Exceptions.chuck(ex);
                            }
                            for (int j = 0; j < 50; j++) {
                                System.gc();
                                System.runFinalization();
                            }
                            int count = output.jfs().list(StandardLocation.SOURCE_OUTPUT, (loc, fo) -> {
                                System.out.println(fo + " " + fo.length());
                            });
                            System.out.println("count " + count);
                            assertTrue(count <= 13, "Files not being cleaned up");
                            return null;
                        });
                    }
                }
            } catch (Throwable thrown) {
                this.thrown = new AssertionError("Iteration " + i + " of " + Thread.currentThread(), thrown);
            } finally {
                latch.countDown();
            }
        }

    }

    private int listJFS() {
        Int result = Int.create();
//        System.out.println("\nJFS:");
        jfs.list(StandardLocation.SOURCE_OUTPUT, (loc, fo) -> {
//            System.out.println(" - " + fo.getName() + " " + fo.length());
            result.increment();
        });
        return result.getAsInt();
    }

    private void forceGC() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            System.gc();
            System.runFinalization();
            Thread.sleep(5);
        }
    }

    static int lix;

    private static String writeToStream(int wordsPerLine, int lines, PrintStream ps) {
        StringBuilder sb = new StringBuilder();
        Supplier<String> text = text(wordsPerLine);
        for (int i = 0; i < lines; i++) {
            String line = ++lix + ". " + text.get();
            ps.println(line);
            sb.append(line).append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static Supplier<String> text(int words) {
        return () -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < words; i++) {
                int len = RND.nextInt(14) + 2;
                for (int j = 0; j < len; j++) {
                    int cix = 'a' + RND.nextInt(26);
                    sb.append((char) cix);
                }
                if (i != words) {
                    sb.append(' ');
                }
            }
            return sb.toString();
        };
    }

    @BeforeEach
    public void before() throws IOException {
        jfs = JFS.builder()
                .useOffHeapStorage()
                .withCharset(US_ASCII)
                .withListener(listener = new Listener()).build();
        output = new Output(jfs, MAX_AGE);
    }

    @AfterEach
    public void after() throws IOException {
        jfs.close();
        jfs = null;
        output = null;
    }

    private static final class Listener implements BiConsumer<Location, FileObject> {

        JFSFileObject last;

        @Override
        public void accept(Location t, FileObject u) {
            assertSame(StandardLocation.SOURCE_OUTPUT, t);
            last = (JFSFileObject) u;
        }

        void assertAllocated(UnixPath path) {
            JFSFileObject last = this.last;
            this.last = null;
            assertNotNull(last, "Nothing allocated");
            String nm = last.getName();
            assertEquals(path, UnixPath.get(nm));
        }

        void assertNotAllocated() {
            JFSFileObject last = this.last;
            this.last = null;
            assertNull(last);
        }
    }
}
