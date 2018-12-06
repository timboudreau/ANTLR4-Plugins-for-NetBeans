package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.Arrays;
import java.util.HashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.Index;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.Item;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.ReferenceSets;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.ReferenceSets.ReferenceSet;

/**
 *
 * @author Tim Boudreau
 */
public class OffsetsTest {

    static char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    static char[] chars2 = "ABCDEFGHIJKLMNOPQRSTUVWXY".toCharArray();
    String[] names;
    String[] names2;

    @Test
    public void testOddAndEvenLengths() {
        testOffsets(names2, 5);
        testOffsets(names, 5);
    }

    @Test
    public void testSingle() {
        Offsets offsets = new Offsets(new String[]{"foo"});
        offsets.setOffsets("foo", 10, 20);
        assertEquals(0, offsets.indexOf("foo"));
        assertEquals(10, offsets.start("foo"));
        assertEquals(20, offsets.end("foo"));
        Index ix = offsets.index();
        for (int i = 10; i < 20; i++) {
            Item item = ix.atOffset(10);
            assertEquals("foo", item.name());
            assertEquals(10, item.start());
            assertEquals(20, item.end());
            assertEquals(19, item.stop());
            assertEquals(0, item.index());
        }
        for (int i = 0; i < 10; i++) {
            assertNull(ix.atOffset(i));
        }
        for (int i = 20; i < 30; i++) {
            assertNull(ix.atOffset(i));
        }
    }

    @Test
    public void testEmpty() {
        Offsets offsets = new Offsets(new String[0]);
        assertEquals(0, offsets.size());
        assertNull(offsets.index().atOffset(0));
        assertNull(offsets.index().atOffset(1));
        assertNull(offsets.index().atOffset(-1));
        assertNull(offsets.index().atOffset(Integer.MAX_VALUE));
        assertNull(offsets.index().atOffset(Integer.MIN_VALUE));
        assertFalse(offsets.contains(""));
        assertFalse(offsets.iterator().hasNext());
        assertEquals(-1, offsets.indexOf(""));
    }

    public void testOffsets(String[] names, int dist) {
        names = Arrays.copyOf(names, names.length);
        Offsets offsets = new Offsets(names);
        assertEquals(names.length, offsets.size());
        int pos = 0;
        for (int i = 0; i < names.length; i++) {
            offsets.setOffsets(names[i], pos, pos + dist);
            pos += dist * 2;
            assertTrue(offsets.contains(names[i]));
        }
        assertEquals(names.length, offsets.size());
        int ix = 0;
        for (Item i : offsets) {
            assertEquals(ix, i.index());
            assertEquals(names[ix++], i.name());
            assertTrue(i.containsPosition(i.start()));
            assertTrue(i.containsPosition(i.start() + 1));
            assertTrue(i.containsPosition(i.end() - 1));
        }
        pos = 0;
        Index index = offsets.index();
        for (int i = 0; i < names.length; i++) {
            int start = pos;
            int end = pos + dist;
            Item it = index.withStart(start);
            assertNotNull(it);
            assertEquals(names[i], it.name());
            assertEquals(i, it.index());
            it = index.withEnd(end);
            assertNotNull(it);
            assertEquals(names[i], it.name());
            assertEquals(i, it.index());

            for (int j = start; j < end; j++) {
                it = index.atOffset(j);
                assertNotNull("Got null for offset " + j, it);
                assertEquals(names[i], it.name());
                assertEquals(i, it.index());
            }
            for (int j = 1; j < dist - 1; j++) {
                assertNull("" + (end + j), index.atOffset(end + j));
            }
            assertNull("" + (end + 4), index.atOffset(end + 4));
            assertNull("" + (end + 3), index.atOffset(end + 3));
            assertNull("" + (end + 2), index.atOffset(end + 2));
            assertNull("" + (end + 1), index.atOffset(end + 1));
            assertNull("-1", index.atOffset(-1));
            assertNull("" + Integer.MAX_VALUE, index.atOffset(Integer.MAX_VALUE));
            assertNull("" + Integer.MIN_VALUE, index.atOffset(Integer.MIN_VALUE));
            pos += dist * 2;
        }
        String[] toRemove = new String[]{"A", "C", "E", "G", "I", "K", "Y"};
        Offsets sans = offsets.sans(toRemove);
        for (String s : toRemove) {
            assertFalse(s, sans.contains(s));
        }
        assertNotSame(offsets, sans);
        for (String n : names) {
            switch (n) {
                case "A":
                case "C":
                case "E":
                case "G":
                case "I":
                case "K":
                case "Y":
                    assertFalse(sans.contains(n));
                    break;
                default:
                    assertTrue(sans.contains(n));
                    assertEquals(offsets.start(n), sans.start(n));
                    assertEquals(offsets.start(n), sans.start(n));
                    assertEquals(offsets.end(n), sans.end(n));
            }
        }
        assertEquals(sans + "", (offsets.size() - toRemove.length), sans.size());

        int oldSize = sans.size();
        assertTrue(sans.contains("Q"));
        sans.remove("Q");
        assertEquals(oldSize - 1, sans.size());
        assertFalse(sans.contains("Q"));

        Offsets withoutOffsets = offsets.secondary();
        assertEquals(new HashSet<>(Arrays.asList(names)), withoutOffsets.itemsWithNoOffsets());

        ReferenceSets sets = offsets.newReferenceSets();
        assertNotNull(sets);
        sets.addReference("B", 5, 6);
        sets.addReference("C", 7, 8);
        sets.addReference("B", 16, 17);
        sets.addReference("C", 92, 93);
        sets.addReference("N", 107, 108);


        ReferenceSet s = sets.references("B");
        assertEquals(2, s.referenceCount());
        assertTrue(s.contains(5));
        assertTrue(s.contains(16));
        assertFalse(s.contains(0));
        assertFalse(s.contains(7));
        assertFalse(s.contains(8));

        assertFalse(s.contains(17));
        assertFalse(s.contains(6));
        int ct = 0;
        for (ReferenceSet set : sets) {
            assertNotNull("Null set at " + ct + " in " + sets, set);
            String nm = set.name();
            assertNotNull(nm);
            switch (nm) {
                case "B":
                    assertEquals(set.referenceCount(), 2);
                    assertEquals(0, ct);
                    break;
                case "C":
                    assertEquals(set.referenceCount(), 2);
                    assertEquals(1, ct);
                    break;
                case "N":
                    assertEquals(set.referenceCount(), 1);
                    assertEquals(2, ct);
                    break;
            }
            ct++;
        }
    }

    @Before
    public void setup() {
        names = new String[chars.length];
        for (int i = 0; i < chars.length; i++) {
            names[i] = new String(new char[]{chars[i]});
        }
        names2 = new String[chars2.length];
        for (int i = 0; i < chars2.length; i++) {
            names2[i] = new String(new char[]{chars2[i]});
        }
    }

}
