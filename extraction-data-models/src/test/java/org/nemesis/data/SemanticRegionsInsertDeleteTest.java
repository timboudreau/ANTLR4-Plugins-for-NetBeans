/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.data;

import com.mastfrog.range.RangeRelation;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.nemesis.data.SemanticRegions.SemanticRegionsBuilder;

/**
 *
 * @author Tim Boudreau
 */
public class SemanticRegionsInsertDeleteTest {

    @Test
    public void testSearch() {
        SemanticRegions<String> str = SemanticRegions.builder(String.class)
                .add("A", 0, 6)
                .add("B", 0, 2)
                .add("C", 1, 2)
                .add("D", 4, 5)
                .add("E", 10, 20)
                .add("F", 12, 15)
                .add("G", 13, 15)
                .add("H", 16, 18)
                .add("I", 18, 19)
                .add("Q", 25, 30)
                .build();

        assertSearch(1, str, "C");
        assertSearch(4, str, "D");
        assertSearch(0, str, "B");
        assertSearch(8, str, "A");
        assertSearch(9, str, "E");
        assertSearch(6, str, "A");
        assertSearch(20, str, "E");
        assertSearch(21, str, "E");
        assertSearch(22, str, "E");
        assertSearch(18, str, "I");
        assertSearch(19, str, "E");
        assertSearch(23, str, "Q");
        assertSearch(24, str, "Q");
        assertSearch(25, str, "Q");
        assertSearch(26, str, "Q");
        assertSearch(30, str, "Q");
        assertSearch(1000, str, "Q");
        assertSearch(-1, str, "A");
        assertSearch(-100, str, "A");
    }

    private void assertSearch(int position, SemanticRegions<String> regions, String match) {
        int ix = regions.indexOf(match);
        assertTrue("Test bug - " + match + " not present in " + regions, ix >= 0);
        SemanticRegion<String> near = regions.nearestTo(position);
        assertNotNull("No match for " + position, near);
        if (!match.equals(near.key())) {
            StringBuilder nearby = new StringBuilder();
            int start = Math.max(0, ix - 2);
            int end = Math.min(regions.size() - 1, ix + 2);
            for (int i = start; i <= end; i++) {
                if (nearby.length() > 0) {
                    nearby.append(" / ");
                }
                nearby.append(regions.forIndex(i));
            }
            assertEquals("Nearest to " + position + " should be " + match + " but was " + near.key()
                    + " target region " + regions.forIndex(ix) + " got " + near
                    + " - nearby " + nearby, near.key());
        }
    }

    @Test
    public void testFlatten() {
        SemanticRegions<String> str = SemanticRegions.builder(String.class)
                .add("A", 0, 6)
                .add("B", 0, 2)
                .add("C", 1, 2)
                .add("D", 4, 5)
                .add("E", 10, 20)
                .add("F", 12, 15)
                .add("G", 13, 15)
                .add("H", 16, 18)
                .add("I", 18, 19)
                .add("Q", 25, 30)
                .build();

        SemanticRegions<String> flat = str.flatten(strs -> {
            StringBuilder sb = new StringBuilder(strs.size());
            for (String s1 : strs) {
                sb.append(s1);
            }
            return sb.toString();
        });

        for (int i = 0; i < flat.size(); i++) {
            SemanticRegion<String> r = flat.forIndex(i);
            switch (i) {
                case 0:
                    assertEquals(0, r.start());

                    assertEquals(1, r.end());
                    assertEquals("BA", r.key());
                    break;
                case 1:
                    assertEquals(1, r.start());

                    assertEquals(2, r.end());
                    assertEquals("CBA", r.key());
                    break;
                case 2:
                    assertEquals(2, r.start());

                    assertEquals(4, r.end());
                    assertEquals("A", r.key());
                    break;
                case 3:
                    assertEquals(4, r.start());

                    assertEquals(5, r.end());
                    assertEquals("DA", r.key());
                    break;
                case 4:
                    assertEquals(5, r.start());

                    assertEquals(6, r.end());
                    assertEquals("A", r.key());
                    break;
                case 5:
                    assertEquals(10, r.start());

                    assertEquals(12, r.end());
                    assertEquals("E", r.key());
                    break;
                case 6:
                    assertEquals(12, r.start());

                    assertEquals(13, r.end());
                    assertEquals("FE", r.key());
                    break;
                case 7:
                    assertEquals(13, r.start());

                    assertEquals(15, r.end());
                    assertEquals("GFE", r.key());
                    break;
                case 8:
                    assertEquals(15, r.start());

                    assertEquals(16, r.end());
                    assertEquals("E", r.key());
                    break;
                case 9:
                    assertEquals(16, r.start());

                    assertEquals(18, r.end());
                    assertEquals("HE", r.key());
                    break;
                case 10:
                    assertEquals(18, r.start());

                    assertEquals(19, r.end());
                    assertEquals("IE", r.key());
                    break;
                case 11:
                    assertEquals(19, r.start());

                    assertEquals(20, r.end());
                    assertEquals("E", r.key());
                    break;
                case 12:
                    assertEquals(25, r.start());
                    assertEquals(30, r.end());
                    assertEquals("Q", r.key());
                    break;
            }
        }
    }

