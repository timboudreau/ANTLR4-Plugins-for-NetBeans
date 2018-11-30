package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.nio;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class FunctionalLockImplTest {

    private IntSource src;
    private IntSource src2;

    @Before
    public void before() {
        src = new IntSource(23);
        src2 = new IntSource(42);
    }

    @Test
    public void testSimple() {
        src.assertValue(23);
        src.get(v -> {
            assertEquals(23, v);
        });
        src.set(24);
        src.assertValue(24);
        src.get(v -> {
            assertEquals(24, v);
        });
        src.set(() -> {
            return 25;
        });
        src.assertValue(25);
        src.get(v -> {
            assertEquals(25, v);
        });
    }

    @Test
    public void testBlocking() throws Throwable {
        BlockingWriter bw = new BlockingWriter(30).go();
        ReadIt readIt = new ReadIt().go();
        readIt.assertNoValue();
        bw.assertNotCalled().release();
        readIt.assertValue(30);
        assertEquals(30, src.get());
    }

    abstract class Goer<T extends Goer<T>> implements Runnable {
        CountDownLatch threadStartLatch = new CountDownLatch(1);

        @SuppressWarnings("unchecked")
        T go() throws InterruptedException {
            new Thread(this).start();
            threadStartLatch.await(10, TimeUnit.SECONDS);
            Thread.sleep(50);
            return (T) this;
        }
        
        protected abstract void doRun();

        public final void run() {
            threadStartLatch.countDown();
            doRun();
        }
    }

    final class ReadIt extends Goer<ReadIt> {

        volatile int val = -1;

        volatile boolean blocked;

        @Override
        public void doRun() {
            blocked = true;
            try {
                val = src.get();
            } finally {
                blocked = false;
            }
        }

        ReadIt assertNoValue() {
            int old = val;
            val = -1;
            assertEquals(-1, old);
            return this;
        }

        ReadIt assertValue(int v) {
            int old = val;
            val = -1;
            assertEquals(v, old);
            return this;
        }
    }

    final class BlockingWriter extends Goer<BlockingWriter> implements IntSupplier {

        private final int newValue;
        private final CountDownLatch latch;
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile boolean called;

        public BlockingWriter(int newValue) {
            this(newValue, new CountDownLatch(1));
        }

        public BlockingWriter(int newValue, CountDownLatch latch) {
            this.newValue = newValue;
            this.latch = latch;
        }

        BlockingWriter release() throws InterruptedException {
            latch.countDown();
            completed.await(1000, TimeUnit.MILLISECONDS);
            Thread.sleep(50);
            return this;
        }

        BlockingWriter assertNotCalled() {
            boolean old = called;
            called = false;
            assertFalse(old);
            return this;
        }

        BlockingWriter assertCalled() {
            boolean old = called;
            called = false;
            assertTrue(old);
            return this;
        }

        @Override
        public int getAsInt() {
            try {
                latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            called = true;
            try {
                return newValue;
            } finally {
                completed.countDown();
            }
        }

        @Override
        public void doRun() {
            src.set(this);
        }
    }

    static final class BlockingFetcher implements IntConsumer {

        private final CountDownLatch latch;
        private int value = -1;

        public BlockingFetcher(CountDownLatch latch) {
            this.latch = latch;
        }

        void assertValue(int val) {
            assertEquals(val, value);
        }

        @Override
        public void accept(int value) {
            try {
                latch.await();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            this.value = value;
        }

    }

    static final class IntSource {

        private final FunctionalLockImpl lock = new FunctionalLockImpl(true, "foo");
        private int val;

        IntSource(int initial) {
            this.val = initial;
        }

        void assertValue(int val) {
            assertEquals(val, get());
        }

        int get() {
            return lock.underReadLockInt(() -> {
                return val;
            });
        }

        public void get(IntConsumer cons) {
            lock.underReadLock(() -> {
                cons.accept(val);
            });
        }

        public void set(IntSupplier supp) {
            lock.underWriteLock(() -> {
                this.val = supp.getAsInt();
            });
        }

        public void set(int val) {
            lock.writeLock();
            try {
                this.val = val;
            } finally {
                lock.writeUnlock();
            }
        }
    }
}
