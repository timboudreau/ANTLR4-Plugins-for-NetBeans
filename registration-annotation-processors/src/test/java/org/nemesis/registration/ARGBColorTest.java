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
package org.nemesis.registration;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.TreeSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class ARGBColorTest {

    @Test
    public void testOmittingHex() throws Throwable {
        ARGBColor color = new ARGBColor(10, 20, 30, 255);
        assertEquals(6, color.toString().length());
        assertEquals("ff" + color.toString(), color.toHexString(false));
        assertEquals(color, new ARGBColor(color.toString()));
        assertEquals(color, new ARGBColor(color.toHexString(false)));
    }

    @Test
    public void testConstants() throws IllegalArgumentException, IllegalAccessException {
        Set<String> all = new TreeSet<>();
        for (Field f : ARGBColor.class.getDeclaredFields()) {
            f.setAccessible(true);
            if (f.getType() == String.class) {
                String val = (String) f.get(null);
                if (!"alpha".equals(val) && val.indexOf('_') < 0) {
                    all.add(val);
                }
            }
        }
        for (String c : all) {
            ARGBColor color = new ARGBColor(c);
            assertEquals(c, color.toString());
            assertEquals(255, color.getAlpha());
            ARGBColor nue = new ARGBColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            assertEquals(color, nue);

            ARGBColor c1 = new ARGBColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() - 1);
            assertNotEquals(c, c1.toString());
            assertNotEquals(color, c1);
        }
    }

    @Test
    public void testSomeMethod() {
        int inc = 10;
        for (int a = 0; a <= 255; a += inc) {
            for (int r = 0; r <= 255; r += inc) {
                for (int g = 0; g <= 255; g += inc) {
                    for (int b = 0; b <= 255; b += inc) {
                        ARGBColor color = new ARGBColor(r, g, b, a);
                        assertEquals(r, color.getRed());
                        assertEquals(g, color.getGreen());
                        assertEquals(b, color.getBlue());
                        assertEquals(a, color.getAlpha());
                        assertEquals(color, color);
                        String str = color.toString();
                        assertNotNull(str);
                        ARGBColor parsed = new ARGBColor(str);
                        assertEquals(color, parsed, () -> parsed.toHexString() + " expected " + color.toHexString());
                        assertEquals(color.hashCode(), parsed.hashCode());
                        ARGBColor fromArgbArray = new ARGBColor(color.toARGB());
                        assertEquals(color, fromArgbArray);
                        ARGBColor fromRgbaArray = ARGBColor.fromRGBA(color.toRGBA());
                        assertEquals(color, fromRgbaArray);
                    }
                }
            }
        }
    }
}