    @Test
    public void testDeletions() {
        SemanticRegions<String> regs = regionsForText("  This here is some stuff");
        assertSameRegions(regionsForText(" This here is some stuff"), regs.withDeletion(1, 0));
        assertSameRegions(regionsForText("This here is some stuff"), regs.withDeletion(2, 0));
        assertSameRegions(regionsForText(" This here is some stuff"), regs.withDeletion(1, 1));
        assertSame(regs, regs.withDeletion(10, 30));

        assertSameRegions(regionsForText("  Thi here is some stuff"), regs.withDeletion(1, 4));
        assertSameRegions(regionsForText("  Th here is some stuff"), regs.withDeletion(2, 4));

        SemanticRegions<String> sanityCheck = regionsForText("  T here is some stuff");
        assertEquals(2, sanityCheck.forIndex(0).start());
        assertEquals(1, sanityCheck.forIndex(0).size());
        assertEquals(3, sanityCheck.forIndex(0).end());
        assertEquals("T", sanityCheck.forIndex(0).key());

        assertSameRegions(regionsForText("  T here is some stuff"), regs.withDeletion(3, 3));
        assertSameRegions(regionsForText("   here is some stuff"), regs.withDeletion(4, 2));

        System.out.println("\n-----------\n");
        assertSameRegions(regionsForText("Thi here is some stuff"), regs.withDeletion(3, 0));
        assertSameRegions(regionsForText("Th here is some stuff"), regs.withDeletion(4, 0));
        assertSameRegions(regionsForText("T here is some stuff"), regs.withDeletion(5, 0));
        assertSameRegions(regionsForText(" here is some stuff"), regs.withDeletion(6, 0));
        assertSameRegions(regionsForText("here is some stuff"), regs.withDeletion(7, 0));
        assertSameRegions(regionsForText("her is some stuff"), regs.withDeletion(8, 0));
        assertSameRegions(regionsForText("he is some stuff"), regs.withDeletion(9, 0));
        assertSameRegions(regionsForText("h is some stuff"), regs.withDeletion(10, 0));
        assertSameRegions(regionsForText(" is some stuff"), regs.withDeletion(11, 0));
        assertSameRegions(regionsForText("is some stuff"), regs.withDeletion(12, 0));
        assertSameRegions(regionsForText("i some stuff"), regs.withDeletion(13, 0));

        assertSameRegions(regionsForText("  This her is some stuff"), regs.withDeletion(1, 7));
        assertSameRegions(regionsForText("  This her is some stuff"), regs.withDeletion(1, 8));
        assertSameRegions(regionsForText("  This her is some stuff"), regs.withDeletion(1, 9));
        assertSameRegions(regionsForText("  This her is some stuff"), regs.withDeletion(1, 10));

        assertEquals("  This hereis some stuff", regionsAsText(regs.withDeletion(1, 11)));
        assertEquals("  This herei some stuff", regionsAsText(regs.withDeletion(2, 11)));
        assertEquals("  This here some stuff", regionsAsText(regs.withDeletion(3, 11)));
        assertEquals("  This heresome stuff", regionsAsText(regs.withDeletion(4, 11)));
        assertEquals("  This heresom stuff", regionsAsText(regs.withDeletion(5, 11)));
        assertEquals("  This hereso stuff", regionsAsText(regs.withDeletion(6, 11)));
        assertEquals("  This heres stuff", regionsAsText(regs.withDeletion(7, 11)));
        assertEquals("  This here stuff", regionsAsText(regs.withDeletion(8, 11)));
        assertEquals("  This herestuff", regionsAsText(regs.withDeletion(9, 11)));
        assertEquals("  This herestuf", regionsAsText(regs.withDeletion(10, 11)));
        assertEquals("  This herestu", regionsAsText(regs.withDeletion(11, 11)));
        assertEquals("  This herest", regionsAsText(regs.withDeletion(12, 11)));
        assertEquals("  This heres", regionsAsText(regs.withDeletion(13, 11)));
        assertEquals("  This here", regionsAsText(regs.withDeletion(14, 11)));
        assertEquals("  This here", regionsAsText(regs.withDeletion(15, 11)));
        assertEquals("  This here", regionsAsText(regs.withDeletion(16, 11)));
        assertEquals("  This her", regionsAsText(regs.withDeletion(16, 10)));
        assertEquals("  This he", regionsAsText(regs.withDeletion(17, 9)));
        assertEquals("  This h", regionsAsText(regs.withDeletion(18, 8)));
        assertEquals("  This", regionsAsText(regs.withDeletion(19, 7)));
        assertSameRegions(regionsForText(" her is some stuff"), regs.withDeletion(7, 1));

        assertEquals("  This here is some stuf", regionsAsText(regs.withDeletion(1, 23)));
        assertEquals("  This here is some stuf", regionsAsText(regs.withDeletion(1, 22)));
        assertEquals("  This here is some stuf", regionsAsText(regs.withDeletion(1, 21)));
        assertEquals("  This here is some stuf", regionsAsText(regs.withDeletion(1, 20)));
        assertEquals("  This here is somestuff", regionsAsText(regs.withDeletion(1, 19)));
        assertEquals("  This here is som stuff", regionsAsText(regs.withDeletion(1, 18)));

        assertEquals("  This here is some stu", regionsAsText(regs.withDeletion(2, 23)));
        assertEquals("  This here is some stu", regionsAsText(regs.withDeletion(2, 22)));
        assertEquals("  This here is some stu", regionsAsText(regs.withDeletion(2, 21)));
    }

