package org.nemesis.antlrformatting.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.misc.Interval;

/**
 * A temporary hack to ensure that FormattingContextImpl really returns the
 * right current line position. You can do that by simply calling the rewriter's
 * getText() method for all processed tokens, but it is very slow and does a lot
 * of work, when the only thing we are interested in is how many characters
 * behind the current token is the last newline in the revised text. This
 * instead keeps an index of the last token that was modified to include a
 * newline, and handles it that way. Seriously, doing it the brute force way
 * means a one-second freeze when reformatting with very simple rules.
 *
 * @author Tim Boudreau
 */
final class LinePositionComputingRewriter extends TokenStreamRewriter {

    private int rev = 0;
    private int lastRequestedNlPos = -1;
    private int lastRequestedNlPosResult = -1;
    private int lastRequestRev;
    private final StringBuilder scratch = new StringBuilder(120);
    private int lastTokenModifiedWithNewline;

    LinePositionComputingRewriter(TokenStream tokens) {
        super(tokens);
    }

    private void maybeUpdateNewlineTokenIndex(int token, Object text) {
        if (containsNewline(text)) {
            lastTokenModifiedWithNewline = token;
        }
    }

    int index(RewriteOperation rew) {
        // An awkward way to get the value of the internal index
        // field of a rewrite operation
        int result = rew.execute(scratch);
        scratch.setLength(0);
        String name = rew.getClass().getSimpleName();
        if ("InsertBeforeOp".equals(name) || "ReplaceOp".equals(name)) {
            result--;
        }
        return result;
    }

    private boolean containsNewline(Object text) {
        return text != null && text.toString().indexOf('\n') >= 0;
    }

    @Override
    public void replace(String programName, int from, int to, Object text) {
        maybeUpdateNewlineTokenIndex(to, text);
        super.replace(programName, from, to, text);
        rev++;
    }

    @Override
    public void insertBefore(String programName, int index, Object text) {
        maybeUpdateNewlineTokenIndex(index, text);
        super.insertBefore(programName, index, text);
        rev++;
    }

    @Override
    public void insertAfter(String programName, int index, Object text) {
        maybeUpdateNewlineTokenIndex(index + 1, text);
        super.insertAfter(programName, index, text);
        rev++;
    }

    private List<RewriteOperation> filteredRewrites(int start, int end) {
        List<RewriteOperation> rewrites = programs.get(DEFAULT_PROGRAM_NAME);
        if (rewrites == null) {
            return null;
        }
        List<RewriteOperation> result = new ArrayList<>(rewrites.size() + 1);
        for (RewriteOperation rew : rewrites) {
            // This is messy.  reduceToSingleOperationPerIndex assumes
            // that it each operation's private index field will match
            // its position in the list, so if we want to skip irrelevant
            // formatting operations that occurred before the last newline,
            // we MUST provide nulls for those or all hell breaks loose
            if (rew == null) {
                result.add(null);
                continue;
            }
            int ix = index(rew);
            if (ix >= start && ix <= end) {
                result.add(rew);
            } else {
                result.add(null);
            }
        }
        return result;
    }

    private int trailingCharactersAfterNewline(String txt) {
        int ix = txt.lastIndexOf('\n');
        if (ix < 0) {
            return txt.length();
        }
        return (txt.length() - 1) - ix;
    }

    int cachedResult(int tokenIndex) {
        if (rev == lastRequestRev && lastRequestedNlPos == tokenIndex) {
            return lastRequestedNlPosResult;
        }
        return -1;
    }

    public int lastNewlineDistance(int tokenIndex) {
        int res = cachedResult(tokenIndex);
        if (res >= 0) {
            return res;
        }
        lastRequestRev = rev;
        lastRequestedNlPos = tokenIndex;
        return lastRequestedNlPosResult = _lastNewlineDistance(tokenIndex);
    }

    private int _lastNewlineDistance(int tokenIndex) {

        int start = lastTokenModifiedWithNewline;
        int stop = tokenIndex;

        if (stop < start) {
            return 0;
        }

        // ensure start/end are in range
        if (stop > tokens.size() - 1) {
            stop = Math.min(tokenIndex, tokens.size() - 1);
        }

        List<RewriteOperation> rewrites = filteredRewrites(start, stop);
        if (rewrites == null || rewrites.isEmpty()) {
            return trailingCharactersAfterNewline(tokens.getText(new Interval(start, stop)));
        }

        // First, optimize instruction stream
        Map<Integer, RewriteOperation> indexToOp = reduceToSingleOperationPerIndex(rewrites);

        int result = 0;
        // Walk buffer, executing instructions and emitting tokens
        for (int i = stop; i >= 0; i--) {
            RewriteOperation op = indexToOp.get(i);
            indexToOp.remove(i); // remove so any left have index size-1
            Token t = tokens.get(i);
            if (op == null) {
                // no operation at that index, just dump token
                if (t.getType() != Token.EOF) {
                    String txt = t.getText();
                    int ix = txt.lastIndexOf('\n');
                    if (ix >= 0) {
                        result += (txt.length() - 1) - ix;
                        break;
                    } else {
                        result += txt.length();
                    }
                }
            } else {
                scratch.setLength(0);
                op.execute(scratch);
                if (scratch.length() > 0) {
                    int ix = lastIndexOfNewline(scratch);
                    if (ix >= 0) {
                        result += (scratch.length() - 1) - ix;
                        break;
                    } else {
                        result += scratch.length();
                    }
                }
            }
        }
        return result;
    }

    String text(RewriteOperation op) {
        StringBuilder sb = new StringBuilder();
        op.execute(sb);
        return sb.toString();
    }

    private int lastIndexOfNewline(StringBuilder sb) {
        int max = sb.length() - 1;
        for (int i = max; i >= 0; i--) {
            if (sb.charAt(i) == '\n') {
                return i;
            }
        }
        return -1;
    }
}
