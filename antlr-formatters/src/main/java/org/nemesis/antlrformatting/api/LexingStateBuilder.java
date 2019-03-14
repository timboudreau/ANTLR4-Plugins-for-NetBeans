package org.nemesis.antlrformatting.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import org.antlr.v4.runtime.Token;

/**
 * Builder that allows you to define integers and booleans that you would like
 * the lexing process to capture, in order to define rules that depend on the
 * value of such captured values. For example, if you want to record the
 * location of the most recent ':' character, and in your grammar that has a
 * rule named <code>COLON</code>, you can "program" the lexing state to record
 * that value and then create rules that use it when present (say, as an indent
 * position).
 * <p>
 * The keys for looking up values are enum constants - the enum can be one you
 * create. Note that LexingState is not parameterized on the enum type. It is
 * not an error to pass a different enum type to its methods (the ordinal of the
 * enum constant is what is used), but it is likely enough to be a bug that a
 * warning will be logged.
 * </p>
 * <p>
 * A key may be used for exactly one thing - key reuse results in an exception
 * being thrown.
 * </p>
 * <p>
 * Note - token and distance <i>counting</i> has some cost, as it needs to be
 * done for every token it applies to - it pays to make these as specific as
 * possible.
 * </p>
 * <p>
 * Some builders can specify whether updates occur before or after token
 * processing - for example, if when processing an '}', you want to know the
 * position of the preceding '{', you want to ensure the '{' position is only
 * reset after processing of the '}' has completed, by calling the correct
 * method on the sub-builder.
 * </p>
 *
 * @author Tim Boudreau
 */
public final class LexingStateBuilder<T extends Enum<T>, R> {

    private final List<FinishableIncrementDecrementBuilder<T, R>> builders = new ArrayList<>(5);
    private final List<FinishablePushPositionBuilder<T, R>> pushers = new ArrayList<>(5);
    private final List<FinishableSetBuilder<T, R>> booleans = new ArrayList<>(5);
    private final List<FinishableTokenCountBuilder<T, R>> counters = new ArrayList<>(5);
    private final List<FinishableDistanceBuilder<T, R>> distances = new ArrayList<>(5);
    private final List<FinishablePositionRecorderBuilder<T, R>> positionRecorders = new ArrayList<>(5);
    private final Class<T> type;
    private final Function<LexingState, R> convert;
    private Set<T> used;

    enum Kind {
        STACK, BOOLEAN, COUNTER
    }

    LexingStateBuilder(Class<T> type, Function<LexingState, R> convert) {
        this.type = type;
        used = EnumSet.noneOf(type);
        this.convert = convert;
    }

    /**
     * Create the lexing state, and return whatever the function passed to the
     * constructor provides when it receives it.
     *
     * @return The object to be built
     */
    public R build() {
        int ct = type.getEnumConstants().length;
        boolean[] booleanState = new boolean[ct];
        LexingState.IntList[] stacks = new LexingState.IntList[ct];
        int[] values = new int[ct];
        Arrays.fill(values, -1);
        Kind[] kinds = new Kind[ct];
        List<BiConsumer<Token, LexerScanner>> befores = new ArrayList<>(5);
        List<BiConsumer<Token, LexerScanner>> afters = new ArrayList<>(5);
        for (FinishableIncrementDecrementBuilder<T, R> b : builders) {
            assert kinds[b.item.ordinal()] == null;
            kinds[b.item.ordinal()] = Kind.COUNTER;
            if (b.before) {
                afters.add(buildOne(b, values));
            } else {
                befores.add(buildOne(b, values));
            }
        }
        for (FinishablePushPositionBuilder<T, R> pusher : pushers) {
            assert kinds[pusher.item.ordinal()] == null;
            kinds[pusher.item.ordinal()] = Kind.STACK;
            if (pusher.before) {
                befores.add(buildOne(pusher, stacks));
            } else {
                afters.add(buildOne(pusher, stacks));
            }
        }
        for (FinishableSetBuilder<T, R> setter : booleans) {
            assert kinds[setter.item.ordinal()] == null;
            kinds[setter.item.ordinal()] = Kind.BOOLEAN;
            boolean after = setter.after;
            if (after) {
                afters.add(buildOne(setter, booleanState));
            } else {
                befores.add(buildOne(setter, booleanState));
            }
        }
        for (FinishableTokenCountBuilder<T, R> counter : counters) {
            assert kinds[counter.item.ordinal()] == null;
            kinds[counter.item.ordinal()] = Kind.COUNTER;
            befores.add(buildOne(counter, values));
        }
        for (FinishableDistanceBuilder<T, R> dist : distances) {
            assert kinds[dist.item.ordinal()] == null;
            int ord = dist.item.ordinal();
            kinds[ord] = Kind.COUNTER;
            befores.add(buildOne(dist, values));
        }
        for (FinishablePositionRecorderBuilder<T, R> recorder : positionRecorders) {
            assert kinds[recorder.item.ordinal()] == null;
            int ord = recorder.item.ordinal();
            kinds[ord] = Kind.COUNTER;
            if (recorder.before) {
                befores.add(buildOne(recorder, values));
            } else {
                afters.add(buildOne(recorder, values));
            }
        }
        LexingState result = new LexingState(booleanState, stacks, values, kinds, befores, afters, type);
        return convert.apply(result);
    }

