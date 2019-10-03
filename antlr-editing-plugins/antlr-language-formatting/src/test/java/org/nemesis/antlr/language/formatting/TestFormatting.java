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

        harn.debugLogOn(this::block);
//        harn.logFormattingActions();
        System.out.println(harn.reformat(MockPreferences.of(
                AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_AFTER,
                AntlrFormatterConfig.KEY_FLOATING_INDENT, true,
                AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, true,
                AntlrFormatterConfig.KEY_WRAP, true,
                AntlrFormatterConfig.KEY_MAX_LINE, 40
        )));
    }

    private boolean seen;
    private boolean justSeen;

    private boolean block(Token tok) {
        return "HEX".equals(tok.getText());
    }

    /*
    @Test
    public void test2() throws IOException {
        ANTLRv4Lexer lexer = AntlrSampleFiles.RUST_PARSER.lexer();
        for (int i = 0;; i++) {
            CommonToken tok = (CommonToken) lexer.nextToken();
            if (tok.getType() == -1) {
                break;
            }
            int mode = lexer._mode;
            String modeName = ANTLRv4Lexer.modeNames[mode];
            String typeName = ANTLRv4Lexer.VOCABULARY.getSymbolicName(tok.getType());
            System.out.println(i + ". " + modeName + ": " + typeName + ": '"
                    + Strings.escape(tok.getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE) + "'");
        }
    }
    */

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
