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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.prefs.Preferences;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.IntStream;
import org.antlr.runtime.LegacyCommonTokenStream;
import org.antlr.runtime.TokenStream;
import org.antlr.v4.parse.GrammarASTAdaptor;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.tool.ErrorType;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.language.formatting.AntlrCriteria.ALL_WHITESPACE;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.ColonHandling;
import static org.nemesis.antlr.language.formatting.config.OrHandling.ALIGN_WITH_PARENTHESES;
import static org.nemesis.antlr.language.formatting.config.OrHandling.INDENT;
import org.nemesis.antlrformatting.api.GoldenFiles;
import org.nemesis.test.fixtures.support.ProjectTestHelper;

/**
 *
 * @author Tim Boudreau
 */
public class GoldenFilesTest {

    private static GoldenFiles<AntlrCounters, G4FormatterStub, ANTLRv4Lexer, Preferences> gf;

    @Test
    public void test() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_AFTER,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, true,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, true,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, false,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 40
        );
        testOne(AntlrSampleFiles.RUST_PARSER, prefs);
    }

    @Test
    public void test2() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
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

    @Test
    public void testLexerWithModes() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_AFTER,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, true,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, true,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, false,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 40
        );
        testOne(AntlrSampleFiles.MARKDOWN_LEXER, prefs);
    }

    @Test
    public void testLexerWithModes2() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.INLINE,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, false,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false,
                AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, true,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, true,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 80
        );
        testOne(AntlrSampleFiles.MARKDOWN_LEXER, prefs);
    }

    @Test
    public void testLexerWithModes3() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_BEFORE,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, false,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false,
                AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, true,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, true,
                AntlrFormatterConfig.KEY_WRAP, false,
                AntlrFormatterConfig.KEY_MAX_LINE, 80
        );
        testOne(AntlrSampleFiles.MARKDOWN_LEXER, prefs);
    }

    @Test
    public void testLexerWithModes4() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.STANDALONE,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, false,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false,
                AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, false,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, false,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 80
        );
        testOne(AntlrSampleFiles.MARKDOWN_LEXER, prefs);
    }


    @Test
    public void testLexerWithModes5() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_AFTER,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, true,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, true,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, false,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 40
        );
        testOne(AntlrSampleFiles.MARKDOWN_LEXER_2, prefs);
    }

    @Test
    public void testLexerWithModes6() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.INLINE,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, false,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false,
                AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, true,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, true,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 80
        );
        testOne(AntlrSampleFiles.MARKDOWN_LEXER_2, prefs);
    }

    @Test
    public void testLexerWithModes7() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_BEFORE,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, false,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false,
                AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, true,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, true,
                AntlrFormatterConfig.KEY_WRAP, false,
                AntlrFormatterConfig.KEY_MAX_LINE, 80
        );
        testOne(AntlrSampleFiles.MARKDOWN_LEXER_2, prefs);
    }

    @Test
    public void testLexerWithModes8() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.STANDALONE,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, false,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false,
                AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, false,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, false,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 80
        );
        testOne(AntlrSampleFiles.MARKDOWN_LEXER_2, prefs);
    }

    @Test
    public void testParserWithTokenVocab1() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.STANDALONE,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, false,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false,
                AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, false,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, false,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 80
        );
        testOne(AntlrSampleFiles.MARKDOWN_PARSER, prefs);
    }

    @Test
    public void testParserWithTokenVocab2() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_BEFORE,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, false,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false,
                AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, true,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, true,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 80
        );
        testOne(AntlrSampleFiles.MARKDOWN_PARSER, prefs);
    }

    @Test
    public void testOrFormattingAlign() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_BEFORE,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, false,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false,
                AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, true,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, false,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_OR_HANDLING, ALIGN_WITH_PARENTHESES,
                AntlrFormatterConfig.KEY_MAX_LINE, 80
        );
        testOne(AntlrSampleFiles.RUST_PARSER, prefs);
    }

    @Test
    public void testOrFormattingIndent() throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        MockPreferences prefs = MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_BEFORE,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, false,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false,
                AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, true,
                AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, true,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_OR_HANDLING, INDENT,
                AntlrFormatterConfig.KEY_MAX_LINE, 80
        );
        testOne(AntlrSampleFiles.RUST_PARSER, prefs);
        testOne(AntlrSampleFiles.MARKDOWN_LEXER_2, prefs);
    }

    private void testOne(AntlrSampleFiles f, MockPreferences prefs) throws IOException, DiffException, org.antlr.runtime.RecognitionException {
        String fileName = prefs.filename(f.name().toLowerCase(), "g4");
        String s = gf.test(f, prefs, fileName, false);
        doubleCheckLexing(f, s);
    }

    private void doubleCheckLexing(AntlrSampleFiles f, String formatted) throws IOException, org.antlr.runtime.RecognitionException {

        if (formatted.contains("modePARAGRAPH")) {
            int ix = formatted.indexOf("modePARAGRAPH");
            while (ix > 0 && formatted.charAt(ix) != '\n') {
                ix--;
            }
            fail("Mode statements were mangled:\n"
                    + formatted.substring(ix, Math.min(formatted.length(), ix + 30)));
        }

        ANTLRv4Lexer lex = f.lexer(new AL());
        lex.setInputStream(CharStreams.fromString(formatted, f.name()));
        for (Token t = lex.nextToken(); t.getType() != -1; t = lex.nextToken()) {
            // do nothing
        }
        // Also test with the real lexer from inside Antlr itself
        byte[] b = formatted.getBytes(UTF_8);
        InputStream bytesIn = new ByteArrayInputStream(b);
        org.antlr.runtime.ANTLRInputStream in = new org.antlr.runtime.ANTLRInputStream(bytesIn, "UTF-8");
        Lex lex2 = new Lex(in);
        for (org.antlr.runtime.Token t = lex2.nextToken(); t.getType() != -1; t = lex2.nextToken()) {
            // do nothing
        }
        bytesIn = new ByteArrayInputStream(b);
        in = new org.antlr.runtime.ANTLRInputStream(bytesIn, "UTF-8");
        lex2 = new Lex(in);

        Par p = new Par(
                new LegacyCommonTokenStream(lex2, 0));

        // XXX this call never exits
        // Something wrong with the legacy stream we are using
//        p.grammarSpec();
    }

    static final class Par extends org.antlr.v4.parse.ANTLRParser {

        @SuppressWarnings("OverridableMethodCallInConstructor")
        public Par(TokenStream input) {
            super(input);
            setTreeAdaptor(new GrammarASTAdaptor());
        }

        @Override
        public void reportError(org.antlr.runtime.RecognitionException e) {
//            throw new AssertionError(e);
            super.reportError(e);
        }

        @Override
        public void grammarError(ErrorType etype, org.antlr.runtime.Token token, Object... args) {
            throw new AssertionError("GrammarError " + etype + " on " + token);
        }

        @Override
        protected Object getMissingSymbol(IntStream input, org.antlr.runtime.RecognitionException e, int expectedTokenType, org.antlr.runtime.BitSet follow) {
            throw new AssertionError("MissingSymbol expected type "
                    + expectedTokenType, e);
        }

    }

    static class Lex extends org.antlr.v4.parse.ANTLRLexer {

        public Lex(CharStream input) {
            super(input);
        }

        @Override
        public void grammarError(ErrorType etype, org.antlr.runtime.Token token, Object... args) {
            fail("Error " + etype + " on " + token + " aargs " + Arrays.toString(args));
        }
    }

    static class AL implements ANTLRErrorListener {

        @Override
        public void syntaxError(Recognizer<?, ?> rcgnzr, Object o, int i, int i1, String string, RecognitionException re) {
            AssertionError err = new AssertionError("Synax err at " + i + ":" + i1 + " with " + o);
            if (re != null) {
                err.addSuppressed(re);
            }
            throw err;
        }

        @Override
        public void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean bln, BitSet bitset, ATNConfigSet atncs) {
        }

        @Override
        public void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitset, ATNConfigSet atncs) {
        }

        @Override
        public void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atncs) {
        }
    }

    @BeforeAll
    public static void setup() throws URISyntaxException {
        Path dir = ProjectTestHelper.relativeTo(GoldenFilesTest.class).projectBaseDir();
        gf = new GoldenFiles<>(dir.resolve("src/test/resources/org/nemesis/antlr/language/formatting"),
                G4FormatterStub.class, ALL_WHITESPACE);
    }
}
