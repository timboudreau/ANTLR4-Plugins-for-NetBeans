package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.nio;

import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class BlockManagerTest {

    private static final int AMT = 25;
    private L l = new L();
    private final BlockManager man = new BlockManager(AMT, l);

    {
        man.defrag = false;
    }

    private void assertRegion(int start, int end, boolean allocated) throws IOException {
        boolean[] found = new boolean[1];
        man.regions(allocated, false, (a, b) -> {
            if (a == start && b == end) {
                found[0] = true;
                return false;
            }
            return true;
        });
        assertTrue("Did not find a region " + start + ":" + end + " in " + man, found[0]);
    }

    @Test
    public void testGrowAndShrink() throws Exception {
        assertEquals(0, man.allocate(1));
        assertEquals(1, man.allocate(2));
        assertEquals(2, man.lastUsedBlock());
        assertRegion(0, 2, true);
        man.grow(1, 2, 5, false);
        l.assertResized(1, 2, 5);
        assertEquals(man.toString(), 5, man.lastUsedBlock());
        assertRegion(0, 5, true);

        man.shrink(1, 5, 3);
        l.assertDeallocated(4, 2);
        assertTrue(man.regions(true, false, new RegionExpecter(0, 3)));
        l.assertResized(1, 5, 3);

        assertEquals(4, man.allocate(7));

        assertEquals(11, man.grow(1, 3, 5, true));
        l.assertMigrated(1, 3, 11);
        l.assertDeallocated(1, 3);
        assertTrue(man.regions(true, false, new RegionExpecter(0, 0, 4, 15)));
    }

    @Test
    public void testSpecifics() throws Exception {
        BlockManager man = new BlockManager(2, l);
        assertEquals(0, man.allocate(11));
        assertEquals(11, man.allocate(2));
        assertEquals(13, man.allocate(2));
        assertEquals(15, man.allocate(2));
        assertEquals(17, man.allocate(2));
    }

    @Test
    public void testAllocPos() {
        for (int i = 1; i <= AMT; i++) {
            assertEquals(i + "", 0, man.findContigiuousUnallocated(i));
        }
        for (int i = AMT + 1; i < AMT * 2 + 1; i++) {
            assertEquals(i + "", -1, man.findContigiuousUnallocated(i));
        }
    }

    @Test
    public void testExpansion() throws Exception {
        man.allocate(AMT + 10);
        man.allocate(1);
        man.allocate(AMT + 10);
        man.deallocate(AMT + 10, 1);
        System.out.println("MAN: " + man);
        assertEquals((AMT + 10) * 2, man.usedBlocks());
        int[] last = new int[]{-1, -1};
        man.regions(0, true, false, (a, b) -> {
            if (last[0] == -1) {
                last[0] = a;
                last[1] = b;
                assertEquals(0, a);
                assertEquals(34, b);
            } else {
                assertEquals(36, a);
                assertEquals(70, b);
            }
            return true;
        });
    }

    @Test
    public void testFragmentation() throws Exception {
        man.defrag = true;
        for (int i = 0; i < AMT; i++) {
            if (i % 2 == 0) {
                man.set(i);
            }
        }

        assertEquals(12, man.fragmentedBlocks());
        assertEquals(0.48F, man.fragmentation(), 0.04f);
        man.simpleDefrag();
        assertEquals(0F, man.fragmentation(), 0.01f);
    }

    @Test
    public void testFullDefrag() throws Exception {
        for (int i = 0; i < AMT; i++) {
            if (i % 2 == 0) {
                man.set(i);
            }
        }
        System.out.println("BEFORE: " + man);
        man.fullDefrag();
        System.out.println("AFTER: " + man);
    }

    @Test
    public void testRangeCornerCases() throws Exception {
        man.defrag = false;
        // an empty manager should never list regions
        assertFalse(man.regions(true, false, (a, b) -> {
            fail("Should not be called: " + a + " -> " + b);
            return true;
        }));
        assertFalse(man.regions(true, true, (a, b) -> {
            fail("Should not be called: " + a + " -> " + b);
            return true;
        }));

        assertTrue(man.regions(false, true, new RegionExpecter(0, AMT - 1)));
        assertTrue(man.regions(false, false, new RegionExpecter(0, AMT - 1)));

        assertEquals(0, man.allocate(AMT));
        assertTrue(man.regions(true, false, new RegionExpecter(0, 24)));

        assertEquals(AMT, man.usedBlocks());
        assertEquals(0, man.availableBlocks());
        l.assertNotExpanded();

        man.deallocate(0, 1);
        assertEquals(AMT - 1, man.usedBlocks());
        assertEquals(1, man.availableBlocks());
        assertTrue(man.regions(true, false, new RegionExpecter(1, 24)));
        System.out.println("\n\n--------------------\n\n");
        System.out.println("NOW " + man);
        assertTrue("Not called for leading deallocated region: " + man.toString(), man.regions(false, false, new RegionExpecter(0, 0)));
        assertTrue(man.toString(), man.regions(false, true, new RegionExpecter(0, 0)));
        assertTrue(man.toString(), man.regions(true, true, new RegionExpecter(1, 24)));

        man.deallocate(AMT - 1, 1);
        assertEquals(AMT - 2, man.usedBlocks());
        assertEquals(2, man.availableBlocks());
        System.out.println("\nAND NOW: " + man + "\n");
        assertTrue(man.regions(true, false, new RegionExpecter(1, 23)));
        assertTrue(man.regions(false, true, new RegionExpecter(24, 24, 0, 0)));
        assertTrue(man.regions(false, false, new RegionExpecter(0, 0, 24, 24)));
        assertTrue(man.regions(true, true, new RegionExpecter(1, 23)));

        man.deallocate(1, 1);
        assertEquals(man.toString(), AMT - 3, man.usedBlocks());
        assertEquals(man.toString(), 3, man.availableBlocks());
        assertTrue(man.toString(), man.regions(true, false, new RegionExpecter(2, 23)));
        assertTrue(man.toString(), man.regions(false, true, new RegionExpecter(24, 24, 0, 1)));
        assertTrue(man.toString(), man.regions(false, false, new RegionExpecter(0, 1, 24, 24)));
        assertTrue(man.toString(), man.regions(true, true, new RegionExpecter(2, 23)));

        System.out.println("MAN " + man);
        assertEquals(1, man.allocate(1));
        assertTrue(man.toString(), man.regions(true, false, new RegionExpecter(1, 23)));
        assertTrue(man.toString(), man.regions(false, true, new RegionExpecter(24, 24, 0, 0)));
        assertTrue(man.toString(), man.regions(false, false, new RegionExpecter(0, 0, 24, 24)));
        assertTrue(man.toString(), man.regions(true, true, new RegionExpecter(1, 23)));
//        assertEquals(0, man.allocate(1));
//        assertTrue(man.toString(), man.regions(true, false, new RegionExpecter(0, 0, 2, 23)));
//        assertTrue(man.toString(), man.regions(false, true, new RegionExpecter(24, 24, 1, 1)));
//        assertTrue(man.toString(), man.regions(false, false, new RegionExpecter(1, 1, 24, 24)));
//        assertTrue(man.toString(), man.regions(true, true, new RegionExpecter(2, 23, 0, 0)));

        man.deallocate(AMT - 2, 1);
        assertEquals(man.toString(), AMT - 3, man.usedBlocks());
        assertEquals(man.toString(), 3, man.availableBlocks());
        assertTrue(man.toString(), man.regions(true, false, new RegionExpecter(1, 22)));
        assertTrue(man.toString(), man.regions(false, false, new RegionExpecter(0, 0, 23, 24)));
        assertTrue(man.toString(), man.regions(false, true, new RegionExpecter(23, 24, 0, 0)));
        assertTrue(man.toString(), man.regions(true, true, new RegionExpecter(1, 22)));
//        assertTrue(man.toString(), man.regions(true, false, new RegionExpecter(0, 0, 2, 22)));
//        assertTrue(man.toString(), man.regions(false, false, new RegionExpecter(1, 1, 23, 24)));
//        assertTrue(man.toString(), man.regions(false, true, new RegionExpecter(23, 24, 1, 1)));
//        assertTrue(man.toString(), man.regions(true, true, new RegionExpecter(2, 22, 0, 0)));

        man.set(AMT - 1);
        assertEquals(man.toString(), AMT - 2, man.usedBlocks());
        assertEquals(man.toString(), 2, man.availableBlocks());
        assertTrue(man.toString(), man.regions(true, false, new RegionExpecter(1, 22, 24, 24)));
        assertTrue(man.toString(), man.regions(true, true, new RegionExpecter(24, 24, 1, 22)));
        assertTrue(man.toString(), man.regions(false, true, new RegionExpecter(23, 23, 0, 0)));
        assertTrue(man.toString(), man.regions(false, false, new RegionExpecter(0, 0, 23, 23)));
//        assertTrue(man.toString(), man.regions(true, false, new RegionExpecter(0, 0, 2, 22, 24, 24)));
//        assertTrue(man.toString(), man.regions(true, true, new RegionExpecter(24, 24, 2, 22, 0, 0)));
//        assertTrue(man.toString(), man.regions(false, true, new RegionExpecter(23, 23, 1, 1)));
//        assertTrue(man.toString(), man.regions(false, false, new RegionExpecter(1, 1, 23, 23)));

        man.clear();

        assertEquals(man.toString(), 0, man.usedBlocks());
        man.set(0);
        man.set(AMT - 1);
        assertEquals(2, man.usedBlocks());
        assertTrue(man.toString(), man.regions(true, false, new RegionExpecter(0, 0, 24, 24)));
    }

    @Test
    public void testSimpleAllocation() throws IOException {
        assertEquals(0, man.usedBlocks());
        assertEquals(AMT, man.availableBlocks());

        int firstBatch = man.allocate(5);
        assertEquals(0, firstBatch);
        l.assertAllocated(0, 5);
        l.assertNotExpanded();
        l.assertNotDeallocated();
        assertEquals(5, man.usedBlocks());
        assertEquals(AMT - 5, man.availableBlocks());

        int secondBatch = man.allocate(5);
        assertEquals(5, secondBatch);
        l.assertAllocated(5, 5);
        l.assertNotExpanded();
        l.assertNotDeallocated();
        assertEquals(10, man.usedBlocks());
        assertEquals(AMT - 10, man.availableBlocks());

        int thirdBatch = man.allocate(5);
        assertEquals(10, thirdBatch);
        l.assertAllocated(10, 5);
        l.assertNotExpanded();
        l.assertNotDeallocated();
        assertEquals(15, man.usedBlocks());
        assertEquals(AMT - 15, man.availableBlocks());

        int fourthBatch = man.allocate(5);
        assertEquals(15, fourthBatch);
        l.assertAllocated(15, 5);
        l.assertNotExpanded();
        l.assertNotDeallocated();
        assertEquals(20, man.usedBlocks());
        assertEquals(AMT - 20, man.availableBlocks());

        assertTrue(man.regions(true, false, new RegionExpecter(0, 19)));
        assertTrue(man.regions(true, true, new RegionExpecter(0, 19)));

        assertTrue(man.regions(false, false, new RegionExpecter(20, 24)));
        assertTrue(man.regions(false, true, new RegionExpecter(20, 24)));

        man.deallocate(5, 5);
        l.assertNotAllocated();
        l.assertNotExpanded();
        l.assertDeallocated(5, 5);
        assertEquals(15, man.usedBlocks());
        assertEquals(AMT - 15, man.availableBlocks());

        System.out.println("REGIONS: " + man);
        man.regions(true, false, new RegionExpecter(0, 4, 10, 19));
        man.regions(true, true, new RegionExpecter(10, 19, 0, 4));
        man.regions(false, false, new RegionExpecter(5, 9, 20, 24));
        man.regions(false, true, new RegionExpecter(20, 24, 5, 9));

        int secondBatch2 = man.allocate(5);
        assertEquals(5, secondBatch2);
        l.assertAllocated(5, 5);
        l.assertNotExpanded();
        l.assertNotDeallocated();
        assertEquals(20, man.usedBlocks());
        assertEquals(AMT - 20, man.availableBlocks());

        int fifthBatch = man.allocate(5);
        assertEquals(20, fifthBatch);
        l.assertAllocated(20, 5);
        l.assertNotExpanded();
        l.assertNotDeallocated();
        assertEquals(25, man.usedBlocks());
        assertEquals(AMT - 25, man.availableBlocks());

        int sixthBatch = man.allocate(5);
        assertEquals(25, sixthBatch);
        l.assertAllocated(25, 5);
        l.assertExpanded(AMT, AMT * 2);
        l.assertNotDeallocated();
        assertEquals(30, man.usedBlocks());
        assertEquals(20, man.availableBlocks());
    }

    private static final class RegionExpecter implements BlockManager.RegionReceiver {

        private final int[] vals;
        private int cursor = 0;

        RegionExpecter(int... vals) {
            this.vals = vals;
            assert vals.length % 2 == 0 : Arrays.toString(vals) + " not even";
        }

        @Override
        public boolean receive(int start, int end) {
            if (cursor >= vals.length - 1) {
                fail("Unexpected additional call: " + start + ", " + end);
            }
            assertEquals("Unexpected start in " + start + "," + end + " with end " + end + " where expecting " + vals[cursor] + ", " + vals[cursor + 1], vals[cursor], start);
            assertEquals("Unexpected end in " + start + "," + end + " with start " + start + " where expecting " + vals[cursor] + ", " + vals[cursor + 1], vals[cursor + 1], end);
            assertEquals(vals[cursor + 1], end);
            cursor += 2;
            return true;
        }
    }

    private class L implements BlockManager.Listener {

        private int[] lastAllocated, lastDeallocated, lastExpansion, lastResize = null;
        private int[] lastMigrated;

        private String msg(String s, int[] val) {
            if (val != null) {
                return s + ": " + val[0] + ", " + val[1];
            }
            return s;
        }

        public void assertAllocated(int start, int length) {
            int[] last = this.lastAllocated;
            this.lastAllocated = null;
            assertNotNull("No allocation", last);
            assertEquals(msg("Unexpected allocation start", last), start, last[0]);
            assertEquals(msg("Unexpected allocation length", last), length, last[1]);
        }

        public void assertExpanded(int oldBlockCount, int newBlockCount) {
            int[] last = this.lastExpansion;
            this.lastExpansion = null;
            assertNotNull("No allocation", last);
            assertEquals(msg("Unexpected old block count", last), oldBlockCount, last[0]);
            assertEquals(msg("Unexpected new block count", last), newBlockCount, last[1]);
        }

        public void assertDeallocated(int start, int length) {
            int[] last = this.lastDeallocated;
            this.lastDeallocated = null;
            assertNotNull("No deallocation", last);
            assertEquals(msg("Unexpected deallocation start", last), start, last[0]);
            assertEquals(msg("Unexpected deallocation length", last), length, last[1]);
        }

        public void assertNotAllocated() {
            assertNull(msg("Allocation occurred", lastAllocated), lastAllocated);
        }

        public void assertNotExpanded() {
            assertNull(msg("Expansion occurred", lastExpansion), lastExpansion);
        }

        public void assertNotDeallocated() {
            assertNull(msg("Deallocation occurred", lastDeallocated), lastDeallocated);
        }

        @Override
        public void onBeforeExpand(int oldBlockCount, int minimumBlocks, int lastUsedBlock) throws IOException {
            System.out.println("EXPAND " + oldBlockCount + " -> " + minimumBlocks + " last used " + lastUsedBlock);
            lastExpansion = new int[]{oldBlockCount, minimumBlocks};
        }

        @Override
        public void onDeallocate(int start, int blocks) throws IOException {
            lastDeallocated = new int[]{start, blocks};
        }

        @Override
        public void onAllocate(int start, int blocks) throws IOException {
            lastAllocated = new int[]{start, blocks};
        }

        void assertNotResized() {
            int[] x = lastResize;
            lastResize = null;
            assertNull(x);
        }

        void assertResized(int start, int oldSize, int newSize) {
            int[] x = lastResize;
            lastResize = null;
            assertNotNull("Not resized", x);
            String s = Arrays.toString(x);
            assertEquals("Unexpected resize start in " + s, start, x[0]);
            assertEquals("Unexpected resize oldSize in " + s, oldSize, x[1]);
            assertEquals("Unexpected resize newSize in " + s, newSize, x[2]);
        }

        void assertNotMigrated() {
            int[] x = lastMigrated;
            lastMigrated = null;
            assertNull(x);
        }

        void assertMigrated(int start, int count, int dest) {
            int[] x = lastMigrated;
            lastMigrated = null;
            assertNotNull("Not migrated", x);
            String s = Arrays.toString(x);
            assertEquals("Unexpected resize start in " + s, start, x[0]);
            assertEquals("Unexpected migrate block count in " + s, count, x[1]);
            assertEquals("Unexpected migrate destination in " + s, dest, x[2]);
        }

        @Override
        public void onResized(int start, int oldSize, int newSize) throws IOException {
            lastResize = new int[]{start, oldSize, newSize};
        }

        @Override
        public void onMigrate(int firstBlock, int blockCount, int dest, int newBlockCount) throws IOException {
            lastMigrated = new int[]{firstBlock, blockCount, dest};
        }
    }
}
