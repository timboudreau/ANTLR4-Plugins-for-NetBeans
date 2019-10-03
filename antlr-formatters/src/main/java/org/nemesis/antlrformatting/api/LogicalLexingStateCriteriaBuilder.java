package org.nemesis.antlrformatting.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Builder for FormattingRule test conditions that logically combine multiple
 * queries of the lexing state, to condition a rule's enablement on
 * particular combination of states configured in the StateBuilder
 * passed when configuring formatting.
 *
 * @see org.nemesis.antlrformatting.api.FormattingRule.whenCombinationOf
 * @see org.nemesis.antlrformatting.api.LexingStateBuilder
 * @author Tim Boudreau
 */
public final class LogicalLexingStateCriteriaBuilder<R> {

    private final List<LogicalBuilder<?, R>> builders = new ArrayList<>();
    private final Function<LogicalLexingStateCriteriaBuilder<R>, R> converter;

    private Consumer<FormattingRule> consumer;

    public LogicalLexingStateCriteriaBuilder(Function<LogicalLexingStateCriteriaBuilder<R>, R> converter) {
        this.converter = converter;
    }

    private void addConsumer(Consumer<FormattingRule> c) {
        if (consumer == null) {
            consumer = c;
        } else {
            consumer = consumer.andThen(c);
        }
    }

    Consumer<FormattingRule> consumer() {
        if (consumer == null) {
            return fr -> {
                throw new IllegalStateException("Nothing built - " + fr);
            };
        }
        return consumer;
    }

    public R then() {
        Predicate<LexingState> result = null;
        if (builders.isEmpty()) {
            throw new IllegalStateException("No conditions added");
        }
        for (LogicalBuilder<?, R> b : builders) {
            result = b.build(result);
        }
        Predicate<LexingState> res = result;
        addConsumer(rule -> {
            rule.addStateCriterion(res);
        });
        return converter.apply(this);
    }

    <T extends Enum<T>> LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<R>> start(T key) {
        LogicalBuilder<T, R> result = new LogicalBuilder<>(key, false, false, this);
        builders.add(result);
        return result;
    }

    public <T extends Enum<T>> LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<R>> or(T key) {
        LogicalBuilder<T, R> result = new LogicalBuilder<>(key, false, false, this);
        builders.add(result);
        return result;
    }

    public <T extends Enum<T>> LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<R>> orNot(T key) {
        LogicalBuilder<T, R> result = new LogicalBuilder<>(key, false, true, this);
        builders.add(result);
        return result;
    }

    public <T extends Enum<T>> LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<R>> and(T key) {
        LogicalBuilder<T, R> result = new LogicalBuilder<>(key, true, false, this);
        builders.add(result);
        return result;
    }

    public <T extends Enum<T>> LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<R>> andNot(T key) {
        LogicalBuilder<T, R> result = new LogicalBuilder<>(key, true, true, this);
        builders.add(result);
        return result;
    }

    private enum OP {
        GREATER(">"), GREATER_OR_EQUAL(">="), LESS("<"), LESS_OR_EQUAL("<="),
        EQUAL("=="), UNSET("-unset"), TRUE("==true"), FALSE("==false"), NOT_EQUAL("!=");
        private final String stringValue;

        OP(String stringValue) {
            this.stringValue = stringValue;
        }

        private boolean takesArgument() {
            switch (this) {
                case UNSET:
                case FALSE:
                case TRUE:
                    return false;
                default:
                    return true;
            }
        }

        public String toString() {
            return stringValue;
        }
    }

    private static class LogicalBuilder<T extends Enum<T>, R> extends LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<R>> {

        private final T key;
        private final boolean isAnd;
        private final boolean isNegated;
        private final LogicalLexingStateCriteriaBuilder<R> parent;
        private OP op;
        private int value = -1;

        LogicalBuilder(T key, boolean isAnd, boolean isNegated, LogicalLexingStateCriteriaBuilder<R> parent) {
            this.key = key;
            this.isAnd = isAnd;
            this.isNegated = isNegated;
            this.parent = parent;
        }

        Predicate<LexingState> build(Predicate<LexingState> prev) {
            Predicate<LexingState> result = new LogicalComponentPredicate<T>(op, key, value);
            if (isNegated) {
                result = result.negate();
            }
            if (prev != null) {
                if (isAnd) {
                    result = prev.and(result);
                } else {
                    result = prev.or(result);
                }
            }
            return result;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isGreaterThan(int value) {
            op = OP.GREATER;
            this.value = value;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isGreaterThanOrEqualTo(int value) {
            op = OP.GREATER_OR_EQUAL;
            this.value = value;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isLessThan(int value) {
            op = OP.LESS;
            this.value = value;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isLessThanOrEqualTo(int value) {
            op = OP.LESS_OR_EQUAL;
            this.value = value;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isEqualTo(int value) {
            op = OP.EQUAL;
            this.value = value;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isUnset() {
            op = OP.UNSET;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isTrue() {
            op = OP.TRUE;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isSet() {
            op = OP.NOT_EQUAL;
            this.value = -1;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isFalse() {
            op = OP.FALSE;
            return parent;
        }

        private static class LogicalConnector<T> implements Predicate<T> {

            // for logging purposes
            private final Predicate<T> a;
            private final Predicate<? super T> b;
            private final boolean and;

            LogicalConnector(Predicate<T> a, Predicate<? super T> b, boolean and) {
                this.a = a;
                this.b = b;
                this.and = and;
            }

            @Override
            public boolean test(T t) {
                if (and) {
                    return a.test(t) && b.test(t);
                } else {
                    return a.test(t) || b.test(t);
                }
            }

            @Override
            public String toString() {
                if (and) {
                    return a + " & " + b;
                } else {
                    return a + " | " + b;
                }
            }
        }

        private static class LogicalComponentPredicate<T extends Enum<T>> implements Predicate<LexingState> {

            private final OP op;
            private final T key;
            private final int value;

            LogicalComponentPredicate(OP op, T key, int value) {
                this.op = op;
                this.key = key;
                this.value = value;
            }

            @Override
            public Predicate<LexingState> or(Predicate<? super LexingState> other) {
                return new LogicalConnector<>(this, other, false);
            }

            @Override
            public Predicate<LexingState> and(Predicate<? super LexingState> other) {
                return new LogicalConnector<>(this, other, true);
            }

            @Override
            public boolean test(LexingState t) {
                switch (op) {
                    case EQUAL:
                        return t.get(key) == value;
                    case GREATER:
                        return t.get(key) > value;
                    case GREATER_OR_EQUAL:
                        return t.get(key) >= value;
                    case LESS:
                        return t.get(key) < value;
                    case LESS_OR_EQUAL:
                        return t.get(key) <= value;
                    case TRUE:
                        return t.getBoolean(key);
                    case FALSE:
                        return !t.getBoolean(key);
                    case UNSET:
                        return t.get(key) == -1;
                    case NOT_EQUAL:
                        return t.get(key) != value;
                    default:
                        throw new AssertionError(op);
                }
            }

            @Override
            public String toString() {
                return key + op.toString() + (op.takesArgument() ? Integer.toString(value) : "");
            }

            public Predicate<LexingState> negate() {
                return new Predicate<LexingState>() {
                    @Override
                    public boolean test(LexingState t) {
                        return !LogicalComponentPredicate.this.test(t);
                    }

                    public String toString() {
                        return "!" + LogicalComponentPredicate.this.toString();
                    }
                };
            }
        }
    }

}
