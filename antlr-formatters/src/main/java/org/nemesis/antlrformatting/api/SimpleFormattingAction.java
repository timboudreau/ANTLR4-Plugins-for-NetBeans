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

import java.util.EnumSet;
import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.nemesis.misc.utils.StringUtils;

/**
 * Simple formatting action implementations, usable for most tasks; in
 * particular, the <code>by()</code> methods allow you to specify a statistic
 * you've chosen to gather in your LexingStateBuilder to automatically pick up
 * the number of spaces or tab stops to indent.
 *
 * @author Tim Boudreau
 */
public enum SimpleFormattingAction implements FormattingAction {
    /**
     * Indent the current token by the default number of tab stops the formatter
     * was configured with.
     */
    INDENT,
    /**
     * Prepend one space to the current token (note that in the case the
     * preceding token specified to append <i>newlines</i>, this will be ignored
     * so as not to generate one-space-indented lines.
     */
    PREPEND_SPACE,
    /**
     * Prepend a double newline to the current token.
     */
    PREPEND_DOUBLE_NEWLINE,
    /**
     * Prepend a double newline to the current token and indent by the one tab
     * stop (as configured).
     */
    PREPEND_DOUBLE_NEWLINE_AND_INDENT,
    /**
     * Prepend a newline to the current token.
     */
    PREPEND_NEWLINE,
    /**
     * Prepend a newline and indent one tab stop to the current token.
     */
    PREPEND_NEWLINE_AND_INDENT,
    /**
     * Prepend a newline and two tab stops to the current token.
     */
    PREPEND_NEWLINE_AND_DOUBLE_INDENT,
    /**
     * Append a space after the current token (unless the subsequent token
     * prepends newlines or a greater number of spaces).
     */
    APPEND_SPACE,
    /**
     * Append a newline after the current token.
     */
    APPEND_NEWLINE,
    /**
     * Append two newlines after the current token.
     */
    APPEND_DOUBLE_NEWLINE,
    /**
     * Append a newline to the current token and indent the subsequent token by
     * one tab stop.
     */
    APPEND_NEWLINE_AND_INDENT,
    /**
     * Append a newline and two tab stop indents after the current token.
     */
    APPEND_NEWLINE_AND_DOUBLE_INDENT;

    /**
     * If this enum constant specifies indenting, specify a specific number of
     * tab stops to indent by.
     *
     * @param amount The number of tab stops
     * @return a wrapper for this action
     */
    public FormattingAction by(int amount) {
        return new IndentByAmount(this, amount, false);
    }

    /**
     * If this enum constant specifies indenting, specify a specific number of
     * <i>spaces</i> to indent by.
     *
     * @param amount The number of spaces
     * @return a wrapper for this action
     */
    public FormattingAction bySpaces(int amount) {
        return new IndentByAmount(this, amount, true);
    }

    @Override
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

    /**
     * For constants that support indenting, specify the number of tab stops to
     * indent based on a value fetched from the LexingState. This must be an
     * enum constant that you set up in your LexingStateBuilder when configuring
     * your formatter. If unset, no indenting will be performed.
     *
     * @param <T> The enum type
     * @param amountKey The key to use to look up the number of tab stops to
     * indent
     * @throws IllegalArgumentException if this enum does not do any indenting
     * @return A wrapper for this formatting action
     */
    public <T extends Enum<T>> FormattingAction by(T amountKey) {
        if (!KeyAction.SUPPORTED.contains(this)) {
            throw new IllegalArgumentException("Not supported for keys: " + this);
        }
        return new KeyAction<>(amountKey, this);
    }

    /**
     * For constants that support indenting, specify the number of <i>spaces</i>
     * to indent based on a value fetched from the LexingState. This must be an
     * enum constant that you set up in your LexingStateBuilder when configuring
     * your formatter. If unset, no indenting will be performed.
     *
     * @param <T> The enum type
     * @param amountKey The key to use to look up the number of spaces to indent
     * @throws IllegalArgumentException if this enum does not do any indenting
     * @return A wrapper for this formatting action
     */
    public <T extends Enum<T>> FormattingAction bySpaces(T amountKey) {
        if (!KeyAction.SUPPORTED.contains(this)) {
            throw new IllegalArgumentException("Not supported for keys: " + this);
        }
        return new KeyAction<>(amountKey, this, true);
    }

