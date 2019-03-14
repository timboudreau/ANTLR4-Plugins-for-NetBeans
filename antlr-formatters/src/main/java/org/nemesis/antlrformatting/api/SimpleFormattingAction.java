package org.nemesis.antlrformatting.api;

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
    PREPEND_DOUBLE_NEWLINE_AND_INDENT,
    PREPEND_NEWLINE,
    PREPEND_NEWLINE_AND_INDENT,
    PREPEND_NEWLINE_AND_DOUBLE_INDENT,
    APPEND_SPACE,
    APPEND_NEWLINE,
    APPEND_DOUBLE_NEWLINE,
    APPEND_NEWLINE_AND_INDENT,
    APPEND_NEWLINE_AND_DOUBLE_INDENT;

    public FormattingAction by(int amount) {
        return new IndentByAmount(this, amount, false);
    }

    public FormattingAction bySpaces(int amount) {
        return new IndentByAmount(this, amount, true);
    }

    public String toString() {
        return name().toLowerCase();
    }

    static final class IndentByAmount implements FormattingAction {

        private final SimpleFormattingAction base;
        private final int amount;
        private final boolean spacesNotStops;

        IndentByAmount(SimpleFormattingAction base, int amount, boolean spacesNotStops) {
            this.base = base;
            this.amount = amount;
            this.spacesNotStops = spacesNotStops;
        }

        @Override
        public String toString() {
            return base.toString() + "(" + amount
                    + (spacesNotStops ? "-spaces" : "-tab-stops")
                    + ")";
        }

        @Override
        public void accept(Token token, FormattingContext ctx, LexingState state) {
            if (amount <= 0) {
                return;
            }
            switch (base) {
                case PREPEND_SPACE:
                case INDENT:
                    if (amount > 0) {
                        if (spacesNotStops) {
                            ctx.indentBySpaces(amount);
                        } else {
                            ctx.indentBy(amount);
                        }
                    }
                    break;
                case PREPEND_NEWLINE_AND_INDENT:
                    if (spacesNotStops) {
                        ctx.prependNewline();
                        if (amount > 0) {
                            ctx.indentBySpaces(amount);
                        }
                    } else {
                        if (amount > 0) {
                            ctx.prependNewlineAndIndentBy(amount);
                        } else {
                            ctx.prependNewline();
                        }
                    }
                    break;
                case APPEND_NEWLINE_AND_INDENT:
                    if (spacesNotStops) {
                        ctx.appendNewline();
                        if (amount > 0) {
                            ctx.indentBySpaces(amount);
                        }
                    } else {
                        if (amount > 0) {
                            ctx.appendNewlineAndIndentBy(amount);
                        } else {
                            ctx.appendNewline();
                        }
                    }
                    break;
                case PREPEND_NEWLINE_AND_DOUBLE_INDENT:
                    if (spacesNotStops) {
                        ctx.prependNewline();
                        if (amount > 0) {
                            ctx.indentBySpaces(amount + ctx.indentSize());
                        } else {
                            ctx.indent();
                        }
                    } else {
                        ctx.prependNewlineAndIndentBy(amount + 1);
                    }
                    break;
                case PREPEND_DOUBLE_NEWLINE_AND_INDENT:
                    if (spacesNotStops) {
                        ctx.prependDoubleNewline();
                        if (amount > 0) {
                            ctx.indentBySpaces(amount);
                        }
                    } else {
                        if (amount > 0) {
                            ctx.prependDoubleNewlineAndIndentBy(amount + 1);
                        } else {
                            ctx.prependDoubleNewlineAndIndent();
                        }
                    }
                    break;
                case APPEND_NEWLINE_AND_DOUBLE_INDENT:
                    if (spacesNotStops) {
                        ctx.appendNewline();
                        if (amount > 0) {
                            ctx.indentBySpaces(amount + ctx.indentSize());
                        } else {
                            ctx.indentBySpaces(ctx.indentSize());
                        }
                    } else {
                        ctx.appendNewline();
                        if (amount > 0) {
                            ctx.indentBy(amount + 1);
                        } else {
                            ctx.indent();
                        }
                    }
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

    public <T extends Enum<T>> FormattingAction bySpaces(T amountKey) {
        if (!KeyAction.SUPPORTED.contains(this)) {
            throw new IllegalArgumentException("Not supported for keys: " + this);
        }
        return new KeyAction<>(amountKey, this, true);
    }

    @SafeVarargs
    public final <T extends Enum<T>> FormattingAction by(T amountKey, T... more) {
        if (!KeyAction.SUPPORTED.contains(this)) {
            throw new IllegalArgumentException("Not supported for keys: " + this);
        }
        return new KeyAction<>(amountKey, more, this);
    }

    @SafeVarargs
    public final <T extends Enum<T>> FormattingAction bySpaces(T amountKey, T... more) {
        if (!KeyAction.SUPPORTED.contains(this)) {
            throw new IllegalArgumentException("Not supported for keys: " + this);
        }
        return new KeyAction<>(amountKey, more, this, true);
    }

    static final class KeyAction<T extends Enum<T>> implements FormattingAction {

        private final T key;
        private T[] more;
        private final SimpleFormattingAction action;
        private final boolean spacesNotStops;

        private static final Set<SimpleFormattingAction> SUPPORTED
                = EnumSet.of(PREPEND_SPACE, INDENT, PREPEND_NEWLINE_AND_INDENT,
                        PREPEND_NEWLINE_AND_DOUBLE_INDENT, APPEND_NEWLINE_AND_INDENT,
                        APPEND_NEWLINE_AND_DOUBLE_INDENT, PREPEND_DOUBLE_NEWLINE_AND_INDENT);

        KeyAction(T key, SimpleFormattingAction action) {
            this.key = key;
            this.action = action;
            this.spacesNotStops = false;
        }

        KeyAction(T key, SimpleFormattingAction action, boolean spacesNotStops) {
            this.key = key;
            this.action = action;
            this.spacesNotStops = spacesNotStops;
        }

        KeyAction(T key, T[] more, SimpleFormattingAction action) {
            this.key = key;
            this.more = more;
            this.action = action;
            this.spacesNotStops = false;
        }

        KeyAction(T key, T[] more, SimpleFormattingAction action, boolean spacesNotStops) {
            this.key = key;
            this.more = more;
            this.action = action;
            this.spacesNotStops = spacesNotStops;
        }

        @Override
        public String toString() {
            return action.name() + "(" + keysString()
                    + (spacesNotStops ? "-spaces" : "-tab-stops")
                    + ")";
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
                        int nxt = state.get(m);
                        if (nxt > 0) {
                            if (amt == -1) {
                                amt = nxt;
                            } else {
                                amt = Math.min(amt, nxt);
                            }
                        }
                    }
                }
            }
            switch (action) {
                case PREPEND_SPACE:
                case INDENT:
                    if (amt > 0) {
                        if (spacesNotStops) {
                            ctx.indentBySpaces(amt);
                        } else {
                            ctx.indentBy(amt);
                        }
                    }
                    break;
                case PREPEND_NEWLINE_AND_INDENT:
                    if (amt > 0) {
                        if (spacesNotStops) {
                            ctx.prependNewline();
                            ctx.indentBySpaces(amt);
                        } else {
                            ctx.prependNewlineAndIndentBy(amt);
                        }
                    } else {
                        ctx.prependNewline();
                    }
                    break;
                case PREPEND_DOUBLE_NEWLINE_AND_INDENT:
                    if (amt > 0) {
                        if (spacesNotStops) {
                            ctx.prependDoubleNewline();
                            ctx.indentBySpaces(amt);
                        } else {
                            ctx.prependDoubleNewlineAndIndentBy(amt);
                        }
                    } else {
                        ctx.prependDoubleNewline();
                    }
                    break;
                case PREPEND_NEWLINE_AND_DOUBLE_INDENT:
                    if (amt > 0) {
                        if (spacesNotStops) {
                            ctx.prependNewline();
                            ctx.indentBySpaces(amt + ctx.indentSize());
                        } else {
                            ctx.prependNewlineAndIndentBy(amt + ctx.indentSize());
                        }
                    } else {
                        ctx.prependNewline();
                    }
                    break;
                case APPEND_NEWLINE_AND_INDENT:
                    if (amt > 0) {
                        if (spacesNotStops) {
                            ctx.appendNewline();
                            ctx.indentBySpaces(amt);
                        } else {
                            ctx.appendNewlineAndIndentBy(amt);
                        }
                    } else {
                        ctx.appendNewline();
                    }
                    break;
                case APPEND_NEWLINE_AND_DOUBLE_INDENT:
                    if (amt > 0) {
                        if (spacesNotStops) {
                            ctx.appendNewline();
                            ctx.indentBySpaces(ctx.indentSize() + amt);
                        } else {
                            ctx.appendNewline();
                            ctx.indentBy(amt + 1);
                        }
                    } else {
                        ctx.appendDoubleNewline();;
                    }
                    break;
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
            case PREPEND_DOUBLE_NEWLINE:
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
            case PREPEND_DOUBLE_NEWLINE_AND_INDENT:
                t.prependDoubleNewlineAndIndent();
                break;
            default:
                throw new AssertionError();
        }
    }

}
