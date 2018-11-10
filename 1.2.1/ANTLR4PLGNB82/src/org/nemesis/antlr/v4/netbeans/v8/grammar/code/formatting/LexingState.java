package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 *
 * @author Tim Boudreau
 */
public class LexingState {

    interface LexerScanner {

        int countForwardOccurrencesUntilNext(IntPredicate toCount, IntPredicate stopType);

        int countBackwardOccurrencesUntilPrevious(IntPredicate toCount, IntPredicate stopType);

        int tokenCountToNext(boolean ignoreWhitespace, IntPredicate targetType);

        int tokenCountToPreceding(boolean ignoreWhitespace, IntPredicate targetType);
    }

    public static final class Builder<T extends Enum<T>> {

        private final List<FinishableIncrementDecrementBuilder<?>> builders = new ArrayList<>();
        private final List<FinishablePushPositionBuilder<?>> pushers = new ArrayList<>();
        private final List<FinishableSetBuilder<?>> booleans = new ArrayList<>();
        private final List<FinishableTokenCountBuilder<?>> counters = new ArrayList<>();
        private final List<FinishableDistanceBuilder<?>> distances = new ArrayList<>();
        private final Class<T> type;

        Builder(Class<T> type) {
            this.type = type;
        }

        public LexingState build() {
            int ct = type.getEnumConstants().length;
            boolean[] booleanState = new boolean[ct];
            IntList[] stacks = new IntList[ct];
            int[] values = new int[ct];
            Arrays.fill(values, -1);
            return null;
        }

        public IncrementDecrementBuilder<T> increment(T item) {
            return new IncrementDecrementBuilder<>(this, item);
        }

        public PushPositionBuilder<T> pushPosition(T item) {
            return new PushPositionBuilder<>(this, item);
        }

        public SetBuilder<T> set(T item) {
            return new SetBuilder<>(this, item);
        }

        public TokenCountBuilder<T> count(T item) {
            return new TokenCountBuilder<>(item, this);
        }

        public DistanceBuilder<T> computeDistance(T item) {
            return new DistanceBuilder<>(item, this);
        }
    }

    public static final class DistanceBuilder<T extends Enum<T>> {

        private final Builder<T> builder;
        private final T item;

        DistanceBuilder(T item, Builder<T> builder) {
            this.item = item;
            this.builder = builder;
        }

        public FinishableDistanceBuilder<T> toNext(int token) {
            return toNext(matching(token));
        }

        public FinishableDistanceBuilder<T> toNext(int token, int... more) {
            return toNext(matchingAny(token, more));
        }

        public FinishableDistanceBuilder<T> toNext(IntPredicate token) {
            return new FinishableDistanceBuilder<>(item, token, builder, true);
        }

        public FinishableDistanceBuilder<T> toPreceding(int tokenType, int... more) {
            return toPreceding(matchingAny(tokenType, more));
        }

        public FinishableDistanceBuilder<T> toPreceding(int tokenType) {
            return toPreceding(matching(tokenType));
        }

        public FinishableDistanceBuilder<T> toPreceding(IntPredicate token) {
            return new FinishableDistanceBuilder<>(item, token, builder, false);
        }
    }

    public static final class FinishableDistanceBuilder<T extends Enum<T>> {

        private final T item;
        private final IntPredicate token;
        private final Builder<T> builder;
        private final boolean forward;
        private boolean includeWhitespace;
        private boolean built;

        FinishableDistanceBuilder(T item, IntPredicate token, Builder<T> builder, boolean forward) {
            this.item = item;
            this.token = token;
            this.builder = builder;
            this.forward = forward;
        }

        public Builder<T> includingWhitespace() {
            if (built) {
                throw new IllegalStateException("Already built.");
            }
            built = true;
            this.includeWhitespace = true;
            builder.distances.add(this);
            return builder;
        }

        public Builder<T> ignoringWhitespace() {
            if (built) {
                throw new IllegalStateException("Already built.");
            }
            built = true;
            this.includeWhitespace = false;
            builder.distances.add(this);
            return builder;
        }
    }

    public static final class TokenCountBuilder<T extends Enum<T>> {

        private final T item;

        private final Builder<T> builder;