    private static <T extends Enum<T>, R> BiConsumer<Token, LexerScanner> buildOne(FinishableSetBuilder<T, R> setter, boolean[] booleanState) {
        int ord = setter.item.ordinal();
        IntPredicate matcher = setter.tokenType;
        IntPredicate clearer = setter.clearOn;
        return new SetBooleanConsumer(matcher, booleanState, ord, clearer);
    }

    private static <T extends Enum<T>, R> BiConsumer<Token, LexerScanner> buildOne(FinishableTokenCountBuilder<T, R> counter, int[] values) {
        int ord = counter.item.ordinal();
        IntPredicate tester = counter.onEnter;
        boolean isReverse = counter.reverse;
        IntPredicate stopTokenMatcher = counter.stopAt;
        IntPredicate matcher = counter.matcher;
        return new CountTokensConsumer(tester, isReverse, matcher, stopTokenMatcher, values, ord);
    }

    private static <T extends Enum<T>, R> BiConsumer<Token, LexerScanner> buildOne(FinishableDistanceBuilder<T, R> dist, int[] values) {
        int ord = dist.item.ordinal();
        boolean forward = dist.forward;
        boolean ignoreWhitespace = !dist.includeWhitespace;
        IntPredicate matcher = dist.trigger;
        IntPredicate distanceTo = dist.token;
        return new DistanceConsumer(matcher, forward, ignoreWhitespace, distanceTo, values, ord);
    }

    private static <T extends Enum<T>, R> BiConsumer<Token, LexerScanner> buildOne(FinishablePositionRecorderBuilder<T, R> recorder, int[] values) {
        int ord = recorder.item.ordinal();
        IntPredicate matcher = recorder.tokenMatcher;
        IntPredicate clearer = recorder.clearer;
        boolean original = recorder.originalPosition;
        return new SetPositionConsumer(matcher, values, ord, clearer, original);
    }

    private static <T extends Enum<T>, R> BiConsumer<Token, LexerScanner> buildOne(FinishablePushPositionBuilder<T, R> pusher, LexingState.IntList[] stacks) {
        int ord = pusher.item.ordinal();
        stacks[ord] = new LexingState.IntList();
        IntPredicate matcher = pusher.tokenMatcher;
        IntPredicate popper = pusher.popper;
        boolean original = pusher.originalPosition;
        return new PushConsumer(matcher, stacks, ord, popper, original);
    }

    private static <T extends Enum<T>, R> BiConsumer<Token, LexerScanner> buildOne(FinishableIncrementDecrementBuilder<T, R> b, int[] values) {
        int ord = b.item.ordinal();
        IntPredicate matcher = b.tokenMatcher;
        IntPredicate respond = b.clearPredicate == null ? b.decPredicate : b.clearPredicate;
        boolean isClear = b.clearPredicate != null;
        return new IncrementDecrementConsumer(matcher, values, ord, respond, isClear);
    }

    private void checkUsed(T item) {
        if (used.contains(item)) {
            throw new IllegalStateException("Already used " + item);
        }
        used.add(item);
    }

    /**
     * Increment the value of this key on some token, decrementing it on
     * encountering some other token.
     *
     * @param key The key to use
     * @throws IllegalStateException if they key has already been used for
     * something else in this builder
     * @return An increment/decrement builder
     */
    public IncrementDecrementBuilder<T, R> increment(T key) {
        checkUsed(key);
        return new IncrementDecrementBuilder<>(this, key);
    }

    /**
     * Record the line-start-relative position of some token, using a stack-like
     * structure suitable for nested parentheses, braces, etc., such that if you
     * encounter a second one, and then its exit token, the value is restored to
     * that of the first on exiting the second exit token.
     *
     * @param key The key to store this under
     * @throws IllegalStateException if they key has already been used for
     * something else in this builder
     * @return A push-position builder
     */
    public PushPositionBuilder<T, R> pushPosition(T key) {
        checkUsed(key);
        return new PushPositionBuilder<>(this, key);
    }

