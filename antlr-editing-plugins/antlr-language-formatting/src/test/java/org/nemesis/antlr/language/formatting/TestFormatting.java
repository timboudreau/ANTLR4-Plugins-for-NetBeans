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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.prefs.Preferences;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.ColonHandling;
import org.nemesis.antlrformatting.api.AntlrFormattingHarness;
import org.nemesis.antlrformatting.api.FormattingHarness;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import org.nemesis.simple.SampleFile;

/**
 *
 * @author Tim Boudreau
 */
public class TestFormatting {

    private static BiFunction<AntlrSampleFiles, BiConsumer<LexingStateBuilder<AntlrSampleFiles, ?>, FormattingRules>, FormattingHarness<AntlrSampleFiles>> hf;

    @Test
    public void testDotsInImportsNotSpaced() throws Exception {
        AntlrFormattingHarness<Preferences, AntlrCounters> harn = harness(new SF());

        harn.debugLogOn(ANTLRv4Lexer.DOT);
//        harn.debugLogOn(this::block);
//        harn.logFormattingActions();
        String fmt = harn.reformat(MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_AFTER,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, true,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, true,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 40
        ));
        System.out.println("fmt;\n" + fmt);
        assertTrue(fmt.contains("import org.antlr.v4.runtime.misc.IntegerList;"));
        assertTrue(fmt.contains("import org.antlr.v4.runtime.misc.IntegerStack;"));
        assertTrue(fmt.contains("import java.util.*;"));
        assertTrue(fmt.contains("import java.util.function.Supplier;"));
    }

    static class SF implements SampleFile<ANTLRv4Lexer, ANTLRv4Parser> {
        static String txt = "lexer grammar Thingamabob;\n"
                + "\n"
                + "import xidstart, xidcontinue, keywords, symbols, types;\n"
                + "\n"
                + "@header { import org.antlr.v4.runtime.misc.IntegerList;\n"
                + "          import org.antlr.v4.runtime.misc.IntegerStack;\n"
                + "          import java.util.*;\n"
                + "          import java.util.function.Supplier;}\n\nFoo='x';\n";

        @Override
        public CharStream charStream() throws IOException {
            return CharStreams.fromString(txt);
        }

        @Override
        public InputStream inputStream() {
            return new ByteArrayInputStream(txt.getBytes(UTF_8));
        }

        @Override
        public int length() throws IOException {
            return txt.getBytes(UTF_8).length;
        }

        @Override
        public ANTLRv4Lexer lexer() throws IOException {
            ANTLRv4Lexer lex = new ANTLRv4Lexer(charStream());
            lex.removeErrorListeners();
            return lex;
        }

        @Override
        public ANTLRv4Lexer lexer(ANTLRErrorListener l) throws IOException {
            ANTLRv4Lexer result = lexer();
            result.addErrorListener(l);
            return result;
        }

        @Override
        public ANTLRv4Parser parser() throws IOException {
            CommonTokenStream cts = new CommonTokenStream(lexer());
            ANTLRv4Parser parser = new ANTLRv4Parser(cts);
            parser.removeErrorListeners();
            return parser;
        }

        @Override
        public String text() throws IOException {
            return txt;
        }

        @Override
        public String fileName() {
            return "Thingamabob.g4";
        }
    }

    @Test
    public void test() throws IOException, Exception {
        AntlrFormattingHarness<Preferences, AntlrCounters> harn = harness(AntlrSampleFiles.SENSORS_PARSER);

//        harn.debugLogOn(this::block);
//        harn.logFormattingActions();
        System.out.println(harn.reformat(MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_AFTER,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, true,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, true,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 40
        )));
    }

    private boolean block(Token tok) {
        return "HEX".equals(tok.getText());
    }

//    @Test
//    public void test2() throws IOException {
//        ANTLRv4Lexer lexer = AntlrSampleFiles.RUST_PARSER.lexer();
//        boolean lastWasIt = false;
//        for (int i = 0;; i++) {
//            CommonToken tok = (CommonToken) lexer.nextToken();
//            if (tok.getType() == -1) {
//                break;
//            }
//            if (tok.getType() == ID_WS) {
//                continue;
//            }
//            int mode = lexer._mode;
//            String modeName = ANTLRv4Lexer.modeNames[mode];
//            String typeName = ANTLRv4Lexer.VOCABULARY.getSymbolicName(tok.getType());
//            boolean isSharp = "#".equals(tok.getText());
//            if (isSharp || lastWasIt || i == 2475 || i == 2476 || i == 2474 || i == 2325 || i == 2326) {
//                System.out.println(i + ". " + modeName + ": " + typeName + ": '"
//                        + Strings.escape(tok.getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE) + "'");
//                lastWasIt = isSharp;
//            }
//        }
//    }
    static AntlrFormattingHarness<Preferences, AntlrCounters> harness(SampleFile<ANTLRv4Lexer, ANTLRv4Parser> file) throws Exception {
        return new AntlrFormattingHarness<>(file, G4FormatterStub.class, AntlrCriteria.ALL_WHITESPACE);
    }

    static AntlrFormattingHarness<Preferences, AntlrCounters> xharness(SampleFile<ANTLRv4Lexer, ANTLRv4Parser> file) {
        return new AntlrFormattingHarness<>(file, AntlrCounters.class,
                new G4FormatterStub().toFormatterProvider("text/x-g4", AntlrCounters.class,
                        ANTLRv4Lexer.VOCABULARY, ANTLRv4Lexer.modeNames, ANTLRv4Lexer::new,
                        AntlrCriteria.ALL_WHITESPACE, ANTLRv4Parser.ruleNames, TestFormatting::rn));
//        });
    }

    static RuleNode rn(Lexer lexer) {
        CommonTokenStream str = new CommonTokenStream(lexer);
        ANTLRv4Parser parser = new ANTLRv4Parser(str);
        parser.removeErrorListeners();
        return parser.grammarFile();
    }
}
