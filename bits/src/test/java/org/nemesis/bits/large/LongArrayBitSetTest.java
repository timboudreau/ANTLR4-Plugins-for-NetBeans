package org.nemesis.bits.large;

import org.nemesis.bits.large.MappedFileLongArray;
import org.nemesis.bits.large.JavaLongArray;
import org.nemesis.bits.large.UnsafeLongArray;
import org.nemesis.bits.large.LongArray;
import org.nemesis.bits.large.LongArrayBitSet;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Random;
import java.util.function.LongFunction;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(Parameterized.class)
public class LongArrayBitSetTest {

    private LongArrayBitSet odds;
    private BitSet oddBitSet;
    private LongArrayBitSet random;
    private BitSet randomBitSet;
    private static final int SIZE = 530;
    private Random rnd;
    private final LongFunction<LongArray> arrayFactory;

    @Parameters(name = "{index}:{0}")
    public static Collection<LongFunction<LongArray>> params() {
        return Arrays.asList(new UnsafeLong(), new MappedLong(), new JavaLong());
    }

    public LongArrayBitSetTest(LongFunction<LongArray> arrayFactory) {
        this.arrayFactory = arrayFactory;
    }

    @Before
    public void setup() {
        rnd = new Random(2398203973213L);
        odds = new LongArrayBitSet(SIZE, arrayFactory);
        assertTrue(odds.isEmpty());
        oddBitSet = new BitSet(SIZE);
        for (int i = 0; i < SIZE; i++) {
            if (i % 2 == 1) {
                odds.set(i);
                oddBitSet.set(i);
            } else {
                odds.clear(i);
                oddBitSet.clear(i);
            }
        }
        assertFalse(odds.isEmpty());
        random = new LongArrayBitSet(SIZE, arrayFactory);
        assertTrue(random.isEmpty());
        randomBitSet = new BitSet(SIZE);
        for (int i = 0; i < SIZE; i++) {
            if (rnd.nextBoolean()) {
                random.set(i);
                randomBitSet.set(i);
            }
        }
        assertFalse(random.isEmpty());
    }

    @Test
    public void testBasic() {
        assertMatch(randomBitSet, random);
        assertMatch(oddBitSet, odds);
        int i = randomBitSet.nextSetBit(0);
        long l = random.nextSetBit(0L);
        for (; l >= 0 && i >= 0; i = randomBitSet.nextSetBit(i + 1), l = random.nextSetBit(l + 1)) {
            int il = (int) l;
            assertEquals(i, il);
        }
    }

    @Test
    public void testOr() {
        odds.or(random);
        oddBitSet.or(randomBitSet);
        assertMatch(oddBitSet, odds);
    }

    @Test
    public void testXor() {
        odds.xor(random);
        oddBitSet.xor(randomBitSet);
        assertMatch(oddBitSet, odds);
    }

    @Test
    public void testAnd() {
        odds.and(random);
        oddBitSet.and(randomBitSet);
        assertMatch(oddBitSet, odds);
    }

    @Test
    public void testAndNot() {
        odds.andNot(random);
        oddBitSet.andNot(randomBitSet);
        assertMatch(oddBitSet, odds);
    }

    @Test
    public void testFlip() {
        for (long i = 0; i < odds.size(); i++) {
            if (rnd.nextBoolean()) {
                odds.flip(i);
                oddBitSet.flip((int) i);
                assertMatch(oddBitSet, odds);
            }
        }
    }

    @Test
    public void testClear() {
        for (long i = 0; i < odds.size(); i++) {
            if (rnd.nextBoolean()) {
                odds.clear(i);
                oddBitSet.clear((int) i);
                assertMatch(oddBitSet, odds);
            }
        }
    }

    @Test
    public void testClearMany() {
        for (long i = 0; i < odds.size(); i++) {
            if (rnd.nextBoolean()) {
                odds.clear(0, i);
                oddBitSet.clear(0, (int) i);
                assertMatch("Iter " + i, oddBitSet, odds);
            }
        }
    }

    @Test
    public void testSetMany() {
        for (long i = 0; i < odds.size(); i++) {
            if (rnd.nextBoolean()) {
                odds.set(0, i);
                oddBitSet.set(0, (int) i);
                assertMatch("Iter " + i, oddBitSet, odds);
            }
        }
    }

    @Test
    public void testFlipMany() {
        for (long i = 0; i < odds.size(); i++) {
            if (rnd.nextBoolean()) {
                odds.flip(0, i);
                oddBitSet.flip(0, (int) i);
                assertMatch(oddBitSet, odds);
            }
        }
    }

    @Test
    public void testSet() {
        for (long i = 0; i < odds.size(); i++) {
            if (rnd.nextBoolean()) {
                odds.set(i);
                oddBitSet.set((int) i);
                assertMatch(oddBitSet, odds);
                assertEquals(oddBitSet.cardinality(), (int) odds.cardinality());
                assertEquals(oddBitSet.length(), (int) odds.length());
            }
        }
    }

    static final class UnsafeLong implements LongFunction<LongArray> {

        @Override
        public LongArray apply(long value) {
            return new UnsafeLongArray(value);
        }

        public String toString() {
            return getClass().getSimpleName();
        }
    }

    static final class MappedLong implements LongFunction<LongArray> {

        @Override
        public LongArray apply(long value) {
            return new MappedFileLongArray(value);
        }

        public String toString() {
            return getClass().getSimpleName();
        }
    }

    static final class JavaLong implements LongFunction<LongArray> {

        @Override
        public LongArray apply(long value) {
            return new JavaLongArray(value);
        }

        public String toString() {
            return getClass().getSimpleName();
        }
    }

    private static void assertMatch(BitSet expected, LongArrayBitSet got) {
        assertMatch("", expected, got);
    }

    private static void assertMatch(String msg, BitSet expected, LongArrayBitSet got) {
        if (!expected.equals(got)) {
            BitSet expectedBits = expected;
            BitSet gotBits = got.toBitSet();
            if (!expectedBits.equals(gotBits)) {
                BitSet missing = (BitSet) expectedBits.clone();
                for (int bit = gotBits.nextSetBit(0); bit >= 0; bit = gotBits.nextSetBit(bit + 1)) {
                    missing.clear(bit);
                }
                BitSet extra = (BitSet) gotBits.clone();
                for (int bit = expectedBits.nextSetBit(0); bit >= 0; bit = expectedBits.nextSetBit(bit + 1)) {
                    extra.clear(bit);
                }
                fail((msg.isEmpty() ? msg : msg + ": ") + "Sets do not match - " + (missing.isEmpty() ? "" : "missing " + missing)
                        + (extra.isEmpty() ? ""
                        : " extra " + extra)
                        + "\nexpected:\n" + expectedBits + "\ngot:\n" + gotBits
                );
            }
        }
    }
}
