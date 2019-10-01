package org.nemesis.antlrformatting.api;

import com.mastfrog.util.collections.IntList;
import java.io.IOException;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import static org.nemesis.antlrformatting.api.FormattingHarness.criteria;
import static org.nemesis.antlrformatting.api.FormattingHarness.keywords;

import static org.nemesis.simple.language.SimpleLanguageLexer.*;
import static org.nemesis.antlrformatting.api.SLState.*;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.*;
import org.nemesis.simple.SampleFiles;

/**
 *
 * @author Tim Boudreau
 */
public class LexingStateTest {

    static final String MAX_LINE_LENGTH = "maxLine";
    static final int LIMIT = 40;

    @Test
    public void testSomeMethod() throws IOException {
//        SampleFiles f = SampleFiles.MUCH_NESTING_WITH_EXTRA_NEWLINES;

        FormattingHarness harn = new FormattingHarness(SampleFiles.MUCH_NESTING_UNFORMATTED, LexingStateTest::configure);

//        harn.onBeforeEachToken(mtok -> {
//            if (true) {
//                System.out.println("\n\n*******************************");
//                System.out.println(mtok.getTokenIndex() + ". " + mtok.getText() + " DNL " + harn.context().rew.lastNewlineDistance(16));
//                harn.logRecentOps(5);
//                System.out.println("\n\n*******************************");
//            }
//        });
//        harn.onAfterEachToken(mtok -> {
////            System.out.println("B-TOKEN: " + mtok);
//        });
        ChangeChecker check = new ChangeChecker(16, harn);
        harn.onBeforeEachToken(check::before);
        harn.onAfterEachToken(check::after);

        harn.logFormattingActions();
        harn.debugLogOn(15, 16, 17);

        String formatted = harn.reformat();

        harn.context().rew.getText();
        System.out.println("FORMATTED:\n" + formatted);
    }

    static class ChangeChecker {

        private final int target;
        private final FormattingHarness harn;
        private IntList lastStarts;
        private IntList lastNewlinePositions;
        private int lastDistanceToNewline;

        ChangeChecker(int target, FormattingHarness harn) {
            this.target = target;
            this.harn = harn;
        }

        void before(ModalToken tok) {
            long newDistance = harn.rewriter().lastNewlineDistance(target);
            if (lastDistanceToNewline > 3 && newDistance <= 3) {
                System.out.println("AFTER " + tok.getTokenIndex() + " " + newDistance + " <- " + lastDistanceToNewline);
                System.out.println("\n\n*****************UNEXPECTED INDENT CHANGE**********************");
                System.out.println(" DISTANCE TO NEWLINE FOR " + target + " WENT FROM " + lastDistanceToNewline + " TO " + newDistance);
                System.out.println("    prev starts: " + lastStarts);
                System.out.println("    curr starts: " + harn.rewriter().startPositions);
                System.out.println("    prev newlines: " + lastNewlinePositions);
                System.out.println("    curr newlines: " + harn.rewriter().newlinePositions);
                System.out.println(" OPS:");
                harn.logRecentOps(5);
                System.out.println("\n\n***************************************\n");
            }

            lastDistanceToNewline = harn.rewriter().lastNewlineDistance(target);
            lastStarts = harn.rewriter().startPositions.copy();
            lastNewlinePositions = harn.rewriter().newlinePositions.copy();
            System.out.println("BEFORE " + tok.getTokenIndex() + " " + lastDistanceToNewline);
                System.out.println("    curr newlines: " + harn.rewriter().newlinePositions);
        }

        void after(ModalToken tok) {
//            long newDistance = harn.rewriter().lastNewlineDistance(target);
//            System.out.println("AFTER " + tok.getTokenIndex() + " " + newDistance + " <- " + lastDistanceToNewline);
//            if (lastDistanceToNewline > 3 && newDistance <= 3) {
//                System.out.println("\n\n*****************UNEXPECTED INDENT CHANGE**********************");
//                System.out.println(" DISTANCE TO NEWLINE FOR " + target + " WENT FROM " + lastDistanceToNewline + " TO " + newDistance);
//                System.out.println("    prev starts: " + lastStarts);
//                System.out.println("    curr starts: " + harn.rewriter().startPositions);
//                System.out.println("    prev newlines: " + lastNewlinePositions);
//                System.out.println("    curr newlines: " + harn.rewriter().newlinePositions);
//                System.out.println(" OPS:");
//                harn.logRecentOps(5);
//                System.out.println("\n\n***************************************\n");
//            }
        }
    }