        TokenCountBuilder(T item, Builder<T> builder) {
            this.item = item;
            this.builder = builder;
        }

        public final FinishableTokenCountBuilder<T> onEntering(IntPredicate pred) {
            return new FinishableTokenCountBuilder<T>(item, builder, pred);
        }

        public final FinishableTokenCountBuilder<T> onEntering(int tokenType) {
            return onEntering(matching(tokenType));
        }

        public final FinishableTokenCountBuilder<T> onEntering(int tokenType, int... more) {
            return onEntering(matchingAny(tokenType, more));
        }
    }

    public static final class FinishableTokenCountBuilder<T extends Enum<T>> {

        private final T item;
        private final Builder<T> builder;
        private final IntPredicate onEnter;
        private IntPredicate stopAt;
        private boolean reverse;

        FinishableTokenCountBuilder(T item, Builder<T> builder, IntPredicate onEnter) {
            this.item = item;
            this.builder = builder;
            this.onEnter = onEnter;
            this.reverse = reverse;
        }

        public Builder<T> scanningForwardUntil(int stopToken) {
            return scanningForwardUntil(matching(stopToken));
        }

        public Builder<T> scanningForwardUntil(int stopToken, int... more) {
            return scanningForwardUntil(matchingAny(stopToken, more));
        }

        public Builder<T> scanningForwardUntil(IntPredicate stopToken) {
            if (stopAt != null) {
                throw new IllegalStateException("Already completed");
            }
            this.stopAt = stopToken;
            this.reverse = false;
            builder.counters.add(this);
            return builder;
        }

        public Builder<T> scanningBackwardUntil(int stopToken) {
            return scanningBackwardUntil(matching(stopToken));
        }

        public Builder<T> scanningBackwardUntil(int stopToken, int... more) {
            return scanningBackwardUntil(matchingAny(stopToken, more));
        }

        public Builder<T> scanningBackwardUntil(IntPredicate stopToken) {
            if (stopAt != null) {
                throw new IllegalStateException("Already completed");
            }
            this.stopAt = stopToken;
            this.reverse = true;
            builder.counters.add(this);
            return builder;
        }
    }

    public static final class SetBuilder<T extends Enum<T>> {

        private final Builder<T> builder;
        private final T item;

        SetBuilder(Builder<T> builder, T item) {
            this.builder = builder;
            this.item = item;
        }

        public FinishableSetBuilder<T> onTokenType(IntPredicate tokenType) {
            return new FinishableSetBuilder<>(tokenType, builder, item);
        }

        public FinishableSetBuilder<T> onTokenType(int tokenType) {
            return new FinishableSetBuilder<>(matching(tokenType), builder, item);
        }

        public FinishableSetBuilder<T> onTokenType(int tokenType, int... more) {
            return new FinishableSetBuilder<>(matchingAny(tokenType, more), builder, item);
        }
    }

    private static class FinishableSetBuilder<T extends Enum<T>> {

        private final IntPredicate tokenType;
        private final Builder<T> b;
        private final T item;
        private IntPredicate clearOn;

        FinishableSetBuilder(IntPredicate tokenType, Builder<T> b, T item) {
            this.tokenType = tokenType;
            this.b = b;
            this.item = item;
        }

        public Builder<T> clearingOnTokenType(int type) {
            return clearingOnTokenType(matching(type));
        }

        public Builder<T> clearingOnTokenType(int type, int... more) {
            return clearingOnTokenType(matchingAny(type, more));
        }

        public Builder<T> clearingOnTokenType(IntPredicate pred) {
            this.clearOn = pred;
            b.booleans.add(this);
            return b;
        }
    }

    public static final class PushPositionBuilder<T extends Enum<T>> {

        private final T item;
        private final Builder<T> builder;

        PushPositionBuilder(Builder<T> builder, T item) {
            this.builder = builder;
            this.item = item;
        }

        public FinishablePushPositionBuilder<T> onTokenType(int type) {
            return onTokenType(matching(type));
        }

        public FinishablePushPositionBuilder<T> onTokenType(int type, int... more) {
            return onTokenType(matchingAny(type, more));
        }

        public FinishablePushPositionBuilder<T> onTokenType(IntPredicate pred) {
            return new FinishablePushPositionBuilder<>(item, builder, pred);
        }
    }

