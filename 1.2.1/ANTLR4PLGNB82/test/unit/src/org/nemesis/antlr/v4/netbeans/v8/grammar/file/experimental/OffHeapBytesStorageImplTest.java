package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import javax.tools.StandardLocation;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.OffHeapBytesStorageImpl.Pool;

/**
 *
 * @author Tim Boudreau
 */
public class OffHeapBytesStorageImplTest {

//    @Test
    public void thrashTest() throws Throwable {
        thrash(1);
        thrash(13849273);
        thrash(1638492319L);
    }

    long count = 0;

    public void thrash(final long mul) throws Throwable {
        System.out.println("THRASH " + (++count));
        int iterations = 23;
        int chunkCount = 128;
        int minSize = 32;
        int maxSize = 16384;
        Random mainRandom = new Random(mul);
        List<byte[]> chunks = new ArrayList<>();
        List<byte[]> chunks2 = new ArrayList<>();
        List<byte[]> chunks3 = new ArrayList<>();
        List<byte[]> chunks4 = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            byte[] b = new byte[mainRandom.nextInt(maxSize - minSize) + minSize];
            mainRandom.nextBytes(b);
            chunks.add(b);
            b = new byte[mainRandom.nextInt(maxSize - minSize) + minSize];
            mainRandom.nextBytes(b);
            chunks2.add(b);
            b = new byte[maxSize];
            mainRandom.nextBytes(b);
            chunks3.add(b);
            b = new byte[32];
            mainRandom.nextBytes(b);
            chunks4.add(b);
        }
        final ThreadLocal<Random> deterministicChaos = new ThreadLocal<>();
        Pool pool = new Pool(1024, 512);
        try {
            CountDownLatch latch = new CountDownLatch(iterations);
            Callable<Void> doIt = () -> {
                Random rnd = deterministicChaos.get();
                assertNotNull(rnd);
                OffHeapBytesStorageImpl[] files = new OffHeapBytesStorageImpl[chunkCount];
                for (int i = 0; i < chunkCount; i++) {
                    files[i] = pool.allocate(null, Name.forFileName(Thread.currentThread().getName() + "f1-" + i), null);
                    files[i].setBytes(chunks.get(i), 0);
                    assertArrayEquals(files[i].name.toString(), chunks.get(i), files[i].asBytes());
                }
                if (Thread.interrupted()) {
                    return null;
                }
                iterate(chunkCount, rnd, i -> {
                    if (i % 2 == 0) {
                        files[i].setBytes(chunks2.get(i), 0);
                        assertArrayEquals(chunks2.get(i), files[i].asBytes());
                    } else {
                        assertArrayEquals(files[i].name.toString(), chunks.get(i), files[i].asBytes());
                    }
                });
                if (Thread.interrupted()) {
                    return null;
                }
                for (int i = 0; i < chunkCount; i++) {
                    if (i % 2 != 0) {
                        files[i].setBytes(chunks2.get(i), 0);
                        assertArrayEquals(files[i].name.toString(), chunks2.get(i), files[i].asBytes());
                    } else {
                        assertArrayEquals(files[i].name.toString(), chunks2.get(i), files[i].asBytes());
                    }
                }
                if (Thread.interrupted()) {
                    return null;
                }
                for (int i = chunkCount - 1; i >= 0; i--) {
                    if (i % 3 == 0) {
                        files[i].discard();
                        files[i] = null;
                    } else {
                        assertArrayEquals(files[i].name.toString(), chunks2.get(i), files[i].asBytes());
                    }
                }
                if (Thread.interrupted()) {
                    return null;
                }
                iterate(chunkCount, rnd, i -> {
                    if (i % 3 == 0) {
                        files[i] = pool.allocate(null, Name.forFileName(Thread.currentThread().getName() + "f2-" + i), null);
                        files[i].setBytes(chunks.get(i), 0);
                        assertArrayEquals(files[i].name.toString(), chunks.get(i), files[i].asBytes());
                    } else {
                        files[i].discard();
                        files[i] = null;
                    }
                });
                if (Thread.interrupted()) {
                    return null;
                }
                iterate(chunkCount, rnd, i -> {
                    if (i % 3 == 0) {
                        assertArrayEquals(files[i].name.toString(), chunks.get(i), files[i].asBytes());
                    } else {
                        files[i] = pool.allocate(null, Name.forFileName(Thread.currentThread().getName() + "f3-" + i), null);
                        files[i].setBytes(chunks3.get(i), 0);
                        assertArrayEquals(files[i].name.toString(), chunks3.get(i), files[i].asBytes());
                    }
                });
                if (Thread.interrupted()) {
                    return null;
                }
                iterate(chunkCount, rnd, i -> {
                    if (i % 3 != 0) {
                        assertArrayEquals(files[i].name.toString(), chunks3.get(i), files[i].asBytes());
                        files[i].setBytes(chunks4.get(i), 0);
                        assertArrayEquals(files[i].name.toString(), chunks4.get(i), files[i].asBytes());
                    } else {
                        files[i].discard();
                        files[i] = pool.allocate(null, Name.forFileName(Thread.currentThread().getName() + "f4-" + i), null);
                        files[i].setBytes(chunks4.get(i), 0);
                        assertArrayEquals(files[i].name.toString(), chunks4.get(i), files[i].asBytes());
                    }
                });
                if (Thread.interrupted()) {
                    return null;
                }
                for (int i = 0; i < chunkCount; i++) {
                    files[i].setBytes(chunks.get(i), 0);
                }
                if (Thread.interrupted()) {
                    return null;
                }
                for (int i = 0; i < chunkCount; i++) {
                    assertArrayEquals(files[i].name.toString(), chunks.get(i), files[i].asBytes());
                    files[i].discard();
                }
                if (Thread.interrupted()) {
                    return null;
                }
                return null;
            };

            Thread[] threads = new Thread[iterations];
            class UEH implements Thread.UncaughtExceptionHandler {

                private Throwable thrown;
                private Thread thread;

                @Override
                public synchronized void uncaughtException(Thread t, Throwable e) {
                    if (thrown == null) {
                        thrown = e;
                        thread = t;
                    } else {
                        thrown.addSuppressed(e);
                    }
                    for (Thread t1 : threads) {
                        if (t1 != null && t1 != t) {
                            t1.interrupt();
                        }
                    }
                }

                synchronized void rethrow() throws Throwable {
                    if (thrown != null) {
                        throw thrown;
                    }
                }

            }
            UEH ueh = new UEH();
            class Thrasher implements Runnable {

                private final int ix;
                private final Callable<Void> call;

                Thrasher(int ix, Callable<Void> call) {
                    this.ix = ix;
                    this.call = call;
                }

                public void run() {
                    deterministicChaos.set(new Random(mul * (ix + 1) * 163849319L));
                    try {
                        call.call();
                    } catch (Throwable t) {
                        ueh.uncaughtException(Thread.currentThread(), t);
                        for (Thread thr : threads) {
                            if (thr != null && thr != Thread.currentThread()) {
                                thr.interrupt();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                }
            }
            Thrasher[] thrashers = new Thrasher[iterations];
            for (int i = 0; i < iterations; i++) {
                thrashers[i] = new Thrasher(i, doIt);
                threads[i] = new Thread(thrashers[i]);
                threads[i].setName("t-" + i);
                threads[i].setPriority(Thread.NORM_PRIORITY - 1);
                threads[i].setUncaughtExceptionHandler(ueh);
                threads[i].start();
            }
            latch.await(60, TimeUnit.SECONDS);
            ueh.rethrow();
        } finally {
            pool.destroy();
        }
    }

    void iterate(int count, Random rnd, IntConsumer cons) {
        int[] vals = new int[count];
        for (int i = 0; i < vals.length; i++) {
            vals[i] = i;
        }
        shuffle(rnd, vals);
        for (int i = 0; i < count; i++) {
            cons.accept(vals[i]);
        }
    }

    public static void shuffle(Random rnd, int[] array) {
        // fisher-yates shuffle
        for (int i = 0; i < array.length - 2; i++) {
            int r = rnd.nextInt(array.length);
            if (i != r) {
                int hold = array[i];
                array[i] = array[r];
                array[r] = hold;
            }
        }
    }

    @Test
    public void testReadWrite() throws IOException {
        Pool pool = new Pool(256, 64);
        OffHeapBytesStorageImpl item = pool.allocate(null, Name.forFileName("foo"), StandardLocation.ANNOTATION_PROCESSOR_PATH);
        OffHeapBytesStorageImpl item2 = pool.allocate(null, Name.forFileName("foo"), StandardLocation.ANNOTATION_PROCESSOR_PATH);

        byte[] bytes = new byte[128];
        ThreadLocalRandom.current().nextBytes(bytes);
        try (OutputStream out = item.openOutputStream()) {
            out.write(bytes);
        }
        assertEquals(item.length(), bytes.length);
        ByteBuffer buf = item.asByteBuffer();
        byte[] nue = new byte[bytes.length];
        buf.get(nue);
        assertArrayEquals(bytes, nue);

        String text = "Hello world, this is some bytes here ain't it?  Pretty marvelous huh?";
        byte[] hello = text.getBytes(UTF_8);
        try (OutputStream out = item2.openOutputStream()) {
            out.write(hello);
        }
        assertEquals(text, item2.asCharBuffer(UTF_8, true).toString());

        try (InputStream in = item.openInputStream()) {
            byte[] b = new byte[bytes.length];
            int val = in.read(b);
            assertEquals(val, bytes.length);
            assertArrayEquals(bytes, b);
        }

        try (InputStream in = item.openInputStream()) {
            byte[] b = new byte[bytes.length * 2];
            int val = in.read(b);
            assertEquals(val, bytes.length);
            byte[] copy = Arrays.copyOf(b, bytes.length);
            assertArrayEquals(bytes, copy);
        }

        try (InputStream in = item2.openInputStream()) {
            byte[] bb = new byte[hello.length];
            int val = in.read(bb);
            assertEquals(val, hello.length);
            assertArrayEquals(hello, bb);
        }
    }
}
