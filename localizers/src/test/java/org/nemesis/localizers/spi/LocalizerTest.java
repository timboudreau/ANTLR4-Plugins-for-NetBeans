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
package org.nemesis.localizers.spi;

import com.mastfrog.mock.named.services.MockNamedServices;
import com.mastfrog.abstractions.Named;
import com.mastfrog.function.throwing.ThrowingRunnable;
import static com.mastfrog.util.collections.CollectionUtils.map;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.localizers.api.Localizers;
//import org.nemesis.localizers.api.Localizers;
import static org.nemesis.localizers.spi.Thing.THING_1;
import static org.nemesis.localizers.spi.Thing.THING_2;
import static org.nemesis.localizers.spi.ThingSubtype.THING_3;
import org.nemesis.localizers.spi.foo.OtherLocalizableInterface;
import org.nemesis.localizers.spi.otherpkg.LocalizedEnum;
import static org.nemesis.localizers.spi.otherpkg.LocalizedEnum.BOAT;
import static org.nemesis.localizers.spi.otherpkg.LocalizedEnum.CAR;
import static org.nemesis.localizers.spi.otherpkg.LocalizedEnum.PLANE;
import static org.nemesis.localizers.spi.otherpkg.LocalizedEnum.TRAIN;

/**
 *
 * @author Tim Boudreau
 */
public class LocalizerTest {

    private static ThrowingRunnable onShutdown;

    @Test
    public void testThing1SingletonLocalizer() {
        String t1 = Localizer.getDisplayName(THING_1);
        assertEquals("This is Thing One", t1);
        assertIcon("thing-1", Localizer.getIcon(THING_1), Color.RED);

        assertEquals(map("about").to("that").map("cat").to("hat").map("knows")
                .to("lot").map("otherHints").finallyTo("are also merged"),
                Localizer.getHints(THING_1));
    }

    @Test
    public void testThing2SingletonLocalizer() {
        String t2 = Localizer.getDisplayName(THING_2);
        assertEquals("This is Thing Two", t2);
        assertIcon("thing-2", Localizer.getIcon(THING_2), Color.BLUE);
    }

    @Test
    public void testThing3InterfaceLocalizer() {
        String t3 = Localizer.getDisplayName(THING_3);
        assertEquals("3-Thing localized by OtherLocalizer", t3);
        assertIcon("thing-2", Localizer.getIcon(THING_3), Color.YELLOW);
    }

    @Test
    public void testThing4SubtypeLocalizer() {
        String t4 = Localizer.getDisplayName(new Thing(4));
        assertEquals("4-Thing localized by OtherLocalizer", t4);
    }

    @Test
    public void testThingClassIsLocalized() {
        String t5 = Localizer.getDisplayName(Thing.class);
        assertEquals("The Type Of Things", t5);
        assertIcon("Thing.class", Localizer.getIcon(Thing.class), Color.GREEN);
    }

    @Test
    public void testStringClassIsLocalized() {
        String s1 = Localizer.getDisplayName(String.class);
        assertEquals("By Bundle Key", s1);
        assertIcon("String.class", Localizer.getIcon(String.class), Color.WHITE);
        assertEquals(map("string").to("type").map("hey").finallyTo("you"),
                Localizer.getHints(String.class));
    }

    @Test
    public void testToStringIsFallback() {
        String s2 = Localizer.getDisplayName(new StringBuilder("foo"));
        assertEquals("foo", s2);
        assertTrue(Localizer.getIcon(new StringBuilder("foo")) instanceof NoIcon);
    }

    @Test
    public void testNamedLocalizationUsedNameMethod() {
        String nm = Localizer.getDisplayName(new NamedThing("what"));
        assertEquals("Named what", nm);
    }

    @Test
    public void testNonLocalizedEnumsReturnName() {
        for (NotLocalized nl : NotLocalized.values()) {
            assertEquals(nl.name(), Localizer.getDisplayName(nl));
            assertSame(NoIcon.INSTANCE, Localizer.getIcon(nl));
            assertNotNull(Localizer.getHints(nl));
            assertTrue(Localizer.getHints(nl).isEmpty());
            assertEquals(nl.name(), Localizer.getDisplayName(nl));
        }
    }

    @Test
    public void testNull() {
        assertNotNull(Localizer.getDisplayName(null));
        assertNotNull(Localizer.getIcon(null));
        assertNotNull(Localizer.getImage(null));
        assertNotNull(Localizer.getHints(null));
        assertEquals(Bundle.noValue(), Localizer.getDisplayName(null));
        assertEquals(Bundle.noValue(), Localizers.displayName(null));
    }

