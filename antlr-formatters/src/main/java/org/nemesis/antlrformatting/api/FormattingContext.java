package org.nemesis.antlrformatting.api;

/**
 * Formatting context passed into the lambda that performs formatting operations
 * for a matched token.
 *
 * @author Tim Boudreau
 */
public abstract class FormattingContext {

    FormattingContext() {

    }

    /**
     * Get the character position in the line of the original token in the
     * unmodified text.
     */
    public abstract int origCharPositionInLine();

    public abstract int indentSize();

    /**
     * Get the current character position in the line where the current token
     * will be rendered, accounting for any preceding calls to prepend for this
     * token.
     *
     * @return
     */
    public abstract int currentCharPositionInLine();

    /**
     * Prepend a space before this token.
     */
    public abstract void prependSpace();

    /**
     * Prepend a newline before this token.
     */
    public abstract void prependNewline();

    /**
     * Prepend a newline and indent to the colon position or the settings indent
     * depth for this token (depending on whether or not colons are put on a new
     * line).
     */
    public abstract void prependNewlineAndIndent();

    /**
     * Prepend a newline and indent to the colon position or the settings indent
     * depth for this token (depending on whether or not colons are put on a new
     * line).
     */
    public abstract void prependNewlineAndDoubleIndent();

    /**
     * Prepend a newline and indent by a specific number of characters.
     *
     * @param amt The number of characters
     */
    public abstract void prependNewlineAndIndentBy(int amt);

    public abstract void prependDoubleNewline();

    public abstract void prependDoubleNewlineAndIndent();

    public abstract void prependDoubleNewlineAndIndentBy(int amt);

    /**
     * Prepend a newline and indent by a specific number of characters.
     *
     * @param amt The number of characters
     */
    public abstract void appendNewlineAndIndentBy(int amt);

    public abstract void appendDoubleNewlineAndIndentBy(int amt);

    /**
     * Append a space after the current token. Note that if the subsequent token
     * calls prependSpace() after a rule for the preceding token called
     * appendSpace(), only a single space will be inserted.
     */
    public abstract void appendSpace();

    /**
     * Append a newline before this token.
     */
    public abstract void appendNewline();

    /**
     * Append two newlines before this token.
     */
    public abstract void appendDoubleNewline();

    /**
     * Append a newline and indent before this token.
     */
    public abstract void appendNewlineAndIndent();

    /**
     * Append a newline and double indent the next line.
     */
    public abstract void appendNewlineAndDoubleIndent();

    /**
     * Indent this token by the indent amount.
     */
    public abstract void indent();

    /**
     * Indent this token by the indent amount.
     */
    public abstract void indentBy(int stops);

    /**
     * Indent this token by the indent amount.
     */
    public abstract void indentBySpaces(int spaces);

    /**
     * Replace the text of this token with some other text.
     *
     * @param replacement The replacement text.
     */
    public abstract void replace(String replacement);
}
