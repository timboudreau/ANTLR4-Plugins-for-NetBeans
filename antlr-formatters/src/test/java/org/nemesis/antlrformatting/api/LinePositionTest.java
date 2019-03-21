/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlrformatting.api;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.*;
import org.nemesis.antlrformatting.spi.AntlrFormatterProvider;
import org.nemesis.simple.SampleFiles;
import org.nemesis.simple.language.SimpleLanguageLexer;
import org.netbeans.modules.editor.indent.spi.Context;

/**
 *
 * @author Tim Boudreau
 */
public class LinePositionTest {

    @Test
    public void testPositionAfterAppendNewline() throws IOException {
        SampleFiles f = SampleFiles.MINIMAL;
        String formatted = new NewlineMadness((stateBuilder, rules) -> {
            rules.onTokenType(Criterion.ALWAYS).format(APPEND_NEWLINE.and(new LinePositionChecker()));
        }).reformattedString(f.text(), 0, f.length(), null);
        System.out.println("FORMATTED:\n" + formatted);
    }

    @Test
    public void testPositionAfterPrependNewline() throws IOException {
        SampleFiles f = SampleFiles.MINIMAL;
        String formatted = new NewlineMadness((stateBuilder, rules) -> {
            rules.onTokenType(Criterion.ALWAYS).format(PREPEND_NEWLINE.and(new LinePositionChecker()));
        }).reformattedString(f.text(), 0, f.length(), null);
        System.out.println("FORMATTED:\n" + formatted);
    }

    @Test
    public void testPositionAfterAppendSpace() throws IOException {
        SampleFiles f = SampleFiles.ABSURDLY_MINIMAL;
        String formatted = new NewlineMadness((stateBuilder, rules) -> {
            rules.onTokenType(Criterion.ALWAYS).format(APPEND_SPACE.trimmingWhitespace().and(new SpaceChecker()));
        }).reformattedString(f.text(), 0, f.length(), null);
        System.out.println("FORMATTED:\n" + formatted);
    }

    static final class NewlineMadness extends AntlrFormatterProvider<Preferences, LexingStateTest.SLState> {

        private final BiConsumer<LexingStateBuilder<LexingStateTest.SLState, ?>, FormattingRules> configurer;

        NewlineMadness(BiConsumer<LexingStateBuilder<LexingStateTest.SLState, ?>, FormattingRules> configurer) {
            super(LexingStateTest.SLState.class);
            this.configurer = configurer;
        }

        @Override
        protected Preferences configuration(Context ctx) {
            return null;
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
        protected void configure(LexingStateBuilder<LexingStateTest.SLState, ?> stateBuilder, FormattingRules rules, Preferences config) {
            configurer.accept(stateBuilder, rules);
        }
    }

    private static final class SpaceChecker implements FormattingAction {

        int expectedPosition;
        int count;

        @Override
        public void accept(Token token, FormattingContext ctx, LexingState state) {
            int pos = ctx.currentCharPositionInLine();
            System.out.println(count++ + ": '" + token.getText() + "' " + pos + " expect " + expectedPosition + " match? " + (pos == expectedPosition));
            assertEquals(expectedPosition, ctx.currentCharPositionInLine(),
                    "Mispositioned at token '" + count++ + " '" + token.getText() + "' " + ctx);
            expectedPosition += token.getText().trim().length() + 1;
        }

    }

    private static final class LinePositionChecker implements FormattingAction {

        int count = 0;

        @Override
        public void accept(Token token, FormattingContext ctx, LexingState state) {
//            System.out.println(count++ + ": LP " + ctx.currentCharPositionInLine() + " was " + ctx.origCharPositionInLine() + " '" + token.getText() + "'");
            int pos = ctx.currentCharPositionInLine();
            assertEquals(0, pos, count++ + ": Position should always be 0, but got " + pos + " for " + token.getText());
        }

    }
}
