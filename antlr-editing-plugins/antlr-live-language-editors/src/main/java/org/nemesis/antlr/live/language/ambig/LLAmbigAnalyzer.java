/*
 * Copyright (C) 2020 Tim Boudreau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nemesis.antlr.live.language.ambig;

import com.mastfrog.util.collections.IntList;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.collections.IntSet;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNConfig;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.AbstractPredicateTransition;
import org.antlr.v4.runtime.atn.BlockEndState;
import org.antlr.v4.runtime.atn.EpsilonTransition;
import org.antlr.v4.runtime.atn.NotSetTransition;
import org.antlr.v4.runtime.atn.PredictionContext;
import org.antlr.v4.runtime.atn.RuleStopState;
import org.antlr.v4.runtime.atn.RuleTransition;
import org.antlr.v4.runtime.atn.SingletonPredictionContext;
import org.antlr.v4.runtime.atn.Transition;
import org.antlr.v4.runtime.atn.WildcardTransition;
import org.antlr.v4.runtime.misc.IntegerList;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 *
 * @author Tim Boudreau
 */
public class LLAmbigAnalyzer {

    /**
     * Special value added to the lookahead sets to indicate that we hit a
     * predicate during analysis if {@code seeThruPreds==false}.
     */
    public static final int HIT_PRED = Token.INVALID_TYPE;

    public final ATN atn;
    private final String[] ruleNames;
    private final Vocabulary vocab;

    IntMap<IntSet> tokensForRule;
    private IntMap<Set<IntList>> pathsToRule;

    public LLAmbigAnalyzer(ATN atn, String[] ruleNames, Vocabulary vocab) {
        this.atn = atn;
        this.ruleNames = ruleNames;
        this.vocab = vocab;
        tokensForRule = IntMap.create(ruleNames.length, true, () -> IntSet.create(vocab.getMaxTokenType() + 2));
        pathsToRule = IntMap.create(ruleNames.length, true, HashSet::new);
    }

    public IntMap<IntSet> reset() {
        IntMap<IntSet> result = tokensForRule;
        tokensForRule = IntMap.create(ruleNames.length, true, () -> IntSet.create(vocab.getMaxTokenType() + 2));
        return result;
    }

    public IntMap<Set<IntList>> resetPaths() {
        IntMap<Set<IntList>> result = pathsToRule;
        pathsToRule = IntMap.create(ruleNames.length);
        return result;
    }

    /**
     * Compute set of tokens that can follow {@code s} in the ATN in the
     * specified {@code ctx}.
     *
     * <p>
     * If {@code ctx} is {@code null} and the end of the rule containing
     * {@code s} is reached, {@link Token#EPSILON} is added to the result set.
     * If {@code ctx} is not {@code null} and the end of the outermost rule is
     * reached, {@link Token#EOF} is added to the result set.</p>
     *
     * @param s the ATN state
     * @param ctx the complete parser context, or {@code null} if the context
     * should be ignored
     *
     * @return The set of tokens that can follow {@code s} in the ATN in the
     * specified {@code ctx}.
     */
    public IntervalSet LOOK(int targetRule, ATNState s, RuleContext ctx,
            ATNConfigSet altsFilter, BitSet targetAlts, boolean seeThruPreds) {
        return LOOK(targetRule, s, null, ctx, altsFilter, targetAlts, seeThruPreds);
    }

    /**
     * Compute set of tokens that can follow {@code s} in the ATN in the
     * specified {@code ctx}.
     *
     * <p>
     * If {@code ctx} is {@code null} and the end of the rule containing
     * {@code s} is reached, {@link Token#EPSILON} is added to the result set.
     * If {@code ctx} is not {@code null} and the end of the outermost rule is
     * reached, {@link Token#EOF} is added to the result set.</p>
     *
     * @param s the ATN state
     * @param stopState the ATN state to stop at. This can be a
     * {@link BlockEndState} to detect epsilon paths through a closure.
     * @param ctx the complete parser context, or {@code null} if the context
     * should be ignored
     *
     * @return The set of tokens that can follow {@code s} in the ATN in the
     * specified {@code ctx}.
     */
    public IntervalSet LOOK(int targetRule, ATNState s, ATNState stopState,
            RuleContext ctx, ATNConfigSet altsFilter, BitSet targetAlts, boolean seeThruPreds) {
        IntervalSet r = new IntervalSet();
        PredictionContext lookContext = ctx != null ? PredictionContext.fromRuleContext(s.atn, ctx) : null;
        _LOOK(targetRule, s, stopState, lookContext,
                r, new HashSet<ATNConfig>(), new BitSet(),
                IntList.create(ruleNames.length),
                seeThruPreds, true, altsFilter, targetAlts);
        return r;
    }

