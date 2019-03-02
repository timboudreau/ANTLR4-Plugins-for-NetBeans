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
