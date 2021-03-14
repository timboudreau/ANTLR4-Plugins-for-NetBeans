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
package org.nemesis.antlrformatting.api;

import com.mastfrog.antlr.utils.Criteria;
import com.mastfrog.antlr.utils.Criterion;
import com.mastfrog.util.collections.IntList;
import java.io.IOException;
import java.util.prefs.Preferences;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlrformatting.spi.AntlrFormatterProvider;
import org.nemesis.simple.SampleFiles;
import org.nemesis.simple.language.SimpleLanguageLexer;
import org.nemesis.simple.language.SimpleLanguageParser;
import org.netbeans.modules.editor.indent.spi.Context;
import org.openide.util.NbPreferences;

/**
 *
 * @author Tim Boudreau
 */
public class TokenCountCacheTest {

    private static final Criteria criteria = Criteria.forVocabulary(SimpleLanguageLexer.VOCABULARY);
    private static final Criterion WS = criteria.anyOf(SimpleLanguageLexer.S_WHITESPACE);
    private static final Criterion OBRACES = criteria.anyOf(SimpleLanguageLexer.S_OPEN_BRACE);
    private static final Criterion CBRACES = criteria.anyOf(SimpleLanguageLexer.S_CLOSE_BRACE);
    private static final Criterion IDS = criteria.anyOf(SimpleLanguageLexer.ID);
    private TkTf tktf;
    private static final Criterion[] all;

    static {
        all = new Criterion[SimpleLanguageLexer.VOCABULARY.getMaxTokenType() - 1];
        for (int i = 0; i < all.length; i++) {
            all[i] = criteria.matching(i + 1);
        }
    }

    @Test
    public void test() throws IOException {
        SampleFiles f = SampleFiles.MUCH_NESTING;
        String formatted = tktf.reformattedString(f.text(), 0, f.length(), null);
        System.out.println("Formatted to\n" + formatted);
        assertTrue(tktf.action.count > 0);
    }

