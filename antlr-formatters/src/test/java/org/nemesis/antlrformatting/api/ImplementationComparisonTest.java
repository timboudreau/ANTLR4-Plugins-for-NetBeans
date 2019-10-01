/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlrformatting.api;

import com.mastfrog.util.collections.IntList;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.util.LinkedList;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.nemesis.antlrformatting.api.FormattingHarness.keywords;
import static org.nemesis.antlrformatting.api.LexingStateTest.LIMIT;
import static org.nemesis.antlrformatting.api.SLState.BRACE_DEPTH;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_NEWLINE_AND_DOUBLE_INDENT;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_NEWLINE_AND_INDENT;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_SPACE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_DOUBLE_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_DOUBLE_NEWLINE_AND_INDENT;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_NEWLINE_AND_INDENT;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_SPACE;
import org.nemesis.antlrformatting.impl.CaretFixer;
import org.nemesis.antlrformatting.impl.CaretInfo;
import org.nemesis.simple.SampleFiles;
import org.nemesis.simple.language.SimpleLanguageLexer;
import static org.nemesis.simple.language.SimpleLanguageLexer.COMMENT;
import static org.nemesis.simple.language.SimpleLanguageLexer.DESCRIPTION;
import static org.nemesis.simple.language.SimpleLanguageLexer.ID;
import static org.nemesis.simple.language.SimpleLanguageLexer.K_IMPORT;
import static org.nemesis.simple.language.SimpleLanguageLexer.K_NAMESPACE;
import static org.nemesis.simple.language.SimpleLanguageLexer.K_TYPE;
import static org.nemesis.simple.language.SimpleLanguageLexer.LINE_COMMENT;
import static org.nemesis.simple.language.SimpleLanguageLexer.QUALIFIED_ID;
import static org.nemesis.simple.language.SimpleLanguageLexer.S_CLOSE_BRACE;
import static org.nemesis.simple.language.SimpleLanguageLexer.S_COLON;
import static org.nemesis.simple.language.SimpleLanguageLexer.S_OPEN_BRACE;
import static org.nemesis.simple.language.SimpleLanguageLexer.S_SEMICOLON;
import static org.nemesis.simple.language.SimpleLanguageLexer.S_WHITESPACE;
import org.nemesis.simple.language.SimpleLanguageParser;
import static org.nemesis.antlrformatting.api.SLState.BRACE_POSITION;

/**
 *
 * @author Tim Boudreau
 */
public class ImplementationComparisonTest {

    private SimpleLanguageLexer lex;
    private EverythingTokenStream str;
    private BiRewriter rew;

    @Test
    public void testNewlinesWithInserts() throws Exception {
        if (true) {
            return;
        }
        IntList oldNls = rew.b.newlinePositions.copy();
        rew.insertBefore(3, "\n   ");
        System.out.println("OLD NLS: " + oldNls);
        System.out.println("NEW NLS: " + rew.b.newlinePositions);
        System.out.println("RT: " + Strings.escape(rew.getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE));
        assertFirstCharactersAfterNewlinesMatch(str, rew, 30, true);
    }

    @Test
    public void testNewlineIdentification() throws IOException {
        assertFirstCharactersAfterNewlinesMatch(str, rew, false);
    }

    @Test
    public void test() throws IOException {
        if (true) {
            return;
        }
        SampleFiles f = SampleFiles.BASIC;
        LexingStateBuilder<SLState, LexingState> lsb = LexingState.builder(SLState.class);
        FormattingRules rules = new FormattingRules(SimpleLanguageLexer.VOCABULARY,
                SimpleLanguageLexer.modeNames, SimpleLanguageParser.ruleNames);

        configureFormatter(lsb, rules);
        Criteria criteria = Criteria.forVocabulary(SimpleLanguageLexer.VOCABULARY);
        Criterion ws = criteria.matching(S_WHITESPACE);

        rew.throwOnMismatch = false;
        FormattingContextImpl impl = new FormattingContextImpl(rew, 0, f.length(),
                4, rules, lsb.build(), ws, Criterion.NEVER.toTokenPredicate(), null);

        FormattingResult res = impl.go(str, CaretInfo.NONE, CaretFixer.none());
        System.out.println("RES:\n" + res.text());
    }

    @BeforeEach
    public void setup() throws IOException {
        lex = SampleFiles.MUCH_NESTING_WITH_EXTRA_NEWLINES.lexer();
        str = new EverythingTokenStream(lex, SimpleLanguageLexer.modeNames);
        rew = new BiRewriter(str);
    }

    static final class BiRewriter implements StreamRewriterFacade {

        final LinePositionComputingRewriter a;
        final FastStreamRewriter b;
        private final LinkedList<String> ops = new LinkedList<>();
        private FormattingContextImpl impl;
        boolean throwOnMismatch;

        public BiRewriter(LinePositionComputingRewriter a, FastStreamRewriter b) {
            this.a = a;
            this.b = b;
        }

