package org.nemesis.antlrformatting.api;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
final class LexingStateCriteriaBuilderImpl<T extends Enum<T>, R> extends LexingStateCriteriaBuilder<T, R> implements Consumer<FormattingRule> {

    private final T key;
    private Consumer<FormattingRule> consumer = null;
    private final Function<LexingStateCriteriaBuilderImpl<T,R>, R> ret;

    LexingStateCriteriaBuilderImpl(T key, Function<LexingStateCriteriaBuilderImpl<T,R>, R> ret) {
        this.key = key;
        this.ret = ret;
    }

    void apply(FormattingRule rule) {
        if (consumer != null) {
            consumer.accept(rule);
        }
    }

    void addConsumer(Consumer<FormattingRule> c) {
        if (consumer == null) {
            consumer = c;
        } else {
            consumer = consumer.andThen(c);
        }
    }

    @Override
    public R isGreaterThan(int value) {
        addConsumer(rule -> {
            rule.addStateCriterion(key, Criterion.greaterThan(value));
        });
        return ret.apply(this);
    }

    @Override
    public R isGreaterThanOrEqualTo(int value) {
        addConsumer(rule -> {
            rule.addStateCriterion(key, Criterion.greaterThan(value - 1));
        });
        return ret.apply(this);
    }

    @Override
    public R isLessThan(int value) {
        addConsumer(rule -> {
            rule.addStateCriterion(key, Criterion.lessThan(value));
        });
        return ret.apply(this);
    }

    @Override
    public R isLessThanOrEqualTo(int value) {
        addConsumer(rule -> {
            rule.addStateCriterion(key, Criterion.lessThan(value + 1));
        });
        return ret.apply(this);
    }

    @Override
    public R isEqualTo(int value) {
        addConsumer(rule -> {
            rule.addStateCriterion(key, Criterion.equalTo(value));
        });
        return ret.apply(this);
    }

    @Override
    public R isUnset() {
        addConsumer(rule -> {
            rule.addStateCriterion(key, Criterion.equalTo(-1));
        });
        return ret.apply(this);
    }

    @Override
    public R isSet() {
        addConsumer(rule -> {
            rule.addStateCriterion(key, Criterion.equalTo(-1).negate());
        });
        return ret.apply(this);
    }

    @Override
    public R isTrue() {
        addConsumer(rule -> {
            rule.addStateCriterion(new BooleanStateCriterion<>(key, true));
        });
        return ret.apply(this);
    }

    @Override
    public R isFalse() {
        addConsumer(rule -> {
            rule.addStateCriterion(new BooleanStateCriterion<>(key, false));
        });
        return ret.apply(this);
    }

    @Override
    public void accept(FormattingRule t) {
        apply(t);
    }

    private static final class BooleanStateCriterion<T extends Enum<T>> implements Predicate<LexingState> {

        private final boolean val;
        private final T key;

        BooleanStateCriterion(T key, boolean val) {
            this.key = key;
            this.val = val;
        }

        @Override
        public boolean test(LexingState t) {
            return t.getBoolean(key) == val;
        }

        public String toString() {
            return key + "==" + val;
        }

        @Override
        public Predicate<LexingState> negate() {
            return new Predicate<LexingState>() {
                @Override
                public boolean test(LexingState t) {
                    return !BooleanStateCriterion.this.test(t);
                }

                public String toString() {
                    return "!" + BooleanStateCriterion.this.toString();
                }

            };
        }
    }
}
