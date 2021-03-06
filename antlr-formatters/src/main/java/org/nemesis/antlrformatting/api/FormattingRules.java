/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlrformatting.api;

import com.mastfrog.antlr.utils.Criterion;
import com.mastfrog.predicates.integer.IntPredicates;
import com.mastfrog.predicates.string.StringPredicates;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import org.antlr.v4.runtime.Vocabulary;
import static com.mastfrog.antlr.utils.Criterion.anyOf;
import static com.mastfrog.antlr.utils.Criterion.matching;
import static com.mastfrog.antlr.utils.Criterion.noneOf;
import static com.mastfrog.antlr.utils.Criterion.notMatching;
import static org.nemesis.antlrformatting.api.FormattingRule.notMode;

/**
 * A builder for the set of formatting rules which can be appled to reformat a
 * file that uses an ANTLR grammar. Call individual methods to add rules which
 * are applied under different conditions.
 *
 * @author Tim Boudreau
 */
public final class FormattingRules {

    private final List<FormattingRule> rules = new LinkedList<>();
    private volatile boolean sorted;
    private final Vocabulary vocabulary;
    private final String[] modeNames;
    private final ParserRulePredicates rulePredicates;
    private int layers;

    /**
     * Create a new formatting rule set.
     *
     * @param vocabulary The vocabulary for the lexer being used, which is used
     * to construct human-readable log messages
     * @param modeNames Mode names - used to look up mode numbers for
     * constraining rules based on mode.
     */
    FormattingRules(Vocabulary vocabulary, String[] modeNames, String[] parserRuleNames) {
        assert vocabulary != null : "vocabulary null";
        assert modeNames != null : "mode names null";
        this.vocabulary = vocabulary;
        this.modeNames = modeNames;
        this.rulePredicates = new ParserRulePredicates(parserRuleNames);
    }

    private FormattingRules(Vocabulary vocabulary, String[] modeNames, ParserRulePredicates rulePredicates) {
        this.vocabulary = vocabulary;
        this.modeNames = modeNames;
        this.rulePredicates = rulePredicates;
    }

    // for debugging - see FormattingHarness in the tests
    FormattingRules wrapAllRules(Function<FormattingAction, FormattingAction> wrapRule) {
        List<FormattingRule> old = new ArrayList<>(this.rules);
        this.rules.clear();
        for (FormattingRule r : old) {
            FormattingRule nue = r.wrapAction(this, wrapRule);
            this.rules.add(nue);
        }
        return this;
    }

    ParserRulePredicates parserRulePredicates() {
        return rulePredicates;
    }

    Vocabulary vocabulary() {
        return vocabulary;
    }

    String[] modeNames() {
        return modeNames;
    }

    void addRule(FormattingRule rule) {
        if (ruleProcessor != null) {
            ruleProcessor.accept(rule);
        }
        rules.add(rule);
    }

    public FormattingRules layer(Consumer<FormattingRules> c) {
        int layerIncrease = ++layers;
        return applyRuleProcessor(rule -> {
            rule.addPriority(layerIncrease * 1000);
        }, c);
    }

    /**
     * Convenience method for bulk-adding rules which all have one constraint in
     * common - in this case, only apply this rule when it occurs within a
     * particular <b>parser</b> (not <i>lexer</i>) rule.
     * <p>
     * Bear in mind that <i>the normal state of source files in an editor is
     * broken</i> - it is always preferable to match a pattern of tokens to
     * using a parser rule test, since the source is not necessarily parsable.
     * </p><p>
     * Apply the rule test passed to all rules added within the closure of the
     * passed consumer.
     * </p>
     *
     * @param rule A <b>parser</b> type
     * @param c A consumer
     * @return this
     */
    public FormattingRules whenInParserRule(int rule, Consumer<FormattingRules> c) {
        return applyRuleProcessor(r -> {
            r.whenInParserRule(rule);
        }, c);
    }

    public FormattingRules whenInParserRules(Consumer<FormattingRules> c, int rule, int... moreRules) {
        return applyRuleProcessor(r -> {
            r.whenInParserRule(rule, moreRules);
        }, c);
    }

    public FormattingRules whenNotInParserRule(int rule, Consumer<FormattingRules> c) {
        return applyRuleProcessor(r -> {
            r.whenNotInParserRule(rule);
        }, c);
    }

    public FormattingRules whenNotInParserRules(Consumer<FormattingRules> c, int rule, int... moreRules) {
        return applyRuleProcessor(r -> {
            r.whenNotInParserRule(rule, moreRules);
        }, c);
    }

    /**
     * Apply a bunch of rules to this FormattingRules inside the passed Consumer
     * and all of them will have the criterion that the mode must be the passed
     * mode value.
     *
     * @param mode A mode number
     * @param rules A consumer which can add some rules
     * @return this
     */
    public FormattingRules whenMode(int mode, Consumer<FormattingRules> rules) {
        return applyRuleProcessor(rule -> {
            rule.whereMode(mode);
        }, rules);
    }

