/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

import java.util.function.Predicate;
import java.util.prefs.Preferences;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.ColonHandling;
import org.nemesis.antlr.language.formatting.config.OrHandling;
import org.nemesis.antlrformatting.api.AntlrFormattingHarness;

/**
 *
 * @author Tim Boudreau
 */
public class TestParensFormatting {

    @Test
    public void testOrs() throws Exception {
        AntlrFormattingHarness<Preferences, AntlrCounters> harn = harness(org.nemesis.antlr.sample.AntlrSampleFiles.MEGA_PARENTHESES);

//        harn.debugLogOn(AntlrCriteria.lineComments());
//        harn.debugLogOn(PLUS);
//        harn.debugLogOn(LPAREN, OR);
//        harn.debugLogOn(LPAREN, OR);
//        harn.debugLogOn(PLUS);
//        harn.debugLogOn(this::block);
//        harn.logFormattingActions();
        harn.debugLogOn(new Predicate<Token>(){
            int state = -1;
            public boolean test(Token tok) {
                if ("*".equals(tok.getText())) {
                    state = 0;
                }
                boolean result = false;
                if (state != -1) {
                    result = true;
                    state++;
                    if (state == 3) {
                        state = -1;
                    }
                }
                return result;
            }
        });

        String formatted
                = harn.reformat(MockPreferences.of(
                        AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_BEFORE,
                        AntlrFormatterConfig.KEY_FLOATING_INDENT, false,
                        AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false,
                        AntlrFormatterConfig.KEY_WRAP, false,
                        AntlrFormatterConfig.KEY_OR_HANDLING, OrHandling.INDENT,
                        AntlrFormatterConfig.KEY_REFLOW_LINE_COMMENTS, true,
                        AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, true,
                        AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, true,
                        AntlrFormatterConfig.KEY_MAX_LINE, 80
                ));
        System.out.println(formatted);
    }

    static AntlrFormattingHarness<Preferences, AntlrCounters> harness(org.nemesis.antlr.sample.AntlrSampleFiles file) throws Exception {
        return new AntlrFormattingHarness<>(file, G4FormatterStub.class, AntlrCriteria.ALL_WHITESPACE);
    }
}
