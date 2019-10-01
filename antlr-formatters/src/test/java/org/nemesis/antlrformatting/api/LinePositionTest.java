package org.nemesis.antlrformatting.api;

import java.io.IOException;
import org.antlr.v4.runtime.Token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import static org.nemesis.antlrformatting.api.FormattingHarness.keywords;
import static org.nemesis.antlrformatting.api.LexingStateTest.LIMIT;
import static org.nemesis.antlrformatting.api.SLState.BRACE_DEPTH;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.*;
import org.nemesis.simple.SampleFiles;
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

/**
 *
 * @author Tim Boudreau
 */
public class LinePositionTest {

    @Test
    public void testLinePositionWithFullFile() throws IOException {
        if (true) {
            return;
        }
        SampleFiles f = SampleFiles.BASIC;
        String formatted = new GenericConfig(LinePositionTest::checkingFormat)
                .reformattedString(f.text(), 0, f.length(), null);
        System.out.println("FORMATTED\n" + formatted);
    }

    @Test
    public void testPositionAfterAppendNewline() throws IOException {
        SampleFiles f = SampleFiles.MINIMAL_MULTILINE;
        String formatted = new GenericConfig((stateBuilder, rules) -> {
            rules.onTokenType(Criterion.ALWAYS).format(APPEND_NEWLINE.trimmingWhitespace().and(new LinePositionChecker().and(new ComputedAndRealPositionChecker())));
        }).reformattedString(f.text(), 0, f.length(), null);
        System.out.println("FORMATTED:\n" + formatted);
    }

    @Test
    public void testPositionAfterPrependNewline() throws IOException {
        SampleFiles f = SampleFiles.MINIMAL;
        String formatted = new GenericConfig((stateBuilder, rules) -> {
            rules.onTokenType(Criterion.ALWAYS).format(PREPEND_NEWLINE.and(new LinePositionChecker().and(new ComputedAndRealPositionChecker())));
        }).reformattedString(f.text(), 0, f.length(), null);
        System.out.println("FORMATTED:\n" + formatted);
    }

//    @Test
    public void testPositionAfterAppendSpace() throws IOException {
        SampleFiles f = SampleFiles.ABSURDLY_MINIMAL;
        String formatted = new GenericConfig((stateBuilder, rules) -> {
            rules.onTokenType(Criterion.ALWAYS).format(APPEND_SPACE.trimmingWhitespace().and(new SpaceChecker().and(new ComputedAndRealPositionChecker())));
        }).reformattedString(f.text(), 0, f.length(), null);
        System.out.println("FORMATTED:\n" + formatted);
    }

    static void checkingFormat(LexingStateBuilder<SLState, ?> stateBuilder, FormattingRules rules) {
        stateBuilder.increment(BRACE_DEPTH)
                .onTokenType(S_OPEN_BRACE)
                .decrementingWhenTokenEncountered(S_CLOSE_BRACE);

        FormattingAction checker = new ComputedAndRealPositionChecker();

        int maxLineLength = 80;

        FormattingAction doubleIndentForWrappedLines = APPEND_NEWLINE_AND_DOUBLE_INDENT
                .by(BRACE_DEPTH).and(checker);

        FormattingAction indentCurrent = PREPEND_NEWLINE_AND_INDENT
                .by(BRACE_DEPTH)
                .wrappingLines(maxLineLength, doubleIndentForWrappedLines).and(checker);

        FormattingAction indentSubseqeuent = APPEND_NEWLINE_AND_INDENT
                .by(BRACE_DEPTH)
                .wrappingLines(maxLineLength, doubleIndentForWrappedLines).and(checker);

        FormattingAction doubleNewlineAndIndentIt
                = PREPEND_DOUBLE_NEWLINE_AND_INDENT.by(BRACE_DEPTH)
                        .wrappingLines(maxLineLength, doubleIndentForWrappedLines).and(checker);

        rules.onTokenType(K_TYPE)
                .whereNotFirstTokenInSource()
                .format(PREPEND_DOUBLE_NEWLINE.and(checker));

        rules.onTokenType(LINE_COMMENT)
                .format(indentCurrent.and(indentSubseqeuent).trimmingWhitespace());

        rules.onTokenType(ID, QUALIFIED_ID)
                .wherePrevTokenType(K_IMPORT, K_NAMESPACE, K_TYPE)
                .format(PREPEND_SPACE.and(checker));

        rules.onTokenType(ID)
                .whereNextTokenType(S_COLON)
                .format(indentCurrent);
        rules.onTokenType(ID, QUALIFIED_ID)
                .whereNextTokenTypeNot(S_SEMICOLON, S_COLON)
                .format(PREPEND_SPACE.and(APPEND_SPACE).and(checker));
        rules.onTokenType(S_SEMICOLON)
                .whereNextTokenTypeNot(DESCRIPTION, S_CLOSE_BRACE)
                .format(indentSubseqeuent).named("semiIndentNext");
        rules.onTokenType(S_COLON)
                .format(PREPEND_SPACE.and(APPEND_SPACE).and(checker));
        rules.onTokenType(keywords)
                .whereNextTokenTypeNot(S_SEMICOLON)
                .format(PREPEND_SPACE.and(APPEND_SPACE).and(checker));
        rules.onTokenType(keywords)
                .whereNextTokenType(S_SEMICOLON)
                .format(PREPEND_SPACE.and(checker));
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


    private static final class SpaceChecker implements FormattingAction {

        int expectedPosition;
        int count;

        @Override
        public void accept(Token token, FormattingContext ctx, LexingState state) {
            int pos = ctx.currentCharPositionInLine();
//            System.out.println(count++ + ": '" + token.getText() + "' " + pos + " expect " + expectedPosition + " match? " + (pos == expectedPosition));
            assertEquals(expectedPosition, pos,
                    "Mispositioned at token '" + count++ + " '" + token.getText() + "' " + ctx);
            expectedPosition += token.getText().trim().length() + 1;
        }

    }

    private static final class LinePositionChecker implements FormattingAction {

        int count = 0;

        @Override
        public void accept(Token token, FormattingContext ctx, LexingState state) {
            int pos = ctx.currentCharPositionInLine();
            assertEquals(0, pos, count++ + ": Position should always be 0, but got " + pos + " for " + token.getText());
        }
    }

    private static final class ComputedAndRealPositionChecker implements FormattingAction {

        boolean sync = true;
        int count;

        @Override
        public void accept(Token token, FormattingContext ctx, LexingState state) {
            int pos = ctx.currentCharPositionInLine();
            int compPos = ((FormattingContextImpl) ctx).bruteForceComputedTokenPosition();
            boolean nowSync = pos == compPos;
            String tokInfo = " proc-tok " + count++ + " tok " + token.getTokenIndex()
                    + " '" + token.getText().replace("\n", "\\n") + "' " + pos + " / " + compPos;
            if (nowSync != sync) {
                if (nowSync) {
                    System.out.println("POSITIONS BACK IN SYNC: " + tokInfo);
                } else {
                    String msg = "POSITIONS OUT OF SYNC: " + tokInfo + " brute force char position in line "
                            + pos + " computed char position in line (indirectly) "
                            + "from rewriter " + compPos;
                    fail(msg);
                    System.out.println(msg);
                }
                sync = nowSync;
            }
        }
    }
}
