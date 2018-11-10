package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.antlr.v4.runtime.Token;

/**
 *
 * @author Tim Boudreau
 */
interface FormattingAction extends BiConsumer<Token, FormattingContext> {

    static FormattingAction EMPTY = new FormattingAction(){
        @Override
        public void accept(Token t, FormattingContext u) {
            // do nothing
        }

        public String toString() {
            return "<noop>";
        }
    };

    default FormattingAction unless(Predicate<FormattingContext> test) {
        return new FormattingAction() {
            @Override
            public void accept(Token t, FormattingContext u) {
                if (!test.test(u)) {
                    FormattingAction.this.accept(t, u);
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
            public void accept(Token t, FormattingContext u) {
                if (test.test(u)) {
                    FormattingAction.this.accept(t, u);
                }
            }

            public String toString() {
                return "IfTrue(" + test + "):" + FormattingAction.this.toString();
            }
        };
    }

    default FormattingAction andActivate(FormattingRule rule) {
        return (Token tok, FormattingContext t) -> {
            FormattingAction.this.accept(tok, t);
            rule.activate();
        };
    }

    default FormattingAction andDeactivate(FormattingRule rule) {
        return (Token tok, FormattingContext t) -> {
            FormattingAction.this.accept(tok, t);
            rule.deactivate();
        };
    }

    default FormattingAction and(FormattingAction other) {
        return new FormattingAction() {
            @Override
            public void accept(Token tok, FormattingContext t) {
                FormattingAction.this.accept(tok, t);
                other.accept(tok, t);
            }

            public String toString() {
                return FormattingAction.this.toString() + " & " + other;
            }
        };
    }

}
