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

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.prefs.Preferences;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.ColonHandling;
import org.nemesis.antlrformatting.api.AntlrFormattingHarness;
import org.nemesis.antlrformatting.api.FormattingHarness;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;

/**
 *
 * @author Tim Boudreau
 */
public class TestFormatting {

    private static BiFunction<AntlrSampleFiles, BiConsumer<LexingStateBuilder<AntlrSampleFiles, ?>, FormattingRules>, FormattingHarness<AntlrSampleFiles>> hf;

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

    static AntlrFormattingHarness<Preferences, AntlrCounters> harness(AntlrSampleFiles file) throws Exception {
        return new AntlrFormattingHarness<>(file, G4FormatterStub.class, AntlrCriteria.ALL_WHITESPACE);
    }

    static AntlrFormattingHarness<Preferences, AntlrCounters> xharness(AntlrSampleFiles file) {
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
