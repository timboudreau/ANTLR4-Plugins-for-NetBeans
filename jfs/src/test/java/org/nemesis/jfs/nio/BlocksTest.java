package org.nemesis.jfs.nio;

import java.util.Arrays;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import com.mastfrog.range.IntRange;

/**
 *
 * @author Tim Boudreau
 */
public class BlocksTest {

    private void assertOverlap(int start, int stop, Blocks a, Blocks b) {
        int[] vals = blocksOverlap(a, b);
        assertEquals("Unexpected start a in " + vals[0] + "," + vals[1], start, vals[0]);
        assertEquals("Unexpected stop a in " + vals[0] + "," + vals[1], stop, vals[1]);
        vals = blocksOverlap(b, a);
        assertEquals("Unexpected start b in " + vals[0] + "," + vals[1], start, vals[0]);
        assertEquals("Unexpected stop b in " + vals[0] + "," + vals[1], stop, vals[1]);
    }

    private void assertNonOverlap(int start, int stop, Blocks a, Blocks b) {
        int[] vals = a.nonOverlappingCoordinates(b);
        assertEquals("Unexpected start a in " + vals[0] + "," + vals[1], start, vals[0]);
        assertEquals("Unexpected stop a in " + vals[0] + "," + vals[1], stop, vals[1]);
        vals = b.nonOverlappingCoordinates(a);
        assertEquals("Unexpected start b in " + vals[0] + "," + vals[1], start, vals[0]);
        assertEquals("Unexpected stop b in " + vals[0] + "," + vals[1], stop, vals[1]);
        assertEquals(Arrays.toString(vals), 2, vals.length);
    }

    private void assertNonOverlap(int start, int stop, int start2, int stop2, Blocks a, Blocks b) {
        int[] vals = a.nonOverlappingCoordinates(b);
        assertEquals(Arrays.toString(vals), 4, vals.length);
        assertEquals("Unexpected start a in " + vals[0] + "," + vals[1] + "," + vals[2] + "," + vals[3], start, vals[0]);
        assertEquals("Unexpected stop a in " + vals[0] + "," + vals[1] + "," + vals[2] + "," + vals[3], stop, vals[1]);
        assertEquals("Unexpected start2 a in " + vals[0] + "," + vals[1] + "," + vals[2] + "," + vals[3], start2, vals[2]);
        assertEquals("Unexpected stop2 a in " + vals[0] + "," + vals[1] + "," + vals[2] + "," + vals[3], stop2, vals[3]);
        vals = b.nonOverlappingCoordinates(a);
        assertEquals("Unexpected start b in " + vals[0] + "," + vals[1] + "," + vals[2] + "," + vals[3], start, vals[0]);
        assertEquals("Unexpected stop b in " + vals[0] + "," + vals[1] + "," + vals[2] + "," + vals[3], stop, vals[1]);
        assertEquals("Unexpected start2 b in " + vals[0] + "," + vals[1] + "," + vals[2] + "," + vals[3], start2, vals[2]);
        assertEquals("Unexpected stop2 b in " + vals[0] + "," + vals[1] + "," + vals[2] + "," + vals[3], stop2, vals[3]);
    }

    private int[] blocksOverlap(Blocks a, Blocks b) {
        Blocks overlap = a.overlapWith(b);
        if (overlap.isEmpty()) {
            return new int[0];
        }
        int[] result = new int[] { overlap.start(), overlap.stop() };
        return result;
    }

    @Test
    public void testSpecificOverlap() {
        // Mangled overlap [136:146 (11)] and [125:135 (11)] gets [136, 135]
        Blocks a = new Blocks(136, 11);
        Blocks b = new Blocks(125, 11);
        int[] ao = blocksOverlap(a, b);
        int[] bo = blocksOverlap(a, b);
        assertArrayEquals(Arrays.toString(ao), new int[0], ao);
        assertArrayEquals(Arrays.toString(bo), new int[0], bo);
    }

    @Test
    public void testGetOverlap() {
        Blocks a = new Blocks(10, 90);
        Blocks b = new Blocks(0, 20);
        assertOverlap(10, 19, a, b);
        assertNonOverlap(0, 9, 20, 99, a, b);

        b = new Blocks(10, 20);
        assertOverlap(10, 29, a, b);
        assertNonOverlap(30, 99, a, b);

        b = new Blocks(90, 110);
        assertOverlap(90, 99, a, b);
        assertNonOverlap(10, 89, 100, 199, a, b);

        b = new Blocks(10, 90);
        assertOverlap(10, 99, a, b);
        assertArrayEquals(new int[0], a.nonOverlappingCoordinates(b));
        assertArrayEquals(new int[0], b.nonOverlappingCoordinates(a));

        b = new Blocks(20, 30);
        assertOverlap(20, 49, a, b);
        assertNonOverlap(10, 19, 50, 99, a, b);

        b = new Blocks(1, 3);
        assertArrayEquals(new int[0], blocksOverlap(a,b));
        assertArrayEquals(new int[0], blocksOverlap(b,a));
        assertArrayEquals(new int[0], a.nonOverlappingCoordinates(b));
        assertArrayEquals(new int[0], b.nonOverlappingCoordinates(a));
    }

