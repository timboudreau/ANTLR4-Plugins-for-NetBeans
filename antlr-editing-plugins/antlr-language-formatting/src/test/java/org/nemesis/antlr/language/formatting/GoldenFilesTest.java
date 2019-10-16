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
        gf.test(f, prefs, fileName, false);
    }

    @BeforeAll
    public static void setup() throws URISyntaxException {
        Path dir = ProjectTestHelper.relativeTo(GoldenFilesTest.class).projectBaseDir();
        gf = new GoldenFiles<>(dir.resolve("src/test/resources/org/nemesis/antlr/language/formatting"),
                G4FormatterStub.class, ALL_WHITESPACE);
    }
}
