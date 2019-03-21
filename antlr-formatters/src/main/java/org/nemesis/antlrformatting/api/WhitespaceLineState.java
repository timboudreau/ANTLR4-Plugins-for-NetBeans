package org.nemesis.antlrformatting.api;

/**
 * Manages the whitespace to be prepended and appended. All whitespace except
 * the last token in the file is handled by <i>prepending</i>. This class
 * collects prepend operations and append operations, and when the prepend is
 * performed, flips the prepend whitespace instructions to now be the set of
 * append instructions that were applied to the previous token. Applying the
 * prepend instructions coalesces the prepend and append values.
 *
 * @author Tim Boudreau
 */
final class WhitespaceLineState {

    private WhitespaceState prepend;
    private WhitespaceState append;

    WhitespaceLineState(int indentChars) {
        WhitespaceStringCache cache = new WhitespaceStringCache();
        prepend = new WhitespaceState(indentChars, cache);
        append = new WhitespaceState(indentChars, cache);
    }

    public WhitespaceState prepend() {
        return prepend;
    }

    public WhitespaceState append() {
        return append;
    }

    public boolean isPrependEmpty() {
        return prepend.isEmpty();
    }

    public boolean isAppendEmpty() {
        return append.isEmpty();
    }

    public String getAppendStringAndReset() {
        if (isAppendEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(20);
        applyAppend(sb);
        return sb.toString();
    }

    /**
     * Fetch the string that should be prepended, then swap the current append
     * instructions to the prepend position for the next token and clearing the
     * append instructions for use with that token.
     *
     * @param indentDepth The indent depth
     * @param lineOffsetDest An int array which is updated to reflect the new
     * character offset within the line.
     * @return A prepend string, null if no instructions were provided
     */
    public String getPrependStringAndReset(int[] lineOffsetDest) {
        if (isPrependEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(20);
        applyPrepend(sb, lineOffsetDest);
        return sb.toString();
    }

    public int linePositionWithPrepend(int origLinePosition) {
        return prepend.getLineOffset(origLinePosition);
    }

    public int linePositionWithAppend(int origLinePosition) {
        return append.getLineOffset(origLinePosition);
    }

    public void flip() {
        WhitespaceState curr = prepend;
        prepend = append.flip();
        append = curr.clear();
    }

    public void applyPrepend(StringBuilder sb, int[] lineOffsetDest) {
        WhitespaceState curr = prepend;
        curr.apply(sb, lineOffsetDest);
        prepend = append.flip();
        append = curr;
        assert append != prepend;
    }

    public int prependCharCount() {
        return prepend.charCount();
    }

    /**
     * Apply the append string to the passed string builder and clear it. This
     * is used only for the last token in a file; all others are turned into
     * prepends before application.
     *
     * @param sb A string builder to write into
     */
    public void applyAppend(StringBuilder sb) {
        append.apply(sb, new int[0]);
        append.clear();
    }

    /**
     * For logging purposes, get a description of the current prepend
     * instructions.
     *
     * @return A loggable description of the state of things
     */
    String prependAsString() {
        return prepend.description();
    }
}
