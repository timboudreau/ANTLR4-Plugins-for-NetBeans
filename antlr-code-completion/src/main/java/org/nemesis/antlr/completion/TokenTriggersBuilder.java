package org.nemesis.antlr.completion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.IntPredicate;
import org.antlr.v4.runtime.Token;
import org.openide.util.Parameters;

/**
 * Token triggers provide a fast way to rule out code completions which are not
 * going to return any results of interest, by allowing a
 * {@link CompletionItemProvider} simply never to be called when code completion
 * is invoked before or after a token which is irrelevant.
 *
 * @see org.nemesis.antlr.completion.CompletionBuilder
 * @author Tim Boudreau
 */
public class TokenTriggersBuilder<I> {

    private static final int[] EMPTY = new int[0];

    IntPredicate ignoring = Any.NONE;
    List<TokenTriggerPatternBuilder<I>> patterns = new ArrayList<>();
    final CompletionBuilder<I> bldr;

    TokenTriggersBuilder(TokenTriggersBuilder<I> other) {
        this.bldr = other.bldr;
        this.patterns.addAll(other.patterns);
        this.ignoring = other.ignoring;
    }

    TokenTriggersBuilder(CompletionBuilder<I> bldr) {
        this.bldr = bldr;
    }

    FinishableTokenTriggersBuilder<I> add(TokenTriggerPatternBuilder<I> bldr) {
        if (bldr.isEmpty()) {
            throw new IllegalArgumentException("Pattern specifies no before or"
                    + "after tokens to match");
        }
        patterns.add(bldr);
        return new FinishableTokenTriggersBuilder<>(this);
    }

    /**
     * Start a pattern which will match only when the tokens preceding the caret
     * are the passed token ids, in order such that the last element in the
     * array is the token immediately before the caret token.
     *
     * @param patternName The name of the pattern which will be passed into your
     * item provider to potentially compute different items based on that.
     *
     * @param preceding An array of token ids (static fields on your Antlr
     * generated lexer)
     * @return A builder for handling subsequent tokens.
     */
    public TokenTriggerPatternBuilder<I> whenPrecedingTokensMatch(String patternName, int... preceding) {
        Parameters.notNull("preceding", preceding);
        Parameters.notNull("patternName", patternName);
        if (patternName == null) {
            throw new IllegalArgumentException("Null pattern");
        }
        return new TokenTriggerPatternBuilder<>(patternName, this, preceding);
    }

    static final class IntArrayPredicate implements IntPredicate {

        private final int[] array;

        public IntArrayPredicate(int[] array) {
            this.array = Arrays.copyOf(array, array.length);
            Arrays.sort(array);
        }

        @Override
        public boolean test(int value) {
            return Arrays.binarySearch(array, value) >= 0;
        }

        @Override
        public String toString() {
            return "Matching " + Arrays.toString(array);
        }

    }

    /**
     * A builder for token triggers which contains at least one token pattern
     * and therefore can be finished.
     *
     * @param <I> The item type
     */
    public static class FinishableTokenTriggersBuilder<I> extends TokenTriggersBuilder<I> {

        FinishableTokenTriggersBuilder(TokenTriggersBuilder<I> other) {
            super(other);
        }

        /**
         * Indicate which token types to ignore when computing before / after
         * tokens for matching purposes (typically you want to ignore comments
         * and/or whitespace), and finish this token matching builder, adding it
         * to your completion configuration.
         *
         * @param tokenTypes A list of token types from your Antlr lexer, which
         * should be skipped when pattern matching
         * @return The completion builder that created this builder
         */
        public CompletionBuilder<I> ignoring(int... tokenTypes) {
            return ignoring(new IntArrayPredicate(tokenTypes));
        }

        /**
         * Indicate which token types to ignore when computing before / after
         * tokens for matching purposes (typically you want to ignore comments
         * and/or whitespace), and finish this token matching builder, adding it
         * to your completion configuration.
         *
         * @param ignore A predicate which will match token types
         * @return The completion builder that created this builder
         */
        public CompletionBuilder<I> ignoring(IntPredicate ignore) {
            if (this.ignoring != null) {
                this.ignoring = this.ignoring.or(ignore);
            } else {
                this.ignoring = ignore;
            }
            return build();
        }