    /**
     * Apply a bunch of rules to this FormattingRules inside the passed Consumer
     * and all of them will have the criterion that subsequent token type must
     * be the one passed.
     *
     * @param type A token type
     * @param rules A consumer which can add some rules
     * @return this
     */
    public FormattingRules whenNextTokenType(int type, Consumer<FormattingRules> applier) {
        return whenNextTokenType(Criterion.matching(vocabulary, type), applier);
    }

    /**
     * Apply a bunch of rules to this FormattingRules inside the passed Consumer
     * and all of them will have the criterion that preceding token type must be
     * the one passed.
     *
     * @param type A token type
     * @param rules A consumer which can add some rules
     * @return this
     */
    public FormattingRules whenPreviousTokenType(int type, Consumer<FormattingRules> applier) {
        return whenPreviousTokenType(Criterion.matching(vocabulary, type), applier);
    }

    /**
     * Apply a bunch of rules to this FormattingRules inside the passed Consumer
     * and all of them will have the criterion that subsequent token type must
     * match the passed anyOf.
     *
     * @param type A token type anyOf
     * @param rules A consumer which can add some rules
     * @see Criterion
     * @return this
     */
    public FormattingRules whenNextTokenType(IntPredicate pred, Consumer<FormattingRules> applier) {
        return applyRuleProcessor(rule -> {
            rule.whereNextTokenType(pred);
        }, applier);
    }

    /**
     * Apply a bunch of rules to this FormattingRules inside the passed Consumer
     * and all of them will have the criterion that preceding token type must
     * match the passed anyOf.
     *
     * @param type A token type anyOf
     * @param rules A consumer which can add some rules
     * @see Criterion
     * @return this
     */
    public FormattingRules whenPreviousTokenType(IntPredicate pred, Consumer<FormattingRules> applier) {
        return applyRuleProcessor(rule -> {
            rule.wherePreviousTokenType(pred);
        }, applier);
    }

    /**
     * Apply a bunch of rules to this FormattingRules inside the passed Consumer
     * and all of them will have the criterion that the lexing state must match
     * one of those which the returned builder gets configured to match.
     *
     * @param T the enum type
     * @param state The enum to use for state matching
     * @param rules A consumer which can add some rules, all of which will get
     * the lexing state criterion applied to them
     * @see Criterion
     * @return this
     */
    public <T extends Enum<T>> LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<FormattingRules>> whenLexingStateOneOf(T state, Consumer<FormattingRules> c) {
        LogicalLexingStateCriteriaBuilder<FormattingRules> res = new LogicalLexingStateCriteriaBuilder<>(llscb -> {
            applyRuleProcessor(llscb.consumer(), c);
            return this;
        });
        return res.start(state);
    }

    /**
     * Apply a bunch of rules to this FormattingRules inside the passed Consumer
     * and all of them will have the criterion that the lexing state must match
     * one of those which the returned builder gets configured to match.
     *
     * @param T the enum type
     * @param state The enum to use for state matching
     * @param rules A consumer which can add some rules, all of which will get
     * the lexing state criterion applied to them
     * @see Criterion
     * @return this
     */
    public <T extends Enum<T>> LexingStateCriteriaBuilder<T, FormattingRules> whenLexingState(T state, Consumer<FormattingRules> c) {
        return new LexingStateCriteriaBuilderImpl<>(state, (lscbi) -> {
            applyRuleProcessor(lscbi, c);
            return this;
        });
    }

    FormattingRules applyRuleProcessor(Consumer<FormattingRule> ruleProc, Consumer<FormattingRules> applier) {
        Consumer<FormattingRule> old = ruleProcessor;
        if (old != null) {
            ruleProc = old.andThen(ruleProc);
        }
        ruleProcessor = ruleProc;
//        setRuleProcessor(ruleProc);
        try {
            applier.accept(this);
        } finally {
            ruleProcessor = old;
        }
        return this;
    }

    private Consumer<FormattingRule> ruleProcessor;

    private List<Replacer> replacers;