    public static final class FinishablePushPositionBuilder<T extends Enum<T>> {

        private final T item;
        private final Builder<T> builder;
        private final IntPredicate tokenMatcher;
        private IntPredicate popper;

        FinishablePushPositionBuilder(T item, Builder<T> builder, IntPredicate tokenMatcher) {
            this.item = item;
            this.builder = builder;
            this.tokenMatcher = tokenMatcher;
        }

        public Builder<T> poppingOnTokenType(IntPredicate popper) {
            this.popper = popper;
            builder.pushers.add(this);
            return builder;
        }

        public Builder<T> poppingOnTokenType(int type) {
            return poppingOnTokenType(matching(type));
        }

        public Builder<T> poppingOnTokenType(int type, int... more) {
            return poppingOnTokenType(matchingAny(type, more));
        }
    }

    private static IntPredicate matching(int type) {
        return new Match(type);
    }

    private static IntPredicate matchingAny(int first, int[] more) {
        int[] items = new int[more.length + 1];
        items[0] = first;
        System.arraycopy(more, 0, items, 1, more.length);
        Arrays.sort(items);
        return new MultiMatch(items);
    }

    private static final class MultiMatch implements IntPredicate {

        private final int[] items;

        MultiMatch(int[] items) {
            Arrays.sort(items);
            this.items = items;
        }

        @Override
        public boolean test(int value) {
            return Arrays.binarySearch(items, value) >= 0;
        }

        public String toString() {
            return "AnyOf " + Arrays.toString(items);
        }
    }

    private static final class Match implements IntPredicate {

        private final int type;

        public Match(int type) {
            this.type = type;
        }

        @Override
        public boolean test(int i) {
            return type == i;
        }

        @Override
        public String toString() {
            return "matching(" + type + ")";
        }
    }

    public static final class IncrementDecrementBuilder<T extends Enum<T>> {

        private final Builder<T> builder;
        private final T item;

        IncrementDecrementBuilder(Builder<T> builder, T item) {
            this.builder = builder;
            this.item = item;
        }

        public FinishableIncrementDecrementBuilder<T> onTokenType(int token) {
            return new FinishableIncrementDecrementBuilder<>(builder, matching(token), item);
        }

        public FinishableIncrementDecrementBuilder<T> onTokenType(int token, int... more) {
            return new FinishableIncrementDecrementBuilder<>(builder, matchingAny(token, more), item);
        }

        public FinishableIncrementDecrementBuilder<T> onTokenType(IntPredicate pred) {
            return new FinishableIncrementDecrementBuilder<>(builder, pred, item);
        }
    }

    static final class FinishableIncrementDecrementBuilder<T extends Enum<T>> {

        private IntPredicate clearPredicate;
        private IntPredicate decPredicate;
        private boolean built;
        private final T item;
        private final Builder<T> builder;
        private final IntPredicate tokenMatcher;

        FinishableIncrementDecrementBuilder(Builder<T> builder, IntPredicate tokenMatcher, T item) {
            this.builder = builder;
            this.tokenMatcher = tokenMatcher;
            this.item = item;
        }

        private void build() {
            if (built) {
                throw new IllegalStateException("Already built");
            }
            built = true;
            builder.builders.add(this);
        }

        public Builder<T> clearingWhenTokenEncountered(int token) {
            return clearingWhenTokenEncountered(matching(token));
        }

        public Builder<T> clearingWhenTokenEncountered(int token, int... more) {
            return clearingWhenTokenEncountered(matchingAny(token, more));
        }

        public Builder<T> decrementingWhenTokenEncountered(int token) {
            return decrementingWhenTokenEncountered(matching(token));
        }

        public Builder<T> decrementingWhenTokenEncountered(int token, int... more) {
            return decrementingWhenTokenEncountered(matchingAny(token, more));
        }

        public Builder<T> clearingWhenTokenEncountered(IntPredicate pred) {
            clearPredicate = pred;
            build();
            return builder;
        }

        public Builder<T> decrementingWhenTokenEncountered(IntPredicate pred) {
            decPredicate = pred;
            build();
            return builder;
        }
    }

    static final class IntList extends LinkedList<Integer> {

    }
}