    @Test
    public void testOverlaps() {
        Blocks b = new Blocks(0, 9);
        assertTrue(b.overlaps(com.mastfrog.range.Range.ofCoordinates(0, 8)));
        assertTrue(b.overlaps(com.mastfrog.range.Range.ofCoordinates(2, 2)));
        assertTrue(b.overlaps(com.mastfrog.range.Range.ofCoordinates(2, 3)));
        assertTrue(b.overlaps(com.mastfrog.range.Range.ofCoordinates(0, 10)));
        assertTrue(b.overlaps(com.mastfrog.range.Range.ofCoordinates(0, 9)));
        assertTrue(b.overlaps(com.mastfrog.range.Range.ofCoordinates(8, 11)));
        assertFalse(b.overlaps(com.mastfrog.range.Range.ofCoordinates(10, 11)));
        assertFalse(b.overlaps(com.mastfrog.range.Range.ofCoordinates(10, 12)));

        // migrate 58:1 to 12507:14 in [12506:12510 (5)]
        b = new Blocks(12506, 5);
        assertFalse(b.overlaps(new Blocks(58, 1)));
    }

    @Test
    public void testMapping() {
        BlockToBytesConverter m = new BlockToBytesConverter(10);
        Blocks b = new Blocks(1, 3);
        assertFalse(b.contains(0));
        assertTrue(b.contains(1));
        assertTrue(b.contains(2));
        assertTrue(b.contains(3));
        assertFalse(b.contains(4));

        assertEquals(1, b.start());
        assertEquals(3, b.stop());
        assertEquals(4, b.end());

        IntRange<?> r = b.toPhysicalRange(m);
        assertEquals(10, r.start());
        assertEquals(30, r.stop());
        assertEquals(40, r.end());
    }

    @Test
    public void testSimpleMigration() {
        Blocks b = new Blocks(15, 20);
        assertEquals(15, b.start());
        assertEquals(35, b.end());
        assertEquals(34, b.stop());
        assertTrue(b.maybeMigrate(15, 20, 30, 20));
        assertEquals(30, b.start());
        assertEquals(50, b.end());
        b = new Blocks(15, 20);
        assertTrue(b.maybeMigrate(15, 20, 30, 100));
        assertEquals(30, b.start());
        assertEquals(130, b.end());
    }

    @Test
    public void testOverlapMigration() {
        Blocks b = new Blocks(15, 20);
        assertEquals(15, b.start());
        assertEquals(35, b.end());
        assertEquals(34, b.stop());
        assertTrue(b.maybeMigrate(0, 100, 1, 100));
        assertEquals(16, b.start());
        assertEquals(36, b.end());
        assertEquals(35, b.stop());

        assertTrue(b.maybeMigrate(1, 100, 0, 100));
        assertEquals(15, b.start());
        assertEquals(35, b.end());
        assertEquals(34, b.stop());

        // [8920:8935 (16)]{52319} from 8919:17 to 5759:17 gets 14679:17
        b = new Blocks(8920, 16);
        assertTrue(b.maybeMigrate(8919, 17, 5759, 17));
        assertEquals(5760, b.start());
        assertEquals(16, b.size());

        assertTrue(b.maybeMigrate(5759, 17, 8919, 17));
        assertEquals(8920, b.start());
        assertEquals(16, b.size());
        assertTrue(new Blocks(8920, 16).matches(b));

        assertFalse(b.maybeMigrate(1, 20, 30, 20));
        assertFalse(b.maybeMigrate(1, 20, 30, 23));
        assertTrue(new Blocks(8920, 16).matches(b));

        assertTrue(b.maybeMigrate(8919, 100, 5759, 100));
        assertEquals(5760, b.start());
        assertEquals(16, b.size());
        assertTrue(new Blocks(5760, 16).matches(b));
        assertFalse(new Blocks(5761, 16).matches(b));
        assertFalse(new Blocks(5760, 17).matches(b));

        b = new Blocks(0, 2);
        assertFalse(b.maybeMigrate(1, 666, 0, 666));
        assertEquals(0, b.start());
        assertEquals(2, b.size());

        b = new Blocks(712, 114);
        assertTrue(b.maybeMigrate(712, 114, 667, 114));
        assertEquals(114, b.size());
        b = new Blocks(712, 114);

        assertTrue(b.maybeMigrate(712, 114, 667, 780));
        assertEquals(780, b.size());
    }

    @Test
    public void testOps() {
        Ops ops = new Ops(7);
        for (int i = 0; i < 12; i++) {
            ops.set("Item {0}", i);
        }
        String[] s = ops.toString().split("\n");
        assertEquals(7, s.length);
        for (int i = 0; i < s.length; i++) {
            s[i] = s[i].trim();
            assertTrue(s[i].endsWith("Item " + (i + 5)));
        }
    }
}
