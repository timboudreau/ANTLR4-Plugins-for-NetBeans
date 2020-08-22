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

import com.mastfrog.antlr.utils.Criterion;
import com.github.difflib.algorithm.DiffException;
import com.mastfrog.util.collections.IntList;
import java.io.IOException;
import org.antlr.v4.runtime.Token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import static org.nemesis.antlrformatting.api.FormattingHarness.keywords;
import static org.nemesis.antlrformatting.api.GoldenFiles.diff;

import static org.nemesis.simple.language.SimpleLanguageLexer.*;
import static org.nemesis.antlrformatting.api.SLState.*;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.*;
import org.nemesis.simple.SampleFiles;
import org.nemesis.simple.language.SimpleLanguageLexer;

/**
 *
 * @author Tim Boudreau
 */
public class LexingStateTest {

    static final String MAX_LINE_LENGTH = "maxLine";
    static final int LIMIT = 40;

    static final String EXP = "types Stuff;\nperson : object {\n"
            + "                place : object {\n"
            + "                               thing : object {\n"
            + "                                              otherThing : object {\n"
            + "                                                                  ratio : float default 23.42;\n"
            + "                                                                  }\n"
            + "                                              }\n"
            + "                               }\n"
            + "                }";

    @Test
    public void testSomeMethod() throws IOException {
        FormattingHarness<SLState> harn = new FormattingHarness<>(SampleFiles.LONG_ITEMS,
                SLState.class, LexingStateTest::configure);
        // 38, 39
        // 72, 73

        harn.debugLogOn((Token tok) -> {
            switch(tok.getTokenIndex()) {
                case 5 :
                case 6 :
                case 7 :
                case 8 :
                case 38 :
//                case 70 :
                    System.out.println("NLPS: " + FormattingHarness.rewrittenNewlinePositionsInDocumentSnapshot());
                    System.out.println("STARTS: " + FormattingHarness.rewrittenTokenStartPositionsSnapshot());
//                case 40 :
//                case 72 :
                    System.out.println("\n****************************\n");
                    return true;
            }
            return false;
        });

        String txt = harn.reformat();

//        for (ModalToken mt : FormattingHarness.stream()) {
//            if (mt.getType() == L_INT) {
//                System.out.println("L_INT " + mt.getTokenIndex() + " '" + mt.getText() + "'");
//            }
//        }

        System.out.println("FORMATTED:\n" + txt);
    }

    @Test
    public void testLinePositions() throws IOException, DiffException {
        FormattingHarness<SLState> harn = new FormattingHarness<>(SampleFiles.MUCH_NESTING_UNFORMATTED,
                SLState.class, LexingStateTest::configure);

        ChangeChecker check = new ChangeChecker(16, harn, 3);
        harn.onBeforeEachToken(check::before);
        harn.onAfterEachToken(check::after);

        harn.logFormattingActions();
        harn.debugLogOn(15, 16, 17);

        String formatted = harn.reformat();

        System.out.println("FORMATTED:\n" + formatted);
        assertEquals(EXP, formatted, diff(EXP, formatted));
    }

    static class ChangeChecker {

        private final int target;
        private final FormattingHarness harn;
        private IntList lastStarts;
        private IntList lastNewlinePositions;
        private int lastDistanceToNewline;
        private final int limit;

        ChangeChecker(int target, FormattingHarness harn, int limit) {
            this.target = target;
            this.harn = harn;
            this.limit = limit;
        }

