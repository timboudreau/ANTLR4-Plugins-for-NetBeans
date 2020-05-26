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
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.nemesis.antlrformatting.impl.CaretFixer;
import org.nemesis.antlrformatting.impl.CaretInfo;

/**
 * Implements the state and context of a formatting operation.
 *
 * @author Tim Boudreau
 */
class FormattingContextImpl extends FormattingContext implements LexerScanner {

    final StreamRewriterFacade rew;
    private final int startPosition;
    private final int endPosition;
    private final int indentSize;
    private final FormattingRules rules;
    private boolean lastContainedNewline;
    private int posInOriginalLine;
    private String replacement;
    private EverythingTokenStream stream;
    final LexingState state;
    private final Criterion notWhitespace;
    private final LineState lineState;
    private final Criterion whitespace;
    private final Predicate<Token> debugLogging;
    private int firstTokenInRange = -1;
    private int lastTokenInRange = -1;
    private final IntFunction<Set<Integer>> ruleFinder;

    FormattingContextImpl(StreamRewriterFacade rew, int start, int end,
            int indentSize, FormattingRules rules, LexingState state,
            Criterion whitespace, Predicate<Token> debugLogging, IntFunction<Set<Integer>> ruleFinder) {
        this.rew = rew;
        this.startPosition = start;
        this.endPosition = end;
        this.rules = rules;
        this.indentSize = indentSize;
        this.state = state;
        this.notWhitespace = whitespace.negate();
        this.whitespace = whitespace;
        this.debugLogging = debugLogging;
        lineState = new LineState(indentSize);
        this.ruleFinder = ensureRuleFinder(ruleFinder);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FormattingContextImpl(");
        sb.append("  indentSize: ").append(indentSize).append('\n');
        sb.append("  whitespace: ").append(whitespace).append('\n');
        if (stream != null) {
            sb.append("  stream with ").append(stream.size()).append(" tokens, cursor: ").append(stream.cursor).append('\n');
            sb.append("  firstTokenInRange: ").append(firstTokenInRange).append(" last: ").append(lastTokenInRange).append('\n');
        } else {
            sb.append("  stream: none").append('\n');
        }
        sb.append("  rewriter: ").append(rew).append('\n');
        sb.append("  rules: ").append(rules);
        return sb.append("\n)").toString();
    }

    static IntFunction<Set<Integer>> ensureRuleFinder(IntFunction<Set<Integer>> finder) {
        if (finder == null) {
            return ignored -> {
                throw new IllegalStateException("Cannot use parser rule "
                        + "constraints - no "
                        + "parser or rule node was provided when "
                        + "constructing this formatting context");
            };
        }
        return finder;
    }

    FormattingResult go(EverythingTokenStream tokens, CaretInfo caretPos, CaretFixer updateWithCaretPositionAndLength) {
        // Starting a new file, wipe the state clean
        state.clear();
        stream = tokens;
        // Make sure the stream hasn't been used already - seek it to the
        // beginning
        tokens.seek(0);
        // Store the last token type here
        int prevType = -1;
        // Get the token count
        int size = tokens.size();

        int caretToken = -1;

        boolean[] caretUpdated = new boolean[1];

        int prevMode = -1;
        int tokensFormatted = 0;
        for (int i = 0; i < size; i++) {
            ModalToken tok = tokens.get(i);
            // If we have passed the index of the last token we want to
            // reformat, stop looping over tokens
            int positionBeforeProcessingToken = currentCharPositionInLine();
            if (tok.getStartIndex() > endPosition) {
                lineState.finish();
                if (tok.getStartIndex() <= caretPos.start() && updateWithCaretPositionAndLength != null) {
                    int diff = positionBeforeProcessingToken - origCharPositionInLine();
                    updateWithCaretPositionAndLength.updateStart(caretPos.start() + diff);
                }
                // We have formatted all we were asked to; get out
                break;
            }
            // Let the state update its enum-keyed variables based on the
            // current token
            state.onBeforeProcessToken(tok, this);
            // Move to the next, and get us on the first token
            tokens.seek(i + 1);
            boolean res = false;
            try {
                // Find the next non-whitespace token
                ModalToken nxt = tokens.findSubsequent(i, notWhitespace);
                // If null, we are at EOF
                int nextType = nxt == null ? -1 : nxt.getType();
                // Make sure we're on that token
                tokens.seek(i);
                // Get the next token, since FormattingRule takes one look ahead
                // and one look behind
                ModalToken immediatelyNext = i == tokens.size() - 1 ? null : tokens.get(i + 1);
                boolean hasFollowingNewline = false;
                // Set our flag for whether there was a newline preceding in the
                // original text
                if (immediatelyNext != null && whitespace.test(immediatelyNext.getType())) {
                    hasFollowingNewline = immediatelyNext.newlinePositions().length > 0;
//                            immediatelyNext.getText() != null
//                            && immediatelyNext.getText().indexOf('\n') >= 0;
                }

                // Process the token
                if (res = onOneToken(tok, prevType, prevMode, nextType, tokens, hasFollowingNewline, tokensFormatted)) {
                    prevType = tok.getType();
                    prevMode = tok.mode();
                    tokensFormatted++;
                }
            } finally {
                if (res) {
                    onAfterProcessToken(tok, tok.getType());
                }
                // Let the state update its after-processing rules, while
                // letting the current token position be that of the token
                // we just processed, so callees will see that position
                lineState.withPreviousPosition(() -> {
                    state.onAfterProcessToken(tok, this);
                    if (caretPos.isViable() && caretToken == -1 && updateWithCaretPositionAndLength != null && tok.getType() != -1) {
                        if (tok.isSane() && tok.getStopIndex() >= caretPos.start() && tok.getStartIndex() <= caretPos.start()) {
                            if (!whitespace.test(tok.getType())) {
                                int offset = caretPos.start() - tok.getStartIndex();

                                // UGH, this is brutal, and it is our performance problem right here.
                                String rewrittenThusFar = rew.getText(new Interval(0, tok.getTokenIndex()));
                                int docPosition = (rewrittenThusFar.length()
                                        - tok.getText().length());
                                updateWithCaretPositionAndLength.updateStart(docPosition + offset);
                                caretUpdated[0] = true;
                            }
                        }
                    }
                });
                try {
                    // Consume the token, moving to the next
                    tokens.consume();
                } catch (IllegalStateException ex) {
                    // ok, consumed eof
                    break;
                }
            }
        }
        // If any token group rewriters were still chewing when we finished
        // parsing, allow them to clean up and do their replacing
        rules.finish(rew);
        FormattingResult res = getFormattingResult();
        if (!caretUpdated[0]) {
            if (updateWithCaretPositionAndLength != null && firstTokenInRange != 0) {
                updateWithCaretPositionAndLength.updateStart(startPosition);
            }
            if (updateWithCaretPositionAndLength != null) {
                updateWithCaretPositionAndLength.updateLength(res.text().length());
            }
        }
        // Discard cached info
        try {
            return getFormattingResult();
        } finally {
            close(stream);
        }
    }

    protected void close(EverythingTokenStream str) {
        // overridden by FormattingHarness for tests to avoid clearing state
        // so tests can call it after formatting is complete
        rew.close();
        str.close();
    }

    static boolean useCache = !Boolean.getBoolean("FormattingContextImpl.noCache");
    TokenCountCache cache;

    @Override
    public int tokenCountToNext(boolean ignoreWhitespace, IntPredicate targetType) {
        if (useCache) {
            if (cache == null) {
                cache = new TokenCountCache();
            }
            return cache.tokenCountToNext(stream, whitespace, ignoreWhitespace, targetType);
        }
        return defaultTokenCountToNext(ignoreWhitespace, targetType);
    }

    int defaultTokenCountToNext(boolean ignoreWhitespace, IntPredicate targetType) {
        // Original implementation - keep for tests to ensure caching implementation
        // does what it's supposed to
        int count = 0;
        for (int ix = stream.cursor + 1; ix < stream.size(); ix++) {
            Token t = stream.get(ix);
            if (targetType.test(t.getType())) {
                return count;
            }
            if (ignoreWhitespace && whitespace.test(t.getType())) {
                continue;
            }
            count++;
        }
        return -1;
    }

    @Override
    public int tokenCountToPreceding(boolean ignoreWhitespace, IntPredicate targetType) {
        if (useCache) {
            if (cache == null) {
                cache = new TokenCountCache();
            }
            return cache.tokenCountToPreceding(stream, whitespace, ignoreWhitespace, targetType);
        }
        return defaultTokenCountToPreceding(ignoreWhitespace, targetType);
    }

    int defaultTokenCountToPreceding(boolean ignoreWhitespace, IntPredicate targetType) {
        // Original implementation - keep for tests to ensure caching implementation
        // does what it's supposed to
        int count = 0;
        for (int ix = stream.cursor - 1; ix >= 0; ix--) {
            Token t = stream.get(ix);
            if (targetType.test(t.getType())) {
                return count;
            }
            if (ignoreWhitespace && whitespace.test(t.getType())) {
                continue;
            }
            count++;
        }
        return -1;
    }

    @Override
    public int countForwardOccurrencesUntilNext(IntPredicate toCount, IntPredicate stopType) {
        if (useCache) {
            if (cache == null) {
                cache = new TokenCountCache();
            }
            return cache.countForwardOccurrencesUntilNext(stream, toCount, stopType);
        }
        return defaultCountForwardOccurrencesUntilNext(toCount, stopType);
    }

    int defaultCountForwardOccurrencesUntilNext(IntPredicate toCount, IntPredicate stopType) {
        // Original implementation - keep for tests to ensure caching implementation
        // does what it's supposed to
        int count = 0;
        for (int ix = stream.cursor + 1; ix < stream.size(); ix++) {
            Token t = stream.get(ix);
            if (toCount.test(t.getType())) {
                count++;
            } else if (stopType.test(t.getType())) {
                break;
            }
        }
        return count;
    }

    @Override
    public int countBackwardOccurrencesUntilPrevious(IntPredicate toCount, IntPredicate stopType) {
        if (useCache) {
            if (cache == null) {
                cache = new TokenCountCache();
            }
            return cache.countBackwardOccurrencesUntilPrevious(stream, toCount, stopType);
        }
        return defaultCountBackwardOccurrencesUntilPrevious(toCount, stopType);
    }

    int defaultCountBackwardOccurrencesUntilPrevious(IntPredicate toCount, IntPredicate stopType) {
        // Original implementation - keep for tests to ensure caching implementation
        // does what it's supposed to
        int count = 0;
        for (int ix = stream.cursor - 1; ix >= 0; ix--) {
            Token t = stream.get(ix);
            if (toCount.test(t.getType())) {
                count++;
            } else if (stopType.test(t.getType())) {
                break;
            }
        }
        return count;
    }

    public FormattingResult getFormattingResult() {
        if (firstTokenInRange == -1) {
            return new FormattingResult(0, 0, "");
        }
        ModalToken first = stream.get(firstTokenInRange);
        ModalToken last = stream.get(lastTokenInRange);
        int start = first.getStartIndex();
        int end = last.getStopIndex() + 1;
        String text = rew.getText(new Interval(firstTokenInRange, lastTokenInRange));
        return new FormattingResult(start, end, text);
    }

    /**
     * Called by the main processing loop for every token processed.
     *
     * @param tok The token
     * @param prevType The type of the preceding token
     * @param prevMode The mode of the preceding token
     * @param nextType The type of the subsequent token
     * @param tokens The stream
     * @param hasFollowingNewline Whether a newline is subsequent in the
     * original document
     * @return true if the token was non-whitespace and was processed by the
     * rules
     */
    boolean onOneToken(ModalToken tok, int prevType, int prevMode, int nextType, EverythingTokenStream tokens, boolean hasFollowingNewline, int tokensFormatted) {
        int tokenType = tok.getType();
        if (whitespace.test(tok.getType()) || tok.isWhitespace()) {
            // We don't pass whitespace tokens on; but we do need to update
            // the line positions to reflect any newlines in the unprocessed
            // tokens
//            int newlinePosition = tok.getText().lastIndexOf('\n');
            int newlinePosition = tok.lastNewlinePosition();
            lastContainedNewline = newlinePosition >= 0;
            if (lastContainedNewline) {
                posInOriginalLine = (tok.length() - newlinePosition);
            }
            // Delete the whitespace token - we are applying our own whitespace
            // processing
            rew.delete(tok);
            if (!lastContainedNewline) {
                posInOriginalLine += tok.length();
            } else {
                posInOriginalLine = tok.length() - newlinePosition;
            }
            lineState.onUnprocessedToken(tok, false);
            return false;
        }
        // Check that we're within the bounds of the text we should reformat
        if (tok.getStartIndex() >= startPosition && tok.getStopIndex() <= endPosition) {
            // Set the first token if unset
            if (firstTokenInRange == -1) {
                // Prepended whitespace in the formatting result is a bad
                // thing, as it can cause lines to be pushed down the
                // page.  So postpone to the next token if we are in range
                // but this token is witespace
                if (!whitespace.test(tok.getType())) {
                    firstTokenInRange = tok.getTokenIndex();
                }
            }
            // Update the last token
            lastTokenInRange = tok.getTokenIndex();
            // Really process the token
//            lineState.lastToken = tok;
            onToken(tok, prevType, prevMode, tokenType, nextType, lastContainedNewline, hasFollowingNewline, tokensFormatted);
        }
        return true;
    }

    @Override
    public int charPositionInLine(Token token) {
        return rew.lastNewlineDistance(token.getTokenIndex());
    }

    void onAfterProcessToken(ModalToken tok, int tokenType) {
        // Update the line length with the token, etc.
        lineState.onToken(tok);
        posInOriginalLine = tok.getCharPositionInLine() + tok.length();
        // Update the flag with whether or not we just processed a newline,
        // for passing to FormattingRule instances run against the next
        // token

        int[] nlps = tok.newlinePositions();
        if (nlps.length > 0) {
            int last = nlps[nlps.length - 1];
            posInOriginalLine = (tok.getText().length() - last);
        }

        /*
        String txt = tok.getText();
        if (txt != null) {
            int len = txt.length();
            if (len > 0) {
                int ix = txt.lastIndexOf('\n');
                if (ix > 0) {
                    posInOriginalLine = txt.length() - ix;
                }
            }
        }
         */
        // If we are replacing the token text, do it now and clear the
        // replacement
        if (replacement != null) {
            rew.replace(tok, replacement);
            replacement = null;
        }
    }

    @Override
    public void replace(String replacement) {
        this.replacement = replacement;
    }

    @Override
    public void prependSpace() {
        lineState.prepend().space();
    }

    @Override
    public void appendSpace() {
        lineState.append().space();
    }

    @Override
    public void prependNewline() {
        lineState.prepend().newline();
    }

    @Override
    public void prependNewlineAndIndentBy(int amt) {
        lineState.prepend().newline().indentBy(amt);
    }

    @Override
    public void prependDoubleNewlineAndIndentBy(int amt) {
        lineState.prepend().doubleNewline().indentBy(amt);
    }

    @Override
    public void prependDoubleNewline() {
        lineState.prepend().doubleNewline();
    }

    @Override
    public void prependTripleNewline() {
        lineState.prepend().tripleNewLine();
    }

    @Override
    public void prependTripleNewlineAndIndentBy(int amt) {
        lineState.prepend().tripleNewLine().indentBy(amt);
    }

    @Override
    public void prependTripleNewlineAndIndent() {
        lineState.prepend().tripleNewLine().indent();
    }

    @Override
    public void appendNewlineAndIndentBy(int amt) {
        lineState.append().newline().indentBy(amt);
    }

    @Override
    public void prependNewlineAndIndent() {
        lineState.prepend().newline().indent();
    }

    @Override
    public void prependDoubleNewlineAndIndent() {
        lineState.prepend().doubleNewline().indent();
    }

    @Override
    public void appendNewline() {
        lineState.append().newline();
    }

    @Override
    public int origCharPositionInLine() {
        return posInOriginalLine;
    }

    @Override
    public int currentCharPositionInLine() {
        return lineState.currentTokenPosition();
    }

    @Override
    public void appendNewlineAndIndent() {
        lineState.append().newline().indent();
    }

    @Override
    public void appendNewlineAndDoubleIndent() {
        lineState.append().newline().doubleIndent();
    }

    @Override
    public void appendDoubleNewlineAndIndentBy(int amt) {
        lineState.append().doubleNewline().indentBy(amt);
    }

    @Override
    public void prependNewlineAndDoubleIndent() {
        lineState.prepend().doubleNewline().doubleIndent();
    }

    @Override
    public void appendDoubleNewline() {
        lineState.append().doubleNewline();
    }

    @Override
    public void indentBy(int stops) {
        lineState.prepend().indentBy(stops);
    }

    @Override
    public void indentBySpaces(int stops) {
        lineState.prepend().spaces(stops);
    }

    @Override
    public int indentSize() {
        return indentSize;
    }

    public int initialIndentSize() {
        return indentSize();
    }

    @Override
    public void indent() {
        lineState.prepend().indent();
    }

    private void onToken(ModalToken token, int prevType, int prevMode, int tokenType, int nextType, boolean hasPrecedingNewline, boolean hasFollowingNewline, int tokensFormattedThusFar) {
        boolean log = debugLogging.test(token);
        if (log) {
            System.out.println("\n\nProcess token " + token.getTokenIndex() + " '"
                    + token.getText() + "'\t"
                    + rules.vocabulary().getSymbolicName(tokenType) + " nextType "
                    + rules.vocabulary().getSymbolicName(nextType) + " prevType "
                    + rules.vocabulary().getSymbolicName(prevType) + " precedingNewline "
                    + hasPrecedingNewline + " followingNewline "
                    + hasFollowingNewline
                    + " mode " + token.modeName() + "(" + token.mode() + ")"
                    + " state " + state
            );
        }
        rules.apply(token, prevType, prevMode, nextType, hasPrecedingNewline, this, log, state, hasFollowingNewline, rew, ruleFinder, tokensFormattedThusFar == 0);
    }

    public int computedTokenPosition() {
        return lineState.computedTokenPosition();
    }

    int bruteForceComputedTokenPosition() {
        // for tests - this is always accurate
        return lineState.bruteForceTokenPosition();
    }

    /**
     * Manages and applies appended and prepended whitespace, and answers
     * questions about the current token's relationship to the current line
     * incorporating changes before they have really been applied, so
     * FormattingActions can be given accurate prospective answers to make
     * decisions based on.
     */
    private final class LineState {

        private int linePosition;
        private Token lastToken;
        private int lastPosition;
        private final WhitespaceLineState whitespace;
        private int documentPosition;
        private int lastDocumentPosition;
        private final int[] lineOffsetScratch = new int[]{linePosition};

        LineState(int indentDepth) {
            whitespace = new WhitespaceLineState(indentDepth);
        }

        int lastTokenIndex() {
            return lastToken == null ? 0 : lastToken.getTokenIndex();
        }

        WhitespaceState prepend() {
            // Get the prepend instructions
            return whitespace.prepend();
        }

        WhitespaceState append() {
            // Get the append instructions, which will be flipped to
            // prepend instructions for the next token
            return whitespace.append();
        }

        boolean onUnprocessedToken(ModalToken token, boolean postProcess) {
            // Look at tokens before we hit the region we care about, to
            // keep our line position up to date
            // Find any newlines and update the line position if needed
            int tokenTextLength = token.length();
            if (tokenTextLength > 0) {
                int newlineIndex = token.lastNewlinePosition();
                if (newlineIndex > 0) {
                    tokenTextLength -= newlineIndex;
                    lastPosition = linePosition;
                    linePosition = tokenTextLength;
                    return true;
                }
            }
            return false;
        }

        void withPreviousPosition(Runnable run) {
            // Put back whatever the position was before the last token
            // was processed, run the runnable and then set it back to
            // the current position
            int curr = linePosition;
            int currDoc = documentPosition;
            try {
                documentPosition = lastDocumentPosition;
                linePosition = lastPosition;
                run.run();
            } finally {
                linePosition = curr;
                documentPosition = currDoc;
            }
        }

        void onToken(ModalToken token) {
            // Get the current position and record the last token for
            // logging purposes
            lastPosition = currentTokenPosition();
            lastDocumentPosition = documentPosition;
            lastToken = token;
            if (!whitespace.isPrependEmpty()) {
                // If we need to prepend some whitespace, do it and update
                // the line position in the process
                lineOffsetScratch[0] = linePosition;
                String toInsert = whitespace.getPrependStringAndReset(lineOffsetScratch);
                // If we're formatting the middle of a document, don't prepend
                // spaces - it can move tokens away from each other in strange
                // ways.
                boolean skipPrepend = token.getTokenIndex() == firstTokenInRange && startPosition > 0;
                if (!skipPrepend) {
                    rew.insertBefore(token, toInsert);
                    documentPosition += toInsert.length();
                    linePosition = lineOffsetScratch[0];
                }
            } else {
                // Any append instructions for this token get flipped to
                // become prepend instructions for the next one
                whitespace.flip();
            }
            // Huh?
            if (!onUnprocessedToken(token, true)) {
                int len = token.length();
                documentPosition += len;
                linePosition += len;
                linePosition = whitespace.linePositionWithAppend(linePosition);
            }
        }

        public void finish() {
            // For the very last token, we need to use the append instructions
            // as append instructions, instead of flipping them to get coalesced
            // with any prepend instructions for the next token
            whitespace.flip();
            if (lastToken != null && !whitespace.isAppendEmpty()) {
                rew.insertAfter(lastToken, whitespace.getAppendStringAndReset());
            }
        }

        int computedTokenPosition() {
            // Get the current token position, taking into account any prepend
            // instructions that are pending
            int result = linePosition;
            if (!whitespace.isPrependEmpty()) {
                result = whitespace.linePositionWithPrepend(result);
            }
            return result;
        }

        int bruteForceTokenPosition() {
            // Keep this method around for tests
            if (lastToken == null) {
                return 0;
            }

            // This is a horrible way to do this
            String text = rew.getText(new Interval(0, lastToken.getTokenIndex()))
                    + whitespace.prepend().preview();

            // XXX could start by checking back two tokens, and work
            // backwards - even less efficient in the pathological case,
            // but could work it
            for (int i = text.length() - 1; i >= 0; i--) {
                if (text.charAt(i) == '\n') {
                    return (text.length() - 1) - i;
                }
            }
            return text.length();
        }

        public int currentTokenPosition() {
            if (lastToken == null) {
                return 0;
            }
            int result = rew.lastNewlineDistance(lineState.lastTokenIndex());
            result = lineState.whitespace.linePositionWithPrepend(result);
            return result;
        }
    }
}
