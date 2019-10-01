package org.nemesis.antlrformatting.api;

import com.mastfrog.predicates.integer.IntPredicates;
import com.mastfrog.predicates.string.StringPredicates;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import org.antlr.v4.runtime.Token;
/**
 *
 * @author Tim Boudreau
 */
class Debug implements Predicate<Token>, BooleanSupplier {

    private boolean enabled = true;
    private Token lastToken;
    private Predicate<String> tokenText;
    private final Predicate<String> disableOnText;
    private final IntPredicate disableOn;
    private final IntPredicate enableOn;
    private final IntPredicate types;

    private Debug(boolean defaultEnabled, IntPredicate types,
            Predicate<String> tokenText, IntPredicate enableOn,
            IntPredicate disableOn, Predicate<String> disableOnText) {
        this.enabled = defaultEnabled;
        this.types = types;
        this.tokenText = tokenText;
        this.enableOn = enableOn;
        this.disableOn = disableOn;
        if (disableOn instanceof CountingPredicate) {
            ((CountingPredicate) disableOn).setSupplier(this);
        }
        if (enableOn instanceof CountingPredicate) {
            ((CountingPredicate) enableOn).setSupplier(this);
        }
        this.disableOnText = disableOnText;
    }

    public boolean getAsBoolean() {
        return enabled;
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    @Override
    public boolean test(Token token) {
        if (lastToken != null && enableOn != null && !enabled) {
            if (enableOn.test(lastToken.getType())) {
                enabled = true;
            }
        }
        boolean result = enabled || (types.test(token.getType()) || tokenText.test(token.getText()));
        if (disableOn.test(token.getType()) || disableOnText.test(token.getText())) {
            enabled = false;
        }
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    static IntPredicate predicate(int first, int... types) {
        int[] vals = IntPredicates.combine(first, types);
        Arrays.sort(vals);
        return new IntPredicate() {
            @Override
            public boolean test(int val) {
                if (vals.length == 1) {
                    return vals[0] == val;
                }
                return Arrays.binarySearch(vals, val) >= 0;
            }

            public String toString() {
                if (vals.length == 1) {
                    return "match(" + vals[0] + ")";
                } else {
                    return "match(" + Arrays.toString(vals) + ")";
                }
            }
        };
    }

    static Predicate<String> predicate(String first, String... more) {
        return StringPredicates.predicate(first, more);
    }

    public static class Builder {

        IntPredicate types = t -> {
            return false;
        };
        boolean defaultEnabled = true;
        Predicate<String> tokenText = s -> {
            return false;
        };
        IntPredicate enableOn = (val) -> {
            return false;
        };

        Builder() {
        }

        public Debug build() {
            return new ConditionalBuilder(this).build();
        }

        public Builder onTokenTypes(int first, int... types) {
            this.types = predicate(first, types);
            return this;
        }

        public Builder onTokenText(String txt) {
            this.tokenText = s -> {
                return txt.equals(s);
            };
            return this;
        }

        public ConditionalBuilder enablingOn(int first, int... types) {
            this.enableOn = predicate(first, types);
            return new ConditionalBuilder(this);
        }

        public static class ConditionalBuilder extends Builder {

            private IntPredicate disableOn = t -> {
                return false;
            };
            private Predicate<String> disableOnText = t -> {
                return false;
            };

            ConditionalBuilder(Builder builder) {
                this.defaultEnabled = builder.defaultEnabled;
                this.types = builder.types;
                this.tokenText = builder.tokenText;
                this.enableOn = builder.enableOn;
            }

            @Override
            public Debug build() {
                return new Debug(defaultEnabled, types, tokenText, enableOn, disableOn, disableOnText);
            }

            public ConditionalBuilder disablingAfter(int howManyTokens) {
                assert howManyTokens > 0 : "<= 0: " + howManyTokens;
                disableOn = new CountingPredicate(howManyTokens);
                return this;
            }

            public ConditionalBuilder disablingOn(String text, String... moreText) {
                disableOnText = predicate(text, moreText);
                return this;
            }

            public ConditionalBuilder disablingOn(int tokenType, int... moreTypes) {
                disableOn = predicate(tokenType, moreTypes);
                return this;
            }
        }
    }

    private static final class CountingPredicate implements IntPredicate {

        private BooleanSupplier enablement;
        private int ix;
        private final int max;

        CountingPredicate(int max) {
            this.max = max;
        }

        void setSupplier(BooleanSupplier enablement) {
            this.enablement = enablement;
            ix = 0;
        }

        @Override
        public boolean test(int value) {
            if (enablement == null) {
                return false;
            }
            boolean counting = enablement.getAsBoolean();
            if (counting) {
                return ix++ > max;
            } else {
                ix = 0;
            }
            return true;
        }
    }

}