    /**
     * Set a boolean on some condition, unsetting it on another.
     *
     * @param key The key to store the boolean under
     * @throws IllegalStateException if they key has already been used for
     * something else in this builder
     * @return A builder to define the conditions
     */
    public SetBuilder<T, R> set(T key) {
        checkUsed(key);
        return new SetBuilder<>(this, key);
    }

    /**
     * Record the line-start-relative position when encountering a token or set
     * of tokens. Use this for delimiter tokens (such as the ':' in an ANTLR
     * rule) which mark the beginning of a predicate, but which do not have a
     * corresponding "close" token - i.e. nesting is not possible (use
     * pushLocation for that).
     *
     * @param key The key to store this under
     * @throws IllegalStateException if they key has already been used for
     * something else in this builder
     * @return A builder to define the conditions
     */
    public RecordPositionBuilder<T, R> recordPosition(T key) {
        checkUsed(key);
        return new RecordPositionBuilder<>(this, key);
    }

    /**
     * Record the number of tokens matching some condition, scanning forward or
     * backward, on matching some token. Useful for, for example, determining if
     * a line of code is the only one present in an ANTLR action (by counting
     * semicolon tokens between curly braces) and therefore should be rendered
     * inline as a one-liner, rather than with newlines on either side of the
     * code lines.
     *
     * @param key The key to record the value under
     * @throws IllegalStateException if they key has already been used for
     * something else in this builder
     * @return A token count builder to define the conditions under which tokens
     * should be counted
     */
    public TokenCountBuilder<T, R> count(T key) {
        checkUsed(key);
        return new TokenCountBuilder<>(key, this);
    }

    /**
     * Record the distance, forward or backward, to some token. For example,
     * when processing lines of code delimited by <code>{}</code> characters, it
     * is useful to know if you are or are not processing the final line before
     * the closing <code>}</code> in order to perform different space or newline
     * handling in that case.
     *
     * @param key The key to record the value under.
     * @throws IllegalStateException if they key has already been used for
     * something else in this builder
     * @return A token distance builder
     */
    public TokenDistanceBuilder<T, R> computeTokenDistance(T key) {
        checkUsed(key);
        return new TokenDistanceBuilder<>(key, this);
    }

    private static final class IncrementDecrementConsumer implements BiConsumer<Token, LexerScanner> {

        private final IntPredicate matcher;
        private final int[] values;
        private final int ord;
        private final IntPredicate respond;
        private final boolean isClear;

        IncrementDecrementConsumer(IntPredicate matcher, int[] values, int ord, IntPredicate respond, boolean isClear) {
            this.matcher = matcher;
            this.values = values;
            this.ord = ord;
            this.respond = respond;
            this.isClear = isClear;
        }

        @Override
        public void accept(Token t, LexerScanner u) {
            int tokenType = t.getType();
            if (matcher.test(tokenType)) {
                if (values[ord] == -1) {
                    values[ord] = 0;
                }
                values[ord]++;
            }
            if (respond.test(tokenType)) {
                if (isClear) {
                    values[ord] = -1;
                } else {
                    values[ord]--;
                    if (values[ord] <= 0) {
                        values[ord] = -1;
                    }
                }
            }
        }
    }

    private static final class PushConsumer implements BiConsumer<Token, LexerScanner> {

        private final IntPredicate matcher;
        private final LexingState.IntList[] stacks;
        private final int ord;
        private final IntPredicate popper;
        private final boolean original;

        PushConsumer(IntPredicate matcher, LexingState.IntList[] stacks, int ord, IntPredicate popper, boolean original) {
            this.matcher = matcher;
            this.stacks = stacks;
            this.ord = ord;
            this.popper = popper;
            this.original = original;
        }

        @Override
        public void accept(Token t, LexerScanner u) {
            int type = t.getType();
            if (matcher.test(type)) {
                if (original) {
                    stacks[ord].push(u.origCharPositionInLine());
                } else {
                    stacks[ord].push(u.currentCharPositionInLine());
                }
            }
            if (popper.test(type)) {
                if (!stacks[ord].isEmpty()) {
                    stacks[ord].pop();
                }
            }
        }
    }

    private static final class CountTokensConsumer implements BiConsumer<Token, LexerScanner> {

        private final IntPredicate tester;
        private final boolean isReverse;
        private final IntPredicate matcher;
        private final IntPredicate stopTokenMatcher;
        private final int[] values;
        private final int ord;

