package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import org.antlr.v4.runtime.Token;

/**
 * Simple formatting action implementations, usable for most tasks.
 *
 * @author Tim Boudreau
 */
public enum SimpleFormattingAction implements FormattingAction {
    INDENT,
    PREPEND_SPACE, 
    PREPEND_NEWLINE,
    PREPEND_NEWLINE_AND_INDENT,
    PREPEND_NEWLINE_AND_DOUBLE_INDENT,
    APPEND_SPACE,
    APPEND_NEWLINE,
    APPEND_DOUBLE_NEWLINE,
    APPEND_NEWLINE_AND_INDENT,
    APPEND_NEWLINE_AND_DOUBLE_INDENT,
    ;

    @Override
    public void accept(Token tok, FormattingContext t) {
        switch (this) {
            case INDENT :
                t.indent();
                break;
            case PREPEND_SPACE:
                t.prependSpace();
                break;
            case PREPEND_NEWLINE:
                t.prependNewline();
                break;
            case APPEND_NEWLINE:
                t.appendNewline();
                break;
            case APPEND_DOUBLE_NEWLINE:
                t.appendDoubleNewline();
                break;
            case APPEND_NEWLINE_AND_INDENT:
                t.appendNewlineAndIndent();
                break;
            case APPEND_NEWLINE_AND_DOUBLE_INDENT:
                t.appendNewlineAndDoubleIndent();
                break;
            case PREPEND_NEWLINE_AND_INDENT:
                t.prependNewlineAndIndent();
                break;
            case APPEND_SPACE:
                t.appendSpace();
                break;
            case PREPEND_NEWLINE_AND_DOUBLE_INDENT :
                t.prependNewlineAndDoubleIndent();
                break;
            default:
                throw new AssertionError();
        }
    }

}