    @Test
    public void testInsertions() {
        SemanticRegions<String> regs = regionsForText("  This here is some stuff");
        SemanticRegions<String> insertAtZero = regs.withInsertion(2, 0);

        SemanticRegions<String> regs2 = regionsForText("    This here is some stuff");
        assertSameRegions(regs2, insertAtZero);

        SemanticRegions<String> insertAtOne = regs.withInsertion(2, 1);
        assertSameRegions(regs2, insertAtOne);

        SemanticRegions<String> insertAtTwo = regs.withInsertion(2, 2);
        assertSameRegions(regs2, insertAtTwo);

        assertEquals(regionsAsText(regs2), regionsAsText(insertAtZero));

        SemanticRegions<String> insertPastEnd = regs.withInsertion(10, 30);
        assertSame("Adding an assertion after the end should have no effect", regs, insertPastEnd);

        SemanticRegions<String> insertFourAfterThis = regs.withInsertion(4, 6);
        SemanticRegions<String> expFourAfterThis = regionsForText("  This     here is some stuff");

        assertEquals("test bug - regions built incorrectly: " + expFourAfterThis.forIndex(0), 2, expFourAfterThis.forIndex(0).start());
        assertEquals("test bug - regions built incorrectly: " + expFourAfterThis.forIndex(1), 11, expFourAfterThis.forIndex(1).start());
        assertSameRegions(expFourAfterThis, insertFourAfterThis);

        SemanticRegions<String> insertTwoInThis = regs.withInsertion(2, 5);
        SemanticRegions<String> expTwoInThis = regionsForText("  This-- here is some stuff");
        assertSameRegions(expTwoInThis, insertTwoInThis);

        SemanticRegions<String> insertTwoAtIs = regs.withInsertion(2, 12);
        SemanticRegions<String> expTwoAtIs = regionsForText("  This here is-- some stuff");
        assertSameRegions(expTwoAtIs, insertTwoAtIs);

        SemanticRegions<String> insertTwoAfterStuff = regs.withInsertion(2, 25);
        SemanticRegions<String> expTwoAfterStuff = regionsForText("  This here is some --stuff");
        assertSameRegions(expTwoAfterStuff, insertTwoAfterStuff);

        SemanticRegions<String> insertTwoInStuff = regs.withInsertion(2, 23);
        SemanticRegions<String> expTwoInStuff = regionsForText("  This here is some --stuff");
        assertSameRegions(expTwoInStuff, insertTwoInStuff);

        // and test the simplest case
        regs = regionsForText("This here is some stuff");
        SemanticRegions<String> insertAtZeroSimple = regs.withInsertion(2, 0);
        SemanticRegions<String> expInsertAtZeroSimple = regionsForText("  This here is some stuff");
        assertSameRegions("Insertion at or before start should just shift everything", expInsertAtZeroSimple, insertAtZeroSimple);
    }

