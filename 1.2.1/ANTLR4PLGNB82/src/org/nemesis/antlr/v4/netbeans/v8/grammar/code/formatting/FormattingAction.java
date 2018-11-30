package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.function.Predicate;
import org.antlr.v4.runtime.Token;

/**
 * A formatting action, which can manipulate the FormattingContext it is passed
 * to prepend or append whitespace.
 * <p>
 * A note on coalescing: A FormattingAction can both <i>prepend</i> and
 * <i>append</i> whitespace, before and after a token. In practice, these are
 * all treated as prepend operations - which means if token A has an action that
 * appends a newline to it, and subsequent token B has an action that prepends a
 * space to it, these two contradictory requests need to be coalesced somehow
 * (prepending two spaces is almost always undesired, and two rules may
 * legitimately request appending two newlines and appending one newline, but
 * the desired outcome is unlikely to be <i>three</i> newlines). The coalescing
 * algorithm as currently implemented (but subject to improvement) is as
 * follows:
 * </p>
 * <ol>
 * <li>If two identical prepend strings, do not combine, just use the string
 * once
 * </li><li>If one prepend string is the empty string, and the other is not, use
 * the non empty one
 * </li><li>If one string contains a newline and one doesn't, then
 * <ul>
 * <li>If the one then doesn't consists of a single space, throw that one away
 * and use the other</li>
 * <li>Otherwise concatenate them, prepending the one which does contain a
 * newline to the one that doesn't</li>
 * </ul>
 * </li><li>If neither contains a newline, take the longer of the two
 * </li><li>Prefer the one which contains more newlines if both contain newlines
 * </li><li>Otherwise take the shorter of the two</li>
 * </li><li>If none of the other tests have been matched, use the newer one (the
 * prepend string from the current token as opposed to the append string from
 * the preceding one</li>
 * </ol>
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
interface FormattingAction {

    /**
     * Manipulate the FormattingContext to prepend or append whitespace.
     *
     * @param token The token
     * @param ctx The formatting context, whose methods allow you to prepend
     * or append whitespace and newlines to this token
     * @param state The lexing state, which can be queried for values it
     * was configured to provide
     */
    void accept(Token token, FormattingContext ctx, LexingState state);

    default FormattingAction andThen(FormattingAction other) {
        return new FormattingAction() {
            @Override
            public void accept(Token token, FormattingContext ctx, LexingState state) {
                FormattingAction.this.accept(token, ctx, state);
                other.accept(token, ctx, state);
            }

            public String toString() {
                return FormattingAction.this + " then " + other;
            }
        };
    }

    static FormattingAction EMPTY = new FormattingAction() {
        @Override
        public void accept(Token t, FormattingContext u, LexingState state) {
            // do nothing
        }

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

    default FormattingAction and(FormattingAction other) {
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
     * For debugging purposes, wrapper an action so it logs a string.
     *
     * @param s
     * @return
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
