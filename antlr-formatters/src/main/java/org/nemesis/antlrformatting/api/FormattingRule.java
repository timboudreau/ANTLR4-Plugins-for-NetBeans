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
import com.mastfrog.function.IntBiPredicate;
import com.mastfrog.predicates.integer.IntPredicates;
import com.mastfrog.predicates.string.StringPredicates;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * A formatting rule; consists of several matching criteria and a
 * FormattingAction which can manipulate the formatting context to modify
 * formatting. Create instances via the methods on FormattingRules and then call
 * methods on the returned instance to configure matching criteria.
 * <p>
 * Take care to match as specific conditions as possible. Rules are matched in
 * order of specificity. For any token encountered during lexing, either no
 * rule, or <i>exactly one rule</i> will be applied; the rule can specify an
 * action to apply to whitespace before or after the rule. Cases where one token
 * is processed to append whitespace after it, and the next token is processed
 * to prepend a different pattern of whitespace before it are coalesced. But in
 * general the way this works is:
 * </p>
 * <ul>
 * <li>Each rule has a set of <i>conditions</i> to make it active, and a
 * {@link FormattingAction} to apply if the conditions hold</li>
 * <li>Each rule as a "score" based on the number of constraints that need to be
 * true for that rule to be appled</li>
 * <li>Rules with more conditions applied to them (each call to a method such as
 * <code>whereModeNot("foo")</code> adds a condition to the rule, increase the
 * rule's specificity score and causes it to be tried earlier)</li>
 * <li>There is one exception to "each condition adds one to the score" - rules
 * which specify a <i>parser rule</i> get 10000 added to their score - i.e.
 * parser-rule specific rules get tried before anything else and will always
 * override more general rules that might also match the same token.
 * </li>
 * <li>In a set of rules, only <b>ONE</b> rule will be applied
 * <i>the first (highest score) one that matches a token will be applied</i>
 * (you can goose the priority with the <code>priority()</code> method if
 * absolutely necessary)</li>
 * <li>If a rule matches, its {@link FormattingAction} is called, and that can
 * specify what to do with whitespace around this token (prepend or append
 * spaces, newlines, increase or decrease indent level, rewrite the token,
 * whatever you want etc.)</li>
 * <li>Rules may be conditioned on a wide variety of things, such as lexer mode,
 * token type, and the contents of the LexingState configured when setting up
 * your formatter (the LexingState contains metrics about other tokens in
 * relation to the current one, and you program it with what to collect by
 * populating the LexingStateBuilder passed to you when you set up your
 * formatter).</li>
 * </ul>
 * <p>
 * The methods of this class modify the instance and do not create new
 * instances.
 * </p>
 *
 * @see LexingState
 * @see LexingStateBuilder
 * @see FormattingRules
 * @author Tim Boudreau
 */
public final class FormattingRule implements Comparable<FormattingRule> {

    private final IntPredicate tokenType;
    private IntPredicate prevTokenType;
    private IntPredicate nextTokenType;
    private IntPredicate mode;
    private Boolean requiresPrecedingNewline;
    private Boolean requiresFollowingNewline;
    private Boolean firstTokenInSource;
    private FormattingAction action;
    private boolean active = true;
    private boolean temporarilyActive = false;
    private boolean temporarilyInactive = false;
    private final FormattingRules rules;
    private int priority;
    private String name; // for debugging
    private List<Predicate<LexingState>> stateCriteria;
    private Predicate<Set<Integer>> parserRuleMatch;
    private IntBiPredicate modeTransition;

    FormattingRule(IntPredicate tokenType, FormattingRules rules) {
        this.tokenType = tokenType;
        this.rules = rules;
    }

    FormattingRule wrapAction(FormattingRules owner, Function<FormattingAction, FormattingAction> wrap) {
        FormattingRule result = new FormattingRule(tokenType, owner);
        result.action = wrap.apply(action);
        result.prevTokenType = prevTokenType;
        result.nextTokenType = nextTokenType;
        result.mode = mode;
        result.requiresPrecedingNewline = requiresPrecedingNewline;
        result.requiresFollowingNewline = requiresFollowingNewline;
        result.active = active;
        result.temporarilyInactive = temporarilyInactive;
        result.temporarilyActive = temporarilyActive;
        result.priority = priority;
        result.name = name;
        result.stateCriteria = stateCriteria == null ? null : new ArrayList<>(stateCriteria);
        result.parserRuleMatch = parserRuleMatch;
        result.modeTransition = modeTransition;
        result.firstTokenInSource = firstTokenInSource;
        return result;
    }

    public FormattingRule whenParserRule(Predicate<Set<Integer>> predicate) {
        if (parserRuleMatch == null) {
            parserRuleMatch = predicate;
        } else {
            parserRuleMatch = parserRuleMatch.and(predicate);
        }
        return this;
    }

    public FormattingRule whenInParserRule(int first, int... more) {
        return whenParserRule(rules.parserRulePredicates().inAnyOf(first, more));
    }

    public FormattingRule whenNotInParserRule(int first, int... more) {
        return whenParserRule(rules.parserRulePredicates().notInAnyOf(first, more));
    }

    boolean hasAction() {
        return action != null;
    }

    void perform(ModalToken tok, FormattingContext ctx, LexingState state) {
        if (this.action != null) {
            this.action.accept(tok, ctx, state);
        }
    }

    <T extends Enum<T>> void addStateCriterion(T key, IntPredicate test) {
        addStateCriterion(new StateCriterion<>(key, test));
    }

    void addStateCriterion(Predicate<LexingState> pred) {
        if (stateCriteria == null) {
            stateCriteria = new LinkedList<>();
        }
        stateCriteria.add(pred);
    }

    public String name() {
        return name == null ? toString() : name;
    }

    /**
     * Condition the execution of this rule on a value in the lexing state
     * during parsing - this can be something like "when this is the last token
     * before a <code>}</code> character", and used to distinguish things such
     * as multi-line statements.
     *
     * @param <T>
     * @param key The ad-hoc enum key from the enum used during parsing and set
     * up when the formatter is configured.
     * @return A builder for lexing state conditions
     */
    public <T extends Enum<T>> LexingStateCriteriaBuilder<T, FormattingRule> when(T key) {
        assert key != null : "key null";
        return new LexingStateCriteriaBuilderImpl<>(key, (lscbi) -> {
            lscbi.apply(this);
            return this;
        });
    }

    /**
     * Similar to the single-condition <code>when()</code> method, but used when
     * multiple different combinations of values may match the current lexing
     * state.
     *
     * @param <T> The key type
     * @param key The key
     * @return A builder that can condition execution on multiple lexing states
     */
    public <T extends Enum<T>> LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<FormattingRule>> whenCombinationOf(T key) {
        assert key != null : "key null";
        return new LogicalLexingStateCriteriaBuilder<>((LogicalLexingStateCriteriaBuilder<FormattingRule> llscb) -> {
            llscb.consumer().accept(FormattingRule.this);
            return FormattingRule.this;
        }).start(key);
    }

    /**
     * Adds an optional name used for logging/debugging purposes.
     *
     * @param name A name
     * @return this
     */
    public FormattingRule named(String name) {
        this.name = name;
        return this;
    }

    /**
     * Boost the priority of this rule - if there are two which are similar
     * specificity and this one should take precedence; in general, you should
     * define your rules and the specificity and order they are applied in so as
     * not to need to adjust priority - the purpose of priorities is to allow
     * <i>optional sets of rules</i> to take precedence when a user-defined
     * formatting setting is set - so you define a base set of rules that should
     * always be applied, and then subsequent sets that overlay and take
     * priority over the base rules when matched.
     *
     * @param priority The additional priority
     * @see FormattingRules.adjustPriority
     * @return this
     */
    public FormattingRule priority(int priority) {
        this.priority = priority;
        return this;
    }

    /**
     * Adjust the priority of this rule upwards or downwards from any previously
     * set value (default 0) by some amount; in general, you should define your
     * rules and the specificity and order they are applied in so as not to need
     * to adjust priority - the purpose of priorities is to allow <i>optional
     * sets of rules</i> to take precedence when a user-defined formatting
     * setting is set - so you define a base set of rules that should always be
     * applied, and then subsequent sets that overlay and take priority over the
     * base rules when matched.
     *
     * @param priority The additional priority
     * @return this
     */
    public FormattingRule adjustPriority(int by) {
        this.priority += by;
        return this;
    }

    /**
     * Creates a new rule which has the same criteria as this one and adds it to
     * the owning set of rules. Note that criteria <i>combine</i>, so if you
     * have already set a criterion for, say, previousTokenType, calling
     * wherePreviousTokenType(someNumber) will give you the logical OR of the
     * current criterion and that one.
     *
     * @return A new rule
     */
    public FormattingRule and() {
        FormattingRule nue = new FormattingRule(tokenType, rules);
        nue.prevTokenType = prevTokenType;
        nue.nextTokenType = nextTokenType;
        nue.requiresPrecedingNewline = requiresPrecedingNewline;
        nue.priority = priority;
        if (stateCriteria != null) {
            if (nue.stateCriteria == null) {
                nue.stateCriteria = new LinkedList<>();
            }
            nue.stateCriteria.addAll(stateCriteria);
        }
        rules.addRule(nue);
        return nue;
    }

    /**
     * Make this rule inactive by default. It can be activated as a result of
     * another rule activating it.
     *
     * @return this
     */
    public FormattingRule inactive() {
        active = false;
        return this;
    }

    void enable() {
        active = true;
    }

    void disable() {
        active = false;
    }

    void activate() {
        active = true;
        temporarilyActive = true;
    }

    void deactivate() {
        temporarilyInactive = true;
    }

    /**
     * Rewrite the text of a token.
     *
     * @param rewriter A token rewriter
     * @return this
     */
    public FormattingRule rewritingTokenTextWith(TokenRewriter rewriter) {
        return format(FormattingAction.rewriteTokenText(rewriter));
    }

    /**
     * Set the formatting action which will run if this rule matches a token.
     *
     * @param action The action
     * @return this
     */
    public FormattingRule format(FormattingAction action) {
        if (this.action != null) {
            this.action = this.action.and(action);
        } else {
            this.action = action;
        }
        return this;
    }

    /**
     * Allows for passing an action only if some value is true - e.g.,      <code>rule.formatIf(formattingSettings.isNewlinesAfterFoo(), someAction)
     * .formatIfNot(formattingSettings.isNewlinesAfterFoo(), someOtherAction)</code>.
     *
     * @param val The value to test
     * @param action An action
     * @return this
     */
    public FormattingRule formatIf(boolean val, FormattingAction action) {
        if (val) {
            format(action);
        }
        return this;
    }

    /**
     * Allows for passing an action only if some value is false - e.g.,      <code>rule.formatIf(formattingSettings.isNewlinesAfterFoo(), someAction)
     * .formatIfNot(formattingSettings.isNewlinesAfterFoo(), someOtherAction)</code>.
     *
     * @param val The value to test
     * @param action An action
     * @return this
     */
    public FormattingRule formatIfNot(boolean val, FormattingAction action) {
        if (!val) {
            format(action);
        }
        return this;
    }

    /**
     * Make this rule only active when a particular lexer mode is active. Note
     * this deals in mode <i>numbers</i> which can change when the grammar is
     * edited.
     *
     * @param mode The lexer mode to target
     * @return this
     */
    public FormattingRule whereMode(int mode) {
        if (this.mode != null) {
            this.mode = this.mode.or(Criterion.matching(rules.vocabulary(), mode));
        } else {
            this.mode = Criterion.matching(rules.vocabulary(), mode);
        }
        return this;
    }

    /**
     * Make this rule only active when a particular lexer mode is <i>not</i>
     * active. Note this deals in mode <i>numbers</i> which can change when the
     * grammar is edited.
     *
     * @param mode The lexer mode to avoid
     * @return this
     */
    public FormattingRule whereModeNot(int mode) {
        if (this.mode != null) {
            this.mode = this.mode.or(Criterion.notMatching(rules.vocabulary(), mode));
        } else {
            this.mode = Criterion.notMatching(rules.vocabulary(), mode);
        }
        return this;
    }

    /**
     * Make this rule only active when a one of the passed lexer modes is
     * active. Note this deals in mode <i>numbers</i> which can change when the
     * grammar is edited.
     *
     * @param item the first mode number
     * @param more additional mode numbers
     * @return this
     */
    public FormattingRule whereMode(int item, int... more) {
        if (more.length == 0) {
            return whereMode(item);
        }
        return whereMode(Criterion.anyOf(rules.vocabulary(), IntPredicates.combine(item, more)));
    }

    /**
     * Make this rule only active when a one of the passed lexer modes is
     * <i>not</i> active. Note this deals in mode <i>numbers</i> which can
     * change when the grammar is edited.
     *
     * @param item the first mode name
     * @param more additional mode name
     * @return this
     */
    public FormattingRule whereModeNot(String item, String... more) {
        if (more.length == 0) {
            return whereMode(FormattingRule.notMode(rules.modeNames(), item));
        }
        return whereMode(FormattingRule.notMode(StringPredicates.combine(item, more)));
    }

    /**
     * Make this rule only active when a one of the passed lexer modes is
     * active.
     *
     * @param item the first mode name
     * @param more additional mode name
     * @return this
     */
    public FormattingRule whereMode(String item, String... more) {
        if (more.length == 0) {
            return whereMode(FormattingRule.mode(rules.modeNames(), item));
        }
        return whereMode(FormattingRule.mode(StringPredicates.combine(item, more)));
    }

    /**
     * Make this rule only active when a one of the passed lexer modes is
     * <i>not</i> active. Note this deals in mode <i>numbers</i> which can
     * change when the grammar is edited; where mode names are stable, prefer
     * the overload of this method that takes strings.
     *
     * @param item the first mode number
     * @param more additional mode numbers
     * @return this
     */
    public FormattingRule whereModeNot(int item, int... more) {
        if (more.length == 0) {
            return whereModeNot(item);
        }
        return whereMode(Criterion.noneOf(rules.vocabulary(), IntPredicates.combine(item, more)));
    }

    /**
     * Make this rule only active when the lexer mode matches the passed
     * predicate. Note this deals in mode <i>numbers</i> which can change when
     * the grammar is edited; where mode names are stable, prefer the overload
     * of this method that takes strings.
     *
     * @param item the first mode number
     * @param more additional mode numbers
     * @return this
     */
    public FormattingRule whereMode(IntPredicate pred) {
        if (this.mode != null) {
            this.mode = this.mode.or(pred);
        } else {
            this.mode = pred;
        }
        return this;
    }

    /**
     * Match this rule in the case that the previous token was in one lexer mode
     * (first argument to the passed BiPredicate) and the current token is in
     * another.
     *
     * @param pred A predicate which is passed the preceding token's mode and
     * this one's
     * @return this
     */
    public FormattingRule whereModeTransition(IntBiPredicate pred) {
        if (this.modeTransition == null) {
            this.modeTransition = pred;
        } else {
            this.modeTransition = this.modeTransition.or(pred);
        }
        return this;
    }

    /**
     * Match this rule in the case that the previous token was in the first
     * passed lexer mode the current token is the second argument.
     *
     * @param pred A predicate which is passed the preceding token's mode and
     * this one's
     * @return this
     */
    public FormattingRule whereModeTransition(int previousMode, int currentMode) {
        if (this.modeTransition == null) {
            this.modeTransition = (a, b) -> {
                return a == previousMode && b == currentMode;
            };
        } else {
            this.modeTransition = this.modeTransition.or((a, b) -> {
                return a == previousMode && b == currentMode;
            });
        }
        return this;
    }

    /**
     * Match this rule only when the lexer mode for the preceding token is not
     * the same as the passed one and the passed one is the current token's
     * lexer mode.
     *
     * @param mode A lexer mode index
     * @return this
     */
    public FormattingRule whenEnteringMode(int mode) {
        return whereModeTransition((prevMode, currMode) -> {
            return prevMode != currMode && mode == currMode;
        });
    }

    /**
     * Match this rule only when the lexer mode for the preceding token is the
     * same as the passed one and the current token's lexer mode is not.
     *
     * @param mode A lexer mode index
     * @return this
     */
    public FormattingRule whenLeavingMode(int mode) {
        return whereModeTransition((prevMode, currMode) -> {
            return prevMode != currMode && prevMode == mode;
        });
    }

    /**
     * Match this rule at any mode transition.
     *
     * @return this
     */
    public FormattingRule whenModeChanging() {
        return whereModeTransition((prevMode, currMode) -> {
            return prevMode != currMode;
        });
    }

    /**
     * Match this rule only when the previous and current token's lexer mode are
     * the same.
     *
     * @return this
     */
    public FormattingRule whenModeNotChanging() {
        return whereModeTransition((prevMode, currMode) -> {
            return prevMode == currMode;
        });
    }

    /**
     * Make this rule match if the type of the token preceding the current one
     * matches the passed value.
     *
     * @param item The token type
     * @return this
     */
    public FormattingRule wherePreviousTokenType(int item) {
        return FormattingRule.this.wherePreviousTokenType(Criterion.matching(rules.vocabulary(), item));
    }

    /**
     * Make this rule match if the type of the token preceding the current one
     * <i>does not match</i> the passed value.
     *
     * @param item The token type
     * @return this
     */
    public FormattingRule wherePreviousTokenTypeNot(int item) {
        return FormattingRule.this.wherePreviousTokenType(Criterion.notMatching(rules.vocabulary(), item));
    }

    /**
     * Skip this rule when processing the first token in a source file (to avoid
     * inserting leading whitespace).
     *
     * @return this
     */
    public FormattingRule whereNotFirstTokenInSource() {
        this.firstTokenInSource = Boolean.FALSE;
        return this;
    }

    /**
     * Apply this only to the first token in the source.
     *
     * @return this
     */
    public FormattingRule whereFirstTokenInSource() {
        this.firstTokenInSource = Boolean.TRUE;
        return this;
    }

    /**
     * Make this rule match if the type of the token preceding the current one
     * matches the passed criterion.
     *
     * @param criterion Matching criterion
     * @return this
     */
    public FormattingRule wherePreviousTokenType(Criterion criterion) {
        if (prevTokenType == null) {
            prevTokenType = criterion;
        } else {
            prevTokenType = prevTokenType.or(criterion);
        }
        return this;
    }

    /**
     * Make this rule match if the type of the token preceding the current one
     * matches the passed criterion.
     *
     * @param criterion Matching criterion
     * @return this
     */
    public FormattingRule wherePreviousTokenType(IntPredicate criterion) {
        if (prevTokenType == null) {
            prevTokenType = criterion;
        } else {
            prevTokenType = prevTokenType.or(criterion);
        }
        return this;
    }

    /**
     * Make this rule match if the type of the token preceding the current one
     * <i>does not matche</i> the passed criterion.
     *
     * @param criterion Matching criterion
     * @return this
     */
    public FormattingRule wherePreviousTokenTypeNot(Criterion criterion) {
        return FormattingRule.this.wherePreviousTokenType(criterion.negate());
    }

    /**
     * Make this rule match if the type of the token after the current one
     * <i>does not matche</i> the passed criterion.
     *
     * @param criterion Matching criterion
     * @return this
     */
    public FormattingRule whereNextTokenTypeNot(Criterion criterion) {
        return whereNextTokenType(criterion.negate());
    }

    /**
     * Make this rule match if the type of the token after the current one
     * matches the passed criterion.
     *
     * @param criterion Matching criterion
     * @return this
     */
    public FormattingRule whereNextTokenType(Criterion criterion) {
        if (this.nextTokenType != null) {
            this.nextTokenType = this.nextTokenType.or(criterion);
        } else {
            this.nextTokenType = criterion;
        }
        return this;
    }

    /**
     * Make this rule match if the type of the token after the current one
     * matches the passed anyOf.
     *
     * @param criterion Matching criterion
     * @return this
     */
    public FormattingRule whereNextTokenType(IntPredicate criterion) {
        if (this.nextTokenType != null) {
            this.nextTokenType = this.nextTokenType.or(criterion);
        } else {
            this.nextTokenType = criterion;
        }
        return this;
    }

    /**
     * Make this rule match if the type of the token after the current one
     * matches one of the passed token types.
     *
     * @param item the first token type
     * @param more additional token types
     * @return this
     */
    public FormattingRule whereNextTokenType(int item, int... more) {
        if (more.length == 0) {
            return whereNextTokenType(item);
        }
        return whereNextTokenType(Criterion.anyOf(rules.vocabulary(), IntPredicates.combine(item, more)));
    }

    /**
     * Make this rule match if the type of the token after the current one
     * <i>does not match</i> one of the passed token types.
     *
     * @param item the first token type
     * @param more additional token types
     * @return this
     */
    public FormattingRule whereNextTokenTypeNot(int item, int... more) {
        if (more.length == 0) {
            return whereNextTokenTypeNot(item);
        }
        return whereNextTokenType(Criterion.noneOf(rules.vocabulary(), IntPredicates.combine(item, more)));
    }

    /**
     * Make this rule match if the type of the token preceding the current one
     * <i>does not matche</i> any of the passed token types.
     *
     * @param item the first type
     * @param more additional types
     * @return this
     */
    public FormattingRule wherePreviousTokenType(int item, int... more) {
        if (more.length == 0) {
            return FormattingRule.this.wherePreviousTokenType(item);
        }
        return FormattingRule.this.wherePreviousTokenType(Criterion.anyOf(rules.vocabulary(), IntPredicates.combine(item, more)));
    }

    /**
     * Make this rule match if the type of the token preceding the current one
     * <i>does not matche</i> any of the passed types.
     *
     * @param item the first type
     * @param more additional types
     * @return this
     */
    public FormattingRule wherePreviousTokenTypeNot(int item, int... more) {
        if (more.length == 0) {
            return FormattingRule.this.wherePreviousTokenTypeNot(item);
        }
        int[] vals = new int[more.length + 1];
        vals[0] = item;
        System.arraycopy(more, 0, vals, 1, more.length);
        return FormattingRule.this.wherePreviousTokenType(Criterion.noneOf(rules.vocabulary(), vals));
    }

    /**
     * Make this rule match if the type of the token after the current one
     * matches the passed type.
     *
     * @param item the token type
     * @return this
     */
    public FormattingRule whereNextTokenType(int item) {
        return whereNextTokenType(Criterion.matching(rules.vocabulary(), item));
    }

    /**
     * Make this rule match if the type of the token after the current one
     * <i>does not match</i> the passed type.
     *
     * @param item the token type
     * @return this
     */
    public FormattingRule whereNextTokenTypeNot(int item) {
        return whereNextTokenType(Criterion.notMatching(rules.vocabulary(), item));
    }

    /**
     * Make this rule match only if the passed token was preceded by a newline
     * (with or without trailing whitespace - it is the first non-whitespace
     * token on its line) <i>in the original, unformatted source</i>.
     *
     * @param val Whether a newline must be present or must not be present
     * @return this
     */
    public FormattingRule ifPrecededByNewline(boolean val) {
        this.requiresPrecedingNewline = val;
        return this;
    }

    /**
     * Make this rule match only if the passed token was followed by a newline
     * (with or without trailing whitespace - it is the first non-whitespace
     * token on its line) <i>in the original, unformatted source</i>.
     *
     * @param val Whether a newline must be present or must not be present
     * @return this
     */
    public FormattingRule ifFollowedByNewline(boolean val) {
        this.requiresFollowingNewline = val;
        return this;
    }

    static void log(String s) {
        System.out.println(s); // println ok
    }

    /**
     * The main method which tests the incoming token against all predicates set
     * up for this rule and determines if it matches.
     *
     * @param tokenType The current token's type
     * @param prevTokenType The preceding token's type
     * @param prevTokenMode The preceding token's mode
     * @param nextTokenType The next current token's type (if any)
     * @param precededByNewline Does the preceding token to this one in the
     * original source end with a newline or newline followed by whitespace
     * @param mode The current mode number
     * @param debug If true, log the process of matching and reasons for any
     * non-match
     * @param state The lexing state
     * @param followedByNewline If true, a newline or whitespace followed by a
     * newline was subsequent to this token in the original source
     * @param start The start offset
     * @param stop The stop offset
     * @param parserRuleFinder function which can detect the parser rules that
     * apply to this token, if any
     * @return true if this rule matches
     */
    boolean matches(int tokenType, int prevTokenType, int prevTokenMode,
            int nextTokenType, boolean precededByNewline, int mode, boolean debug,
            LexingState state, boolean followedByNewline, int start, int stop,
            IntFunction<Set<Integer>> parserRuleFinder, boolean isFirstProcessedTokenInSource) {
        boolean log = debug && this.tokenType.test(tokenType);
        if (log) {
            log(" try-match-rule " + this);
        }
        if (!active) {
            if (log) {
                log("  fail-inactive: " + this);
            }
            return false;
        }
        if (temporarilyInactive) {
            if (log) {
                log("  fail-temp-inactive: " + this);
            }
            temporarilyInactive = false;
            return false;
        }
        boolean result = true;
        if (this.tokenType != null) {
            result = this.tokenType.test(tokenType);
            if (log && !result) {
                log("  fail-type-non-match" + this.tokenType);
            }
        }
        if (result && this.firstTokenInSource != null) {
            result = isFirstProcessedTokenInSource == firstTokenInSource.booleanValue();
            if (log && !result) {
                log("  fail-first-token-in-source-mismatch-"
                        + firstTokenInSource + "-vs-" + isFirstProcessedTokenInSource);
            }
        }
        if (result && this.mode != null) {
            result = this.mode.test(mode);
            if (log && !result) {
                String modeName = mode >= 0 && mode < rules.modeNames().length ? rules.modeNames()[mode]
                        : "unknown";
                log("  fail-mode-mismatch-on-" + this.mode + "-with-mode-"
                        + modeName);
            }
        }
        if (result && this.modeTransition != null) {
            result = this.modeTransition.test(prevTokenMode, mode);
            if (log && !result) {
                String modeName = mode >= 0 && mode < rules.modeNames().length ? rules.modeNames()[mode]
                        : "unknown";
                String prevModeName = prevTokenMode >= 0 && prevTokenMode < rules.modeNames().length ? rules.modeNames()[prevTokenMode]
                        : "unknown";

                log("  fail-mode-transition-mismatch("
                        + prevModeName + "," + modeName + " -for- " + modeTransition);
            }
        }
        if (result && this.stateCriteria != null) {
            for (Predicate<LexingState> c : this.stateCriteria) {
                result = c.test(state);
                if (!result) {
                    if (log) {
                        log("  fail-lexing-state-mismatch" + c + "-with-state-" + state);
                    }
                    break;
                }
            }
        }
        if (result && this.prevTokenType != null) {
            result = this.prevTokenType.test(prevTokenType);
            if (log && !result) {
                log("  fail-prev-token-type-mismatch-" + this.prevTokenType + "-with-" + prevTokenType);
            }
        }
        if (result && this.nextTokenType != null) {
            result = this.nextTokenType.test(nextTokenType);
            if (log && !result) {
                log("  fail-next-token-mismatch-" + this.nextTokenType + "-with-" + nextTokenType);
            }
        }
        if (result && this.requiresPrecedingNewline != null) {
            result = this.requiresPrecedingNewline == precededByNewline;
            if (log && !result) {
                log("  fail-preceding-newline-mismatch-requiring-" + this.requiresPrecedingNewline);
            }
        }
        if (result && this.requiresFollowingNewline != null) {
            result = this.requiresFollowingNewline == followedByNewline;
            if (log && !result) {
                log("  fail-subsequent-newline-mismatch-requiring-" + this.requiresPrecedingNewline);
            }
        }
        if (result && parserRuleMatch != null) {
            Set<Integer> rulesAtPoint = parserRuleFinder.apply(start);
            if (rulesAtPoint == null) {
                rulesAtPoint = Collections.emptySet();
            }
            result = this.parserRuleMatch.test(rulesAtPoint);
            if (log && !result) {
                log("  fail-parser-rule-match-" + this.parserRuleMatch + "-with-" + ruleNames(rulesAtPoint));
            }
        }
        if (temporarilyActive) {
            temporarilyActive = false;
            active = false;
        }
        if (log && result) {
            log("  MATCHED " + this);
        }
        return result;
    }

    private String ruleNames(Set<Integer> ints) {
        return rules.parserRulePredicates().names(ints);
    }

    /**
     * Score how specific this rule is, for use when determining the order in
     * which it is used to test incoming tokens. Can be effected by calls to
     * priority().
     *
     * @return The number of specific tests that this rule applies * 10.
     */
    private int specificityScore() {
        int result = 0;
        for (IntPredicate val : new IntPredicate[]{tokenType, prevTokenType, nextTokenType, mode}) {
            if (val != null) {
                result++;
            }
        }
        if (firstTokenInSource != null) {
            result++;
        }
        if (modeTransition != null) {
            result++;
        }
        if (requiresPrecedingNewline != null) {
            result++;
        }
        if (requiresFollowingNewline != null) {
            result++;
        }
        if (stateCriteria != null) {
            result += stateCriteria.size();
        }
        result *= 10;
        result += priority;
        if (this.parserRuleMatch != null) {
            result += 10000;
        }
        return result;
    }

    /**
     * Reverse-compares the specificity score of this and another rule such that
     * higher scores sort lower.
     *
     * @param o Another rule
     * @return Their relationship according to the contract of Comparable
     */
    @Override
    public int compareTo(FormattingRule o) {
        int mc = specificityScore();
        int omc = o.specificityScore();
        int result = mc > omc ? -1 : mc == omc ? 0 : 1;
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Rule{");
        if (tokenType != null) {
            sb.append("tokenType ").append(tokenType).append(' ');
        }
        if (name != null) {
            if (sb.length() > 5) {
                sb.append(' ');
            }
            sb.append("'").append(name).append("'");
        }
        if (prevTokenType != null) {
            if (sb.length() > 5) {
                sb.append(' ');
            }
            sb.append("prevTokenType ").append(prevTokenType);
        }
        if (nextTokenType != null) {
            if (sb.length() > 5) {
                sb.append(' ');
            }
            sb.append("nextTokenType ").append(nextTokenType);
        }
        if (mode != null) {
            if (sb.length() > 5) {
                sb.append(' ');
            }
            sb.append("mode ").append(mode);
        }
        if (modeTransition != null) {
            if (sb.length() > 5) {
                sb.append(' ');
            }
            sb.append("onModeTransition ").append(modeTransition);
        }
        if (requiresPrecedingNewline != null) {
            if (sb.length() > 5) {
                sb.append(' ');
            }
            sb.append("requiresPrecedingNewline ").append(requiresPrecedingNewline);
        }
        if (this.stateCriteria != null) {
            if (sb.length() > 5) {
                sb.append(' ');
            }
            sb.append("states {");
            for (Predicate<LexingState> pred : stateCriteria) {
                sb.append(pred).append(' ');
            }
            sb.append('}');
        }
        if (firstTokenInSource != null) {
            if (sb.length() > 5) {
                sb.append(' ');
            }
            if (firstTokenInSource.booleanValue()) {
                sb.append("onlyWhenFirstTokenInSource");
            } else {
                sb.append("whenNotFirstTokenInSource");
            }
        }
        if (sb.length() > 5) {
            sb.append(' ');
        }
        sb.append("action ").append(action);
        if (name != null) {
            sb.append(":").append(name);
        }
        sb.append(" specificity ").append(this.specificityScore());
        return sb.append('}').toString();
    }

    void addPriority(int layerIncrease) {
        this.priority += layerIncrease;
    }

    private static class StateCriterion<T extends Enum<T>> implements Predicate<LexingState> {

        private final T key;
        private final IntPredicate criterion;

        StateCriterion(T key, IntPredicate criterion) {
            this.key = key;
            this.criterion = criterion;
        }

        public boolean test(LexingState state) {
            return criterion.test(state.get(key));
        }

        @Override
        public String toString() {
            return key + "" + criterion;
        }
    }

    static IntPredicate mode(String[] allNames, String... names) {
        return modeNames(allNames, false, names);
    }

    static IntPredicate notMode(String[] allNames, String... names) {
        return modeNames(allNames, true, names);
    }

    private static IntPredicate modeNames(String[] modeNames, boolean not, String... filteredNames) {
        Set<String> all = new TreeSet<>(Arrays.asList(filteredNames));
        List<Integer> ints = new ArrayList<>(all.size());
        List<String> allModeNames = modeNames == null
                ? Collections.emptyList() : Arrays.asList(modeNames);

        for (String s : all) {
            int ix = allModeNames.indexOf(s);
            if (ix >= 0) {
                ints.add(ix);
            } else if ("DEFAULT_MODE".equals(s) || "default".equals(s)) {
                ints.add(0);
            }
        }
        final int[] vals = new int[ints.size()];
        for (int i = 0; i < ints.size(); i++) {
            vals[i] = ints.get(i);
        }
        Arrays.sort(vals);
        return new ModeNamesCriterion(vals, not, all);
    }

    private static class ModeNamesCriterion implements IntPredicate {

        private final int[] vals;
        private final boolean not;
        private final Set<String> all;

        ModeNamesCriterion(int[] vals, boolean not, Set<String> all) {
            this.vals = vals;
            this.not = not;
            this.all = all;
        }

        @Override
        public IntPredicate negate() {
            return new ModeNamesCriterion(vals, !not, all);
        }

        @Override
        public boolean test(int value) {
            int ix = Arrays.binarySearch(vals, value);
            if (not) {
                return ix < 0;
            } else {
                return ix >= 0;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(not ? "notMode(" : "mode(");
            int initialLength = sb.length();
            for (String s : all) {
                if (sb.length() != initialLength) {
                    sb.append(", ");
                }
                sb.append(s);
            }
            sb.append(" = [");
            for (int i = 0; i < vals.length; i++) {
                sb.append(vals[i]);
                if (i != vals.length - 1) {
                    sb.append(",");
                }
            }
            sb.append("]");
            return sb.append(")").toString();
        }
    }
    /*
    static class ModeTransitionPredicate implements IntBiPredicate {

        private final int expectA;
        private final TestKinds aKind;
        private final int expectB;
        private final TestKinds bKind;
        private final String[] modeNames;

        ModeTransitionPredicate(int expectA, TestKinds aKind, int expectB, TestKinds bKind, String[] modeNames) {
            this.expectA = expectA;
            this.aKind = aKind;
            this.expectB = expectB;
            this.bKind = bKind;
            this.modeNames = modeNames;
        }

        @Override
        public boolean test(int a, int b) {
            boolean result = true;
            if (aKind != null) {
                result = aKind.test(expectA, expectB, a, b);
            }
            if (result && bKind != null) {
                result = bKind.test(expectA, expectB, a, b);
            }
            return result;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (aKind != null) {
                sb.append(aKind.stringify(expectA, expectB, modeNames));
            }
            if (bKind != null) {
                sb.append(" and ").append(bKind.stringify(expectA, expectB, modeNames));
            }
            return sb.toString();
        }

        interface SingleTest {
            boolean test(int expA, int expB, int gotA, int gotB);

            default String name() {
                return getClass().getSimpleName();
            }

            default String stringify(int a, int b, String[] modeNames) {
                return name() + " " + a + "," + b;
            }
        }

        enum TestKinds implements SingleTest {
            EXACTLY_EQUAL,
            DOES_NOT_EQUAL,
            A_EQUALITY,
            B_EQUALITY,
            A_INEQUALITY,
            B_INEQUALITY,
            SAME,
            NOT_SAME
            ;

            @Override
            public String stringify(int a, int b, String[] modeNames) {
                String nameA = a >= 0 && a < modeNames.length ? modeNames[a] : Integer.toString(a);
                String nameB = a >= 0 && a < modeNames.length ? modeNames[a] : Integer.toString(a);
                switch(this) {
                    case EXACTLY_EQUAL :
                        return "prev mode " + nameA + " and new mode " + nameB;
                    case DOES_NOT_EQUAL :
                        return "prev mode not " + nameA + " and new mode not " + nameB;
                    case A_EQUALITY :
                        return "prev mode " + nameA;
                    case B_EQUALITY :
                        return "curr mode " + nameB;
                    case A_INEQUALITY :
                        return "prev mode not " + nameA;
                    case B_INEQUALITY :
                        return "curr mode not " + nameB;
                    case SAME :
                        return "mode-unchanged";
                    case NOT_SAME :
                        return "any-mode-transition";
                    default :
                        throw new AssertionError(this);
                }
            }

            @Override
            public boolean test(int expA, int expB, int gotA, int gotB) {
                switch(this) {
                    case EXACTLY_EQUAL :
                        return expA == gotA && expB == gotB;
                    case DOES_NOT_EQUAL :
                        return expA != gotA && expB != gotB;
                    case A_EQUALITY :
                        return expA == gotA;
                    case B_EQUALITY :
                        return expB == gotB;
                    case A_INEQUALITY :
                        return expA != gotA;
                    case B_INEQUALITY :
                        return expB != gotB;
                    case SAME :
                        return gotA == gotB;
                    case NOT_SAME :
                        return gotA != gotB;
                    default :
                        throw new AssertionError(this);
                }
            }
        }
    }
     */
}
