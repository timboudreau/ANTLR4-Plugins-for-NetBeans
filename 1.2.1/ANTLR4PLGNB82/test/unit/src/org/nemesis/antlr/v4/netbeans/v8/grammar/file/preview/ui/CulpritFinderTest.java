package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui.CulpritFinder.Cursors;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui.CulpritFinder.Cursors3;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui.CulpritFinder.Cursors4;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui.CulpritFinder.Interstices;

/**
 *
 * @author Tim Boudreau
 */
public class CulpritFinderTest {

//    @Test
    public void testBitShiftArray() {

        int sz = 72;

        BitShiftArray b = new BitShiftArray(sz);
        boolean[] called = new boolean[1];
        for (int i = 0; i < 96; i++) {
//            System.out.println(b);
            int ix = i;

            b.visitBits(v -> {
                assertEquals(ix % sz, v);
                called[0] = true;
            });
            assertTrue(i + ":" + b.toString(), called[0]);
            called[0] = false;
            b.shiftLeft();
            assertEquals(1, b.cardinality());
        }

        System.out.println("\n");

        b = new BitShiftArray(sz);

        b.set(0);
        b.set(2);
        b.set(3);
        b.set(5);
        b.set(6);
        for (int i = 0; i < 96; i++) {
//            System.out.println(b);
            b.boundedShiftLeft(5);
        }

        System.out.println("\n");
        b = new BitShiftArray(sz);
        for (int i = 0; i < 96; i++) {
//            System.out.println(b);
            b.setLeftMostUnsetBit();
            int[] gotten = new int[1];
            b.visitBits(v -> {
                assertEquals(gotten[0]++, v);
                return gotten[0] < sz;
            });
            int[] gotten2 = new int[]{i};
            b.visitBitsReverseOrder(v -> {
                assertEquals(gotten2[0]--, v);
            });
            for (int j = 0; j <= Math.min(sz, i); j++) {
                assertTrue(j + "", b.isSet(j));
            }
        }

        b = new BitShiftArray(sz);
        b.set(0);
        b.set(1);
        b.set(3);
        b.set(8);
        b.set(9);
        b.set(17);
        b.set(18);
        b.set(19);
        b.set(22);
        b.set(23);
        b.set(66);
        int lastAfter = 5;
        for (int i = 0; i < 71; i++) {
//            System.out.println(b);
            lastAfter = b.shiftSetBit(lastAfter);
            if (lastAfter == 71) {
                lastAfter = 0;
            }
//            b.moveOneBit();
        }

        b = new BitShiftArray(72);
        b.setRightMostUnsetBit();
        assertTrue(b.toString(), b.isSet(71));

        b = new BitShiftArray(72);
        b.set(69);
        b.set(67);
        b.set(66);
        b.set(20);
        b.set(13);

        b.set(0);
        b.set(33);
        b.set(70);;
        b.set(41);
        b.set(42);
        b.set(9);
        b.set(10);
        b.set(3);
        b.set(2);
        int card = b.cardinality();
        for (int i = 0; i < 96; i++) {
//            System.out.println(b);
            b.shiftRight();
            assertEquals(card, b.cardinality());
        }

        b.set(3);
        b.set(5);
        int leftBit = -1, rightBit = -1;
        for (int i = 0; i < 96; i++) {
            int fsb = b.firstSetBit(leftBit);
            int lsb = b.lastSetBit(rightBit);
            System.out.println(b + "  " + "\t" + leftBit + "\t" + rightBit + "\t" + fsb + "\t" + lsb);
            if (leftBit == -1) {
                leftBit = b.rotateFirstBitLeft();
            } else {
                BitShiftArray copy = b.copy();
                int oldLeftBit = leftBit;
                leftBit = b.rotateBitLeft(leftBit);
                assertNotEquals("rotateBitLeft of " + oldLeftBit + " on " + copy + " got -1", -1, leftBit);
            }
            if (rightBit == -1) {
                rightBit = b.rotateLastBitRight();
            } else {
                rightBit = b.rotateBitRight(rightBit);
            }
        }
    }

    private static String duplicateMessage(Set<BSA> copies, BSA nue, Cursors3 cur) {
        if (copies.contains(nue)) {
            for (BSA bsa : copies) {
                if (bsa.equals(nue)) {
                    return "duplicate of " + bsa.index + " " + cur;
                }
            }
        }
        return "";
    }

