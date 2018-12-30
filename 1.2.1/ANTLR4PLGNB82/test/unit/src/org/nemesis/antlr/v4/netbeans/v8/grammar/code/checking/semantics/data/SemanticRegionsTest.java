package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.impl.ArrayUtil;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.SemanticRegions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.SemanticRegions.SemanticRegion;

/**
 *
 * @author Tim Boudreau
 */
public class SemanticRegionsTest {

    @Test
    public void testLastOffsetFinding() {
        int[] vals = new int[]{0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60};
        int off = ArrayUtil.lastOffsetLessThanOrEqualTo(0, vals, vals.length);
        assertEquals(0, off);

        off = ArrayUtil.lastOffsetLessThanOrEqualTo(6, vals, vals.length);
        assertEquals(1, off);
        off = ArrayUtil.lastOffsetLessThanOrEqualTo(7, vals, vals.length);
        assertEquals(1, off);
        off = ArrayUtil.lastOffsetLessThanOrEqualTo(8, vals, vals.length);
        assertEquals(1, off);
        off = ArrayUtil.lastOffsetLessThanOrEqualTo(9, vals, vals.length);
        assertEquals(1, off);
        off = ArrayUtil.lastOffsetLessThanOrEqualTo(10, vals, vals.length);
        assertEquals(2, off);

        for (int i = 0; i < 65; i++) {
            int expect = i / 5;
            int o = ArrayUtil.lastOffsetLessThanOrEqualTo(i, vals, vals.length);
            assertEquals(i + " got " + o, expect, o);
        }
    }