        CountTokensConsumer(IntPredicate tester, boolean isReverse, IntPredicate matcher, IntPredicate stopTokenMatcher, int[] values, int ord) {
            this.tester = tester;
            this.isReverse = isReverse;
            this.matcher = matcher;
            this.stopTokenMatcher = stopTokenMatcher;
            this.values = values;
            this.ord = ord;
        }

        @Override
        public void accept(Token t, LexerScanner u) {
            int type = t.getType();
            if (tester.test(type)) {
                int count;
                if (isReverse) {
                    count = u.countBackwardOccurrencesUntilPrevious(matcher, stopTokenMatcher);
                } else {
                    count = u.countForwardOccurrencesUntilNext(matcher, stopTokenMatcher);
                }
                values[ord] = count;
            }
        }
    }

    private static final class SetBooleanConsumer implements BiConsumer<Token, LexerScanner> {

        private final IntPredicate matcher;
        private final boolean[] booleanState;
        private final int ord;
        private final IntPredicate clearer;

        SetBooleanConsumer(IntPredicate matcher, boolean[] booleanState, int ord, IntPredicate clearer) {
            this.matcher = matcher;
            this.booleanState = booleanState;
            this.ord = ord;
            this.clearer = clearer;
        }

        @Override
        public void accept(Token t, LexerScanner u) {
            int type = t.getType();
            boolean matcherMatched = matcher.test(type);
            if (matcherMatched) {
                booleanState[ord] = true;
            }
            if (clearer.test(type)) {
                if (matcherMatched) {
                    throw new IllegalStateException("Set and unset for same token " + t + " probably a predicate is wrong: " + matcher + " or " + clearer);
                }
                booleanState[ord] = false;
            }
        }
    }

    private static final class DistanceConsumer implements BiConsumer<Token, LexerScanner> {

        private final IntPredicate matcher;
        private final boolean forward;
        private final boolean ignoreWhitespace;
        private final IntPredicate distanceTo;
        private final int[] values;
        private final int ord;

        DistanceConsumer(IntPredicate matcher, boolean forward, boolean ignoreWhitespace, IntPredicate distanceTo, int[] values, int ord) {
            this.matcher = matcher;
            this.forward = forward;
            this.ignoreWhitespace = ignoreWhitespace;
            this.distanceTo = distanceTo;
            this.values = values;
            this.ord = ord;
        }

        @Override
        public void accept(Token t, LexerScanner u) {
            if (matcher.test(0)) {
                int distance;
                if (forward) {
                    distance = u.tokenCountToNext(ignoreWhitespace, distanceTo);
                } else {
                    distance = u.tokenCountToPreceding(ignoreWhitespace, distanceTo);
                }
                values[ord] = distance;
            }
        }
    }

    private static final class SetPositionConsumer implements BiConsumer<Token, LexerScanner> {

        private final IntPredicate matcher;
        private final int[] values;
        private final int ord;
        private final IntPredicate clearer;
        private final boolean original;

        SetPositionConsumer(IntPredicate matcher, int[] values, int ord, IntPredicate clearer, boolean original) {
            this.matcher = matcher;
            this.values = values;
            this.ord = ord;
            this.clearer = clearer;
            this.original = original;
        }

        @Override
        public void accept(Token t, LexerScanner u) {
            int type = t.getType();
            if (matcher.test(type)) {
                if (original) {
                    values[ord] = u.origCharPositionInLine();
                } else {
                    values[ord] = u.currentCharPositionInLine();
                }
            }
            if (clearer.test(type)) {
                values[ord] = -1;
            }
        }
    }

    /**
     * Builder for counting the number of tokens between this token and the
     * next/previous token of some other type upon encountering a token of some
     * type.
     *
     * @param <T>
     * @param <R>
     */
    public static final class TokenDistanceBuilder<T extends Enum<T>, R> {

        private final LexingStateBuilder<T, R> builder;
        private final T item;

        TokenDistanceBuilder(T item, LexingStateBuilder<T, R> builder) {
            this.item = item;
            this.builder = builder;
        }

        public final TokenDistanceTargetBuilder<T, R> onEntering(IntPredicate pred) {
            return new TokenDistanceTargetBuilder<>(item, builder, pred);
        }

        public final TokenDistanceTargetBuilder<T, R> onEntering(int tokenType) {
            return onEntering(matching(tokenType));
        }

        public final TokenDistanceTargetBuilder<T, R> onEntering(int tokenType, int... more) {
            return onEntering(matchingAny(tokenType, more));
        }
    }

    public static final class TokenDistanceTargetBuilder<T extends Enum<T>, R> {