    @Test
    public void testVisitBits() {
        BitShiftArray b = new BitShiftArray(248);
        boolean[] called = new boolean[1];
        for (int i = 0; i < 248; i++) {
            if (i > 0) {
                assertTrue(b.isSet(i-1));
                b.clear(i - 1);
                assertFalse(b.isSet(i-1));
            }
            int ix = i;
            b.set(i);
            assertTrue(b.isSet(i));
            b.visitBits(bit -> {
                assertEquals(ix, bit);
                called[0] = true;
            });
            assertTrue(i + "", called[0]);
            called[0] = false;
            b.visitBitsReverseOrder(bit -> {
                assertEquals(ix, bit);
                called[0] = true;
            });
            assertTrue(i + "", called[0]);
        }
    }

//    @Test
    public void testSmallSL() {
        BitShiftArray b = new BitShiftArray(8);
        for (int i = 0; i < 18; i++) {
//            System.out.println(b);
            b.shiftLeft();
            assertEquals(1, b.cardinality());
        }
    }

//    @Test
    public void testInterstices() {
        Interstices in = new Interstices(8);
        for (int i = 0; i < 2000; i++) {
            int[] ints = in.nextInterstice();
            if (ints == null) {
                break;
            }
            System.out.println(Arrays.toString(ints));
        }

        System.out.println("\n------------\n");

        Cursors4 cur = new Cursors4(8);
        DecimalFormat fmt = new DecimalFormat("0000000000");
        Set<Byte> expected = new TreeSet<>();
        for (byte i = Byte.MIN_VALUE; i < Byte.MAX_VALUE; i++) {
            if (i != 0) {
                expected.add(i);
            }
        }
        for (int i = 0; i < 256; i++) {
            BitShiftArray b = cur.next();
            if (b == null) {
                break;
            }
            byte bt = 0;
            bt |= b.firstLong();
            expected.remove(bt);
            System.out.println(fmt.format(i) + ": " + b + " - " + bt);
        }
        if (expected.size() > 0) {
            for (Byte b : expected) {
                long l = (long) b & (long) -1;

                System.out.println("MISS: " + BitShiftArray.toBinaryString(l, 8) + " - " + b);
            }
        }
    }

//    @Test(timeout = 1000 * 60 * 60 * 60)
    public void testCursors2() {
        int sz = 16;
        Cursors3 c2 = new Cursors3(sz);
        Set<BSA> copies = new HashSet<>();
        int bim = 1;
        DecimalFormat fmt = new DecimalFormat("0000000000");
        for (int i = 0; i < (sz * sz * sz * sz) + 20; i++) {
            BitShiftArray a = c2.next();
            if (a == null) {
                break;
            }
            int newBim = c2.bitsInMotion();
            if (newBim != bim) {
                bim = newBim;
                copies.clear();
            }
            BSA bsa = new BSA(a.copy(), i);
            System.out.println(fmt.format(i) + ": " + a + " " + duplicateMessage(copies, bsa, c2));
//            if (copies.contains(bsa)) {
//                for (BSA b : copies) {
//                    if (bsa.equals(b)) {
//                        fail("Encountered pattern twice, at " + b.index + " and " + i + ": " + a);
//                    }
//                }
//            }
            copies.add(bsa);
        }
    }

    private static final class BSA {

        private final BitShiftArray arr;
        private final int index;

        public BSA(BitShiftArray arr, int index) {
            this.arr = arr;
            this.index = index;
        }

        @Override
        public int hashCode() {
            return arr.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final BSA other = (BSA) obj;
            if (!arr.equals(other.arr)) {
                return false;
            }
            return true;
        }

    }

//    @Test
    public void testSomeMethod() {
        int max = 25;
        Cursors cursors = new Cursors(max);
        long count = (long) Math.pow(max, max);
        for (long i = 0; i < count; i++) {
            BitSet set = cursors.next();
            if (set == null) {
                System.out.println("DONE AT " + i);
                break;
            }
            System.out.println(i + ":\t\t" + tos(set, max));
        }
    }

    static String tos(BitSet set, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            if (set.get(i)) {
                sb.append('-');
            } else {
                sb.append('0');
            }
        }
        return sb.toString();
    }

}