        void before(ModalToken tok) {
            long newDistance = harn.rewriter().lastNewlineDistance(target);
            if (lastDistanceToNewline > limit && newDistance <= limit) {
                System.out.println("AFTER " + tok.getTokenIndex() + " " + newDistance + " <- " + lastDistanceToNewline);
                System.out.println("\n\n*****************UNEXPECTED INDENT CHANGE**********************");
                System.out.println(" DISTANCE TO NEWLINE FOR " + target + " WENT FROM " + lastDistanceToNewline + " TO " + newDistance);
                System.out.println("    prev starts: " + lastStarts);
                System.out.println("    curr starts: " + FormattingHarness.rewrittenTokenStartPositionsSnapshot());
                System.out.println("    prev newlines: " + lastNewlinePositions);
//                System.out.println("    curr newlines: " + harn.rewriter().newlinePositions);
                System.out.println("    curr newlines: " + FormattingHarness.rewrittenNewlinePositionsInDocumentSnapshot());
                System.out.println(" OPS:");
                harn.logRecentOps(5);
                System.out.println("\n\n***************************************\n");
                fail("Distance changed inappropriately");
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

    static int maxLineLength = 180;
    static FormattingAction doubleIndentForWrappedLines = PREPEND_NEWLINE_AND_INDENT
            .bySpaces(4, BRACE_POSITION);

    static FormattingAction indentCurrent = PREPEND_NEWLINE_AND_INDENT
            .bySpaces(BRACE_POSITION)
            .wrappingLines(maxLineLength, doubleIndentForWrappedLines);

    static FormattingAction doubleNewlineAndIndentIt
            = PREPEND_NEWLINE_AND_INDENT.bySpaces(4, BRACE_POSITION);
//                        .wrappingLines(maxLineLength, doubleIndentForWrappedLines);

    static FormattingAction spaceOrWrap
            = PREPEND_SPACE.wrappingLines(40, PREPEND_NEWLINE_AND_INDENT.bySpaces(4, BRACE_POSITION));

    static void configure(LexingStateBuilder<SLState, ?> stateBuilder, FormattingRules rules) {
        stateBuilder.increment(BRACE_DEPTH)
                .onTokenType(S_OPEN_BRACE)
                .decrementingWhenTokenEncountered(S_CLOSE_BRACE);

        stateBuilder.pushPosition(BRACE_POSITION)
                .onTokenType(S_OPEN_BRACE)
                .poppingOnTokenType(S_CLOSE_BRACE);

        stateBuilder.increment(ARRAY_ITEM_COUNT).
                onTokenType(L_INT)
                .clearingWhenTokenEncountered(S_CLOSE_BRACE);

        rules.onTokenType(SimpleLanguageLexer.OP)
                .format(spaceOrWrap);

        rules.onTokenType(L_INT)
                .when(ARRAY_ITEM_COUNT).isGreaterThan(1)
                .wherePreviousTokenTypeNot(S_OPEN_BRACE)
                .format(spaceOrWrap);

//        rules.onTokenType(K_TYPE)
//                .named("double-newline-between-top-level-entries")
//                .wherePreviousTokenTypeNot(DESCRIPTION)
//                .whereNotFirstTokenInSource()
//                .format(PREPEND_DOUBLE_NEWLINE);

        rules.onTokenType(LINE_COMMENT, DESCRIPTION, S_CLOSE_BRACE)
                .named("indent-line-comments")
                .format(indentCurrent);

        rules.onTokenType(Criterion.ALWAYS)
                .named("indent-after-line-comment-or-brace")
                .wherePreviousTokenType(LINE_COMMENT, S_OPEN_BRACE)
                .format(indentCurrent);

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

//        rules.onTokenType(criteria.noneOf(S_OPEN_BRACE))
//                .wherePreviousTokenType(S_SEMICOLON, S_OPEN_BRACE)
//                .format(indentCurrent);

        rules.onTokenType(S_OPEN_BRACE).format(PREPEND_SPACE);

        rules.onTokenType(S_COLON, S_PERCENT, S_SLASH, S_ASTERISK, L_BOOLEAN, L_FLOAT, L_INT, L_STRING)
                .named("bracket-colon-by-spaces")
                .whereNextTokenTypeNot(S_SEMICOLON)
                .format(PREPEND_SPACE.and(APPEND_SPACE));

        rules.onTokenType(L_FLOAT, L_BOOLEAN, L_INT, L_STRING)
                .format(PREPEND_SPACE);

//        rules.onTokenType(K_TYPE)
//                .format(APPEND_SPACE);

        rules.onTokenType(ID)
                .wherePreviousTokenType(K_TYPE)
                .format(PREPEND_SPACE);

        rules.onTokenType(keywords)
                .named("bracket-keywords-with-spaces")
                .whereNextTokenTypeNot(S_SEMICOLON)
                .format(PREPEND_SPACE);

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
