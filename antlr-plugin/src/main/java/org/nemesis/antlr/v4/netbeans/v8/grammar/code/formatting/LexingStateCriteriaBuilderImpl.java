package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
final class LexingStateCriteriaBuilderImpl<T extends Enum<T>> implements LexingStateCriteriaBuilder<T, FormattingRule> {

    private final T key;
    private final FormattingRule rule;

    LexingStateCriteriaBuilderImpl(T key, FormattingRule rule) {
        this.key = key;
        this.rule = rule;
    }

    public FormattingRule isGreaterThan(int value) {
        rule.addStateCriterion(key, Criterion.greaterThan(value));
        return rule;
    }

    public FormattingRule isGreaterThanOrEqualTo(int value) {
        rule.addStateCriterion(key, Criterion.greaterThan(value - 1));
        return rule;
    }

    public FormattingRule isLessThan(int value) {
        rule.addStateCriterion(key, Criterion.lessThan(value));
        return rule;
    }

    public FormattingRule isLessThanOrEqualTo(int value) {
        rule.addStateCriterion(key, Criterion.lessThan(value + 1));
        return rule;
    }

    public FormattingRule isEqualTo(int value) {
        rule.addStateCriterion(key, Criterion.equalTo(value));
        return rule;
    }

    public FormattingRule isUnset() {
        rule.addStateCriterion(key, Criterion.equalTo(-1));
        return rule;
    }

    public FormattingRule isSet() {
        rule.addStateCriterion(key, Criterion.equalTo(-1).negate());
        return rule;
    }

    public FormattingRule isTrue() {
        rule.addStateCriterion(new BooleanStateCriterion<T>(key, true));
        return rule;
    }

    public FormattingRule isFalse() {
        rule.addStateCriterion(new BooleanStateCriterion<T>(key, false));
        return rule;
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