    @Test
    public void testGenerated() {
        // This passes if run with test-single, but fails as part
        // of the main build - problem with maven-compiler-plugin

        String nm = Localizer.getDisplayName(LocalizedByAnnotation.FIELD);
        assertEquals("This is the localized name", nm);
    }

    @Test
    public void testGeneratedForEnum() {
        String tn = Localizer.getDisplayName(EnumLocalizedByAnnotation.class);
        for (EnumLocalizedByAnnotation v : EnumLocalizedByAnnotation.values()) {
            String name = Localizer.getDisplayName(v);
            Icon icon = Localizer.getIcon(v);
            Map<String, String> hints = Localizer.getHints(v);
            switch (v) {
                case PRAGUE:
                    assertEquals("Prague, Czech Republic", name);
                    // {countryCode=CZ, currency=CSK}
                    assertEquals(map("countryCode").to("CZ").map("currency").finallyTo("CSK"), hints);
                    assertTrue(icon instanceof ImageIcon);
                    break;
                case WASHINGTON:
                    assertEquals("Washington, District of Columbia, USA", name);
                    assertEquals(map("countryCode").to("US").map("currency").finallyTo("USD"), hints);
                    assertTrue(icon instanceof NoIcon);
            }
        }
        assertEquals("Places", tn);
    }

    @Test
    public void testEnumLocalization() {
        for (LocalizedEnum e : LocalizedEnum.values()) {
            Icon icon = Localizer.getIcon(e);
            String dn = Localizer.getDisplayName(e);
            Map<String, String> hints = Localizer.getHints(e);
            assertEquals(e.name(), hints.get("name"), "Hints not augmented");
            assertEquals("b", hints.get("a"), "Wrong hints");
            assertEquals("d", hints.get("c"), "Wrong hints");
            switch (e) {
                case BOAT:
                    assertTrue(icon instanceof ImageIcon,
                            "Should have loaded an image but got " + icon);
                    assertEquals("I will not eat them on a boat", dn,
                            "wrong display name for " + e);
                    break;
                case CAR:
                    assertFalse(icon instanceof ImageIcon,
                            "Should not have loaded an image but got " + icon);
                    assertEquals("I will not eat them in a car", dn,
                            "wrong display name for " + e);
                    break;
                case TRAIN:
                    assertFalse(icon instanceof ImageIcon,
                            "Should not have loaded an image but got " + icon);
                    assertEquals("I will not eat them on a train", dn,
                            "wrong display name for " + e);
                    break;
                case PLANE:
                    assertFalse(icon instanceof ImageIcon,
                            "Should not have loaded an image but got " + icon);
                    assertEquals("I will not eat them on a plane", dn,
                            "wrong display name for " + e);
                    break;
            }
        }
    }

    public static enum NotLocalized {
        FOO, BAR, BAZ
    }

    private static final String THING_LOC_PATH = "loc/instances/"
            + Thing.class.getName().replace('.', '/');

    private static final String OTHER_LOC_PATH = "loc/instances/"
            + OtherLocalizableInterface.class.getName().replace('.', '/');

    private static final String THING_TYPE_LOC_PATH = "loc/types/"
            + Thing.class.getName().replace('.', '/');
    private static final String STRING_TYPE_LOC_PATH = "loc/types/"
            + String.class.getName().replace('.', '/');
    private static final String ENUM_LOC_PATH = "loc/enums/"
            + LocalizedEnum.class.getName().replace('.', '/');
    private static final String GEN_LOC_PATH = "loc/instances/"
            + LocalizedByAnnotation.class.getName().replace('.', '/');

    @BeforeAll
    public static void configureServices() throws ClassNotFoundException {
        MockNamedServices.debug(GEN_LOC_PATH);
        onShutdown = MockNamedServices.builder()
                .add(THING_LOC_PATH, ThingOneLocalizer.class)
                .add(THING_LOC_PATH, ThingTwoLocalizer.class)
                .add(OTHER_LOC_PATH, OtherLocalizer.class)
                .add(THING_TYPE_LOC_PATH, ThingTypeLocalizer.class)
                .add(STRING_TYPE_LOC_PATH, StringByBundleKeyLocalizer.class)
                .add(ENUM_LOC_PATH, LocalizedEnumLocalizer.class)
                .add(GEN_LOC_PATH, LocalizedByAnnotation_FIELD_FieldLocalizer.class)
                .add("loc/enums/org/nemesis/localizers/spi/EnumLocalizedByAnnotation", EnumLocalizedByAnnotation_EnumConstantLocalizer.class)
                .add("loc/types/org/nemesis/localizers/spi/EnumLocalizedByAnnotation", EnumLocalizedByAnnotation_EnumLocalizer.class)
                .build();

    }

