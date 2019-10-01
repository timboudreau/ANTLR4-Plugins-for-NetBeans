package org.nemesis.antlrformatting.api;

import java.util.function.Predicate;
import org.antlr.v4.runtime.Token;

/**
 * A formatting action, which can manipulate the FormattingContext it is passed
 * to prepend or append whitespace. Most common formatting actions are
 * implemented on {@link SimpleFormattingAction} - only directly implement this
 * interface if you are doing something unusual, such as rewriting text or
 * needing to gather multiple pieces of information from the lexing state and
 * context to decide what to do.
 * <p>
 * A note on coalescing: A FormattingAction can both <i>prepend</i> and
 * <i>append</i> whitespace, before and after a token. In practice, these are
 * all treated as prepend operations - which means if token A has an action that
 * appends a newline to it, and subsequent token B has an action that prepends a
 * space to it, these two contradictory requests need to be coalesced somehow
 * (prepending two spaces is almost always undesired, and two rules may
 * legitimately request appending two newlines and appending one newline, but
 * the desired outcome is unlikely to be <i>three</i> newlines). The coalescing
 * algorithm is as follows:
 * </p>
 * <ol>
 * <li>If one set of prepend instructions is empty, take the other</li>
 * <li>If two identical prepend instructions, do not combine, performa that set
 * of instructions once</li>
 * <li>In the case of contradictory instructions:
 * <ul>
 * <li>Take the combination of the greater number of newlines and the greater
 * indent depth from each, and combine one newline instruction with one indent
 * instruction, if both newline and indent instructions are present, using
 * whichever from each results in the greater number of newlines or spaces, with
 * the following caveat: When encountering both newlines and an instruction to
 * indent <i>exactly one space</i>, and no deeper indent instruction is present,
 * ignore the space instruction (otherwise rules to prepend a space before a
 * keyword would combine with rules to put a newline after a semicolon to
 * constantly create one-space-indented lines, which is pretty much never what
 * is wanted).
 * </li>
 * </ul>
 * </li>
 * </ol>
 *
 * org.nemesis.antlrformatting.api.SimpleFormattingAction
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface FormattingAction {

    /**
     * Manipulate the FormattingContext to prepend or append whitespace or
     * otherwise manipulate the token text.
     *
     * @param token The token
     * @param ctx The formatting context, whose methods allow you to prepend or
     * append whitespace and newlines to this token
     * @param state The lexing state, which can be queried for values it was
     * configured to provide
     */
    void accept(Token token, FormattingContext ctx, LexingState state);

    /**
     * Apply this action and also trim any whitespace which is part of the
     * original action.
     *
     * @return A formatting action
     */
    default FormattingAction trimmingWhitespace() {
        return new FormattingAction() {
            @Override
            @SuppressWarnings("StringEquality")
            public void accept(Token token, FormattingContext ctx, LexingState state) {
                FormattingAction.this.accept(token, ctx, state);
                String txt = token.getText();
                String trimmed = txt.trim();
                if (txt != trimmed && !txt.equals(trimmed)) {
                    ctx.replace(trimmed);
                }
            }

            @Override
            public String toString() {
                return FormattingAction.this + " then trim whitespace";
            }
        };
    }

    public static final FormattingAction EMPTY = new FormattingAction() {
        @Override
        public void accept(Token t, FormattingContext u, LexingState state) {
            // do nothing
        }

        @Override
        public String toString() {
            return "<noop>";
        }
    };

    default FormattingAction unless(Predicate<FormattingContext> test) {
        return new FormattingAction() {
            @Override
            public void accept(Token t, FormattingContext u, LexingState state) {
                if (!test.test(u)) {
                    FormattingAction.this.accept(t, u, state);
                }
            }

            @Override
            public String toString() {
                return "Unless(" + test + "):" + FormattingAction.this.toString();
            }
        };
    }

    default FormattingAction ifTrue(Predicate<FormattingContext> test) {
        return new FormattingAction() {
            @Override
            public void accept(Token t, FormattingContext u, LexingState state) {
                if (test.test(u)) {
                    FormattingAction.this.accept(t, u, state);
                }
            }

            @Override
            public String toString() {
                return "IfTrue(" + test + "):" + FormattingAction.this.toString();
            }

        };
    }

    default FormattingAction andActivate(FormattingRule rule) {
        return (Token tok, FormattingContext t, LexingState state) -> {
            FormattingAction.this.accept(tok, t, state);
            rule.activate();
        };
    }

    default FormattingAction andDeactivate(FormattingRule rule) {
        return (Token tok, FormattingContext t, LexingState state) -> {
            FormattingAction.this.accept(tok, t, state);
            rule.deactivate();
        };
    }

    /**
     * Combine this and another action.
     *
     * @param other Another action
     * @return A formatting action
     */
    default FormattingAction and(FormattingAction other) {
        if (other == this) {
            throw new IllegalArgumentException("Combining with self");
        }
        return new FormattingAction() {
            @Override
            public void accept(Token tok, FormattingContext t, LexingState state) {
                FormattingAction.this.accept(tok, t, state);
                other.accept(tok, t, state);
            }

            public String toString() {
                return FormattingAction.this.toString() + " & " + other;
            }
        };
    }

    /**
     * Creates a formatting action which will use the passed rewriter to rewrite
     * token text.
     *
     * @param rewriter A rewriter
     * @return A formatting action
     */
    static FormattingAction rewriteTokenText(TokenRewriter rewriter) {
        return FormattingAction.EMPTY.rewritingTokenTextWith(rewriter);
    }

    /**
     * Rewrite the token text with the passed token rewriter.
     *
     * @param rewriter A rewriter
     * @return A formatting action
     */
    default FormattingAction rewritingTokenTextWith(TokenRewriter rewriter) {
        return new FormattingAction() {
            @Override
            @SuppressWarnings("StringEquality")
            public void accept(Token token, FormattingContext ctx, LexingState state) {
                String origText = token.getText();
                String revisedText = rewriter.rewrite(ctx.indentSize(), origText, ctx.currentCharPositionInLine(), state);
                if (revisedText != null && origText != revisedText && !origText.equals(revisedText)) {
                    ctx.replace(revisedText);
                }
            }

            @Override
            public String toString() {
                return "Rewrite{" + rewriter + "}";
            }
        };
    }

    /**
     * Wrap lines, using the passed formatting action in place of this one if
     * the resulting line would be longer than the limit.
     *
     * @param limit Maximum characters per line
     * @param wrapAction An action to apply instead in that case
     * @return A formatting action
     */
    default FormattingAction wrappingLines(int limit, FormattingAction wrapAction) {
        return new FormattingAction() {
            @Override
            public void accept(Token token, FormattingContext ctx, LexingState state) {
                if (ctx.currentCharPositionInLine() + token.getText().length() > limit) {
//                    System.out.println("WRAP '" + token.getText() + "' for line pos "
//                        + ctx.currentCharPositionInLine() + " with " + wrapAction);
                    wrapAction.accept(token, ctx, state);
//                    System.out.println("after wrap, line position is " + ctx.currentCharPositionInLine());
                } else {
                    FormattingAction.this.accept(token, ctx, state);
                }
            }

            @Override
            public String toString() {
                return FormattingAction.this.toString() + "-wrap-at-" + limit
                        + "-with-" + wrapAction;
            }
        };
    }

    /**
     * Wrap lines, using the value of the passed key (in spaces) from the lexing
     * context.
     *
     * @param <T> The type
     * @param wrapPointKey The key
     * @param limit The wrap point
     * @return A formatting action
     */
    default <T extends Enum<T>> FormattingAction wrappingLines(T wrapPointKey, int limit) {
        return wrappingLines(limit, SimpleFormattingAction.APPEND_NEWLINE_AND_INDENT
                .bySpaces(wrapPointKey));
    }

    /**
     * Wrap lines, double-indenting those that would be longer than the limit if
     * this action were applied. Equivalent of <code>wrappingLines(limit,
     * SimpleFormattingAction.APPEND_NEWLINE_AND_DOUBLE_INDENT)</code>
     *
     * @param limit
     * @return An action which will wrap lines as described
     */
    default FormattingAction wrappingLines(int limit) {
        return wrappingLines(limit,
                (FormattingAction) SimpleFormattingAction.APPEND_NEWLINE_AND_DOUBLE_INDENT);
    }

    /**
     * For debugging purposes, wrapper an action so it logs a string.
     *
     * @param s
     * @return A wrapper action
     */
    default FormattingAction log(String s) {
        return new FormattingAction() {
            @Override
            public void accept(Token tok, FormattingContext t, LexingState state) {
                FormattingAction.this.accept(tok, t, state);
                System.out.println(s);
            }

            public String toString() {
                return FormattingAction.this.toString();
            }
        };
    }

}