        private final LexingStateBuilder<T, R> builder;
        private final T item;
        private final IntPredicate trigger;

        TokenDistanceTargetBuilder(T item, LexingStateBuilder<T, R> builder, IntPredicate trigger) {
            this.item = item;
            this.builder = builder;
            this.trigger = trigger;
        }

        public FinishableDistanceBuilder<T, R> toNext(int token) {
            return toNext(matching(token));
        }

        public FinishableDistanceBuilder<T, R> toNext(int token, int... more) {
            return toNext(matchingAny(token, more));
        }

        public FinishableDistanceBuilder<T, R> toNext(IntPredicate token) {
            return new FinishableDistanceBuilder<>(item, token, builder, true, trigger);
        }

        public FinishableDistanceBuilder<T, R> toPreceding(int tokenType, int... more) {
            return toPreceding(matchingAny(tokenType, more));
        }

        public FinishableDistanceBuilder<T, R> toPreceding(int tokenType) {
            return toPreceding(matching(tokenType));
        }

        public FinishableDistanceBuilder<T, R> toPreceding(IntPredicate token) {
            return new FinishableDistanceBuilder<>(item, token, builder, false, trigger);
        }
    }

    public static final class FinishableDistanceBuilder<T extends Enum<T>, R> {

        private final T item;
        private final IntPredicate token;
        private final LexingStateBuilder<T, R> builder;
        private final boolean forward;
        private boolean includeWhitespace;
        private boolean built;
        private final IntPredicate trigger;

        FinishableDistanceBuilder(T item, IntPredicate token, LexingStateBuilder<T, R> builder, boolean forward, IntPredicate trigger) {
            this.item = item;
            this.token = token;
            this.builder = builder;
            this.forward = forward;
            this.trigger = trigger;
        }

        public LexingStateBuilder<T, R> includingWhitespace() {
            if (built) {
                throw new IllegalStateException("Already built.");
            }
            built = true;
            this.includeWhitespace = true;
            builder.distances.add(this);
            return builder;
        }

        public LexingStateBuilder<T, R> ignoringWhitespace() {
            if (built) {
                throw new IllegalStateException("Already built.");
            }
            built = true;
            this.includeWhitespace = false;
            builder.distances.add(this);
            return builder;
        }
    }

    public static final class TokenCountBuilder<T extends Enum<T>, R> {

        private final T item;
        private final LexingStateBuilder<T, R> builder;

        TokenCountBuilder(T item, LexingStateBuilder<T, R> builder) {
            this.item = item;
            this.builder = builder;
        }

        public final CountWhatBuilder<T, R> onEntering(IntPredicate pred) {
            return new CountWhatBuilder<>(item, builder, pred);
        }

        public final CountWhatBuilder<T, R> onEntering(int tokenType) {
            return onEntering(matching(tokenType));
        }

        public final CountWhatBuilder<T, R> onEntering(int tokenType, int... more) {
            return onEntering(matchingAny(tokenType, more));
        }
    }

    public static final class CountWhatBuilder<T extends Enum<T>, R> {

        private final T item;
        private final LexingStateBuilder<T, R> builder;
        private final IntPredicate onEnter;

        CountWhatBuilder(T item, LexingStateBuilder<T, R> builder, IntPredicate onEnter) {
            this.item = item;
            this.builder = builder;
            this.onEnter = onEnter;
        }

        public FinishableTokenCountBuilder<T, R> countTokensMatching(int tokenToMatch) {
            return countTokensMatching(matching(tokenToMatch));
        }

        public FinishableTokenCountBuilder<T, R> countTokensMatching(int tokenToMatch, int... more) {
            return countTokensMatching(matchingAny(tokenToMatch, more));
        }

        public FinishableTokenCountBuilder<T, R> countTokensMatching(IntPredicate match) {
            return new FinishableTokenCountBuilder<>(item, builder, onEnter, match);
        }
    }

    public static final class FinishableTokenCountBuilder<T extends Enum<T>, R> {

        private final T item;
        private final LexingStateBuilder<T, R> builder;
        private final IntPredicate onEnter;
        private IntPredicate stopAt;
        private boolean reverse;
        private final IntPredicate matcher;

        FinishableTokenCountBuilder(T item, LexingStateBuilder<T, R> builder, IntPredicate onEnter, IntPredicate matcher) {
            this.item = item;
            this.builder = builder;
            this.onEnter = onEnter;
            this.matcher = matcher;
        }

        public LexingStateBuilder<T, R> scanningForwardUntil(int stopToken) {
            return scanningForwardUntil(matching(stopToken));
        }

