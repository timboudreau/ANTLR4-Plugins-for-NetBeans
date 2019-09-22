/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.language;

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

        s = AdhocColoring.concatenate(a, b);
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

        AdhocColoring.Combined s = new AdhocColoring.Combined(new AttributeSet[]{a, b, c, d});
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
}