    /**
     * For constants that support indenting, specify the number of tab stops to
     * indent based on a value fetched from the LexingState. This must be an
     * enum constant that you set up in your LexingStateBuilder when configuring
     * your formatter. If unset, no indenting will be performed. This method
     * lets you check multiple keys, using the first one that's not unset.
     *
     * @param <T> The enum type
     * @param amountKey The key to use to look up the number of tab stops to
     * indent
     * @param more Additional keys
     * @throws IllegalArgumentException if this enum does not do any indenting
     * @return A wrapper for this formatting action
     */
    @SafeVarargs
    public final <T extends Enum<T>> FormattingAction by(T amountKey, T... more) {
        if (!KeyAction.SUPPORTED.contains(this)) {
            throw new IllegalArgumentException("Not supported for keys: " + this);
        }
        return new KeyAction<>(amountKey, more, this);
    }

    /**
     * For constants that support indenting, specify the number of <i>spaces</i>
     * to indent based on a value fetched from the LexingState. This must be an
     * enum constant that you set up in your LexingStateBuilder when configuring
     * your formatter. If unset, no indenting will be performed. This method
     * lets you check multiple keys, using the first one that's not unset.
     *
     * @param <T> The enum type
     * @param amountKey The key to use to look up the number of spaces to indent
     * @param more Additional keys
     * @throws IllegalArgumentException if this enum does not do any indenting
     * @return A wrapper for this formatting action
     */
    @SafeVarargs
    public final <T extends Enum<T>> FormattingAction bySpaces(T amountKey, T... more) {
        if (!KeyAction.SUPPORTED.contains(this)) {
            throw new IllegalArgumentException("Not supported for keys: " + this);
        }
        return new KeyAction<>(amountKey, more, this, true);
    }

    /**
     * For constants that support indenting, specify the number of <i>spaces</i>
     * to indent based on a value fetched from the LexingState. This must be an
     * enum constant that you set up in your LexingStateBuilder when configuring
     * your formatter. If unset, no indenting will be performed. This method
     * lets you check multiple keys, using the first one that's not unset.
     *
     * @param <T> The enum type
     * @param adjustment an amount to add to the number of spaces
     * @param amountKey The key to use to look up the number of spaces to indent
     * @param more Additional keys
     * @throws IllegalArgumentException if this enum does not do any indenting
     * @return A wrapper for this formatting action
     */
    @SafeVarargs
    public final <T extends Enum<T>> FormattingAction bySpaces(int adjustment, T amountKey, T... more) {
        if (!KeyAction.SUPPORTED.contains(this)) {
            throw new IllegalArgumentException("Not supported for keys: " + this);
        }
        return new KeyAction<>(amountKey, more, this, true, adjustment);
    }

    static final class KeyAction<T extends Enum<T>> implements FormattingAction {

        private final T key;
        private T[] more;
        private final SimpleFormattingAction action;
        private final boolean spacesNotStops;
        private int adjustment;
        private boolean warned;

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

        KeyAction(T key, SimpleFormattingAction action, boolean spacesNotStops, int adjustment) {
            this.key = key;
            this.action = action;
            this.spacesNotStops = spacesNotStops;
            this.adjustment = adjustment;
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

        KeyAction(T key, T[] more, SimpleFormattingAction action, boolean spacesNotStops, int adjustment) {
            this.key = key;
            this.more = more;
            this.action = action;
            this.spacesNotStops = spacesNotStops;
            this.adjustment = adjustment;
        }

        @Override
        public String toString() {
            String result = action.toString() + "-by-"
                    + key.toString();
            if (more != null) {
                result += "-" + StringUtils.join("-", (Object[]) more);
            }
            result += spacesNotStops ? "-spaces" : "-tab-stops";
            if (adjustment != 0) {
                result += (adjustment > 0 ? "+" + adjustment : adjustment);
            }
            return result;
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
            if (amt > 4096 && !warned) {
                warned = true;
                new IllegalArgumentException("Distressingly large number of "
                        + "spaces specified by key " + key + ".  Something "
                        + "is probably about to go very wrong. Token: "
                        + token + "\nFormattingContext: " + ctx + "\nLexingState: " + state)
                        .printStackTrace();
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
                            ctx.indentBySpaces(amt + adjustment);
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
                            ctx.indentBySpaces(amt + adjustment);
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
                            ctx.indentBySpaces(amt + ctx.indentSize() + adjustment);
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
                            ctx.indentBySpaces(amt + adjustment);
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
                            ctx.indentBySpaces(ctx.indentSize() + amt + adjustment);
                        } else {
                            ctx.appendNewline();
                            ctx.indentBy(amt + 1 + adjustment);
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
