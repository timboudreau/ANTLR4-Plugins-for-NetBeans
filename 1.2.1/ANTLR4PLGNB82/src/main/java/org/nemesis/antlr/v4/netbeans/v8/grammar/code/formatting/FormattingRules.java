package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.Vocabulary;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.Criterion.anyOf;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.Criterion.matching;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.Criterion.noneOf;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.Criterion.notMatching;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.FormattingRule.combine;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.FormattingRule.mode;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.FormattingRule.notMode;
import org.openide.util.Parameters;

/**
 * A set of formatting rules which can be appled to reformat a file that uses an
 * ANTLR grammar.
 *
 * @author Tim Boudreau
 */
public final class FormattingRules {

    private final List<FormattingRule> rules = new LinkedList<>();
    private volatile boolean sorted;
    private final Vocabulary vocabulary;
    private final String[] modeNames;

    /**
     * Create a new formatting rule set.
     *
     * @param vocabulary The vocabulary for the lexer being used, which is used
     * to construct human-readable log messages
     * @param modeNames Mode names - used to look up mode numbers for
     * constraining rules based on mode.
     */
    public FormattingRules(Vocabulary vocabulary, String[] modeNames) {
        Parameters.notNull("vocabulary", vocabulary);
        Parameters.notNull("modeNames", modeNames);
        this.vocabulary = vocabulary;
        this.modeNames = modeNames;
    }

    Vocabulary vocabulary() {
        return vocabulary;
    }

    String[] modeNames() {
        return modeNames;
    }

    void addRule(FormattingRule rule) {
        rules.add(rule);
    }

    private List<Replacer> replacers;

    /**
     * For cases where you want to replace an entire sequence of adjacent tokens
     * with an edited version of all of them - for the case where your grammar
     * splits tokens that are logically a contiguous thing in less-than-useful
     * ways, and you need to concatenate and handle them as one. Use sparingly.
     *
     * @param tokenType The token type you are targeting
     * @param replacer A function which can return replacement text for the
     * tokens to be replaced, or null if they should be left as-is.
     *
     * @return this
     */
    public FormattingRules replaceAdjacentTokens(Criterion tokenType, BiFunction<List<? extends ModalToken>, LexingState, String> replacer) {
        if (replacers == null) {
            replacers = new LinkedList<>();
        }
        replacers.add(new Replacer(tokenType, replacer));
        return this;
    }

    private static class Replacer {

        private final Criterion tokenTest;
        private final List<ModalToken> collected = new LinkedList<>();
        private final BiFunction<List<? extends ModalToken>, LexingState, String> replacer;
        private LexingState stateAtStartOfLastCollection;

        public Replacer(Criterion tokenTest, BiFunction<List<? extends ModalToken>, LexingState, String> replacer) {
            this.tokenTest = tokenTest;
            this.replacer = replacer;
        }

        public boolean onToken(ModalToken token, TokenStreamRewriter rewriter, LexingState state) {
            boolean matches = tokenTest.test(token.getType());
            if (matches) {
                if (collected.isEmpty()) {
                    // This is not cheap.
                    stateAtStartOfLastCollection = state.snapshot();
                }
                collected.add(token);
            } else if (!collected.isEmpty()) {
                finishPendingRewrites(rewriter);
            }
            return matches;
        }

        public void finishPendingRewrites(TokenStreamRewriter rewriter) {
            if (collected.isEmpty()) {
                return;
            }
            String replacement = replacer.apply(collected, stateAtStartOfLastCollection);
            try {
                if (replacement != null) {
                    rewriter.replace(collected.remove(0), replacement);
                    for (ModalToken other : collected) {
                        rewriter.delete(other);
                    }
                }
            } finally {
                collected.clear();
                stateAtStartOfLastCollection = null;
            }
        }
    }

    /**
     * Create a new rule which matches tokens based on the passed criterion.
     *
     * @param tokenTypeCriterion A criterion for matching tokens.
     * @return A new rule which can be further configured
     */
    public FormattingRule onTokenType(Criterion tokenTypeCriterion) {
        FormattingRule result = new FormattingRule(tokenTypeCriterion, this);
        rules.add(result);
        sorted = false;
        return result;
    }