    /**
     * Compute set of tokens that can follow {@code s} in the ATN in the
     * specified {@code ctx}.
     *
     * <p>
     * If {@code ctx} is {@code null} and {@code stopState} or the end of the
     * rule containing {@code s} is reached, {@link Token#EPSILON} is added to
     * the result set. If {@code ctx} is not {@code null} and {@code addEOF} is
     * {@code true} and {@code stopState} or the end of the outermost rule is
     * reached, {@link Token#EOF} is added to the result set.</p>
     *
     * @param s the ATN state.
     * @param stopState the ATN state to stop at. This can be a
     * {@link BlockEndState} to detect epsilon paths through a closure.
     * @param ctx The outer context, or {@code null} if the outer context should
     * not be used.
     * @param look The result lookahead set.
     * @param lookBusy A set used for preventing epsilon closures in the ATN
     * from causing a stack overflow. Outside code should pass
     * {@code new HashSet<ATNConfig>} for this argument.
     * @param calledRuleStack A set used for preventing left recursion in the
     * ATN from causing a stack overflow. Outside code should pass
     * {@code new BitSet()} for this argument.
     * @param seeThruPreds {@code true} to true semantic predicates as
     * implicitly {@code true} and "see through them", otherwise {@code false}
     * to treat semantic predicates as opaque and add {@link #HIT_PRED} to the
     * result if one is encountered.
     * @param addEOF Add {@link Token#EOF} to the result if the end of the
     * outermost context is reached. This parameter has no effect if {@code ctx}
     * is {@code null}.
     */
    protected void _LOOK(
            int targetRule,
            ATNState s,
            ATNState stopState,
            PredictionContext ctx,
            IntervalSet look,
            Set<ATNConfig> lookBusy,
            BitSet calledRuleStack,
            IntList rulePath,
            boolean seeThruPreds,
            boolean addEOF,
            ATNConfigSet altsFilter,
            BitSet targetAlts) {
        ATNConfig c = new ATNConfig(s, 0, ctx);
        if (!lookBusy.add(c)) {
            return;
        }
        if (s == stopState) {
            if (ctx == null) {
                look.add(Token.EPSILON);
                return;
            } else if (ctx.isEmpty() && addEOF) {
                look.add(Token.EOF);
                return;
            }
        }

        if (s instanceof RuleStopState) {
            if (ctx == null) {
                look.add(Token.EPSILON);
                return;
            } else if (ctx.isEmpty() && addEOF) {
                look.add(Token.EOF);
                return;
            }

            if (ctx != PredictionContext.EMPTY) {
                // run thru all possible stack tops in ctx
                boolean removed = calledRuleStack.get(s.ruleIndex);
                try {
                    calledRuleStack.clear(s.ruleIndex);
                    rulePath.removeLast();
                    for (int i = 0; i < ctx.size(); i++) {
                        ATNState returnState = atn.states.get(ctx.getReturnState(i));
//					    System.out.println("popping back to "+retState);
                        _LOOK(targetRule, returnState, stopState, ctx.getParent(i),
                                look, lookBusy, calledRuleStack, rulePath,
                                seeThruPreds, addEOF, altsFilter, targetAlts);
                    }
                } finally {
                    if (removed) {
                        rulePath.add(s.ruleIndex);
                        calledRuleStack.set(s.ruleIndex);
                    }
                }
                return;
            }
        }

        int n = s.getNumberOfTransitions();
        for (int i = 0; i < n; i++) {
            Transition t = s.transition(i);
            if (rulePath.isEmpty()) {
                if (!targetAlts.get(i + 1)) {
                    continue;
                }
            }
//            System.out.println("TRANS " + t);
            if (t.getClass() == RuleTransition.class) {
                if (calledRuleStack.get(((RuleTransition) t).target.ruleIndex)) {
                    continue;
                }
                RuleTransition rt = (RuleTransition) t;

                PredictionContext newContext
                        = SingletonPredictionContext.create(ctx,
                                ((RuleTransition) t).followState.stateNumber);

                try {
                    calledRuleStack.set(((RuleTransition) t).target.ruleIndex);
                    rulePath.add(((RuleTransition) t).target.ruleIndex);
                    _LOOK(targetRule, t.target, stopState, newContext, look,
                            lookBusy, calledRuleStack, rulePath, seeThruPreds,
                            addEOF, altsFilter, targetAlts);
                } finally {
                    calledRuleStack.clear(((RuleTransition) t).target.ruleIndex);
                    rulePath.removeLast();
                }
            } else if (t instanceof AbstractPredicateTransition) {
                if (seeThruPreds) {
                    _LOOK(targetRule, t.target, stopState, ctx, look, lookBusy,
                            calledRuleStack, rulePath, seeThruPreds, addEOF,
                            altsFilter, targetAlts);
                } else {
                    look.add(HIT_PRED);
                }
            } else if (t.isEpsilon()) {
                if (t instanceof EpsilonTransition) {
                    EpsilonTransition et = (EpsilonTransition) t;
//                    System.out.println("  EPS " + ruleNames[et.target.ruleIndex]);
                }
                _LOOK(targetRule, t.target, stopState, ctx, look, lookBusy,
                        calledRuleStack, rulePath, seeThruPreds, addEOF,
                        altsFilter, targetAlts);
            } else if (t.getClass() == WildcardTransition.class) {
                look.addAll(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, atn.maxTokenType));
            } else {
                IntervalSet set = t.label();
                if (set != null) {
                    if (t instanceof NotSetTransition) {
                        set = set.complement(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, atn.maxTokenType));
                    }
                    IntList copy = rulePath.copy();
                    if (!copy.isEmpty() && copy.first() == targetRule) {
                        copy.removeAt(0);
                    }
                    IntegerList l = set.toIntegerList();
                    IntSet is = tokensForRule.get(t.target.ruleIndex);
                    for (int j = 0; j < l.size(); j++) {
                        if (!copy.isEmpty()) {
                            pathsToRule.get(l.get(i)).add(copy);
                        }
                        is.add(l.get(i));
                    }
                    look.addAll(set);
                }
            }
        }
    }

    public IntMap<Set<IntList>> tokensWithMultiplePaths() {
        IntMap<Set<IntList>> multi = pathsToRule.copy();
        multi.removeIf(set -> set.size() < 2);
        return multi;
    }

    String tokens(IntervalSet is) {
        if (is == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        IntegerList il = is.toIntegerList();
        for (int i = 0; i < il.size(); i++) {
            int tok = il.get(i);
            if (sb.length() != 0) {
                sb.append(", ");
            }
            sb.append(vocab.getSymbolicName(tok));
        }
        return sb.toString();
    }

}