    static String detail(SemanticRegions<String> s) {
        StringBuilder sb = new StringBuilder("{");
        for (SemanticRegion<String> sem : s) {
            sb.append("\n  ");
            detail(sem, sb);
        }
        return sb.append("\n}").toString();
    }

    static StringBuilder detail(SemanticRegion<String> sem, StringBuilder sb) {
        sb.append(sem.index()).append(". '")
                .append(sem.key())
                .append("' start ").append(sem.start()).append(" end ").append(sem.end())
                .append(" size ").append(sem.size());
        return sb;
    }

    static String detail(SemanticRegion<String> sem) {
        return detail(sem, new StringBuilder()).toString();
    }

    static String regionsAsText(SemanticRegions<String> s) {
        assertEquals("Wrong key type " + s.keyType() + " in " + s, String.class, s.keyType());
        StringBuilder sb = new StringBuilder();
        SemanticRegion<String> last = null;
        for (SemanticRegion<String> reg : s) {
            if (last != null) {
                assertFalse("Created regions that overlap: " + detail(last) + " and " + detail(reg)
                        + " in " + detail(s),
                        last.overlaps(reg));
            }
            if (reg.index() == 0) {
                int st = reg.start();
                if (st > 0) {
                    char[] c = new char[st];
                    Arrays.fill(c, ' ');
                    sb.append(c);
                }
            } else {
                int off = reg.start() - last.end();
                char[] c = new char[off];
                Arrays.fill(c, ' ');
                sb.append(c);
            }
            String k = reg.key();
            if (reg.size() > reg.key().length()) {
                while (k.length() < reg.size()) {
                    k += "-";
                }
            } else if (reg.key().length() > reg.size()) {
                assert reg.size() > 0 : "Zero or negative length region: " + detail(reg);

                k = reg.key().substring(0, reg.size());
//                System.out.println("CLIP " + reg.key() + " to " + k + " for " + reg + " size " + reg.size());
            }
            sb.append(k);
            last = reg;
        }
        return sb.toString();
    }

    static SemanticRegions<String> regionsForText(String text) {
        SemanticRegionsBuilder<String> bldr = SemanticRegions.builder(String.class);
        StringBuilder word = new StringBuilder();
        int wordStart = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || i == text.length() - 1) {
                boolean addOne = false;
                if (i == text.length() - 1 && !Character.isWhitespace(c)) {
                    addOne = true;
                    word.append(c);
                }
                if (wordStart >= 0) {
                    bldr.add(word.toString(), wordStart, addOne ? i + 1 : i);
                    wordStart = -1;
                    word.setLength(0);
                }
            } else {
                word.append(c);
                if (wordStart < 0) {
                    wordStart = i;
                }
            }
        }
        SemanticRegions<String> result = bldr.build();
        for (SemanticRegion<String> s : result) {
            assert s.size() > 0 : "Zero length " + s + " in " + result;
        }
        return result;
    }

    static void assertSameRegions(SemanticRegions<String> expected, SemanticRegions<String> got) {
        assertSameRegions(null, expected, got);
    }

    static void assertSameRegions(String msg, SemanticRegions<String> expected, SemanticRegions<String> got) {
        if (msg == null) {
            msg = "";
        }
        if (msg.length() > 0) {
            msg += ". ";
        }
        assertEquals("Sizes differ: \n" + regionsAsText(expected) + " vs \n"
                + regionsAsText(got) + " expected detail:\n" + detail(expected)
                + " but got:\n" + detail(got), expected.size(), got.size());
        for (int i = 0; i < expected.size(); i++) {
            SemanticRegion<?> aa = expected.forIndex(i);
            SemanticRegion<?> bb = got.forIndex(i);
            RangeRelation rel = aa.relationTo(bb);
            assertEquals(msg + "Wrong relation for region " + i + ": " + aa + " vs " + bb
                    + " expected \n'" + regionsAsText(expected) + "' but got \n'" + regionsAsText(got) + "'\n"
                    + "expected detail: " + detail(expected) + "\ngot detail: "
                    + detail(got), RangeRelation.EQUAL, rel);
        }
    }
}