    /**
     * Create a new rule which matches tokens which have the token type passed
     * here.
     *
     * @param type The token type
     * @return A new rule which can be further configured
     */
    public FormattingRule onTokenType(int type) {
        FormattingRule result = new FormattingRule(matching(vocabulary, type), this);
        rules.add(result);
        sorted = false;
        return result;
    }

    /**
     * Create a new rule which matches tokens which have any of the token types
     * passed here. The list of token types may not contain duplicates, counting
     * the first passed value.
     *
     * @param tokenType A token type to match
     * @param moreTokenTypes More token types to match
     * @return this
     */
    public FormattingRule onTokenType(int tokenType, int... moreTokenTypes) {
        if (moreTokenTypes.length == 0) {
            return onTokenType(tokenType);
        }
        FormattingRule result = new FormattingRule(anyOf(vocabulary,
                FormattingRule.combine(tokenType, moreTokenTypes)), this);
        rules.add(result);
        sorted = false;
        return result;
    }

    /**
     * Create a new rule which matches that <i>do not match</i> the passed
     * criterion. Not that the result can easily be a rule that will match
     * <i>almost every token</i> - use with care.
     *
     * @param tokenTypeCriterion A criterion for matching tokens.
     * @return A new rule which can be further configured
     */
    public FormattingRule onTokenTypeNot(Criterion tokenTypeCriterion) {
        FormattingRule result = new FormattingRule(tokenTypeCriterion.negate(), this);
        rules.add(result);
        sorted = false;
        return result;
    }

    /**
     * Create a new rule which matches that <i>do not match</i> the passed
     * criterion. Not that the result can easily be a rule that will match
     * <i>almost every token</i> - use with care.
     *
     * @param type The token type not to match
     * @return A new rule which can be further configured
     */
    public FormattingRule onTokenTypeNot(int type) {
        FormattingRule result = new FormattingRule(notMatching(vocabulary, type), this);
        rules.add(result);
        sorted = false;
        return result;
    }

    /**
     * Create a new rule which matches that <i>do not match</i> the passed
     * criterion. Not that the result can easily be a rule that will match
     * <i>almost every token</i> - use with care. The list of token types may
     * not contain duplicates, counting the first passed value.
     *
     * @param tokenType A token type not to match
     * @param moreTokenTypes More token types not to match
     * @return this
     */
    public FormattingRule onTokenTypeNot(int tokenType, int... moreTokenTypes) {
        if (moreTokenTypes.length == 0) {
            return onTokenTypeNot(tokenType);
        }
        FormattingRule result = new FormattingRule(noneOf(vocabulary,
                FormattingRule.combine(tokenType, moreTokenTypes)), this);
        rules.add(result);
        sorted = false;
        return result;
    }

    /**
     * Apply all rules to one token.
     *
     * @param token The token
     * @param prevToken The previous token type
     * @param nextToken The next token type
     * @param precededByNewline Whether or not a newline (possibly with trailing
     * whitespace) came immediately before this token
     * @param ctx The formatting context which can be used to manipulate
     * formatting
     * @param debug If true, matches and reasons for non-matching will be logged
     */
    void apply(ModalToken token, int prevToken, int nextToken,
            boolean precededByNewline, FormattingContext ctx, boolean debug,
            LexingState state, boolean followedByNewline, TokenStreamRewriter rewriter) {
        if (!sorted) {
            Collections.sort(rules);
            sorted = true;
        }
        if (replacers != null) {
            for (Replacer r : replacers) {
                if (r.onToken(token, rewriter, state)) {
                    return;
                }
            }
        }
        for (FormattingRule rule : rules) {
            if (rule.matches(token.getType(), prevToken, nextToken, precededByNewline, token.mode(), debug, state, followedByNewline)) {
                if (rule.hasAction()) {
                    if (debug) {
                        System.out.println("'" + token.getText() + "' " + vocabulary
                                .getSymbolicName(token.getType()) + " matched by " + rule);
                    }
                    if (ctx instanceof FormattingContextImpl) {
                        ((FormattingContextImpl) ctx).setCurrentRule(rule);
                    }
                    rule.perform(token, ctx, state);
                } else {
                    if (debug) {
                        System.out.println("NULL ACTION: " + rule);
                    }
                }
                break;
            }
        }
    }