        /**
         * Build the token patterns, returning to the outer completion builder.
         *
         * @return The builder that created this token triggers builder.
         * @throws UnmatchablePatternException if one of the token types in the
         * before or after arrays is matched by the predicate for tokens to
         * ignore, since that would result in a pattern that could never be
         * triggered.
         */
        public CompletionBuilder<I> build() {
            int count = patterns.size();
            int[][] befores = new int[count][];
            int[][] afters = new int[count][];
            IntPredicate[] tokenMatches = new IntPredicate[count];
            String[] names = new String[count];
            for (int i = 0; i < count; i++) {
                TokenTriggerPatternBuilder<I> p = patterns.get(i);
                befores[i] = p.preceding;
                assert befores[i] != null;
                afters[i] = p.subsequent;
                assert afters[i] != null;
                names[i] = p.name;
                assert names[i] != null;
                tokenMatches[i] = p.caretTokenMatch;
            }
            if (ignoring != Any.NONE) {
                for (int i = 0; i < names.length; i++) {
                    for (int j = 0; j < befores[i].length; j++) {
                        if (ignoring.test(befores[i][j])) {
                            throw new UnmatchablePatternException("Pattern '" + names[i]
                                    + " is set to ignore tokens with type " + befores[i][j]
                                    + ", but that token type is also one of the ones "
                                    + "to match at index " + j + " in the set of "
                                    + "tokens preceding the caret to match.  This "
                                    + "pattern can never be matched.");
                        }
                    }
                    for (int j = 0; j < afters[i].length; j++) {
                        if (ignoring.test(afters[i][j])) {
                            throw new UnmatchablePatternException("Pattern '" + names[i]
                                    + " is set to ignore tokens with type " + afters[i][j]
                                    + ", but that token type is also one of the ones "
                                    + "to match at index " + j + " in the set of "
                                    + "tokens following the caret to match.  This "
                                    + "pattern can never be matched.");
                        }
                    }
                }
            }
            TokenPatternMatcher matcher = new TokenPatternMatcher(ignoring, befores, afters, names, tokenMatches);
            return bldr.setTokenPatternMatcher(matcher);
        }
    }

    /**
     * Thrown if a pattern has been constructed where the set of tokens to look
     * for is in conflict with the set of tokens to skip.
     */
    public static final class UnmatchablePatternException extends IllegalArgumentException {

        UnmatchablePatternException(String s) {
            super(s);
        }
    }

    private static class TokenPatternMatcher implements BiFunction<List<Token>, Token, String> {

        private final IntPredicate ignoring;
        private final int[][] befores;
        private final int[][] afters;
        private final String[] names;
        private final IntPredicate[] tokenMatches;

        public TokenPatternMatcher(IntPredicate ignoring, int[][] befores, int[][] afters, String[] names, IntPredicate[] tokenMatches) {
            this.ignoring = ignoring;
            this.befores = befores;
            this.afters = afters;
            this.names = names;
            this.tokenMatches = tokenMatches;
        }

        @Override
        public String apply(List<Token> t, Token u) {
            return TokenUtils.isTokenPatternMatched(names, befores, afters, ignoring, tokenMatches, t, u);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(getClass().getSimpleName() + "{\n");
            for (int i = 0; i < names.length; i++) {
                sb.append("  ");
                sb.append(names[i]).append(": ");
                if (befores[i].length > 0) {
                    for (int j = 0; j < befores[i].length; j++) {
                        sb.append(befores[i][j]);
                        if (j != befores[i].length) {
                            sb.append(',');
                        }
                    }
                    sb.append("-->");
                }
                sb.append(tokenMatches[i]);
                if (afters[i].length > 0) {
                    sb.append("<--");
                    for (int j = 0; j < afters[i].length; j++) {
                        sb.append(afters[i][j]);
                        if (j != afters[i].length) {
                            sb.append(',');
                        }
                    }
                }
                sb.append('\n');
            }
            return sb.append('}').toString();
        }
    }

    private static final class Any implements IntPredicate {

        static final Any ANY = new Any(true);
        static final Any NONE = new Any(false);

        private final boolean val;

        public Any(boolean val) {
            this.val = val;
        }

        @Override
        public boolean test(int value) {
            return val;
        }

        @Override
        public String toString() {
            return val ? "any" : "none";
        }
    }