    @Test
    public void testFirstOffsetFinding() {
        int[] vals = new int[]{0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60};
        int off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(0, vals, vals.length);
        assertEquals(0, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(30, vals, vals.length);
        assertEquals(6, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(60, vals, vals.length);
        assertEquals(vals.length - 1, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(61, vals, vals.length);
        assertEquals(-1, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(31, vals, vals.length);
        assertEquals(7, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(41, vals, vals.length);
        assertEquals(9, off);

        vals = new int[]{0, 5, 10, 15, 20, 20, 20, 25, 30, 30, 35, 35, 40, 45, 50, 50, 50, 55, 60};

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(0, vals, vals.length);
        assertEquals(0, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(30, vals, vals.length);
        assertEquals(8, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(60, vals, vals.length);
        assertEquals(vals.length - 1, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(61, vals, vals.length);
        assertEquals(-1, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(31, vals, vals.length);
        assertEquals(10, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(41, vals, vals.length);
        assertEquals(13, off);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced1() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 10, 21);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced2() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 8, 9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced3() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 8, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced4() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 10, 20);
        reg.add("b", 10, 15);
        reg.add("b", 16, 21);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced5() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 10, 20);
        reg.add("b", 10, 15);
        reg.add("b", 15, 20);
        reg.add("b", 15, 21);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced6() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 10, 20);
        reg.add("b", 10, 15);
        reg.add("b", 15, 21);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced7() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 20, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced8() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 20, 20);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced9() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", -3, -2);
    }

    @Test
    public void testDuplicateNesting() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 0, 10);
        reg.add("b", 20, 30);
        reg.add("c", 20, 30);
        reg.add("d", 30, 40);
        reg.add("e", 30, 40);
        reg.add("f", 32, 38);
        reg.add("g", 33, 37);
        reg.add("h", 60, 80);
        SemanticRegion<String> h = reg.at(62);
        assertEquals("h", h.key());
        SemanticRegion<String> g = reg.at(34);
        SemanticRegion<String> f = reg.at(32);
        SemanticRegion<String> e = reg.at(31);
        SemanticRegion<String> e1 = reg.at(30);
        SemanticRegion<String> c = reg.at(25);
        SemanticRegion<String> c1 = reg.at(20);
        SemanticRegion<String> c2 = reg.at(29);
        assertEquals("g", g.key());
        assertEquals("f", f.key());
        assertEquals("e", e.key());
        assertEquals("c", c.key());
        assertEquals(e, e1);
        assertEquals(c, c1);
        assertEquals(c, c2);
        assertEquals("d", e.parent().key());
        assertEquals("b", c.parent().key());

        assertEquals(Arrays.asList("a", "b", "d", "h"), toList(reg.outermostKeys()));
    }

    @Test
    public void testLastElementHasNesting() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 30, 40);
        reg.add("c", 50, 60);
        reg.add("d", 52, 58);
        reg.add("e", 54, 56);
        reg.add("f", 55, 56);
        assertEquals("f", reg.at(55).key());
        assertEquals("a", reg.at(10).key());
        assertEquals("c", reg.at(50).key());
        assertEquals("d", reg.at(53).key());
        assertEquals("e", reg.at(54).key());
        assertEquals("d", reg.at(56).key());
        assertEquals(6, reg.size());
        assertEquals(Arrays.asList("a", "b", "c"), toList(reg.outermostKeys()));
    }

    @Test
    public void testOneOuterElement() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 0, 100);
        reg.add("b", 10, 90);
        reg.add("c", 20, 80);
        reg.add("d", 30, 70);
        reg.add("e", 40, 60);
        reg.add("f", 45, 55);
        reg.add("g", 50, 51);
        assertEquals(Arrays.asList("a"), toList(reg.outermostKeys()));
        String ts = reg.toString();
        for (int i = 0; i < 10; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "a", r.key());
            int other = 99 - i;
            r = reg.at(other);
            assertNotNull(other + " " + r + " in " + ts, r);
            assertEquals(other + " " + r + " in " + ts, "a", r.key());
        }

        for (int i = 10; i < 20; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "b", r.key());
            assertNotNull(r.parent());
            assertEquals("a", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            List<SemanticRegion<String>> kids = r.parent().children();
            assertFalse("Children of parent contains the parent: " + kids, kids.contains(r.parent()));
            assertTrue("Children of parent of " + r + " do not contain the child: " + kids, kids.contains(r));
            assertEquals(1, r.children().size());
            int other = 99 - i;
            r = reg.at(other);
            assertNotNull(other + " " + r + " in " + ts, r);
            assertEquals(other + " " + r + " in " + ts, "b", r.key());
            assertNotNull(r.parent());
            assertEquals("a", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            kids = r.parent().children();
            assertFalse("Children of parent contains the parent: " + kids, kids.contains(r.parent()));
            assertTrue("Children of parent of " + r + " do not contain the child: " + kids, kids.contains(r));
            assertEquals(1, r.children().size());
        }

        for (int i = 20; i < 30; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "c", r.key());
            assertNotNull(r.parent());
            assertEquals("b", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            List<SemanticRegion<String>> kids = r.parent().children();
            assertFalse("Children of parent contains the parent: " + kids, kids.contains(r.parent()));
            assertTrue("Children of parent of " + r + " do not contain the child: " + kids, kids.contains(r));
            assertEquals(1, r.children().size());
            int other = 99 - i;
            r = reg.at(other);
            assertNotNull(other + " " + r + " in " + ts, r);
            assertEquals(other + " " + r + " in " + ts, "c", r.key());
            assertNotNull(r.parent());
            assertEquals("b", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            kids = r.parent().children();
            assertFalse("Children of parent contains the parent: " + kids, kids.contains(r.parent()));
            assertTrue("Children of parent of " + r + " do not contain the child: " + kids, kids.contains(r));
            assertEquals(1, r.children().size());
        }
        for (int i = 30; i < 40; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "d", r.key());
            assertNotNull(r.parent());
            assertEquals("c", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            assertTrue("Children of parent of " + r + " do not contain the child: " + r.parent().children(), r.parent().children().contains(r));
            assertEquals(1, r.children().size());
            int other = 99 - i;
            r = reg.at(other);
            assertNotNull(other + " " + r + " in " + ts, r);
            assertEquals(other + " " + r + " in " + ts, "d", r.key());
            assertNotNull(r.parent());
            assertEquals("c", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            assertEquals(1, r.children().size());
        }
        for (int i = 40; i < 45; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "e", r.key());
            assertNotNull(r.parent());
            assertEquals("d", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            assertTrue("Children of parent of " + r + " do not contain the child: " + r.parent().children(), r.parent().children().contains(r));
            assertEquals(1, r.children().size());
            int other = 99 - i;
            r = reg.at(other);
            assertTrue("Children of parent of " + r + " do not contain the child: " + r.parent().children(), r.parent().children().contains(r));
            assertNotNull(other + " " + r + " in " + ts, r);
            assertEquals(other + " " + r + " in " + ts, "e", r.key());
            assertNotNull(r.parent());
            assertEquals("d", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
        }
        for (int i = 45; i < 50; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "f", r.key());
            assertNotNull(r.parent());
            assertEquals("e", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            assertTrue("Children of parent of " + r + " do not contain the child: " + r.parent().children(), r.parent().children().contains(r));
            assertEquals(1, r.children().size());
        }

        for (int i = 50; i < 51; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertTrue(r.parent().children().contains(r));
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "g", r.key());
            assertNotNull(r.parent());
            assertEquals("f", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            assertTrue("Children of parent of " + r + " do not contain the child: " + r.parent().children(), r.parent().children().contains(r));
            assertEquals(0, r.children().size());
        }

        for (int i = 51; i < 55; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "f", r.key());
            assertNotNull(r.parent());
            assertEquals("e", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            assertTrue("Children of parent of " + r + " do not contain the child: " + r.parent().children(), r.parent().children().contains(r));
        }

        assertNull(reg.at(101));
        assertNull(reg.at(-1));
        sanityCheckRegions(reg);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> toList(Iterable<T> it) {
        if (it instanceof List<?>) {
            return (List<T>) it;
        }
        List<T> result = new ArrayList<>();
        for (T t : it) {
            result.add(t);
        }
        return result;
    }

    @Test
    public void testMultiLayerNesting() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 30, 100);
        reg.add("b1", 32, 50);
        reg.add("b1a1", 35, 45);
        reg.add("b1a1a1", 36, 44);
        reg.add("b1a1a1a1", 38, 42);
        reg.add("b2", 50, 60);
        reg.add("b3", 70, 80);
        reg.add("c", 110, 120);

        assertEquals(Arrays.asList("a", "b", "c"), toList(reg.outermostKeys()));
        assertEquals(9, reg.size());
        assertNotNull(reg.at(110));
        assertEquals("c", reg.at(110).key());
        assertNotNull(reg.at(111));
        assertEquals("c", reg.at(111).key());
        assertNotNull(reg.at(119));
        assertEquals("c", reg.at(119).key());

        assertNotNull(reg.at(10));
        assertEquals("a", reg.at(10).key());
        assertNotNull(reg.at(11));
        assertEquals("a", reg.at(11).key());
        assertNotNull(reg.at(19));
        assertEquals("a", reg.at(19).key());
        assertNull(reg.at(0));
        assertNull(reg.at(1));
        assertNull(reg.at(9));

        SemanticRegion<String> fiftySixty = reg.at(51);
        assertNotNull(fiftySixty);
        assertEquals("b2", fiftySixty.key());
        assertEquals(50, fiftySixty.start());
        assertEquals(60, fiftySixty.end());
        SemanticRegion<String> seventyEighty = reg.at(71);
        assertNotNull(seventyEighty);
        assertEquals("b3", seventyEighty.key());
        assertEquals(70, seventyEighty.start());
        assertEquals(80, seventyEighty.end());

        SemanticRegion<String> deepest = reg.at(39);
        assertNotNull(deepest);
        assertEquals("b1a1a1a1", deepest.key());
        assertEquals(4, deepest.nestingDepth());

        SemanticRegion<String> nextDeepest = reg.at(37);
        assertNotNull(nextDeepest);
        assertEquals("b1a1a1", nextDeepest.key());
        assertEquals(3, nextDeepest.nestingDepth());

        SemanticRegion<String> nextNextDeepest = reg.at(35);
        assertNotNull(nextNextDeepest);
        assertEquals("b1a1", nextNextDeepest.key());
        assertEquals(2, nextNextDeepest.nestingDepth());

        SemanticRegion<String> nextNextNextDeepest = reg.at(34);
        assertNotNull(nextNextNextDeepest);
        assertEquals("b1", nextNextNextDeepest.key());
        assertEquals(1, nextNextNextDeepest.nestingDepth());

        SemanticRegion<String> nextNextNextNextDeepest = reg.at(31);
        assertNotNull(nextNextNextNextDeepest);
        assertEquals("b", nextNextNextNextDeepest.key());
        assertEquals(0, nextNextNextNextDeepest.nestingDepth());

        assertEquals(nextDeepest, deepest.parent());
        assertEquals(nextNextDeepest, nextDeepest.parent());
        assertEquals(nextNextNextDeepest, nextNextDeepest.parent());
        assertEquals(nextNextNextNextDeepest, nextNextNextDeepest.parent());
        assertNull("Parent of " + nextNextNextNextDeepest + " should be null, not " + nextNextNextNextDeepest.parent(), nextNextNextNextDeepest.parent());

        List<SemanticRegion<String>> l = new LinkedList<>();
        l.add(fiftySixty);
        l.add(seventyEighty);
        l.add(deepest);
        l.add(nextDeepest);
        l.add(nextNextDeepest);
        l.add(nextNextNextDeepest);
        l.add(nextNextNextNextDeepest);
        Set<Integer> seen = new HashSet<>();
        for (SemanticRegion<String> s : l) {
            int start = s.start();
            int end = s.end();
            for (int i = start; i < end; i++) {
                if (seen.contains(i)) {
                    continue;
                }
                seen.add(i);
                assertTrue(s.contains(i));
                SemanticRegion<String> found = reg.at(i);
                assertEquals("For position " + i + " of " + s + " in " + reg, s, found);
            }
        }
        sanityCheckRegions(reg);
    }

    @Test
    public void testBoundaryCase1() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 30, 100);
        reg.add("c", 30, 50);
        reg.add("d", 50, 60);
        reg.add("e", 70, 80);
        reg.add("f", 110, 120);
        assertEquals(Arrays.asList("a", "b", "f"), toList(reg.outermostKeys()));
        SemanticRegion<String> sem = reg.at(50);
        assertNotNull(sem);
        assertEquals("Wrong entry " + sem + " for 50 in " + reg, "d", sem.key());
        Assert.assertArrayEquals(new int[]{3, 1}, reg.indexAndDepthAt(50));
        sanityCheckRegions(reg);
    }

    @Test
    public void testBoundarySearch() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 21, 22);
        reg.add("c", 23, 24);
        reg.add("d", 25, 26);
        reg.add("e", 27, 28);
        reg.add("f", 28, 29);
        reg.add("g", 29, 30);
        reg.add("h", 30, 100);
        reg.add("h1", 30, 99);
        reg.add("h2", 35, 60);
        reg.add("h3", 35, 40);
        reg.add("h4", 38, 40);

        assertNotNull(reg.at(29));

        for (SemanticRegion<String> r : reg) {
            assertNotNull("Null result for region start: " + r.start() + " for " + r, reg.at(r.start()));
            assertNotNull("Null result for region end-1: " + r.end() + " for " + r, reg.at(r.end() - 1));
        }

        assertEquals("h1", reg.at(30).key());
        assertEquals("g", reg.at(29).key());
        assertEquals("f", reg.at(28).key());
        assertEquals("e", reg.at(27).key());
        assertEquals("h1", reg.at(31).key());
        assertEquals("h3", reg.at(35).key());
        assertEquals("h3", reg.at(36).key());
        assertEquals("h2", reg.at(41).key());
        assertEquals("h4", reg.at(38).key());
        assertEquals("h4", reg.at(39).key());
        List<String> keys = new LinkedList<>();
        reg.at(30).keysAtPoint(39, keys);
        assertFalse(keys.isEmpty());
        assertEquals(Arrays.asList("h1", "h2", "h3", "h4"), keys);
        sanityCheckRegions(reg);
    }

    @Test
    public void testSingleLayerNesting() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 30, 100);
        reg.add("c", 30, 50);
        reg.add("d", 50, 60);
        reg.add("e", 70, 80);
        reg.add("f", 110, 120);
        assertEquals(Arrays.asList("a", "b", "f"), toList(reg.outermostKeys()));
        assertEquals(6, reg.size());

        SemanticRegion<String> r = reg.at(32);
        assertEquals("c", r.key());

        Assert.assertArrayEquals(new int[]{-1, -1}, reg.indexAndDepthAt(1));
        Assert.assertArrayEquals(new int[]{0, 0}, reg.indexAndDepthAt(11));
        Assert.assertArrayEquals(new int[]{2, 1}, reg.indexAndDepthAt(31));
