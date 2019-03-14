package org.nemesis.antlrformatting.api;

import java.io.IOException;
import java.util.function.Predicate;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.junit.jupiter.api.Test;
import org.nemesis.antlrformatting.spi.AntlrFormatterProvider;
import org.nemesis.simple.language.SimpleLanguageLexer;
import org.netbeans.modules.editor.indent.spi.Context;

import static org.nemesis.simple.language.SimpleLanguageLexer.*;
import static org.nemesis.antlrformatting.api.LexingStateTest.SLState.*;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.*;
import org.nemesis.simple.SampleFiles;

/**
 *
 * @author Tim Boudreau
 */
public class LexingStateTest {

    @Test
    public void testSomeMethod() throws IOException {
        SampleFiles f = SampleFiles.BASIC;
        String formatted = new SimpleTF().reformattedString(f.text(), 0, f.length(), null);
        System.out.println("FORMATTED:\n" + formatted);
    }

    static final class SimpleTF extends AntlrFormatterProvider<Void, SLState> {

        private final Criteria criteria = Criteria.forVocabulary(SimpleLanguageLexer.VOCABULARY);

        SimpleTF() {
            super(SLState.class);
        }

        @Override
        protected Void configuration(Context ctx) {
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
        protected Predicate<Token> debugLogPredicate() {
            return criteria.matching(COMMENT).toTokenPredicate();
        }

        Criterion keywords = criteria.anyOf(K_BOOLEAN, K_DEFAULT, L_BOOLEAN, K_OBJECT, K_STRING, K_FLOAT,
                K_REFERENCE, L_STRING);

        @Override
        protected void configure(LexingStateBuilder<SLState, ?> stateBuilder, FormattingRules rules, Void config) {
            stateBuilder.increment(BRACE_DEPTH)
                    .onTokenType(S_OPEN_BRACE)
                    .decrementingWhenTokenEncountered(S_CLOSE_BRACE);
            FormattingAction indentIt = wrap(PREPEND_NEWLINE_AND_INDENT.by(BRACE_DEPTH));
            FormattingAction indentNext = wrap(APPEND_NEWLINE_AND_INDENT.by(BRACE_DEPTH));
            FormattingAction doubleAndIndentIt = wrap(PREPEND_DOUBLE_NEWLINE_AND_INDENT.by(BRACE_DEPTH)
                    .and(INDENT.by(BRACE_DEPTH)));

            rules.onTokenType(K_TYPE)
                    .whereNotFirstTokenInSource()
                    .format(PREPEND_DOUBLE_NEWLINE);
            rules.onTokenType(LINE_COMMENT)
                    .format(indentNext.and(indentIt).trimmingWhitespace());
            rules.onTokenType(ID, QUALIFIED_ID)
                    .wherePrevTokenType(K_IMPORT, K_NAMESPACE, K_TYPE)
                    .format(PREPEND_SPACE);
            rules.onTokenType(ID)
                    .whereNextTokenType(S_COLON)
                    .format(indentIt);
            rules.onTokenType(ID, QUALIFIED_ID)
                    .whereNextTokenTypeNot(S_SEMICOLON, S_COLON)
                    .format(PREPEND_SPACE.and(APPEND_SPACE));
            rules.onTokenType(S_SEMICOLON)
                    .whereNextTokenTypeNot(DESCRIPTION, S_CLOSE_BRACE)
                    .format(indentNext).named("semiIndentNext");
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
                    .format(indentNext).named("semicolonIfNotCloseBrace");
            rules.onTokenType(DESCRIPTION)
                    .wherePreviousTokenTypeNot(DESCRIPTION)
                    .when(BRACE_DEPTH).isUnset()
                    .format(doubleAndIndentIt.trimmingWhitespace().and(indentNext));
            rules.onTokenType(DESCRIPTION)
                    .wherePreviousTokenTypeNot(DESCRIPTION)
                    .format(indentIt.trimmingWhitespace().and(indentNext));
            rules.onTokenType(DESCRIPTION)
                    .wherePreviousTokenType(DESCRIPTION)
                    .format(indentIt.trimmingWhitespace().and(indentNext));

            rules.onTokenType(S_OPEN_BRACE)
                    .format(indentNext);
            rules.onTokenType(S_CLOSE_BRACE)
                    .format(indentIt);

            rules.onTokenType(COMMENT)
                    .rewritingTokenTextWith(TokenRewriter.simpleReflow(LIMIT));
        }

        static int LIMIT = 40;

        interface FormattingLogger {

            public void onToken(Token token, FormattingContextImpl ctx, LexingState state);
        }

        FormattingAction wrap(FormattingAction a, FormattingLogger log) {
            return new WrapFormattingAction(a, log);
        }

        FormattingAction wrap(FormattingAction a) {
            return new WrapFormattingAction(a);
        }

        static class WrapFormattingAction implements FormattingAction, FormattingLogger {

            private final FormattingLogger logger;
            private final FormattingAction a;

            WrapFormattingAction(FormattingAction a) {
                this.a = a;
                this.logger = this;
            }

            WrapFormattingAction(FormattingAction a, FormattingLogger logger) {
                this.a = a;
                this.logger = logger;
            }

            @Override
            public void accept(Token token, FormattingContext ctx, LexingState state) {
                logger.onToken(token, (FormattingContextImpl) ctx, state);
                a.accept(token, ctx, state);
            }

            @Override
            public void onToken(Token token, FormattingContextImpl ctx, LexingState state) {
                System.out.println("On '" + token.getText() + "' ctx " + ctx + " state " + state
                        + " will run " + a);
            }

            public String toString() {
                return "log(" + a + ")";
            }

        }

        @Override
        protected Criterion whitespace() {
            return criteria.matching(S_WHITESPACE);
        }
    }

    enum SLState {
        BRACE_DEPTH,
    }

}
