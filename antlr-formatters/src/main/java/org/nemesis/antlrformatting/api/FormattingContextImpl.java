package org.nemesis.antlrformatting.api;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.misc.Interval;

/**
 *
 * @author Tim Boudreau
 */
class FormattingContextImpl extends FormattingContext implements LexerScanner {

    final TokenStreamRewriter rew;
    final int startToken;
    final int endToken;
    final int indentSize;
    final FormattingRules rules;
    private boolean lastContainedNewline;
    private int posInOriginalLine;
    private String lastAppended;
    String replacement;
    private EverythingTokenStream stream;
    final LexingState state;
    final Criterion notWhitespace;
    private final LineState lineState = new LineState();
    final Criterion whitespace;
    final Predicate<Token> debugLogging;

    FormattingContextImpl(TokenStreamRewriter rew, int start, int end, int indentSize,
            FormattingRules rules, LexingState state, Criterion whitespace) {
        this(rew, start, end, indentSize, rules, state, whitespace, t -> {
            return false;
        });
    }

    FormattingContextImpl(TokenStreamRewriter rew, int start, int end,
            int indentSize, FormattingRules rules, LexingState state,
            Criterion whitespace, Predicate<Token> debugLogging) {
        this.rew = rew;
        this.startToken = start;
        this.endToken = end;
        this.rules = rules;
        this.indentSize = indentSize;
        this.state = state;
        this.notWhitespace = whitespace.negate();
        this.whitespace = whitespace;
        this.debugLogging = debugLogging;
    }

    String go(Lexer lexer, EverythingTokenStream tokens) {
        state.clear();
        stream = tokens;
        tokens.seek(0);
        int prevType = -1;
        int size = tokens.size();
        for (int i = 0; i < size; i++) {
            ModalToken tok = tokens.get(i);
            if (tok.getStartIndex() > endToken) {
                lineState.finish();
                // We have formatted all we were asked to; get out
                break;
            }
            state.onBeforeProcessToken(tok, this);
            tokens.seek(i + 1);
            boolean res = false;
            try {
                ModalToken nxt = tokens.findSubsequent(i, notWhitespace);
                int nextType = nxt == null ? -1 : nxt.getType();
                tokens.seek(i);
                ModalToken immediatelyNext = i == tokens.size() - 1 ? null : tokens.get(i + 1);
                boolean hasFollowingNewline = false;
                if (immediatelyNext != null && whitespace.test(immediatelyNext.getType())) {
                    hasFollowingNewline = immediatelyNext.getText() != null
                            && immediatelyNext.getText().indexOf('\n') >= 0;
                }
                if (res = onOneToken(tok, prevType, nextType, tokens, hasFollowingNewline)) {
                    prevType = tok.getType();
                }
            } finally {
                if (res) {
                    onAfterProcessToken(tok, tok.getType());
                }
                lineState.withPreviousPosition(() -> {
                    state.onAfterProcessToken(tok, this);
                });
                try {
                    tokens.consume();
                } catch (IllegalStateException ex) {
                    break;
                }
            }
        }
        // If any token group rewriters were still chewing when we finished
        // parsing, allow them to clean up and do their replacing
        rules.finish(rew);
        return getModifiedText();
    }

    @Override
    public int tokenCountToNext(boolean ignoreWhitespace, IntPredicate targetType) {
        int count = 0;
        for (int ix = stream.cursor + 1; ix < stream.size(); ix++) {
            Token t = stream.get(ix);
            if (targetType.test(t.getType())) {
                break;
            }
            boolean isWhitespace = whitespace.test(t.getType());
            if (isWhitespace && ignoreWhitespace) {
                continue;
            }
            count++;
        }
        return count;
    }

    @Override
    public int tokenCountToPreceding(boolean ignoreWhitespace, IntPredicate targetType) {
        int count = 0;
        for (int ix = stream.cursor - 1; ix >= 0; ix--) {
            Token t = stream.get(ix);
            if (targetType.test(t.getType())) {
                break;
            }
            boolean isWhitespace = whitespace.test(t.getType());
            if (isWhitespace && ignoreWhitespace) {
                continue;
            }
            count++;
        }
        return count;
    }