//        Assert.assertArrayEquals(new int[]{3, 1}, reg.indexAndDepthAt(50));
        Assert.assertArrayEquals(new int[]{3, 1}, reg.indexAndDepthAt(51));
        Assert.assertArrayEquals(new int[]{4, 1}, reg.indexAndDepthAt(79));

        int ix = 0;
        for (SemanticRegion<String> ss : reg) {
            assertNotNull(ss);
            int expDepth;
            boolean expectHasChildren = "b".equals(ss.key());
            switch (ss.key()) {
                case "a":
                case "b":
                case "f":
                    expDepth = 0;
                    break;
                default:
                    SemanticRegion<String> par = ss.parent();
                    assertNotNull(ss + " index " + ix + " should not have null parent", par);
                    assertEquals("b", par.key());
                    expDepth = 1;
            }
            assertEquals(expDepth, ss.nestingDepth());
            assertEquals("Unexpected children: " + ss.allChildren() + " in " + ss, expectHasChildren, ss.iterator().hasNext());
            if (!expectHasChildren) {
                for (int i = ss.start(); i < ss.end(); i++) {
                    SemanticRegion<String> found = reg.at(i);
                    assertNotNull(i + " in " + ss + " of " + reg, found);
                    assertEquals(i + " in " + ss + " of " + reg, ss.key(), found.key());
                }
            } else {
                assertNull(ss.parent());
            }
            if (expDepth == 1) {
                assertEquals("b", ss.parent().key());
                assertEquals("b", ss.outermost().key());
            } else {
                assertNull(ss.outermost());
                assertNull(ss.parent());
            }
            ix++;
        }
        sanityCheckRegions(reg);
    }

    @Test
    public void testNoNesting() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 30, 40);
        reg.add("c", 50, 60);
        reg.add("d", 70, 80);
        assertEquals(4, reg.size());
        assertNull(reg.at(1));
        SemanticRegion<String> s = reg.at(10);
        assertEquals("a", s.key());
        assertEquals(10, s.start());
        assertEquals(20, s.end());
        assertTrue(s.contains(10));
        assertTrue(s.contains(15));
        assertTrue(s.contains(19));
        assertFalse(s.contains(20));

        int ix = 0;
        for (SemanticRegion<String> ss : reg) {
            assertEquals(0, ss.nestingDepth());
            for (int i = ss.start(); i < ss.end(); i++) {
                assertEquals(ss, reg.at(i));
            }
        }
        assertEquals(Arrays.asList("a", "b", "c", "d"), toList(reg.outermostKeys()));
        sanityCheckRegions(reg);
    }

    static void sanityCheckRegions(SemanticRegions<String> reg) {
        SemanticRegions.Index<String> index = SemanticRegions.index(reg);
        boolean duplicates = index.size() != reg.size();
        for (SemanticRegion<String> r : reg) {
            SemanticRegion<String> test = reg.forIndex(r.index());
            assertEquals(r, test);
            assertEquals(r.key(), test.key());
            assertEquals(r.start(), test.start());
            assertEquals(r.end(), test.end());
            assertEquals(r.index(), test.index());
            assertEquals(r.nestingDepth(), test.nestingDepth());
            List<SemanticRegion<String>> parents = r.parents();
            List<SemanticRegion<String>> children = r.children();
            List<SemanticRegion<String>> allChildren = r.allChildren();
            assertFalse(children.contains(r));
            assertFalse(parents.contains(r));
            for (int i = r.start(); i < r.end(); i++) {
                SemanticRegion<String> atPoint = reg.at(i);
                assertTrue(r.equals(atPoint) || allChildren.contains(atPoint));
            }
            SemanticRegion<String> parent = r.parent();
            if (parent != null) {
                assertTrue(parent.children().contains(r));
            } else {
                assertEquals(0, r.nestingDepth());
            }
            SemanticRegion<String> outer = r.outermost();
            if (outer != null) {
                assertTrue(outer.allChildren().contains(r));
            }
            int expectedChildDepth = r.nestingDepth() + 1;
            for (SemanticRegion<String> kid : children) {
                int nd = kid.nestingDepth();
                assertEquals("Nesting depth mismatch: " + kid
                        + " child of " + r + " should have nesting depth "
                        + expectedChildDepth + " not " + nd + " (in "
                        + reg + ")", expectedChildDepth, nd);
            }
            if (!duplicates) {
                SemanticRegion<String> fromIndex = index.get(r.key());
                assertNotNull(fromIndex);
                assertEquals(r, fromIndex);
            }
        }
        assertTrue(reg.trim().equalTo(reg.copy()));
    }
}