    void finish(TokenStreamRewriter rew) {
        for (Replacer r : replacers) {
            r.finishPendingRewrites(rew);
        }
    }

    /**
     * Conditionally add a formatting action, depending on the passed value -
     * useful for formatting which only applies if some configuration value was
     * set by the user.
     *
     * @param val The value
     * @param cons The consumer, which will only be called if the value is true
     * @return this
     */
    public FormattingRules ifTrue(boolean val, Consumer<FormattingRules> cons) {
        if (val) {
            cons.accept(this);
        }
        return this;
    }

    /**
     * Conditionally add a formatting action, depending on the passed value -
     * useful for formatting which only applies if some configuration value was
     * set by the user.
     *
     * @param val The value
     * @param cons The consumer, which will only be called if the value is false
     * @return this
     */
    public FormattingRules ifFalse(boolean val, Consumer<FormattingRules> cons) {
        if (!val) {
            cons.accept(this);
        }
        return this;
    }

    /**
     * Apply the same mode criterion to all rules created in the passed
     * consumer.
     *
     * @param cons The consumer
     * @param mode A mode
     * @param more Any more related modes
     * @return this
     */
    public FormattingRules whenInMode(Consumer<ModalRulesBuilder> cons, String mode, String... more) {
        Parameters.notNull("mode", mode);
        IntPredicate modePredicate = FormattingRule.mode(FormattingRule.combine(mode, more));
        cons.accept(new ModalRulesBuilderImpl(this, modePredicate));
        return this;
    }

    /**
     * Apply the same negated mode criterion to all rules created in the passed
     * consumer.
     *
     * @param cons The consumer
     * @param mode A mode
     * @param more Any more related modes
     * @return this
     */
    public FormattingRules whenNotInMode(Consumer<ModalRulesBuilder> cons, String mode, String... more) {
        Parameters.notNull("mode", mode);
        IntPredicate modePredicate = notMode(combine(mode, more));
        cons.accept(new ModalRulesBuilderImpl(this, modePredicate));
        return this;
    }

    private static final class ModalRulesBuilderImpl extends ModalRulesBuilder {

        private final FormattingRules rules;
        private final IntPredicate modePredicate;

        ModalRulesBuilderImpl(FormattingRules rules, IntPredicate modePredicate) {
            this.rules = rules;
            this.modePredicate = modePredicate;
        }

        public ModalRulesBuilder whenNotInMode(Consumer<ModalRulesBuilder> cons, String mode, String... more) {
            IntPredicate pred = modePredicate.and(notMode(combine(mode, more)));
            cons.accept(new ModalRulesBuilderImpl(rules, pred));
            return this;
        }

        public ModalRulesBuilder whenInMode(Consumer<ModalRulesBuilder> cons, String mode, String... more) {
            IntPredicate pred = modePredicate.or(mode(combine(mode, more)));
            cons.accept(new ModalRulesBuilderImpl(rules, pred));
            return this;
        }

        private FormattingRule apply(FormattingRule rule) {
            rule.whereMode(modePredicate);
            return rule;
        }

        @Override
        public FormattingRule onTokenType(int item, int... more) {
            return apply(rules.onTokenType(item, more));
        }

        @Override
        public FormattingRule onTokenType(int type) {
            return apply(rules.onTokenType(type));
        }

        @Override
        public FormattingRule onTokenType(Criterion criterion) {
            return apply(rules.onTokenType(criterion));
        }
    }

    /**
     * Allows multiple rules to be added which all have the same mode
     * constraint.
     */
    public static abstract class ModalRulesBuilder {

        ModalRulesBuilder() {
        }

        /**
         * Perform the returned rule on the passed token types, in the case the
         * mode constraints this builder was created with are also matched.
         *
         * @param tokenType A token type
         * @param moreTokenTypes More token types
         * @return A rule
         */
        public abstract FormattingRule onTokenType(int tokenType, int... moreTokenTypes);

        /**
         * Perform the returned rule on the passed token type, in the case the
         * mode constraints this builder was created with are also matched.
         *
         * @param type A token type
         * @param more More token types
         * @return A rule
         */
        public abstract FormattingRule onTokenType(int type);

        public abstract FormattingRule onTokenType(Criterion tokenTypeCriterion);
    }
}
