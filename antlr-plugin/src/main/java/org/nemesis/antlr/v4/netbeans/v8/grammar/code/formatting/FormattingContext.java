package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

/**
 * Formatting context passed into the lambda that performs formatting operations
 * for a matched token.
 *
 * @author Tim Boudreau
 */
interface FormattingContext {
    /**
     * Get the character position in the line of the original token in the
     * unmodified text.
     */
    int origCharPositionInLine();

    int indentSize();

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

    void prependDoubleNewline();

    /**
     * Prepend a newline and indent by a specific number of characters.
     *
     * @param amt The number of characters
     */
    void appendNewlineAndIndentBy(int amt);

    void appendDoubleNewlineAndIndentBy(int amt);

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
     * Indent this token by the indent amount.
     */
    void indentBy(int spaces);

    /**
     * Replace the text of this token with some other text.
     *
     * @param replacement The replacement text.
     */
    void replace(String replacement);
}