        public LexingStateBuilder<T, R> scanningForwardUntil(int stopToken, int... more) {
            return scanningForwardUntil(matchingAny(stopToken, more));
        }

        public LexingStateBuilder<T, R> scanningForwardUntil(IntPredicate stopToken) {
            if (stopAt != null) {
                throw new IllegalStateException("Already completed");
            }
            this.stopAt = stopToken;
            this.reverse = false;
            builder.counters.add(this);
            return builder;
        }

        public LexingStateBuilder<T, R> scanningBackwardUntil(int stopToken) {
            return scanningBackwardUntil(matching(stopToken));
        }

        public LexingStateBuilder<T, R> scanningBackwardUntil(int stopToken, int... more) {
            return scanningBackwardUntil(matchingAny(stopToken, more));
        }

        public LexingStateBuilder<T, R> scanningBackwardUntil(IntPredicate stopToken) {
            if (stopAt != null) {
                throw new IllegalStateException("Already completed");
            }
            this.stopAt = stopToken;
            this.reverse = true;
            builder.counters.add(this);
            return builder;
        }
    }

    public static final class SetBuilder<T extends Enum<T>, R> {

        private final LexingStateBuilder<T, R> builder;
        private final T item;

        SetBuilder(LexingStateBuilder<T, R> builder, T item) {
            this.builder = builder;
            this.item = item;
        }

        public FinishableSetBuilder<T, R> onTokenType(IntPredicate tokenType) {
            return new FinishableSetBuilder<>(tokenType, builder, item);
        }

        public FinishableSetBuilder<T, R> onTokenType(int tokenType) {
            return new FinishableSetBuilder<>(matching(tokenType), builder, item);
        }

        public FinishableSetBuilder<T, R> onTokenType(int tokenType, int... more) {
            return new FinishableSetBuilder<>(matchingAny(tokenType, more), builder, item);
        }
    }

    public static final class FinishableSetBuilder<T extends Enum<T>, R> {

        private final IntPredicate tokenType;
        private final LexingStateBuilder<T, R> b;
        private final T item;
        private IntPredicate clearOn;
        private boolean after;

        FinishableSetBuilder(IntPredicate tokenType, LexingStateBuilder<T, R> b, T item) {
            this.tokenType = tokenType;
            this.b = b;
            this.item = item;
        }

        public LexingStateBuilder<T, R> clearingOnTokenType(int type) {
            return clearingOnTokenType(matching(type));
        }

        public LexingStateBuilder<T, R> clearingOnTokenType(int type, int... more) {
            return clearingOnTokenType(matchingAny(type, more));
        }

        public LexingStateBuilder<T, R> clearingAfterTokenType(int type) {
            after = true;
            return clearingOnTokenType(matching(type));
        }

        public LexingStateBuilder<T, R> clearingAfterTokenType(int type, int... more) {
            after = true;
            return clearingOnTokenType(matchingAny(type, more));
        }

        public LexingStateBuilder<T, R> clearingAfterTokenType(IntPredicate pred) {
            after = true;
            return clearingAfterTokenType(pred);
        }

        public LexingStateBuilder<T, R> clearingOnTokenType(IntPredicate pred) {
            this.clearOn = pred;
            b.booleans.add(this);
            return b;
        }
    }

    /**
     * Builder for <i>stack-oriented</i> values such as sets of nested braces,
     * where it is useful to be able to determine the nesting depth when
     * formatting (say, to determine indenting depth).
     *
     * @param <T> The enum type
     * @param <R> The builder type
     */
    public static final class PushPositionBuilder<T extends Enum<T>, R> {

        private final T item;
        private final LexingStateBuilder<T, R> builder;
        private boolean before;
        private boolean originalPosition;

        PushPositionBuilder(LexingStateBuilder<T, R> builder, T item) {
            this.builder = builder;
            this.item = item;
        }

        /**
         * If this is called, record the position as it was <i>before</i>
         * any reformatting occurs, rather than after.
         *
         * @return this
         */
        public PushPositionBuilder<T, R> beforeProcessingToken() {
            before = true;
            return this;
        }

        /**
         * If this is called, record the position as it appeared in the input
         * text rather than the next character position after formatting of
         * preceding lines has been applied.
         *
         * @return this
         */
        public PushPositionBuilder<T, R> usingPositionFromInput() {
            this.originalPosition = true;
            return this;
        }

        /**
         * Record the position when a specific token type is encountered.
         *
         * @param type The token type
         * @return a finishable builder
         */
        public FinishablePushPositionBuilder<T, R> onTokenType(int type) {
            return onTokenType(matching(type));
        }

