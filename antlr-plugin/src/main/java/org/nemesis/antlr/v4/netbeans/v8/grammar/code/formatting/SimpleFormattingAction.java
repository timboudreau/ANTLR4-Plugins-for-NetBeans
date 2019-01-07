package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.EnumSet;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * Simple formatting action implementations, usable for most tasks.
 *
 * @author Tim Boudreau
 */
public enum SimpleFormattingAction implements FormattingAction {
    INDENT,
    PREPEND_SPACE,
    PREPEND_DOUBLE_NEWLINE,
    PREPEND_NEWLINE,
    PREPEND_NEWLINE_AND_INDENT,
    PREPEND_NEWLINE_AND_DOUBLE_INDENT,
    APPEND_SPACE,
    APPEND_NEWLINE,
    APPEND_DOUBLE_NEWLINE,
    APPEND_NEWLINE_AND_INDENT,
    APPEND_NEWLINE_AND_DOUBLE_INDENT;

    public FormattingAction by(int amount) {
        return new IndentByAmount(this, amount);
    }

    static final class IndentByAmount implements FormattingAction {

        private final SimpleFormattingAction base;
        private final int amount;

        public IndentByAmount(SimpleFormattingAction base, int amount) {
            this.base = base;
            this.amount = amount;
        }

        public String toString() {
            return base.name() + "(" + amount + ")";
        }

        @Override
        public void accept(Token token, FormattingContext ctx, LexingState state) {
            switch (base) {
                case PREPEND_SPACE:
                case INDENT:
                    ctx.indentBy(amount);
                    break;
                case PREPEND_NEWLINE_AND_INDENT:
                    ctx.prependNewlineAndIndentBy(amount);
                    break;
                case APPEND_NEWLINE_AND_INDENT:
                    ctx.appendNewlineAndIndentBy(amount);
                    break;
                case PREPEND_NEWLINE_AND_DOUBLE_INDENT:
                    ctx.prependNewlineAndIndentBy(amount + ctx.indentSize());
                    break;
                case APPEND_NEWLINE_AND_DOUBLE_INDENT:
                    ctx.appendDoubleNewlineAndIndentBy(amount + ctx.indentSize());
                    break;
                default:
                    throw new AssertionError(base + " not an indenting formatter");
            }
        }
    }

    public <T extends Enum<T>> FormattingAction by(T amountKey) {
        if (!KeyAction.SUPPORTED.contains(this)) {
            throw new IllegalArgumentException("Not supported for keys: " + this);
        }
        return new KeyAction<>(amountKey, this);
    }

    @SafeVarargs
    public final <T extends Enum<T>> FormattingAction by(T amountKey, T... more) {
        if (!KeyAction.SUPPORTED.contains(this)) {
            throw new IllegalArgumentException("Not supported for keys: " + this);
        }
        return new KeyAction<>(amountKey, more, this);
    }

    static final class KeyAction<T extends Enum<T>> implements FormattingAction {

        private final T key;
        private T[] more;
        private final SimpleFormattingAction action;

        private static final Set<SimpleFormattingAction> SUPPORTED
                = EnumSet.of(PREPEND_SPACE, INDENT, PREPEND_NEWLINE_AND_INDENT,
                        PREPEND_NEWLINE_AND_DOUBLE_INDENT, APPEND_NEWLINE_AND_INDENT,
                        APPEND_NEWLINE_AND_DOUBLE_INDENT);

        public KeyAction(T key, SimpleFormattingAction action) {
            this.key = key;
            this.action = action;
        }

        public KeyAction(T key, T[] more, SimpleFormattingAction action) {
            this.key = key;
            this.more = more;
            this.action = action;
        }

        public String toString() {
            return action.name() + "(" + keysString() + ")";
        }

        private String keysString() {
            if (more == null) {
                return key.name();
            } else {
                StringBuilder sb = new StringBuilder(key.name());
                for (int i = 0; i < more.length; i++) {
                    sb.append('|').append(more[i].name());
                }
                return sb.toString();
            }
        }

        @Override
        public void accept(Token token, FormattingContext ctx, LexingState state) {
            int amt = state.get(key);
            if (amt <= 0) {
                if (more != null) {
                    for (T m : more) {
                        int nxt = state.get(key);
                        if (nxt > 0) {
                            if (amt == -1) {
                                amt = nxt;
                            } else {
                                amt = Math.min(amt, nxt);
                            }
                        }
                    }
                }
                if (amt <= 0) {
                    return;
                }
            }
            switch (action) {
                case PREPEND_SPACE:
                case INDENT:
                    ctx.indentBy(amt);
                    break;
                case PREPEND_NEWLINE_AND_INDENT:
                    ctx.prependNewlineAndIndentBy(amt);
                    break;
                case PREPEND_NEWLINE_AND_DOUBLE_INDENT:
                    ctx.prependNewlineAndIndentBy(amt + ctx.indentSize());
                    break;
                case APPEND_NEWLINE_AND_INDENT:
                    ctx.appendNewlineAndIndentBy(amt);
                    break;
                case APPEND_NEWLINE_AND_DOUBLE_INDENT:
                    ctx.appendDoubleNewlineAndIndentBy(amt + ctx.indentSize());
                default:
                    throw new AssertionError("Unsupported: " + this);
            }
        }
    }

    @Override
    public void accept(Token tok, FormattingContext t, LexingState state) {
        switch (this) {
            case INDENT:
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
            case PREPEND_DOUBLE_NEWLINE :
                t.prependDoubleNewline();
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
            case PREPEND_NEWLINE_AND_DOUBLE_INDENT:
                t.prependNewlineAndDoubleIndent();
                break;
            default:
                throw new AssertionError();
        }
    }

}
