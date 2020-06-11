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

import org.nemesis.antlr.live.language.coloring.AdhocColorings;
import org.nemesis.antlr.live.language.coloring.AttrTypes;
import org.nemesis.antlr.live.language.coloring.AdhocColoring;
import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.Test;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocColoringsTest {

    private String mimeType = AdhocMimeTypes.mimeTypeForPath(Paths.get("/tmp/com/foo/Test.g4"));

    @Test
    public void testAttributes() throws Throwable {
        AdhocColorings colorings = new AdhocColorings();
        PCL pcl = new PCL();
        AdhocColoring c = colorings.add("foo", Color.yellow, AttrTypes.ACTIVE, AttrTypes.BACKGROUND, AttrTypes.BOLD);
        colorings.addPropertyChangeListener("foo", pcl);

        assertEquals(2, c.getAttributeCount());
        Set<Object> attrs = attrs(c);
        assertEquals(2, attrs.size());
        assertFalse(attrs.contains(StyleConstants.Foreground));
        assertTrue(attrs.contains(StyleConstants.Background));
        assertTrue(attrs.contains(StyleConstants.Bold));

        assertEquals(Color.yellow, c.color());
        assertTrue(c.isBackgroundColor());
        Object attr = c.getAttribute(StyleConstants.Background);
        assertNotNull(attr);
        assertEquals(Color.yellow, attr);

        assertTrue(c.containsAttribute(StyleConstants.Background, Color.yellow));
        assertFalse(c.containsAttribute(StyleConstants.Foreground, Color.yellow));

        assertTrue(colorings.setForeground("foo", true));
        pcl.assertFired("foo");
        assertTrue("Should now be set to foreground", c.isForegroundColor());
        assertFalse("Should not say it is a background color", c.isBackgroundColor());

        attr = c.getAttribute(StyleConstants.Background);
        assertNull(attr);
        attr = c.getAttribute(StyleConstants.Foreground);
        assertNotNull(attr);
        assertEquals(Color.yellow, attr);

        assertFalse(c.containsAttribute(StyleConstants.Background, Color.yellow));
        assertTrue(c.containsAttribute(StyleConstants.Foreground, Color.yellow));

        attrs = attrs(c);
        assertEquals(2, attrs.size());
        assertTrue(attrs.contains(StyleConstants.Foreground));
        assertFalse(attrs.contains(StyleConstants.Background));
        assertFalse(attrs.contains(StyleConstants.Italic));
        assertTrue(attrs.contains(StyleConstants.Bold));
        assertEquals(2, c.getAttributeCount());

        assertTrue(colorings.setFlag("foo", AttrTypes.ITALIC, true));
        pcl.assertFired("foo");
        assertFalse(colorings.setFlag("foo", AttrTypes.ITALIC, true));
        pcl.assertNotFired();

        attrs = attrs(c);
        assertTrue(attrs.contains(StyleConstants.Foreground));
        assertFalse(attrs.contains(StyleConstants.Background));
        assertTrue(attrs.contains(StyleConstants.Italic));
        assertTrue(attrs.contains(StyleConstants.Bold));

        assertEquals(3, c.getAttributeCount());
    }

    private static Set<Object> attrs(AttributeSet s) {
        Set<Object> result = new HashSet<>();
        Enumeration<?> en = s.getAttributeNames();
        while (en.hasMoreElements()) {
            result.add(en.nextElement());
        }
        return result;
    }

    @Test
    public void testColorings() throws IOException {
        AdhocColorings colorings = new AdhocColorings();
        AdhocColoring c = colorings.add("foo", Color.yellow, AttrTypes.ACTIVE, AttrTypes.BACKGROUND, AttrTypes.BOLD);
        System.out.println(c);
        assertNotNull(c);
        assertTrue(c.isActive());
        assertTrue(c.isBold());
        assertTrue(c.isBackgroundColor());
        assertFalse(c.isForegroundColor());
        assertFalse(c.isItalic());
        assertEquals(Color.yellow, c.color());
        assertTrue(colorings.contains("foo"));
        assertSame(c, colorings.get("foo"));

        AdhocColoring c1 = colorings.add("bar", Color.blue, AttrTypes.ACTIVE, AttrTypes.FOREGROUND);
        System.out.println(c1);
        assertNotNull(c1);
        assertTrue(c1.isActive());
        assertFalse(c1.isBold());
        assertFalse(c1.isBackgroundColor());
        assertTrue(c1.isForegroundColor());
        assertFalse(c1.isItalic());
        assertEquals(Color.blue, c1.color());
        assertTrue(colorings.contains("bar"));
        assertSame(c1, colorings.get("bar"));

        String line = c1.toLine();
        System.out.println("LINE: " + line);
        AdhocColoring reconstituted = AdhocColoring.parse(line);
        assertNotNull(reconstituted);
        assertEquals(c1, reconstituted);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        colorings.store(out);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        AdhocColorings nue = AdhocColorings.load(in);
        System.out.println("WROTE " + new String(out.toByteArray(), UTF_8));
        assertNotNull(nue);
        assertEquals(colorings, nue);

        c1.addFlag(AttrTypes.ITALIC);
        assertTrue(c1.isItalic());
        assertNotEquals(colorings, nue);

        PCL pcl = new PCL();
        colorings.addPropertyChangeListener(pcl);
        colorings.setColor("foo", Color.gray);
        pcl.assertFired("foo");
        colorings.setFlag("bar", AttrTypes.BOLD, true);
        pcl.assertFired("bar");
        colorings.setFlag("bar", AttrTypes.BOLD, true);
        pcl.assertNotFired();
        colorings.setFlag("bar", AttrTypes.BOLD, false);
        pcl.assertFired("bar");
    }

    static final class PCL implements PropertyChangeListener {

        void assertNotFired() {
            assertNull(last);
        }

        String last;

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            last = evt.getPropertyName();
        }

        void assertFired(String name) {
            String l = last;
            last = null;
            assertNotNull(l);
            assertEquals(name, l);
        }

    }
}