        /**
         * Record the position when any of several token types is encountered.
         *
         * @param type The type
         * @param more More token types
         * @return
         */
        public FinishablePushPositionBuilder<T, R> onTokenType(int type, int... more) {
            return onTokenType(matchingAny(type, more));
        }

        /**
         * Record the position when the passed predicate matches the token type.
         *
         * @param pred A predicate
         * @return this
         */
        public FinishablePushPositionBuilder<T, R> onTokenType(IntPredicate pred) {
            return new FinishablePushPositionBuilder<>(item, builder, pred, before, originalPosition);
        }
    }

    public static final class FinishablePushPositionBuilder<T extends Enum<T>, R> {

        private final T item;
        private final LexingStateBuilder<T, R> builder;
        private final IntPredicate tokenMatcher;
        private IntPredicate popper;
        private final boolean before;
        private final boolean originalPosition;

        FinishablePushPositionBuilder(T item, LexingStateBuilder<T, R> builder, IntPredicate tokenMatcher, boolean before, boolean originalPosition) {
            this.item = item;
            this.builder = builder;
            this.tokenMatcher = tokenMatcher;
            this.before = before;
            this.originalPosition = originalPosition;
        }

        /**
         * Remove the last recorded value when some "closing" token is
         * encountered (for example, the corresponding <code>}</code> character
         * to a preceding <code>{</code> character.
         *
         * @param popper A predicate which matches the token type
         * @return the parent builder
         */
        public LexingStateBuilder<T, R> poppingOnTokenType(IntPredicate popper) {
            this.popper = popper;
            builder.pushers.add(this);
            return builder;
        }

        /**
         * Remove the last recorded value when some "closing" token is
         * encountered (for example, the corresponding <code>}</code> character
         * to a preceding <code>{</code> character.
         *
         * @param type the token type to trigger popping the last recorded value
         * from the stack
         * @return the parent builder
         */
        public LexingStateBuilder<T, R> poppingOnTokenType(int type) {
            return poppingOnTokenType(matching(type));
        }

        /**
         * Remove the last recorded value when some "closing" token is
         * encountered (for example, the corresponding <code>}</code> character
         * to a preceding <code>{</code> character.
         *
         * @param type the token type to trigger popping the last recorded value
         * from the stack
         * @param more Additional tokens which should trigger popping
         * @return the parent builder
         */
        public LexingStateBuilder<T, R> poppingOnTokenType(int type, int... more) {
            return poppingOnTokenType(matchingAny(type, more));
        }
    }

    /**
     * Builder which records the line position of a token, clobbering any
     * previously set value.
     *
     * @param <T>
     * @param <R>
     */
    public static final class RecordPositionBuilder<T extends Enum<T>, R> {

        private final T item;
        private final LexingStateBuilder<T, R> builder;
        private boolean before;
        private boolean originalPosition;

        RecordPositionBuilder(LexingStateBuilder<T, R> builder, T item) {
            this.builder = builder;
            this.item = item;
        }

        /**
         * If this is called, record the position as it was <i>before</i>
         * any reformatting occurs, rather than after.
         *
         * @return this
         */
        public RecordPositionBuilder<T, R> usingPositionFromInput() {
            this.originalPosition = true;
            return this;
        }

        /**
         * If this is called, record the position as it appeared in the input
         * text rather than the next character position after formatting of
         * preceding lines has been applied.
         *
         * @return this
         */
        public RecordPositionBuilder<T, R> beforeProcessingToken() {
            before = true;
            return this;
        }

        public FinishablePositionRecorderBuilder<T, R> onTokenType(int type) {
            return onTokenType(matching(type));
        }

        public FinishablePositionRecorderBuilder<T, R> onTokenType(int type, int... more) {
            return onTokenType(matchingAny(type, more));
        }

        public FinishablePositionRecorderBuilder<T, R> onTokenType(IntPredicate pred) {
            return new FinishablePositionRecorderBuilder<>(item, builder, pred, before, originalPosition);
        }
    }

    /**
     * Finishes adding a rule which records the character position of a token.
     *
     * @param <T>
     * @param <R>
     */
    public static final class FinishablePositionRecorderBuilder<T extends Enum<T>, R> {

        private final T item;
        private final LexingStateBuilder<T, R> builder;
        private final IntPredicate tokenMatcher;
        private IntPredicate clearer;
        private final boolean before;
        private final boolean originalPosition;

        FinishablePositionRecorderBuilder(T item, LexingStateBuilder<T, R> builder, IntPredicate tokenMatcher, boolean before, boolean originalPosition) {
            this.item = item;
            this.builder = builder;
            this.tokenMatcher = tokenMatcher;
            this.before = before;
            this.originalPosition = originalPosition;
        }