    /**
     * The second step of defining a token pattern, which allows you to specify
     * tokens that should be matched which may come after the token the caret is
     * in, for the completion you are defining to be used.
     *
     * @param <I> The item type
     */
    public static final class TokenTriggerPatternBuilder<I> {

        private int[] preceding = EMPTY;
        private int[] subsequent = EMPTY;
        private final String name;
        private IntPredicate caretTokenMatch = Any.ANY;

        private final TokenTriggersBuilder<I> bldr;

        private TokenTriggerPatternBuilder(String name, TokenTriggersBuilder<I> bldr, int[] preceding) {
            this.preceding = preceding;
            checkTokenIds(preceding, true);
            this.name = name;
            this.bldr = bldr;
        }

        public TokenTriggerPatternBuilder<I> whereCaretTokenMatches(int... items) {
            return whereCaretTokenMatches(new IntArrayPredicate(items));
        }

        public TokenTriggerPatternBuilder<I> whereCaretTokenMatches(IntPredicate pred) {
            if (caretTokenMatch == Any.ANY) {
                caretTokenMatch = pred;
            } else {
                caretTokenMatch = caretTokenMatch.or(pred);
            }
            return this;
        }

        boolean isEmpty() {
            return preceding.length == 0 && subsequent.length == 0;
        }

        void checkTokenIds(int[] ids, boolean befores) {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] < -1) {
                    throw new IllegalArgumentException("Token values may be "
                            + "less than or equal to the vocabulary's maxTokenType(), "
                            + "or -1 for EOF, but got: " + Arrays.toString(ids));
                }
                if (befores && ids[i] == -1) {
                    throw new IllegalArgumentException(
                            "Cannot be preceded by EOF (-1), but got: " + Arrays.toString(ids));
                }
                if (ids[i] == 0) {
                    throw new IllegalArgumentException("Antlr grammar token IDs are 1-indexed - there "
                            + "will never be a token 0, but got: " + Arrays.toString(ids));
                }
            }
        }

        /**
         * Add to this pattern a list of subsequent lexer token ids, which must
         * match the tokens after the token the caret is in in order for your
         * {@link CompletionItemProvider} to be called.
         *
         * @param subsequentTokens Tokens that may come after the caret
         * @return A finishable builder
         */
        public FinishableTokenTriggersBuilder<I> andSubsequentTokens(int... subsequentTokens) {
            Parameters.notNull("subsequentTokens", subsequentTokens);
            subsequent = subsequentTokens;
            checkTokenIds(subsequentTokens, false);
            return bldr.add(this);
        }

        /**
         * Indicate no subsequent-token-matching is needed and return to the
         * builder.
         *
         * @param subsequentTokens Tokens that may come after the caret
         * @return A finishable builder
         */
        public FinishableTokenTriggersBuilder<I> andNoNextTokenMatchNeeded() {
            return bldr.add(this);
        }

        /**
         * Indicate no subsequent-token-matching is needed and begin defining
         * another token matching pattern.
         *
         * @param subsequentTokens Tokens that may before the caret
         * @return A finishable builder
         */
        public TokenTriggerPatternBuilder<I> whenPrecedingTokensMatch(String patternName, int... preceding) {
            return bldr.add(this).whenPrecedingTokensMatch(patternName, preceding);
        }

        /**
         * Indicate which token types to ignore when computing before / after
         * tokens for matching purposes (typically you want to ignore comments
         * and/or whitespace), and finish this token matching builder, adding it
         * to your completion configuration.
         *
         * @param tokenTypes A list of token types from your Antlr lexer, which
         * should be skipped when pattern matching
         * @return The completion builder that created this builder
         */
        public CompletionBuilder<I> ignoring(int... tokenTypes) {
            Parameters.notNull("tokenTypes", tokenTypes);
            return bldr.add(this).ignoring(tokenTypes);
        }

        /**
         * Indicate which token types to ignore when computing before / after
         * tokens for matching purposes (typically you want to ignore comments
         * and/or whitespace), and finish this token matching builder, adding it
         * to your completion configuration.
         *
         * @param ignore A predicate which will match token types
         * @return The completion builder that created this builder
         */
        public CompletionBuilder<I> ignoring(IntPredicate ignore) {
            Parameters.notNull("ignore", ignore);
            return bldr.add(this).ignoring(ignore);
        }
    }
}
