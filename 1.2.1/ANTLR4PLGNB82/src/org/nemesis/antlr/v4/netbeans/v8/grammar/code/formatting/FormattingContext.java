package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.function.IntPredicate;

/**
 * Formatting context passed into the lambda that performs formatting operations
 * for a matched token.
 *
 * @author Tim Boudreau
 */
interface FormattingContext {

    /**
     * Set an ad-hoc integer value which may be used by another lambda, such as
     * the position of some character.
     *
     * @param <T> The type of the key
     * @param key The key
     * @param val The value
     */
    <T> void set(T key, int val);

    /**
     * Get an ad-hoc integer value which may have been previously set. The
     * implementation sets two magic values, "colon" and "lparen".
     *
     * @param <T> The key type
     * @param key The key
     * @param defaultValue A default value to use if not set
     * @return An integer value
     */
    <T> int get(T key, int defaultValue);

    int countForwardOccurrencesUntilNext(int toCount, int stopType);

    int countBackwardOccurrencesUntilPrevious(int toCount, int stopType);

    int countForwardOccurrencesUntilNext(IntPredicate toCount, IntPredicate stopType);

    int countBackwardOccurrencesUntilPrevious(IntPredicate toCount, IntPredicate stopType);

    /**
     * Count the number of tokens, optionally ignoring whitespace, until the
     * next occurrence of the target token type. Note that on broken sources
     * (unclosed parentheses, etc.) this may give unlikely values.
     *
     * @param ignoreWhitespace If true, don't include whitespace in the count.
     * @param targetType The target type
     * @return The number of tokens
     */
    int tokenCountToNext(boolean ignoreWhitespace, int targetType);

    /**
     * Count the number of tokens, optionally ignoring whitespace, backward to
     * the previous occurrence of the target token type. Note that on broken
     * sources (unclosed parentheses, etc.) this may give unlikely values.
     *
     * @param ignoreWhitespace If true, don't include whitespace in the count.
     * @param targetType The target type
     * @return The number of tokens
     */
    int tokenCountToPreceding(boolean ignoreWhitespace, int targetType);

    /**
     * Get the character position in the line of the original token in the
     * unmodified text.
     */
    int origCharPositionInLine();

    /**
     * Get the current character position in the line where the current token
     * will be rendered, accounting for any preceding calls to prepend for this
     * token.
     *
     * @return
     */
    int currentCharPositionInLine();

    /**
     * Prepend a space before this token.
     */
    void prependSpace();

    /**
     * Prepend a newline before this token.
     */
    void prependNewline();

    /**
     * Prepend a newline and indent to the colon position or the settings indent
     * depth for this token (depending on whether or not colons are put on a new
     * line).
     */
    void prependNewlineAndIndent();

    /**
     * Prepend a newline and indent to the colon position or the settings indent
     * depth for this token (depending on whether or not colons are put on a new
     * line).
     */
    void prependNewlineAndDoubleIndent();

    /**
     * Prepend a newline and indent by a specific number of characters.
     *
     * @param amt The number of characters
     */
    void prependNewlineAndIndentBy(int amt);

    /**
     * Append a space after the current token. Note that if the subsequent token
     * calls prependSpace() after a rule for the preceding token called
     * appendSpace(), only a single space will be inserted.
     */
    void appendSpace();

    /**
     * Append a newline before this token.
     */
    void appendNewline();

    /**
     * Append two newlines before this token.
     */
    void appendDoubleNewline();

    /**
     * Append a newline and indent before this token.
     */
    void appendNewlineAndIndent();

    /**
     * Append a newline and double indent the next line.
     */
    void appendNewlineAndDoubleIndent();

    /**
     * Indent this token by the indent amount.
     */
    void indent();

    /**
     * Replace the text of this token with some other text.
     *
     * @param replacement The replacement text.
     */
    void replace(String replacement);
}