        /**
         * Clear the value for this key when a token matches the passed
         * predicate.
         *
         * @param popper A predicate which will match on token type
         * @return The parent builder
         */
        public LexingStateBuilder<T, R> clearingOnTokenType(IntPredicate popper) {
            this.clearer = popper;
            builder.positionRecorders.add(this);
            return builder;
        }

        /**
         * Clear the value for this key when a token matches the passed value.
         *
         * @param type A token type
         * @return The parent builder
         */
        public LexingStateBuilder<T, R> clearingOnTokenType(int type) {
            return clearingOnTokenType(matching(type));
        }

        /**
         * Clear the value for this key when a token matches any of the passed
         * values.
         *
         * @param type A token type
         * @param more additional token types
         * @return The parent builder
         */
        public LexingStateBuilder<T, R> clearingOnTokenType(int type, int... more) {
            return clearingOnTokenType(matchingAny(type, more));
        }
    }

    private static IntPredicate matching(int type) {
        return new Match(type);
    }

    private static IntPredicate matchingAny(int first, int[] more) {
        if (more.length == 0) {
            return matching(first);
        }
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

        @Override
        public String toString() {
            return "AnyOf " + Arrays.toString(items);
        }
    }

    private static final class Match implements IntPredicate {

        private final int type;

        Match(int type) {
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

    /**
     * Builder which increments a counter when some token is encountered and
     * decrements it on some other.
     *
     * @param <T>
     * @param <R>
     */
    public static final class IncrementDecrementBuilder<T extends Enum<T>, R> {

        private final LexingStateBuilder<T, R> builder;
        private final T item;

        IncrementDecrementBuilder(LexingStateBuilder<T, R> builder, T item) {
            this.builder = builder;
            this.item = item;
        }

        public FinishableIncrementDecrementBuilder<T, R> onTokenType(int token) {
            return new FinishableIncrementDecrementBuilder<>(builder, matching(token), item);
        }

        public FinishableIncrementDecrementBuilder<T, R> onTokenType(int token, int... more) {
            return new FinishableIncrementDecrementBuilder<>(builder, matchingAny(token, more), item);
        }

        public FinishableIncrementDecrementBuilder<T, R> onTokenType(IntPredicate pred) {
            return new FinishableIncrementDecrementBuilder<>(builder, pred, item);
        }
    }

    /**
     * Finish creating a counting rule.
     *
     * @param <T>
     * @param <R>
     */
    public static final class FinishableIncrementDecrementBuilder<T extends Enum<T>, R> {

        private IntPredicate clearPredicate;
        private IntPredicate decPredicate;
        private boolean built;
        private final T item;
        private final LexingStateBuilder<T, R> builder;
        private final IntPredicate tokenMatcher;
        private boolean before;

        FinishableIncrementDecrementBuilder(LexingStateBuilder<T, R> builder, IntPredicate tokenMatcher, T item) {
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

        public LexingStateBuilder<T, R> clearingWhenTokenEncountered(int token) {
            return clearingWhenTokenEncountered(matching(token));
        }

        public LexingStateBuilder<T, R> clearingWhenTokenEncountered(int token, int... more) {
            return clearingWhenTokenEncountered(matchingAny(token, more));
        }

        public LexingStateBuilder<T, R> decrementingBeforeProcessingTokenWhenTokenEncountered(int token) {
            before = true;
            return decrementingWhenTokenEncountered(token);
        }

        public LexingStateBuilder<T, R> decrementingBeforeProcessingTokenWhenTokenEncountered(IntPredicate token) {
            before = true;
            return decrementingWhenTokenEncountered(token);
        }

        public LexingStateBuilder<T, R> decrementingBeforeProcessingTokenWhenTokenEncountered(int token, int... more) {
            before = true;
            return decrementingWhenTokenEncountered(token, more);
        }

        public LexingStateBuilder<T, R> decrementingWhenTokenEncountered(int token) {
            return decrementingWhenTokenEncountered(matching(token));
        }

        public LexingStateBuilder<T, R> decrementingWhenTokenEncountered(int token, int... more) {
            return decrementingWhenTokenEncountered(matchingAny(token, more));
        }

        public LexingStateBuilder<T, R> clearingWhenTokenEncountered(IntPredicate pred) {
            clearPredicate = pred;
            build();
            return builder;
        }

        public LexingStateBuilder<T, R> decrementingWhenTokenEncountered(IntPredicate pred) {
            decPredicate = pred;
            build();
            return builder;
        }
    }

}
