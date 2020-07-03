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

import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.nemesis.registration.NameAndMimeTypeUtils.MimeTypeValidator;

/**
 *
 * @author Tim Boudreau
 */
public class NameAndMimeTypeUtilsTest {

    @Test
    public void testMimeTypeValidation() {
        assertOk("text/plain");
        assertOk("text/xml+docbook");
        assertOk("text/x-g4");
        assertOk("text/x-g4;prefix=Antlr");
        assertNotOk("text");
        assertNotOk("text/\nhoobergoodles");
        assertNotOk("text/");
        assertNotOk("/");
        assertNotOk("");
        assertNotOk("//");
        assertNotOk("text/and/more", false);
        assertNotOk("text/and/more", true);
        assertOk("application/javascript+json", false);
        assertOk("application/javascript+json", true);
        assertWarned("application/javascript+json;charset=UTF-8", true);
        assertWarned("text/x-g4;ouvre=tubers;prefix=Wookie", true);
        assertWarned("text/x-g4;", false);
        assertWarned("text/x-g4;", true);
        assertNotOk("text/x-g4;prefix=Antlr Protruberances");
        assertNotOk("text/x-g4;prefix=Antlr Protruberances;");
        assertNotOk("text/x-g4;prefix=Antlr", false);
        assertNotOk("text/x-g4;prefix=Antlr=food;prefix=Wookie", true);
        assertNotOk("////");
        assertNotOk(";;");
        assertNotOk("//");
        assertNotOk(";/;/;", true, true);
        assertNotOk(";/;/;", false, false);
        assertNotOk("plork", false);
        assertNotOk("plork", true);
        assertNotOk("plork and/bork", true);
        assertNotOk("plork and/bork ", true);
        assertNotOk("plork/bork snork", true);
        assertNotOk(";prefix=Blamp");
        assertNotOk("prefix=Blamp");
        assertNotOk("/prefix=Blamp");
        assertNotOk("thisMimeTypeIsLegalAccordingToTheStandardButForNetBeansIf/ItRunsOverEightyCharactersThenAnExceptionWillBeThrownPrettyQuicklySoYouAreKindOfHosed");
        char[] cc = new char[70];
        Arrays.fill(cc, 'a');
        cc[35] = '/';
        assertOk(new String(cc) + ";prefix=ThePrefixMakesItTooLongButWeOnlyCountTheMainMimeType");

        char[] cd = new char[90];
        Arrays.fill(cd, 'b');
        cd[45] = '/';
        assertNotOk(new String(cd));

        assertNotOk(new String(new char[20]));

        char[] ce = new char[20];
        ce[10] = '/';
        assertNotOk(new String(ce));
    }

    @Test
    public void testExtractPrefix() {
        assertPrefix("text/x-g4;prefix=Antlr", "Antlr");
        assertPrefix("text/x-g4;prefix=Antlr;food=Poison", "Antlr");
        assertPrefix("text/x-g4;prefix=Antlr;prefix=Horns", "Antlr");
        assertPrefix("text/x-g4", "g4");
    }

    private void assertPrefix(String mime, String expect) {
        String result = NameAndMimeTypeUtils.prefixFromMimeType(mime);
        if (expect == null) {
            assertNull(result, "For " + Escaper.CONTROL_CHARACTERS.escape(mime) + "'");
        }
    }

    private void assertOk(String mime) {
        assertOk(mime, true);
    }

    private void assertOk(String mime, boolean complex) {
        String escaped = Escaper.CONTROL_CHARACTERS.escape(mime);
        List<String> fails = new ArrayList<>();
        List<String> warns = new ArrayList<>();
        MimeTypeValidator mtv = new MimeTypeValidator(complex, fails::add, warns::add);
        boolean result = mtv.test(mime);
        assertTrue(fails.isEmpty(), "Got " + fails.size() + " failure messages " + result + ": " + Strings.join(',', fails) + " for '" + escaped + "'");
        assertTrue(warns.isEmpty(), "Got " + warns.size() + " warning messages and result " + result + ": " + Strings.join(',', fails) + " for '" + escaped + "'");
        assertTrue(result, "Test returned false for '" + escaped + "'");
    }

    private void assertNotOk(String mime) {
        assertNotOk(mime, true);
    }

    private void assertNotOk(String mime, boolean complex) {
        assertNotOk(mime, complex, false);
    }
    private void assertNotOk(String mime, boolean complex, boolean expectWarn) {
        String escaped = Escaper.CONTROL_CHARACTERS.escape(mime);
        List<String> fails = new ArrayList<>();
        List<String> warns = new ArrayList<>();
        MimeTypeValidator mtv = new MimeTypeValidator(complex, fails::add, warns::add);
        boolean result = mtv.test(mime);
        assertFalse(fails.isEmpty(), "Got " + fails.size() + " failure messages " + result + ": " + Strings.join(',', fails) + " for '" + escaped + "'");
        assertEquals(expectWarn, !warns.isEmpty(), "Got " + warns.size() + " warning messages and result " + result + ": " + Strings.join(',', warns) + " for '" + escaped + "'");
        assertFalse(result, "Test returned true for '" + escaped + "'");
    }

    private void assertWarned(String mime, boolean complex) {
        String escaped = Escaper.CONTROL_CHARACTERS.escape(mime);
        List<String> fails = new ArrayList<>();
        List<String> warns = new ArrayList<>();
        MimeTypeValidator mtv = new MimeTypeValidator(complex, fails::add, warns::add);
        boolean result = mtv.test(mime);
        assertTrue(fails.isEmpty(), "Got " + fails.size() + " failure messages " + result + ": " + Strings.join(',', fails) + " for '" + escaped + "'");
        assertFalse(warns.isEmpty(), "Got " + warns.size() + " warning messages and result " + result + ": " + Strings.join(',', fails) + " for " + escaped + "'");
        assertTrue(result, "Test returned false for '" + escaped + "'");
    }

}