    @Test
    public void testRangeBoundaryConditionsInSingleElementList() {
        IntList il = IntList.createFrom(20);

        int x = TokenCountCache.countValuesBetween(20, true, 20, true, il);
        assertEquals(1, x, "Wrong value for inclusive/inclusive " + 20 + " to " + 20 + ": " + x + " in " + il);

        for (int i = 0; i < 20; i++) {
            int ct;

            ct = TokenCountCache.countValuesBetween(0, true, i, true, il);
            assertEquals(0, ct, "Wrong value for inclusive/inclusive " + 0 + " to " + i + ": " + ct + " in " + il);

            ct = TokenCountCache.countValuesBetween(0, true, i, false, il);
            assertEquals(0, ct, "Wrong value for inclusive/exclusive " + 0 + " to " + i + ": " + ct + " in " + il);

            ct = TokenCountCache.countValuesBetween(0, false, i, true, il);
            assertEquals(0, ct, "Wrong value for exclusive/inclusive " + 0 + " to " + i + ": " + ct + " in " + il);

            ct = TokenCountCache.countValuesBetween(0, false, i, false, il);
            assertEquals(0, ct, "Wrong value for exclusive/exclusive " + 0 + " to " + i + ": " + ct + " in " + il);

            ct = TokenCountCache.countValuesBetween(21, true, i + 22, true, il);
            assertEquals(0, ct, "Wrong value for inclusive/inclusive " + 21 + " to " + (i + 22) + ": " + ct + " in " + il);

            ct = TokenCountCache.countValuesBetween(21, true, i + 22, false, il);
            assertEquals(0, ct, "Wrong value for inclusive/exclusive " + 21 + " to " + (i + 22) + ": " + ct + " in " + il);

            ct = TokenCountCache.countValuesBetween(21, false, i + 22, true, il);
            assertEquals(0, ct, "Wrong value for exclusive/inclusive " + 21 + " to " + (i + 22) + ": " + ct + " in " + il);

            ct = TokenCountCache.countValuesBetween(21, false, i + 22, false, il);
            assertEquals(0, ct, "Wrong value for exclusive/exclusive " + 21 + " to " + (i + 22) + ": " + ct + " in " + il);
        }
        for (int i = 0, j = 40; i <= 20; i++, j--) {
            int ct = TokenCountCache.countValuesBetween(i, true, j, true, il);
            assertEquals(1, ct, "Wrong value for inclusive/inclusive " + i + " to " + j + ": " + ct + " in " + il);
        }
        int ct = TokenCountCache.countValuesBetween(0, true, 20, true, il);
        assertEquals(1, ct, "Wrong value for inclusive/inclusive " + 0 + " to " + 20 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(0, false, 20, true, il);
        assertEquals(1, ct, "Wrong value for exclusive/inclusive " + 0 + " to " + 20 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(0, false, 20, false, il);
        assertEquals(0, ct, "Wrong value for exclusive/exclusive " + 0 + " to " + 20 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(0, true, 20, false, il);
        assertEquals(0, ct, "Wrong value for inclusive/exclusive " + 0 + " to " + 20 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(19, true, 20, true, il);
        assertEquals(1, ct, "Wrong value for inclusive/inclusive " + 19 + " to " + 20 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(19, true, 20, false, il);
        assertEquals(0, ct, "Wrong value for inclusive/exclusive " + 19 + " to " + 20 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(19, false, 20, true, il);
        assertEquals(1, ct, "Wrong value for exclusive/inclusive " + 19 + " to " + 20 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(19, false, 20, false, il);
        assertEquals(0, ct, "Wrong value for exclusive/exclusive " + 19 + " to " + 20 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(19, true, 21, true, il);
        assertEquals(1, ct, "Wrong value for inclusive/inclusive " + 19 + " to " + 21 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(19, true, 21, false, il);
        assertEquals(1, ct, "Wrong value for inclusive/exclusive " + 19 + " to " + 21 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(19, false, 21, true, il);
        assertEquals(1, ct, "Wrong value for exclusive/inclusive " + 19 + " to " + 21 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(19, false, 21, false, il);
        assertEquals(1, ct, "Wrong value for exclusive/exclusive " + 19 + " to " + 21 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(20, true, 21, true, il);
        assertEquals(1, ct, "Wrong value for inclusive/inclusive " + 20 + " to " + 21 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(20, true, 21, false, il);
        assertEquals(1, ct, "Wrong value for inclusive/exclusive " + 20 + " to " + 21 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(20, false, 21, true, il);
        assertEquals(0, ct, "Wrong value for exclusive/inclusive " + 20 + " to " + 21 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(20, false, 21, false, il);
        assertEquals(0, ct, "Wrong value for exclusive/exclusive " + 20 + " to " + 21 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(20, true, 100, true, il);
        assertEquals(1, ct, "Wrong value for inclusive/inclusive " + 19 + " to " + 100 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(20, true, 100, false, il);
        assertEquals(1, ct, "Wrong value for inclusive/exclusive " + 19 + " to " + 100 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(20, false, 100, true, il);
        assertEquals(0, ct, "Wrong value for exclusive/inclusive " + 19 + " to " + 100 + ": " + ct + " in " + il);

        ct = TokenCountCache.countValuesBetween(20, false, 100, false, il);
        assertEquals(0, ct, "Wrong value for exclusive/exclusive " + 19 + " to " + 100 + ": " + ct + " in " + il);
    }

    @Test
    public void testRangeContainment() {
        IntList il = IntList.createFrom(10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
        for (int x = 1; x < 10; x++) {
            // Test ranges inside and outside the list bounds, which are all between
            // entries and should all return 0
            for (int i = 0; i < 110; i += 10) {
                for (int j = i + 1; j < i + 4; j++) {
                    int ct = TokenCountCache.countValuesBetween(j, true, j + 4, true, il);
                    assertEquals(0, ct, "Wrong value for inclusive/inclusive " + j + " to " + (j + 4) + ": " + ct + " in " + il);

                    ct = TokenCountCache.countValuesBetween(j, false, j + 4, true, il);
                    assertEquals(0, ct, "Wrong value for exclusive/inclusive " + j + " to " + (j + 4) + ": " + ct + " in " + il);

                    ct = TokenCountCache.countValuesBetween(j, true, j + 4, false, il);
                    assertEquals(0, ct, "Wrong value for inclusive/exclusive " + j + " to " + (j + 4) + ": " + ct + " in " + il);

                    ct = TokenCountCache.countValuesBetween(j, false, j + 4, false, il);
                    assertEquals(0, ct, "Wrong value for exclusive/exclusive " + j + " to " + (j + 4) + ": " + ct + " in " + il);
                }
            }
            // Now test ranges that all straddle one element
            for (int i = 5; i < 105; i += 10) {
                int ct = TokenCountCache.countValuesBetween(i, true, i + 10, true, il);
                assertEquals(1, ct, "Wrong value for inclusive/inclusive " + i + " to " + (i + 10) + ": " + ct + " in " + il);

                ct = TokenCountCache.countValuesBetween(i, false, i + 10, true, il);
                assertEquals(1, ct, "Wrong value for exclusive/inclusive " + i + " to " + (i + 10) + ": " + ct + " in " + il);

                ct = TokenCountCache.countValuesBetween(i, true, i + 10, false, il);
                assertEquals(1, ct, "Wrong value for inclusive/exclusive " + i + " to " + (i + 10) + ": " + ct + " in " + il);

                ct = TokenCountCache.countValuesBetween(i, false, i + 10, false, il);
                assertEquals(1, ct, "Wrong value for exclusive/exclusive " + i + " to " + (i + 10) + ": " + ct + " in " + il);
            }
            // Now test ranges that land on exact boundaries
            for (int i = 10; i < 100; i += 10) {
                int ct = TokenCountCache.countValuesBetween(i, true, i + 10, true, il);
                assertEquals(2, ct, "Wrong value for inclusive/inclusive " + i + " to " + (i + 10) + ": " + ct + " in " + il);

                ct = TokenCountCache.countValuesBetween(i, false, i + 10, true, il);
                assertEquals(1, ct, "Wrong value for exclusive/inclusive " + i + " to " + (i + 10) + ": " + ct + " in " + il);

                ct = TokenCountCache.countValuesBetween(i, true, i + 10, false, il);
                assertEquals(1, ct, "Wrong value for inclusive/exclusive " + i + " to " + (i + 10) + ": " + ct + " in " + il);

                ct = TokenCountCache.countValuesBetween(i, false, i + 10, false, il);
                assertEquals(0, ct, "Wrong value for exclusive/exclusive " + i + " to " + (i + 10) + ": " + ct + " in " + il);
            }
            // Now progressively straddle smaller ranges
            for (int i = 10, j = 100; i < 50 && j > 50; i += 10, j -= 10) {
                int expected = ((j - i) / 10) + 1;
                int ct = TokenCountCache.countValuesBetween(i, true, j, true, il);
                assertEquals(expected, ct, "Wrong value for inclusive/inclusive " + i + " to " + j + ": " + ct + " in " + il);

                ct = TokenCountCache.countValuesBetween(i, false, j, true, il);
                assertEquals(expected - 1, ct, "Wrong value for exclusive/inclusive " + i + " to " + j + ": " + ct + " in " + il);

                ct = TokenCountCache.countValuesBetween(i, true, j, false, il);
                assertEquals(expected - 1, ct, "Wrong value for inclusive/exclusive " + i + " to " + j + ": " + ct + " in " + il);

                ct = TokenCountCache.countValuesBetween(i, false, j, false, il);
                assertEquals(expected - 2, ct, "Wrong value for exclusive/exclusive " + i + " to " + j + ": " + ct + " in " + il);

                for (int k = 1; k < 9; k++) {
                    ct = TokenCountCache.countValuesBetween(i - k, true, j + k, true, il);
                    assertEquals(expected, ct, "Wrong value for inclusive/inclusive " + (i - k) + " to " + (j + k) + ": " + ct + " in " + il);

                    ct = TokenCountCache.countValuesBetween(i - k, false, j + k, true, il);
                    assertEquals(expected, ct, "Wrong value for exclusive/inclusive " + (i - k) + " to " + (j + k) + ": " + ct + " in " + il);

                    ct = TokenCountCache.countValuesBetween(i - k, true, j + k, false, il);
                    assertEquals(expected, ct, "Wrong value for inclusive/exclusive " + (i - k) + " to " + (j + k) + ": " + ct + " in " + il);

                    ct = TokenCountCache.countValuesBetween(i - k, false, j + k, false, il);
                    assertEquals(expected, ct, "Wrong value for inclusive/exclusive " + (i - k) + " to " + (j + k) + ": " + ct + " in " + il);
                }
            }
            for (int i = 10; i < 100; i += 10) {
                int start = i - 5;
                int end = i + 15;
                int ct = TokenCountCache.countValuesBetween(start, true, end, true, il);
                assertEquals(2, ct, "Wrong value for inclusive/exclusive " + start + " to " + end + ": " + ct + " in " + il);
            }
            // Ensure odd or even length of array does not affect results (it
            // will affect how binary search carves up the search space)
            il.add(x % 2 == 0 ? 1000 * x : -1000 * x);
            il.sort();
        }
        int count = TokenCountCache.countValuesBetween(0, true, 10, true, il);
        assertEquals(1, count);

        count = TokenCountCache.countValuesBetween(0, true, 10, false, il);
        assertEquals(0, count);
    }

    @BeforeEach
    public void setup() {
        tktf = new TkTf();
    }

    static class TkTf extends AntlrFormatterProvider<Preferences, TkCounters> {

        CheckingAction action = new CheckingAction();

        public TkTf() {
            super(TkCounters.class);
        }

        @Override
        protected String[] parserRuleNames() {
            return SimpleLanguageParser.ruleNames;
        }

        @Override
        protected Criterion whitespace() {
            return WS;
        }

        @Override
        protected int indentSize(Preferences config) {
            return 4;
        }

        @Override
        protected Preferences configuration(Context ctx) {
            return NbPreferences.forModule(TokenCountCacheTest.class);
        }

        @Override
        protected Lexer createLexer(CharStream stream) {
            return new SimpleLanguageLexer(stream);
        }

        @Override
        protected Vocabulary vocabulary() {
            return SimpleLanguageLexer.VOCABULARY;
        }

        @Override
        protected String[] modeNames() {
            return SimpleLanguageLexer.modeNames;
        }

        @Override
        protected void configure(LexingStateBuilder<TkCounters, ?> stateBuilder, FormattingRules rules, Preferences config) {
            rules.onTokenType(Criterion.ALWAYS).format(action);
            stateBuilder.increment(TkCounters.BRACE_DEPTH).onTokenType(criteria.matching(SimpleLanguageLexer.S_OPEN_BRACE))
                    .decrementingBeforeProcessingTokenWhenTokenEncountered(criteria.matching(SimpleLanguageLexer.S_CLOSE_BRACE));
            stateBuilder.increment(TkCounters.WORD_COUNT).onTokenType(criteria.matching(SimpleLanguageLexer.ID))
                    .decrementingWhenTokenEncountered(-1);
        }
    }

    static class CheckingAction implements FormattingAction {

        int count = 0;

        @Override
        public void accept(Token token, FormattingContext ctx, LexingState state) {
            FormattingContextImpl c = (FormattingContextImpl) ctx;
            if (token.getType() != -1) {
                checkToken(token, c, state);
            }
        }

        private void checkToken(Token token, FormattingContextImpl ctx, LexingState state) {
            count++;

            for (int i = 0; i < all.length; i++) {
                if (all[i].equals(WS)) {
                    continue;
                }
                assertNotNull(all[i], "Item " + i + " in array is null");
                int revIdCount = ctx.countBackwardOccurrencesUntilPrevious(IDS, all[i]);
                int defaultRevIdCount = ctx.defaultCountBackwardOccurrencesUntilPrevious(IDS, all[i]);
                assertEquals(defaultRevIdCount, revIdCount,
                        "countBackwardOccurrencesUntilPrevious wrong " + token.getTokenIndex()
                        + ": " + token + " with " + all[i]);
                int idCount = ctx.countForwardOccurrencesUntilNext(IDS, all[i]);
                int defaultIdCount = ctx.defaultCountForwardOccurrencesUntilNext(IDS, all[i]);
                assertEquals(defaultIdCount, idCount, "countForwardOccurrencesUntilNext wrong "
                        + token.getTokenIndex() + ": " + token + " with " + all[i]);
            }
            int idCount = ctx.countForwardOccurrencesUntilNext(IDS, CBRACES);
            int defaultIdCount = ctx.defaultCountForwardOccurrencesUntilNext(IDS, CBRACES);
            assertEquals(defaultIdCount, idCount, "countForwardOccurrencesUntilNext wrong "
                    + token.getTokenIndex() + ": " + token);

            int revIdCount = ctx.countBackwardOccurrencesUntilPrevious(IDS, CBRACES);
            int defaultRevIdCount = ctx.defaultCountBackwardOccurrencesUntilPrevious(IDS, CBRACES);
            assertEquals(defaultRevIdCount, revIdCount,
                    "countBackwardOccurrencesUntilPrevious wrong " + token.getTokenIndex()
                    + ": " + token);

            int nextCbrace = ctx.tokenCountToNext(null, true, CBRACES);
            int defaultNextCbrace = ctx.defaultTokenCountToNext(null, true, CBRACES);
            assertEquals(defaultNextCbrace, nextCbrace, "Different token count to next at "
                    + token.getTokenIndex() + ": " + token);

            int defaultNextCbraceWithWs = ctx.defaultTokenCountToNext(null, false, CBRACES);
            int nextCbraceWithWs = ctx.tokenCountToNext(null, false, CBRACES);
            assertEquals(defaultNextCbraceWithWs, nextCbraceWithWs,
                    "Different token count w/ whitespace to next at "
                    + token.getTokenIndex() + ": " + token);

            int revNextObrace = ctx.tokenCountToPreceding(null, true, OBRACES);
            int defaultRevNextObrace = ctx.defaultTokenCountToPreceding(null, true, OBRACES);
            assertEquals(defaultRevNextObrace, revNextObrace, "Different tokenCountToPreceding at "
                    + token.getTokenIndex() + ": " + token);

            int revNextObraceWithWs = ctx.tokenCountToPreceding(null, false, OBRACES);
            int defaultRevNextObraceWithWs = ctx.defaultTokenCountToPreceding(null, false, OBRACES);
            assertEquals(defaultRevNextObraceWithWs, revNextObraceWithWs,
                    "Different tokenCountToPreceding w/ whitespace at "
                    + token.getTokenIndex() + ": " + token);

            if (token.getTokenIndex() > 0) {
                if ("thing".equals(token.getText())) {
                    ctx.replace(" boogaBoogaWooHooWoovles");
                } else {
                    int val = state.get(TkCounters.BRACE_DEPTH);
                    switch (token.getType()) {
                        case SimpleLanguageLexer.ID:
                        case SimpleLanguageLexer.S_OPEN_BRACE:
                            if (val > 0) {
                                ctx.prependNewlineAndIndentBy(state.get(TkCounters.BRACE_DEPTH));
                            } else {
                                ctx.prependNewline();
                            }
                            break;
                        case SimpleLanguageLexer.S_CLOSE_BRACE:
                            if (val > 1) {
                                ctx.prependNewlineAndIndentBy(state.get(TkCounters.BRACE_DEPTH) - 1);
                            } else {
                                ctx.prependNewline();
                            }
                            break;
                        case SimpleLanguageLexer.S_SEMICOLON:
                            break;
                        default:
                            ctx.prependSpace();
                            break;
                    }
                }
            }
        }

    }

    public enum TkCounters {
        WORD_COUNT,
        BRACE_DEPTH,

    }
}
