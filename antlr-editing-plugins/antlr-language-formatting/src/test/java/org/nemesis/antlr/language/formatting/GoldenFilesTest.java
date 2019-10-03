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
package org.nemesis.antlr.language.formatting;

import com.github.difflib.algorithm.DiffException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.language.formatting.AntlrCriteria.ALL_WHITESPACE;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.ColonHandling;
import org.nemesis.antlrformatting.api.GoldenFiles;
import org.nemesis.test.fixtures.support.ProjectTestHelper;

/**
 *
 * @author Tim Boudreau
 */
public class GoldenFilesTest {

    private static GoldenFiles<AntlrCounters, G4FormatterStub, ANTLRv4Lexer, Preferences> gf;

    @Test
    public void test() throws IOException, DiffException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_AFTER,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, true,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, true,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 40
        );
        testOne(AntlrSampleFiles.RUST_PARSER, prefs);
    }

    @Test
    public void test2() throws IOException, DiffException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.INLINE,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, false,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false,
                AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, true,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, true,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 80
        );
        testOne(AntlrSampleFiles.RUST_PARSER, prefs);
    }

    private void testOne(AntlrSampleFiles f, MockPreferences prefs) throws IOException, DiffException {
        String fileName = prefs.filename(f.name().toLowerCase(), "g4");
        gf.go(f, prefs, fileName, false);
    }

    @BeforeAll
    public static void setup() throws URISyntaxException {
        Path dir = ProjectTestHelper.relativeTo(GoldenFilesTest.class).projectBaseDir();
        gf = new GoldenFiles<>(dir.resolve("src/test/resources/org/nemesis/antlr/language/formatting"),
                G4FormatterStub.class, ALL_WHITESPACE);
    }
}