    static void configure(LexingStateBuilder<SLState, ?> stateBuilder, FormattingRules rules) {
        stateBuilder.increment(BRACE_DEPTH)
                .onTokenType(S_OPEN_BRACE)
                .decrementingWhenTokenEncountered(S_CLOSE_BRACE);

        stateBuilder.pushPosition(BRACE_POSITION)
                .onTokenType(S_OPEN_BRACE)
                .poppingOnTokenType(S_CLOSE_BRACE);

        int maxLineLength = 180;

        FormattingAction doubleIndentForWrappedLines = PREPEND_NEWLINE_AND_INDENT
                .bySpaces(4, BRACE_POSITION);

        FormattingAction indentCurrent = PREPEND_NEWLINE_AND_INDENT
                .bySpaces(BRACE_POSITION)
                .wrappingLines(maxLineLength, doubleIndentForWrappedLines);

//        FormattingAction indentSubseqeuent = APPEND_NEWLINE_AND_INDENT
//                .bySpaces(BRACE_POSITION)
//                .wrappingLines(maxLineLength, doubleIndentForWrappedLines);
//
        FormattingAction doubleNewlineAndIndentIt
                = PREPEND_NEWLINE_AND_INDENT.bySpaces(4, BRACE_POSITION);
//                        .wrappingLines(maxLineLength, doubleIndentForWrappedLines);

        rules.onTokenType(K_TYPE)
                .named("double-newline-between-top-level-entries")
                .whereNotFirstTokenInSource()
                .format(PREPEND_DOUBLE_NEWLINE);

        rules.onTokenType(LINE_COMMENT)
                .named("indent-line-comments")
                .format(indentCurrent);

        rules.onTokenType(Criterion.ALWAYS)
                .wherePrevTokenType(LINE_COMMENT)
                .format(indentCurrent);

//        rules.onTokenType(ID, QUALIFIED_ID)
//                .named("prepend-space-to-ids")
//                .wherePrevTokenType(K_IMPORT, K_NAMESPACE, K_TYPE)
//                .format(PREPEND_SPACE);
        rules.onTokenType(ID)
                .named("indent-before-id-followed-by-colon")
                .whereNextTokenType(S_COLON)
                .priority(100)
                .format(indentCurrent);

        rules.onTokenType(ID, QUALIFIED_ID)
                .named("prepend-and-append-spaces-to-ids")
                .wherePreviousTokenTypeNot(S_OPEN_BRACE)
                .whereNextTokenTypeNot(S_SEMICOLON, S_COLON, S_OPEN_BRACE)
                .format(PREPEND_SPACE);

        rules.onTokenType(criteria.noneOf(S_OPEN_BRACE))
                .wherePrevTokenType(S_SEMICOLON)
                .format(indentCurrent);

        rules.onTokenType(S_OPEN_BRACE).format(PREPEND_SPACE);

        rules.onTokenType(S_COLON, S_PERCENT, S_SLASH, S_ASTERISK, L_BOOLEAN, L_FLOAT, L_INT, L_STRING)
                .named("bracket-colon-by-spaces")
                .whereNextTokenTypeNot(S_SEMICOLON)
                .format(PREPEND_SPACE.and(APPEND_SPACE));

        rules.onTokenType(keywords)
                .named("bracket-keywords-with-spaces")
                .whereNextTokenTypeNot(S_SEMICOLON)
                .format(PREPEND_SPACE);

//        rules.onTokenType(keywords)
//                .whereNextTokenType(S_SEMICOLON)
//                .named("keyword-spaces-if-semi-next")
//                .format(PREPEND_SPACE);
        rules.onTokenType(DESCRIPTION)
                .format(indentCurrent);

//        rules.onTokenType(S_SEMICOLON)
//                .whereNextTokenTypeNot(S_CLOSE_BRACE)
//                .format(indentSubseqeuent).named("semicolonIfNotCloseBrace");
//
//        rules.onTokenType(DESCRIPTION)
//                .wherePreviousTokenTypeNot(DESCRIPTION)
//                .named("newline-for-description-outside-braces")
//                .when(BRACE_DEPTH).isUnset()
//                .format(doubleNewlineAndIndentIt.trimmingWhitespace().and(indentSubseqeuent));
//
//        rules.onTokenType(DESCRIPTION)
//                .named("indent-descriptions-with-described")
//                .wherePreviousTokenTypeNot(DESCRIPTION)
//                .format(indentCurrent.trimmingWhitespace().and(indentSubseqeuent));
//
//        rules.onTokenType(DESCRIPTION)
//                .named("indent-subsequent-descriptions")
//                .wherePreviousTokenType(DESCRIPTION)
//                .format(indentCurrent.trimmingWhitespace().and(indentSubseqeuent));
//        rules.onTokenType(S_OPEN_BRACE)
//                .named("indent-after-open-brace")
//                .format(indentSubseqeuent);
        rules.onTokenType(S_CLOSE_BRACE)
                .named("indent-close-brace")
                .format(indentCurrent);

        rules.onTokenType(Criterion.ALWAYS)
                .wherePrevTokenType(S_CLOSE_BRACE)
                .priority(20)
                .format(indentCurrent);

        rules.onTokenType(COMMENT)
                .named("reflow-comments")
                .rewritingTokenTextWith(TokenRewriter.simpleReflow(LIMIT));
    }

    interface FormattingLogger {

        public void onToken(Token token, FormattingContextImpl ctx, LexingState state);
    }

    static FormattingAction wrap(FormattingAction a, FormattingLogger log) {
        return new WrapFormattingAction(a, log);
    }

    static FormattingAction wrap(FormattingAction a) {
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
}
