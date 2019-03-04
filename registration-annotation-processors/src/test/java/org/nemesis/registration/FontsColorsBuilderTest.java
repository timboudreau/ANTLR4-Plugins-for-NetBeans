package org.nemesis.registration;

import java.io.ByteArrayInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.nemesis.registration.FontsColorsBuilder.ReadableFc;

/**
 *
 * @author Tim Boudreau
 */
public class FontsColorsBuilderTest {

    static final ARGBColor BLUE = ARGBColor.fromNullableString("blue");
    static final ARGBColor RED = ARGBColor.fromNullableString("red");
    static final ARGBColor GREEN = ARGBColor.fromNullableString("green");
    static final ARGBColor YELLOW = new ARGBColor(255, 245, 0, 67);

    @Test
    public void testXmlReadWrite() throws Throwable {
        FontsColorsBuilder fcb = new FontsColorsBuilder("hoogers");
        fcb.add("blueThing").setBackColor(BLUE).setItalic();
        fcb.add("redThing").setBackColor(RED).setForeColor(GREEN).setBold().setDefault("wookie");
        fcb.add("yellowThing").setForeColor(YELLOW);
        fcb.add("italThing").setItalic().setWaveUnderlineColor(GREEN);
        fcb.add("boldThing").setBold().setUnderlineColor(YELLOW);
        fcb.add("multiThing").setBold().setItalic().setUnderlineColor(BLUE)
                .setBackColor(RED).setWaveUnderlineColor(GREEN)
                .setForeColor(YELLOW).setDefault("wiggles");

        ReadableFc blue = fcb.getExisting("blueThing");
        assertNotNull(blue);
        Assertions.assertEquals(BLUE, blue.backColor());
        Assertions.assertEquals(blue.styles(), EnumSet.of(FontStyle.ITALIC), blue.toString());
        Assertions.assertNull(blue.foreColor(), blue.toString());
        Assertions.assertNull(blue.waveColor(), blue.toString());
        Assertions.assertNull(blue.underlineColor(), blue.toString());
        Assertions.assertNull(blue.defawlt(), blue.toString());

        ReadableFc red = fcb.getExisting("redThing");
        Assertions.assertEquals(RED, red.backColor(), red.toString());
        Assertions.assertEquals(red.styles(), EnumSet.of(FontStyle.BOLD), red.toString());
        Assertions.assertEquals(GREEN, red.foreColor(), red.toString());
        Assertions.assertEquals("wookie", red.defawlt(), red.toString());
        Assertions.assertNull(red.waveColor(), red.toString());
        Assertions.assertNull(red.underlineColor(), red.toString());

        ReadableFc yellow = fcb.getExisting("yellowThing");
        Assertions.assertEquals(YELLOW, yellow.foreColor(), yellow.toString());
        Assertions.assertEquals(yellow.styles(), EnumSet.noneOf(FontStyle.class), yellow.toString());
        Assertions.assertNull(yellow.backColor(), yellow.toString());
        Assertions.assertNull(yellow.waveColor(), yellow.toString());
        Assertions.assertNull(yellow.underlineColor(), yellow.toString());
        Assertions.assertNull(yellow.defawlt(), yellow.toString());

        ReadableFc ital = fcb.getExisting("italThing");
        Assertions.assertEquals(GREEN, ital.waveColor(), ital.toString());
        Assertions.assertEquals(ital.styles(), EnumSet.of(FontStyle.ITALIC), ital.toString());
        Assertions.assertNull(ital.foreColor(), ital.toString());
        Assertions.assertNull(ital.backColor(), ital.toString());
        Assertions.assertNull(ital.underlineColor(), ital.toString());
        Assertions.assertNull(ital.defawlt(), ital.toString());

        ReadableFc bold = fcb.getExisting("boldThing");
        Assertions.assertEquals(YELLOW, bold.underlineColor(), bold.toString());
        Assertions.assertEquals(bold.styles(), EnumSet.of(FontStyle.BOLD), bold.toString());
        Assertions.assertNull(bold.foreColor(), bold.toString());
        Assertions.assertNull(bold.backColor(), bold.toString());
        Assertions.assertNull(bold.waveColor(), bold.toString());
        Assertions.assertNull(bold.defawlt(), bold.toString());

        ReadableFc multi = fcb.getExisting("multiThing");
        Assertions.assertEquals(BLUE, multi.underlineColor());
        Assertions.assertEquals(RED, multi.backColor());
        Assertions.assertEquals(GREEN, multi.waveColor());
        Assertions.assertEquals(YELLOW, multi.foreColor());
        Assertions.assertEquals("wiggles", multi.defawlt());
        Assertions.assertEquals(multi.styles(), EnumSet.of(FontStyle.ITALIC, FontStyle.BOLD), multi.toString());

        String res = fcb.toString();

        FontsColorsBuilder b2 = new FontsColorsBuilder("hoogers");
        b2.loadExisting(new ByteArrayInputStream(res.getBytes(UTF_8)));

        assertEquals(fcb, b2);

        Assertions.assertEquals(fcb.toString(), b2.toString());
    }

    private void assertEquals(FontsColorsBuilder expect, FontsColorsBuilder got) {
        Assertions.assertEquals(expect.theme(), got.theme());
        Set<String> names = new HashSet<>(expect.names());
        names.addAll(got.names());
        for (String n : names) {
            ReadableFc afc = expect.getExisting(n);
            ReadableFc bfc = got.getExisting(n);
            assertEquals(n, afc, bfc);
        }

        Assertions.assertEquals(expect.names(), got.names());
    }

    private void assertEquals(String name, ReadableFc expect, ReadableFc got) {
        Assertions.assertEquals(expect.name(), got.name(), name + " name");
        Assertions.assertEquals(expect.backColor(), got.backColor(), name + " bg");
        Assertions.assertEquals(expect.foreColor(), got.foreColor(), name + " fg");
        Assertions.assertEquals(expect.underlineColor(), got.underlineColor(), name + " ul");
        Assertions.assertEquals(expect.waveColor(), got.waveColor(), name + " wave");
        Assertions.assertEquals(expect.defawlt(), got.defawlt(), name + " default");
        Assertions.assertEquals(expect.styles(), got.styles(), name + " styles");
    }
}
