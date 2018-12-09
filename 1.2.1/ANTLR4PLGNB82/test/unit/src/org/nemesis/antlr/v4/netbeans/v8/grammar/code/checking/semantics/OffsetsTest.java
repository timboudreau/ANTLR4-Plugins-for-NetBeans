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
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.OffsetsBuilder;
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
    private Foo[] foos;
    private Foo[] foos2;

    enum Foo {
        FOO,
        BAR,
        BAZ
    }

    @Test
    public void testOddAndEvenLengths() {
        // Test both odd and even lengths, since off-by-one errors in things
        // like binary search tend to result in code that looks like it works
        // but has some corner case around odd or even counts or intervals
        testOffsets(names2, foos2, 5);
        testOffsets(names, foos, 5);
        testOffsets(names2, foos2, 6);
        testOffsets(names, foos, 6);
    }

    @Test
    public void testSingle() {
        Offsets<Foo> offsets = new Offsets<>(new String[]{"foo"}, new int[] { -1}, new int[] {-1}, new Foo[]{Foo.FOO}, 1);
        offsets.setOffsets("foo", 10, 20);
        assertEquals(0, offsets.indexOf("foo"));
        assertEquals(10, offsets.start("foo"));
        assertEquals(20, offsets.end("foo"));
        Index<Foo> ix = offsets.index();
        for (int i = 10; i < 20; i++) {
            Item<Foo> item = ix.atOffset(10);
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
        Offsets<Foo> offsets = new Offsets<>(new String[0], new Foo[0], 0);
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

    public void testOffsets(String[] names, Foo[] foos, int dist) {
        assert names.length == foos.length;
        names = Arrays.copyOf(names, names.length);

        OffsetsBuilder<Foo> bldr = Offsets.builder(Foo.class);
        for (int i = 0; i < names.length; i++) {
            assertNotNull(i + "", names[i]);
            assertNotNull(i + "", foos[i]);
            bldr.add(names[i], foos[i]);
        }
        Offsets<Foo> offsets = bldr.arrayBased().build();

//        Offsets<Foo> offsets = new Offsets<>(names, foos);
        assertEquals(names.length, offsets.size());
        int pos = 0;
        for (int i = 0; i < names.length; i++) {
            offsets.setOffsets(names[i], pos, pos + dist);
            pos += dist * 2;
            assertTrue(offsets.contains(names[i]));
        }
        assertEquals(names.length, offsets.size());
        int ix = 0;
        for (Item<Foo> i : offsets) {
            assertEquals(ix, i.index());
            assertNotNull(i.kind());
            if (ix % 2 == 0) {
                assertEquals(Foo.FOO, i.kind());
            } else {
                assertEquals(Foo.BAR, i.kind());
            }
            assertEquals(names[ix++], i.name());
            assertTrue(i.containsPosition(i.start()));
            assertTrue(i.containsPosition(i.start() + 1));
            assertTrue(i.containsPosition(i.end() - 1));
        }
        pos = 0;
        Index<Foo> index = offsets.index();
        for (int i = 0; i < names.length; i++) {
            int start = pos;
            int end = pos + dist;
            Item<Foo> it = index.withStart(start);
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
        Offsets<Foo> sans = offsets.sans(toRemove);
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

        Offsets<Foo> withoutOffsets = offsets.secondary();
        assertEquals(new HashSet<>(Arrays.asList(names)), withoutOffsets.itemsWithNoOffsets());

        ReferenceSets<Foo> sets = offsets.newReferenceSets();
        assertNotNull(sets);
        sets.addReference("B", 5, 6);
        sets.addReference("C", 7, 8);
        sets.addReference("B", 16, 17);
        sets.addReference("C", 92, 93);
        sets.addReference("N", 107, 108);

        ReferenceSet<Foo> s = sets.references("B");
        assertEquals(2, s.referenceCount());
        assertTrue(s.contains(5));
        assertTrue(s.contains(16));
        assertFalse(s.contains(0));
        assertFalse(s.contains(7));
        assertFalse(s.contains(8));

        assertFalse(s.contains(17));
        assertFalse(s.contains(6));

        assertNotNull("B", sets.itemAt(16));
        assertEquals("B", sets.itemAt(16).name());

        int ct = 0;
        for (ReferenceSet<Foo> set : sets) {
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
        foos = new Foo[chars.length];
        names = new String[chars.length];
        for (int i = 0; i < chars.length; i++) {
            names[i] = new String(new char[]{chars[i]});
            if (i % 2 == 0) {
                foos[i] = Foo.FOO;
            } else {
                foos[i] = Foo.BAR;
            }
        }
        foos2 = new Foo[chars2.length];
        names2 = new String[chars2.length];
        for (int i = 0; i < chars2.length; i++) {
            names2[i] = new String(new char[]{chars2[i]});
            if (i % 2 == 0) {
                foos2[i] = Foo.FOO;
            } else {
                foos2[i] = Foo.BAR;
            }
        }
    }
}