    /**
     * For cases where you want to replace an entire sequence of adjacent tokens
     * with an edited version of all of them - for the case where your grammar
     * splits tokens that are logically a contiguous thing in less-than-useful
     * ways, and you need to concatenate and handle them as one. Use sparingly.
     * <p>
     * Note that processing tokens this way <i>bypasses normal rule
     * processing</i>, so do not expect formatting rules you have set up to
     * process tokens which are captured here - you will need to simulate that
     * in the output. This is generally for cases such as taking a series of
     * line comments some of which run past the line limit, extracting their
     * text less the initial "//\s*", collating and reflowing them into a new
     * set of lines that fit within the limit and replacing them en-masse.
     * </p>
     * <p>
     * If the passed biFunction returns null, the tokens will be processed
     * normally. See if you can use FormattingAction.wrap() before reaching for
     * this.
     * </p>
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

        Replacer(Criterion tokenTest, BiFunction<List<? extends ModalToken>, LexingState, String> replacer) {
            this.tokenTest = tokenTest;
            this.replacer = replacer;
        }

        public boolean onToken(ModalToken token, StreamRewriterFacade rewriter, LexingState state) {
            boolean matches = tokenTest.test(token.getType());
            if (matches) {
                if (collected.isEmpty()) {
                    // This is not cheap.
                    stateAtStartOfLastCollection = state.snapshot();
                }
                collected.add(token);
            } else if (!collected.isEmpty()) {
                return finishPendingRewrites(rewriter);
            }
            return matches;
        }

        public boolean finishPendingRewrites(StreamRewriterFacade rewriter) {
            if (collected.isEmpty()) {
                return false;
            }
            String replacement = replacer.apply(collected, stateAtStartOfLastCollection);
            try {
                if (replacement != null) {
                    rewriter.replace(collected.remove(0), replacement);
                    for (ModalToken other : collected) {
                        rewriter.delete(other);
                    }
                    return true;
                }
            } finally {
                collected.clear();
                stateAtStartOfLastCollection = null;
            }
            return false;
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
        addRule(result);
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
        addRule(result);
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
                IntPredicates.combine(tokenType, moreTokenTypes)), this);
        addRule(result);
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
        addRule(result);
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
        addRule(result);
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
                IntPredicates.combine(tokenType, moreTokenTypes)), this);
        addRule(result);
        sorted = false;
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!sorted) {
            Collections.sort(rules);
            sorted = true;
        }
        int ix = 0;
        for (FormattingRule r : rules) {
            sb.append(++ix).append(". ");
            sb.append(r).append('\n');
        }
        return sb.toString();
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
    void apply(ModalToken token, int prevToken, int prevMode, int nextToken,
            boolean precededByNewline, FormattingContext ctx, boolean debug,
            LexingState state, boolean followedByNewline, StreamRewriterFacade rewriter,
            IntFunction<Set<Integer>> parserRuleFinder, boolean isFirstProcessedTokenInSource) {
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
            if (rule.matches(token.getType(), prevToken, prevMode, nextToken, precededByNewline,
                    token.mode(), debug, state, followedByNewline, token.getStartIndex(),
                    token.getStopIndex(), parserRuleFinder, isFirstProcessedTokenInSource)) {
                if (rule.hasAction()) {
                    if (debug) {
                        FormattingRule.log("  MATCHED: '" + token.getText() + "' " + vocabulary
                                .getSymbolicName(token.getType()) + " matched by " + rule + "\n");
                    }
//                    if (ctx instanceof FormattingContextImpl) {
//                        ((FormattingContextImpl) ctx).setCurrentRule(rule);
//                    }
                    rule.perform(token, ctx, state);
                } else {
                    if (debug) {
                        FormattingRule.log("NULL ACTION: " + rule);
                    }
                }
                break;
            }
        }
    }

    void finish(StreamRewriterFacade rew) {
        if (replacers != null) {
            for (Replacer r : replacers) {
                r.finishPendingRewrites(rew);
            }
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
    public FormattingRules whenInMode(Consumer<FormattingRules> cons, String mode, String... more) {
        assert mode != null : "mode null";
        IntPredicate modePredicate = FormattingRule.mode(StringPredicates.combine(mode, more));
        applyRuleProcessor(rule -> {
            rule.whereMode(modePredicate);
        }, cons);
        return this;
    }

    /**
     * Adjust the priority of rules added within the passed consumer, by the passed
     * amount.
     *
     * @param prio The priority adjustment
     * @param cons A consumer
     * @return A set of rules
     */
    public FormattingRules withAdjustedPriority(int prio, Consumer<FormattingRules> cons) {
        applyRuleProcessor(rule -> {
            rule.adjustPriority(prio);
        }, cons);
        return this;
    }

    /**
     * Apply rules added within the closure of the passed consumer only when the
     * <a href="https://www.oilshell.org/blog/2017/12/17.html">lexer <i>mode</i></a>
     * number matches the passed predicate.
     *
     * @param modePredicate A predicate that takes the mode number
     * @param cons A consumer which will add rules to the FormattingRules passed to it
     * @return this
     */
    public FormattingRules whenInMode(IntPredicate modePredicate, Consumer<FormattingRules> cons) {
        applyRuleProcessor(rule -> {
            rule.whereMode(modePredicate);
        }, cons);
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
    public FormattingRules whenNotInMode(Consumer<FormattingRules> cons, String mode, String... more) {
        assert mode != null : "mode null";
        IntPredicate modePredicate = notMode(StringPredicates.combine(mode, more));
        applyRuleProcessor(rule -> {
            rule.whereMode(modePredicate);
        }, cons);
        return this;
    }
}