        public BiRewriter(EverythingTokenStream str) {
            this(new LinePositionComputingRewriter(str), new FastStreamRewriter(str));
        }

        String ops() {
            return Strings.join("\n  · ", ops);
        }

        private void op(String op) {
            ops.push(op);
        }

        @Override
        public void delete(Token tok) {
            op("Delete " + tok.getTokenIndex());
            a.delete(tok);
            b.delete(tok);
        }

        @Override
        public void delete(int tokenIndex) {
            op("Delete " + tokenIndex);
            a.delete(tokenIndex);
            b.delete(tokenIndex);
        }

        @Override
        public String getText() {
            String atxt = a.getText();
            String btxt = b.getText();
            assertEquals(atxt, btxt, () -> "Texts differ. " + ops());
            return btxt;
        }

        @Override
        public String getText(Interval interval) {
            String atxt = a.getText(interval);
            String btxt = b.getText(interval);
            assertEquals(atxt, btxt, () -> "Texts differ for " + interval);
            return btxt;
        }

        static String elide(String text) {
            StringBuilder sb = new StringBuilder();
            FastStreamRewriter.elideSpaces(text, sb);
            return sb.toString();
        }

        @Override
        public void insertAfter(Token tok, String text) {
            op("InsertAfter " + tok.getTokenIndex() + ": " + elide(Strings.escape(text, Escaper.NEWLINES_AND_OTHER_WHITESPACE)));
            a.insertAfter(tok, text);
            b.insertAfter(tok, text);
        }

        @Override
        public void insertAfter(int index, String text) {
            op("InsertAfter " + index + ": " + elide(Strings.escape(text, Escaper.NEWLINES_AND_OTHER_WHITESPACE)));
            a.insertAfter(index, text);
            b.insertAfter(index, text);
        }

        @Override
        public void insertBefore(Token tok, String text) {
            op("InsertBefore " + tok.getTokenIndex() + ": " + elide(Strings.escape(text, Escaper.NEWLINES_AND_OTHER_WHITESPACE)));
            a.insertBefore(tok, text);
            b.insertBefore(tok, text);
        }

        @Override
        public void insertBefore(int index, String text) {
            op("InsertBefore " + index + ": " + elide(Strings.escape(text, Escaper.NEWLINES_AND_OTHER_WHITESPACE)));
            a.insertBefore(index, text);
            b.insertBefore(index, text);
        }

        @Override
        public int lastNewlineDistance(int tokenIndex) {
            int alnd = a.lastNewlineDistance(tokenIndex);
            int blnd = b.lastNewlineDistance(tokenIndex);
            if (alnd != blnd) {
                if (throwOnMismatch) {
                    throw new IllegalStateException("lastNewlineDistance differs - orig " + alnd
                            + " optimized " + blnd + " for token " + tokenIndex + ". Recent ops: \n" + ops());
                } else {
                    System.out.println("\nlastNewlineDistance differs - orig " + alnd
                            + " optimized " + blnd + " for token " + tokenIndex + ". Recent ops: \n" + ops() + "\n");
                }
            }
            ops.clear();
            return alnd;
        }

        @Override
        public void replace(Token tok, String text) {
            op("Replace " + tok.getTokenIndex() + ": " + elide(Strings.escape(text, Escaper.NEWLINES_AND_OTHER_WHITESPACE)));
            a.replace(tok, text);
            b.replace(tok, text);
        }

        @Override
        public void replace(int index, String text) {
            op("Replace " + index + ": " + elide(Strings.escape(text, Escaper.NEWLINES_AND_OTHER_WHITESPACE)));
            a.replace(index, text);
            b.replace(index, text);
        }

        @Override
        public void close() {
            a.close();
            b.close();
        }

        @Override
        public String rewrittenText(int index) {
            String at = a.rewrittenText(index);
            String bt = b.rewrittenText(index);
            assertEquals(at, bt, "Different text for " + index);
            return at;
        }
    }