    @Override
    public int countForwardOccurrencesUntilNext(IntPredicate toCount, IntPredicate stopType) {
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

    public String getModifiedText() {
        if (firstTokenInRange == -1) {
            return "";
        }
        return rew.getText(new Interval(firstTokenInRange, lastTokenInRange));
    }

    int firstTokenInRange = -1;
    int lastTokenInRange = -1;

    boolean onOneToken(ModalToken tok, int prevType, int nextType, EverythingTokenStream tokens, boolean hasFollowingNewline) {
        int tokenType = tok.getType();
        if (whitespace.test(tok.getType()) || tok.getText().trim().length() == 0) {
            int newlinePosition = tok.getText().lastIndexOf('\n');
            lastContainedNewline = newlinePosition >= 0;
            if (lastContainedNewline) {
                posInOriginalLine = (tok.getText().length() - newlinePosition);
            }
            rew.delete(tok);
            if (!lastContainedNewline) {
                posInOriginalLine += tok.getText().length();
            } else {
                posInOriginalLine = tok.getText().length() - newlinePosition;
            }
            lineState.onUnprocessedToken(tok);
            return false;
        }
        if (tok.getStartIndex() >= startToken && tok.getStopIndex() <= endToken) {
            if (firstTokenInRange == -1) {
                firstTokenInRange = tok.getTokenIndex();
            }
            lastTokenInRange = tok.getTokenIndex();
            onToken(tok, prevType, tokenType, nextType, lastContainedNewline, hasFollowingNewline);
        }
        return true;
    }

    void onAfterProcessToken(ModalToken tok, int tokenType) {
//        posInOriginalLine += tok.getText().length();
        lineState.onToken(tok);
        posInOriginalLine = tok.getCharPositionInLine() + ((tok.getStopIndex() + 1) - tok.getStartIndex());
        lastContainedNewline = false;
        String txt = tok.getText();
        int len = 0;
        if (txt != null) {
            len = txt.length();
            if (len > 0) {
                int ix = txt.lastIndexOf('\n');
                if (ix > 0) {
                    lastContainedNewline = true;
                    len -= (ix + 1);
                    posInOriginalLine = txt.length() - ix;
                }
            }
        }
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
        if (!Objects.equals(lastAppended, " ")) {
            if (getPrepend() != null && getPrepend().length() > 1) {
                return;
            }
            setPrepend(" ");
        }
    }

    @Override
    public void appendSpace() {
        if (getAppend() != null && getAppend().length() > 1) {
            return;
        }
        setAppend(" ");
    }

    @Override
    public void prependNewline() {
        setPrepend("\n");
    }

    @Override
    public void prependNewlineAndIndentBy(int amt) {
        setPrepend("\n" + spacesString(amt));
    }

    @Override
    public void prependDoubleNewline() {
        setPrepend("\n\n");
    }

    @Override
    public void appendNewlineAndIndentBy(int amt) {
        setAppend("\n" + spacesString(amt));
    }

    @Override
    public void prependNewlineAndIndent() {
        setPrepend("\n" + indentString(1));
    }

    @Override
    public void appendNewline() {
        setAppend("\n");
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
        setAppend("\n" + indentString(1));
    }

    @Override
    public void appendNewlineAndDoubleIndent() {
        setAppend("\n" + indentString(2));
    }

    @Override
    public void appendDoubleNewlineAndIndentBy(int amt) {
        setAppend("\n\n" + spacesString(amt + indentSize));
    }

    @Override
    public void prependNewlineAndDoubleIndent() {
        setPrepend("\n" + spacesString(indentSize() * 2));
    }

    @Override
    public void appendDoubleNewline() {
        setAppend("\n\n");
    }

    @Override
    public void indentBy(int spaces) {
        setPrepend(spacesString(spaces));
    }

    public int indentSize() {
        return indentSize;
    }

    public int initialIndentSize() {
        return indentSize();
    }

    @Override
    public void indent() {
        if (this.currentCharPositionInLine() != 0) {
            int offset = this.currentCharPositionInLine() % indentSize();
            if (offset > 0) {
                setPrepend(spacesString(offset));
                return;
            }
        }
        int len = initialIndentSize();
        if (getPrepend() == null) {
            setPrepend(spacesString(len));
        } else {
            setPrepend(spacesString(indentSize()));
        }
    }

    String indentString(int amt) {
        if (amt == 1) {
            return spacesString(initialIndentSize());
        } else if (amt > 1) {
            return spacesString(initialIndentSize() + ((amt - 1) * indentSize()));
        }
        return spacesString(amt * indentSize);
    }

    String getPrepend() {
        return lineState.prepend;
    }

    void setPrepend(String prepend) {
        lineState.prepend(prepend);
    }

    String getAppend() {
        return lineState.append;
    }

    void setAppend(String append) {
        lineState.append(append);
    }
    private final String[] cachedSpacesStrings = new String[32];

    String spacesString(int amt) {
        if (amt < cachedSpacesStrings.length) {
            if (cachedSpacesStrings[amt] != null) {
                return cachedSpacesStrings[amt];
            }
        }
        char[] chars = new char[Math.max(0, amt)];
        Arrays.fill(chars, ' ');
        String result = new String(chars);
        if (amt < cachedSpacesStrings.length) {
            cachedSpacesStrings[amt] = result;
        }
        return result;
    }

    private void onToken(ModalToken token, int prevType, int tokenType, int nextType, boolean hasPrecedingNewline, boolean hasFollowingNewline) {
        boolean log = debugLogging.test(token);
        if (log) {
            System.out.println("\nPROC '"
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
        rules.apply(token, prevType, nextType, hasPrecedingNewline, this, log, state, hasFollowingNewline, rew);
    }

    private FormattingRule currentRule;

    void setCurrentRule(FormattingRule rule) {
        currentRule = rule;
    }

    private final class LineState {

        private String prepend;
        private String append;
        private int linePosition;
        private boolean prependingNewline;
        private int prependOffset;
        private Token lastToken;
        private int lastPosition;
        private FormattingRule lastRule;

        boolean onUnprocessedToken(Token token) {
            int tokenTextLength = 0;
            String tokenText = token.getText();
            if (tokenText != null && tokenText.length() > 0) {
                tokenTextLength = tokenText.length();
                int newlineIndex = tokenText.lastIndexOf('\n');
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
            int curr = linePosition;
            try {
                linePosition = lastPosition;
                run.run();
            } finally {
                linePosition = curr;
            }
        }

        void onToken(Token token) {
            lastPosition = currentCharPositionInLine();
            lastToken = token;
            if (prepend != null) {
//                System.out.println("PREPEND '" + prepend + "' to '" + token.getText() + "'");
                rew.insertBefore(token, prepend);
                if (prependingNewline) {
                    linePosition = prependOffset;
                } else {
                    linePosition += prependOffset;
                }
            }
            if (!onUnprocessedToken(token)) {
                linePosition += token.getText().length();
            }
//            System.out.println("AFTER '" + token.getText() + "' lp " + linePosition);
            prepend = append;
            append = null;
            onPrependUpdated(prepend);
        }

        public void finish() {
            if (lastToken != null && prepend != null) {
                rew.insertAfter(lastToken, prepend);
            }
        }

        public int currentTokenPosition() {
            int result = linePosition;
            if (prependingNewline) {
                result = prependOffset;
            } else {
                result += prependOffset;
            }
            return result;
        }

        public void prepend(String toPrepend) {
//            System.out.println("PREPEND '" + toPrepend + "' by " + currentRule);
            if (this.prepend == null) {
                this.prepend = toPrepend;
            } else {
                this.prepend = coalescePrepend(this.prepend, toPrepend);
            }
            onPrependUpdated(this.prepend);
            lastRule = currentRule;
        }

        public void append(String toAppend) {
            if (this.append == null) {
                append = toAppend;
            } else {
                append = coalesceAppend(append, toAppend);
            }
            lastRule = currentRule;
        }

        private void onPrependUpdated(String prepend) {
            if (prepend == null) {
                prependingNewline = false;
                prependOffset = 0;
                return;
            }
            if (prepend.length() == 1 && prepend.charAt(prepend.length() - 1) == '\n') {
                prependingNewline = true;
                prependOffset = 0;
                return;
            }
            int ix = prepend.lastIndexOf('\n');
            if (ix >= 0) {
                prependOffset = prepend.length() - (ix + 1);
                prependingNewline = true;
            } else {
                prependOffset = prepend.length();
                prependingNewline = false;
            }
        }

        private String coalesceAppend(String old, String nue) {
            return coalescePrepend(old, nue);
        }

        private int newlineCount(String s) {
            int result = 0;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '\n') {
                    result++;
                }
            }
            return result;
        }

        private String coalescePrepend(String old, String nue) {
//            System.out.println("COALESCE '" + old + "' and '" + nue + "'");
//            System.out.println("FROM " + lastRule + " and " + currentRule);
            lastRule = currentRule;
            // Two identical prepend strings means just use one of them
            if (old.equals(nue)) {
                return nue;
            }
            // If one prepend string is the empty string, use the non empty one
            if (old.isEmpty() && !nue.isEmpty()) {
                return nue;
            } else if (nue.isEmpty() && !old.isEmpty()) {
                return old;
            }
            int newlineIndexOld = old.lastIndexOf('\n');
            int newlineIndexNew = nue.lastIndexOf('\n');
            // If one string contains a newline and one doesn't
            // Then if the one then doesn't consists of a single space,
            // throw that one away and use the other; otherwise
            // concatenate them, prepending the one which does contain
            // a newline to the one that doesn't
            if (newlineIndexOld != newlineIndexNew) {
                if (newlineIndexOld >= 0 && newlineIndexNew < 0) {
                    if (nue.length() == 1) {
                        return old;
                    }
                    return old + nue;
                } else if (newlineIndexNew >= 0 && newlineIndexOld < 0) {
                    if (old.length() == 1) {
                        return nue;
                    }
                    return nue + old;
                }
            }
            // If neither contains a newline, take the longer of the two
            if (newlineIndexOld < 0 && newlineIndexNew < 0) {
                return old.length() > nue.length() ? old : nue;
            }
            // Prefer the one which contains more newlines to less if both
            // contain newlines
            int newlinesOld = newlineCount(old);
            int newlinesNew = newlineCount(nue);
            if (newlinesNew > 0 && newlinesOld > 0) {
                if (newlinesNew > newlinesOld) {
                    return nue;
                } else if (newlinesNew < newlinesOld) {
                    return old;
                }
            }
            // Otherwise take the shorter of the two
            if (old.length() > nue.length()) {
                return old;
            } else {
                return nue;
            }
        }
    }
}
