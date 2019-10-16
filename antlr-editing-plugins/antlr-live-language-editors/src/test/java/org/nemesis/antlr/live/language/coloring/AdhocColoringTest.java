/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.live.language.coloring;

import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import java.awt.Color;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocColoringTest {

    @Test
    public void testCombine() {
        AdhocColoring a = new AdhocColoring(Color.GREEN, true, true, false, false, true);
        assertEquals(setOf(StyleConstants.Background, StyleConstants.Italic), toSet(a.getAttributeNames()));
        testConsistency(a);
        AdhocColoring b = new AdhocColoring(Color.BLUE, true, false, true, true, false);
        assertEquals(setOf(StyleConstants.Foreground, StyleConstants.Bold), toSet(b.getAttributeNames()));
        testConsistency(b);

        AttributeSet s = a.combine(b);
        assertEquals(Color.GREEN, s.getAttribute(StyleConstants.Background));
        assertEquals(Color.BLUE, s.getAttribute(StyleConstants.Foreground));
        assertEquals(Boolean.TRUE, s.getAttribute(StyleConstants.Bold));
        assertEquals(Boolean.TRUE, s.getAttribute(StyleConstants.Italic));

        assertEquals(setOf(StyleConstants.Foreground, StyleConstants.Bold, StyleConstants.Background, StyleConstants.Italic),
                toSet(s.getAttributeNames()));
        testConsistency(s);

        s = AdhocColoring.merge(a, b);
        assertEquals(Color.GREEN, s.getAttribute(StyleConstants.Background));
        assertEquals(Color.BLUE, s.getAttribute(StyleConstants.Foreground));
        assertEquals(Boolean.TRUE, s.getAttribute(StyleConstants.Bold));
        assertEquals(Boolean.TRUE, s.getAttribute(StyleConstants.Italic));

        assertEquals(setOf(StyleConstants.Foreground, StyleConstants.Bold, StyleConstants.Background, StyleConstants.Italic),
                toSet(s.getAttributeNames()));

        testConsistency(s);
    }

    private void testConsistency(AttributeSet s) {
        int ct = 0;
        for (Object key : CollectionUtils.toIterable(s::getAttributeNames)) {
            Object val = s.getAttribute(key);
            assertNotNull(val);
            assertTrue(s.containsAttribute(key, val), "Contains " + key + " as " + val + " but does not report it: " + s);
            assertTrue(s.isDefined(key));
            ct++;
        }
        assertEquals(ct, s.getAttributeCount());
        AttributeSet sa = sa(s);
        assertTrue(s.containsAttributes(sa));
        assertTrue(sa.isEqual(s));
        assertTrue(s.isEqual(sa), "isEqual returns false for SimpleAttributeSet copy " + sa);
    }

    static AttributeSet sa(AttributeSet as) {
        SimpleAttributeSet sa = new SimpleAttributeSet(as);
        return sa;
    }

    private Set<Object> toSet(Enumeration<?> e) {
        Set<Object> s = new HashSet<>();
        while (e.hasMoreElements()) {
            s.add(e.nextElement());
        }
        return s;
    }

    @Test
    public void testMultiple() {
        AdhocColoring a = new AdhocColoring(Color.GREEN, true, true, false, false, false);
        AdhocColoring b = new AdhocColoring(Color.BLUE, true, false, true, false, false);
        AdhocColoring c = new AdhocColoring(Color.BLACK, true, false, false, true, false);
        AdhocColoring d = new AdhocColoring(Color.WHITE, true, false, false, false, true);

        assertTrue(a.isBackgroundColor());
        assertFalse(a.isForegroundColor());
        testConsistency(a);
        testConsistency(b);
        testConsistency(c);
        testConsistency(d);

        Combined s = new Combined(new AdhocAttributeSet[]{a, b, c, d});
        assertEquals(Color.GREEN, s.getAttribute(StyleConstants.Background));
        assertEquals(Color.BLUE, s.getAttribute(StyleConstants.Foreground));
        assertEquals(Boolean.TRUE, s.getAttribute(StyleConstants.Bold));
        assertEquals(Boolean.TRUE, s.getAttribute(StyleConstants.Italic));

        a.setColor(Color.ORANGE);
        assertEquals(Color.ORANGE, s.getAttribute(StyleConstants.Background));

        a.toggleBackgroundForeground();
        assertFalse(a.isBackgroundColor());
        assertTrue(a.isForegroundColor());

        a.setBold(true);
        assertTrue(a.isBold());
        assertTrue(a.isForegroundColor());
        assertFalse(a.isBackgroundColor());
        assertTrue(a.isActive());
        assertFalse(a.isItalic());
        assertEquals(Boolean.TRUE, a.getAttribute(StyleConstants.Bold));
        assertNull(a.getAttribute(StyleConstants.Italic));
    }

    @Test
    public void testBasic() {
        AdhocColoring c = new AdhocColoring(Color.BLACK, false, true, false, false, false);
        c.setFlags(EnumSet.of(AttrTypes.ACTIVE, AttrTypes.BACKGROUND));
        c.setColor(Color.YELLOW);
        c.setBold(true);
        c.setItalic(true);
        assertEquals(Color.YELLOW, c.getAttribute(StyleConstants.Background));
        assertEquals(Boolean.TRUE, c.getAttribute(StyleConstants.Bold));
        assertEquals(Boolean.TRUE, c.getAttribute(StyleConstants.Italic));
        testConsistency(c);
    }

    @Test
    public void testInactiveNotMerged() {
        AdhocColoring a = new AdhocColoring(Color.GREEN, false, true, false, true, false);
        assertFalse(a.isActive());
        assertNull(a.getAttribute(StyleConstants.Background));
        AdhocColoring b = new AdhocColoring(Color.BLUE, true, true, false, false, true);
        AttributeSet c = a.combine(b);
        assertNotNull(c);
        AttributeSet d = b.combine(a);
        assertNotNull(d);
        System.out.println("merged to " + c);
        System.out.println("rev merged to " + d);
        assertTrue(c instanceof AdhocColoring);
        assertTrue(d instanceof AdhocColoring);
        AdhocColoring ac = (AdhocColoring) c;
        AdhocColoring ad = (AdhocColoring) d;
        assertFalse(ac.isBold());
        assertFalse(ad.isBold());
        assertTrue(ac.isItalic());
        assertTrue(ad.isItalic());
    }

    @Test
    public void testInactiveNotMergedByMergedColoring() {
        AdhocColoring a = new AdhocColoring(Color.GREEN, false, true, false, true, false);
        AdhocColoring b = new AdhocColoring(Color.BLUE, true, true, false, false, true);
        AdhocColoringMerged m1 = new AdhocColoringMerged(a, b);
        AdhocColoringMerged m2 = new AdhocColoringMerged(b, a);
        assertTrue(m1.isActive(), m1::toString);
        assertTrue(m2.isActive(), m2::toString);
        assertEquals(Color.BLUE, m1.getAttribute(StyleConstants.Background), m1::toString);
        assertEquals(Color.BLUE, m2.getAttribute(StyleConstants.Background), m2::toString);
        assertFalse(m1.isBold(), m1::toString);
        assertFalse(m2.isBold(), m2::toString);
        assertTrue(m1.isItalic(), m1::toString);
        assertTrue(m2.isItalic(), m2::toString);
        assertTrue(m1.isEqual(m2), m1 + " - " + m2);

        AttributeSet comb1 = AdhocColoring.merge(a, b);
        AttributeSet comb2 = AdhocColoring.merge(a, b);
        assertTrue(comb1.isEqual(m1));
        assertTrue(comb2.isEqual(m1));
    }

    @Test
    public void testMutability() {
        AdhocColoring a = new AdhocColoring(Color.GREEN, false, false, false, false, false);
        assertFalse(a.isActive());
        assertFalse(a.isBackgroundColor());
        assertFalse(a.isForegroundColor());
        assertFalse(a.isItalic());
        assertFalse(a.isBold());
        assertTrue(a.isEmpty());
        assertTrue(a.flags().isEmpty());
        a.setActive(true);
        assertTrue(a.isActive());
        assertFalse(a.isBackgroundColor());
        assertFalse(a.isForegroundColor());
        assertFalse(a.isItalic());
        assertFalse(a.isBold());
        assertTrue(a.isEmpty());
        assertFalse(a.flags().isEmpty());

        a.addFlag(AttrTypes.BACKGROUND);
        assertTrue(a.isBackgroundColor());
        assertFalse(a.isForegroundColor());
        assertTrue(a.isActive());
        assertEquals(Color.GREEN, a.getAttribute(StyleConstants.Background));
        assertEquals(EnumSet.of(AttrTypes.ACTIVE, AttrTypes.BACKGROUND), a.flags());

        a.addFlag(AttrTypes.FOREGROUND);
        assertTrue(a.isForegroundColor());
        assertFalse(a.isBackgroundColor());
        assertTrue(a.isActive());
        assertFalse(a.isEmpty());
        assertEquals(Color.GREEN, a.getAttribute(StyleConstants.Foreground));
        assertNull(a.getAttribute(StyleConstants.Background));
        assertEquals(EnumSet.of(AttrTypes.ACTIVE, AttrTypes.FOREGROUND), a.flags());

        a.removeFlag(AttrTypes.ACTIVE);
        assertFalse(a.isActive());
        assertFalse(a.isBackgroundColor());
        assertTrue(a.isForegroundColor());
        assertFalse(a.isItalic());
        assertFalse(a.isBold());
        assertFalse(a.isEmpty());

        a.setBold(true);
        assertTrue(a.isBold());
        assertTrue(a.flags().contains(AttrTypes.BOLD));
        assertEquals(EnumSet.of(AttrTypes.FOREGROUND, AttrTypes.BOLD), a.flags());

        a.toggleBackgroundForeground();
        assertTrue(a.isBackgroundColor());
        assertFalse(a.isForegroundColor());
        assertEquals(EnumSet.of(AttrTypes.BACKGROUND, AttrTypes.BOLD), a.flags());

        a.toggleBackgroundForeground();
        assertFalse(a.isBackgroundColor());
        assertTrue(a.isForegroundColor());
        assertEquals(EnumSet.of(AttrTypes.FOREGROUND, AttrTypes.BOLD), a.flags());
    }

    @Test
    public void testSymmetricEquality() {
        AdhocColoring a = new AdhocColoring(Color.GREEN, true, true, false, true, false);
        AdhocColoringMerged b = new AdhocColoringMerged(a);
        DepthAttributeSet c = new DepthAttributeSet(a.copyAttributes(), 1);
        DepthAttributeSet d = new DepthAttributeSet(b, 2);

        assertAllEqual(a, b, c, d);

        a.setActive(false);
        assertNotEqualSets(a, b);
        assertNotEqualSets(a, c);
        assertNotEqualSets(a, d);

        a.setActive(true);
        a.setItalic(true);
        b = new AdhocColoringMerged(a);
        c = new DepthAttributeSet(a.copyAttributes(), 1);
        d = new DepthAttributeSet(b, 2);

        assertAllEqual(a, b, c, d);

        a.addFlag(AttrTypes.FOREGROUND);
        assertNotEqualSets(a, b);
        assertNotEqualSets(a, c);
        assertNotEqualSets(a, d);
        b = new AdhocColoringMerged(a);
        c = new DepthAttributeSet(a.copyAttributes(), 1);
        d = new DepthAttributeSet(b, 2);

        assertAllEqual(a, b, c, d);

        a.removeFlag(AttrTypes.FOREGROUND);
        a.removeFlag(AttrTypes.BACKGROUND);
        assertNotEqualSets(a, b);
        assertNotEqualSets(a, c);
        assertNotEqualSets(a, d);

        b = new AdhocColoringMerged(a);
        c = new DepthAttributeSet(a.copyAttributes(), 1);
        d = new DepthAttributeSet(b, 2);

        assertAllEqual(a, b, c, d);
    }

    private void assertNotEqualSets(AdhocAttributeSet a, AdhocAttributeSet b) {
        assertFalse(a.isEqual(b));
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode(), "Same hash codes for " + a + "("
                + a.getClass().getSimpleName() + ")" + " and " + b
                + " (" + b.getClass().getSimpleName() + ")");
    }

    private void assertAllEqual(AdhocAttributeSet... all) {
        for (int i = 0; i < all.length; i++) {
            AdhocAttributeSet a = all[i];
            for (int j = 0; j < all.length; j++) {
                AdhocAttributeSet b = all[j];
                assertTrue(a.isEqual(b), a + "("
                        + a.getClass().getSimpleName() + ")"
                        + " returns false erroneously from isEqual() on "
                        + b + " (" + b.getClass().getSimpleName() + ")");
                assertTrue(b.isEqual(a), b + "("
                        + b.getClass().getSimpleName() + ")"
                        + " returns false erroneously from isEqual() on "
                        + a + " (" + a.getClass().getSimpleName() + ")");

                assertTrue(a.equals(b), a + "("
                        + a.getClass().getSimpleName() + ")"
                        + " returns false erroneously from equals() on "
                        + b + " (" + b.getClass().getSimpleName() + ")");
                assertTrue(b.equals(a), b + "("
                        + b.getClass().getSimpleName() + ")"
                        + " returns false erroneously from equals() on "
                        + a + " (" + a.getClass().getSimpleName() + ")");

                assertEquals(a.hashCode(), b.hashCode(), a + "("
                        + a.getClass().getSimpleName() + ")"
                        + " does not have the same hash code as "
                        + b + " (" + b.getClass().getSimpleName() + ")");
            }
        }
    }
}