    static void configureFormatter(LexingStateBuilder<SLState, ?> stateBuilder, FormattingRules rules) {
        stateBuilder.increment(BRACE_DEPTH)
                .onTokenType(S_OPEN_BRACE)
                .decrementingWhenTokenEncountered(S_CLOSE_BRACE);

        stateBuilder.set(BRACE_POSITION)
                .onTokenType(S_COLON)
                .clearingAfterTokenType(-1);

        int maxLineLength = 80;

        FormattingAction doubleIndentForWrappedLines = APPEND_NEWLINE_AND_DOUBLE_INDENT
                .by(BRACE_DEPTH);

        FormattingAction indentCurrent = PREPEND_NEWLINE_AND_INDENT
                .by(BRACE_DEPTH)
                .wrappingLines(maxLineLength, doubleIndentForWrappedLines);

        FormattingAction indentSubseqeuent = APPEND_NEWLINE_AND_INDENT
                .by(BRACE_DEPTH)
                .wrappingLines(maxLineLength, doubleIndentForWrappedLines);

        FormattingAction doubleNewlineAndIndentIt
                = PREPEND_DOUBLE_NEWLINE_AND_INDENT.by(BRACE_DEPTH)
                        .wrappingLines(maxLineLength, doubleIndentForWrappedLines);

        rules.onTokenType(K_TYPE)
                .whereNotFirstTokenInSource()
                .format(PREPEND_DOUBLE_NEWLINE);

        rules.onTokenType(LINE_COMMENT)
                .format(indentCurrent.and(indentSubseqeuent).trimmingWhitespace());

        rules.onTokenType(ID, QUALIFIED_ID)
                .wherePrevTokenType(K_IMPORT, K_NAMESPACE, K_TYPE)
                .format(PREPEND_SPACE);

        rules.onTokenType(ID)
                .whereNextTokenType(S_COLON)
                .format(indentCurrent);
        rules.onTokenType(ID, QUALIFIED_ID)
                .whereNextTokenTypeNot(S_SEMICOLON, S_COLON)
                .format(PREPEND_SPACE.and(APPEND_SPACE));
        rules.onTokenType(S_SEMICOLON)
                .whereNextTokenTypeNot(DESCRIPTION, S_CLOSE_BRACE)
                .format(indentSubseqeuent).named("semiIndentNext");
        rules.onTokenType(S_COLON)
                .format(PREPEND_SPACE.and(APPEND_SPACE));
        rules.onTokenType(keywords)
                .whereNextTokenTypeNot(S_SEMICOLON)
                .format(PREPEND_SPACE.and(APPEND_SPACE));
        rules.onTokenType(keywords)
                .whereNextTokenType(S_SEMICOLON)
                .format(PREPEND_SPACE);
        rules.onTokenType(S_SEMICOLON)
                .whereNextTokenTypeNot(S_CLOSE_BRACE)
                .format(indentSubseqeuent).named("semicolonIfNotCloseBrace");
        rules.onTokenType(DESCRIPTION)
                .wherePreviousTokenTypeNot(DESCRIPTION)
                .when(BRACE_DEPTH).isUnset()
                .format(doubleNewlineAndIndentIt.trimmingWhitespace().and(indentSubseqeuent));
        rules.onTokenType(DESCRIPTION)
                .wherePreviousTokenTypeNot(DESCRIPTION)
                .format(indentCurrent.trimmingWhitespace().and(indentSubseqeuent));
        rules.onTokenType(DESCRIPTION)
                .wherePreviousTokenType(DESCRIPTION)
                .format(indentCurrent.trimmingWhitespace().and(indentSubseqeuent));

        rules.onTokenType(S_OPEN_BRACE)
                .format(indentSubseqeuent);
        rules.onTokenType(S_CLOSE_BRACE)
                .format(indentCurrent);

        rules.onTokenType(COMMENT)
                .rewritingTokenTextWith(TokenRewriter.simpleReflow(LIMIT))
                .format(indentCurrent);
    }

    private void assertFirstCharactersAfterNewlinesMatch(EverythingTokenStream str, BiRewriter rew, boolean log) {
        assertFirstCharactersAfterNewlinesMatch(str, rew, str.size(), log);
    }

    private void assertFirstCharactersAfterNewlinesMatch(EverythingTokenStream str, BiRewriter rew, int limit, boolean log) {
        IntList origFirstTokensAfterNewlines = IntList.create(str.size());
        IntList newFirstTokensAfterNewlines = IntList.create(str.size());
        for (int i = 0; i < limit; i++) {
            System.out.println("\n" + i + ".");
            Token tok = str.get(i);
            String txt = '\'' + Strings.escape(tok.getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE) + '\'';
            int start = str.get(i).getStartIndex();
            int lnd = rew.a.lastNewlineDistance(i);
            int pos = start - lnd;
            if (log) {
                System.out.println("lnd for " + i + " is " + padInt(lnd) + " start " + start + " pos " + padInt(pos) + " " + txt);
            }
            if (origFirstTokensAfterNewlines.isEmpty() || origFirstTokensAfterNewlines.last() != pos) {
                origFirstTokensAfterNewlines.add(pos);
            }

            int lnd2 = rew.b.lastNewlineDistance(i);
            int pos2 = start - lnd2;
            if (log) {
                System.out.println("xnd for " + i + " is " + padInt(lnd2) + " start " + start + " pos " + padInt(pos2) + " " + txt);
            }
            if (newFirstTokensAfterNewlines.isEmpty() || newFirstTokensAfterNewlines.last() != pos2) {
                newFirstTokensAfterNewlines.add(pos2);
            }
        }
        if (log) {
            System.out.println("ORIG NLPs: " + origFirstTokensAfterNewlines);
            System.out.println(" NEW NLPs: " + newFirstTokensAfterNewlines);
        }
        assertEquals(origFirstTokensAfterNewlines, newFirstTokensAfterNewlines);
    }

    static String padInt(int val) {
        String r = Integer.toString(val);
        while (r.length() < 3) {
            r = " " + r;
        }
        return r;
    }
}