    @AfterAll
    public static void deconfigureServices() throws Exception {
        onShutdown.run();
    }

    public static class ThingTypeLocalizer
            extends SingleInstanceLocalizer<Class<?>> {

        public ThingTypeLocalizer() {
            super(Thing.class, "ThingType", null, "the", "type");
        }

        @Override
        protected Icon icon(Class<?> key) {
            return new FakeIcon(Color.GREEN);
        }
    }

    public static class ThingOneLocalizer
            extends SingleInstanceLocalizer<Thing> {

        public ThingOneLocalizer() {
            // This is what happens when you code, and also have a four-year-old.
            super(THING_1, "Thing1", null, "cat", "hat", "knows", "lot",
                    "about", "that");
        }

        @Override
        protected Icon icon(Thing key) {
            return new FakeIcon(Color.RED);
        }
    }

    public static class ThingTwoLocalizer
            extends SingleInstanceLocalizer<Thing> {

        public ThingTwoLocalizer() {
            super(THING_2, "Thing2", null, "cat", "hat", "knows", "lot",
                    "about", "this");
        }

        @Override
        protected Icon icon(Thing key) {
            return new FakeIcon(Color.BLUE);
        }
    }

    public static class OtherLocalizer
            extends Localizer<OtherLocalizableInterface> {

        public OtherLocalizer() {
        }

        @Override
        protected String displayName(OtherLocalizableInterface obj) {
            return obj.locInfo() + " localized by OtherLocalizer";
        }

        @Override
        protected Map<String, String> hints(OtherLocalizableInterface o) {
            Map<String, String> result = new HashMap<>();
            result.put("otherHints", "are also merged");
            return result;
        }

        @Override
        protected boolean matches(Object o) {
            return o instanceof OtherLocalizableInterface;
        }

        @Override
        protected Icon icon(OtherLocalizableInterface key) {
            return new FakeIcon(Color.YELLOW);
        }
    }

    public static class StringByBundleKeyLocalizer extends Localizer<Class<?>> {

        @Override
        protected String displayName(Class<?> obj) {
            return "org.nemesis.localizers.spi.bar.Bundle#ByBundleKey";
        }

        @Override
        protected Map<String, String> hints(Class<?> o) {
            Map<String, String> m = new HashMap<>();
            m.put("string", "type");
            m.put("hey", "you");
            return m;
        }

        @Override
        protected Icon icon(Class<?> key) {
            return new FakeIcon(Color.WHITE);
        }

        @Override
        protected boolean matches(Object o) {
            return String.class == o;
        }
    }

    public static class LocalizedEnumLocalizer
            extends EnumConstantLocalizer<LocalizedEnum> {

        public LocalizedEnumLocalizer() {
            super(LocalizedEnum.class, "a", "b", "c", "d");
        }

        @Override
        protected Map<String, String> hints(LocalizedEnum o) {
            Map<String, String> result = super.hints(o);
            assertFalse(result.isEmpty());
            assertEquals("b", result.get("a"), result::toString);
            assertEquals("d", result.get("c"), result::toString);
            result = new HashMap<>(result);
            result.put("name", o.name());
            return result;
        }
    }

    private static void assertIcon(String what, Icon icon, Color color) {
        if (icon instanceof FakeIcon) {
            Color c = ((FakeIcon) icon).color;
            assertEquals(color, c, what);
        } else {
            fail("Wrong icon type for " + what + ": " + icon);
        }
    }

    static final class FakeIcon implements Icon {

        final Color color;

        public FakeIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y, getIconWidth(), getIconHeight());
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }

        @Override
        public String toString() {
            return "FakeIcon(" + color.getRed() + "," + color.getGreen() + ","
                    + color.getBlue() + ")";
        }
    }

    public static class NamedThing implements Named {

        private final String what;

        public NamedThing(String what) {
            this.what = what;
        }

        public String toString() {
            return "A thing named " + what;
        }

        @Override
        public String name() {
            return "Named " + what;
        }
    }
}
