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
package org.nemesis.antlr.error.highlighting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.antlr.v4.misc.CharSupport;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializationOptions;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ATNSerializer;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.AtomTransition;
import org.antlr.v4.runtime.atn.BlockEndState;
import org.antlr.v4.runtime.atn.CodePointTransitions;
import org.antlr.v4.runtime.atn.DecisionState;
import org.antlr.v4.runtime.atn.EpsilonTransition;
import org.antlr.v4.runtime.atn.NotSetTransition;
import org.antlr.v4.runtime.atn.RangeTransition;
import org.antlr.v4.runtime.atn.SetTransition;
import org.antlr.v4.runtime.atn.Transition;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.IntegerList;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.tool.ErrorType;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.Rule;

/**
 *
 * @author Tim Boudreau
 */
public class ATNAnalysis {

    private final Grammar grammar;

    ATNAnalysis(Grammar grammar) {
        this.grammar = grammar;
    }

    void analyze() {
        ATN atn = grammar.atn;
        ArrayList<Map.Entry<String, Integer>> l = new ArrayList<>(grammar.tokenNameToTypeMap.entrySet());
        Collections.sort(l, (ea, eb) -> {
            return ea.getValue().compareTo(eb.getValue());
        });
        List<String> names = new ArrayList<>(l.size());
        for (Map.Entry<String, Integer> e : l) {
            names.add(e.getKey());
        }
        ATNSerializer ser = new ATNSerializer(atn, names);
        IntegerList il = ser.serialize();
        ATNDeserializationOptions opts = new ATNDeserializationOptions();
        opts.setGenerateRuleBypassTransitions(false);
        ATNDeserializer des = new ATNDeserializer(opts);

        ATN deserialized = des.deserialize(il.toCharArray());

        optimizeSets(deserialized, names);
        optimizeStates(deserialized);
    }

    private void optimizeSets(ATN atn, List<String> names) {
        if (grammar.isParser()) {
            // parser codegen doesn't currently support SetTransition
//            return;
        }
        System.out.println("DFAS " + grammar.decisionDFAs);

        int removedStates = 0;
        List<DecisionState> decisions = atn.decisionToState;
        for (DecisionState decision : decisions) {
            DFA dfa = grammar.decisionDFAs.get(decision.decision);
            if (dfa != null) {
                System.out.println("DECISION " + decision.decision);
                System.out.println("DFA: " + dfa.toLexerString() + " / "
                    + dfa.toString(names.toArray(new String[names.size()])));

            }

            Rule rule = decision.ruleIndex < 0 ? null : grammar.getRule(decision.ruleIndex);
            if (decision.ruleIndex >= 0) {
                if (Character.isLowerCase(rule.name.charAt(0))) {
                    // parser codegen doesn't currently support SetTransition
//                    continue;
                }
            }

            IntervalSet setTransitions = new IntervalSet();
            for (int i = 0; i < decision.getNumberOfTransitions(); i++) {
                Transition epsTransition = decision.transition(i);
                if (!(epsTransition instanceof EpsilonTransition)) {
                    continue;
                }

                if (epsTransition.target.getNumberOfTransitions() != 1) {
                    continue;
                }

                Transition transition = epsTransition.target.transition(0);
                if (!(transition.target instanceof BlockEndState)) {
                    continue;
                }

                if (transition instanceof NotSetTransition) {
                    // TODO: not yet implemented
                    continue;
                }

                if (transition instanceof AtomTransition
                        || transition instanceof RangeTransition
                        || transition instanceof SetTransition) {
                    setTransitions.add(i);
                }
            }

            // due to min alt resolution policies, can only collapse sequential alts
            for (int i = setTransitions.getIntervals().size() - 1; i >= 0; i--) {
                Interval interval = setTransitions.getIntervals().get(i);
                if (interval.length() <= 1) {
                    continue;
                }

                ATNState blockEndState = decision.transition(interval.a).target.transition(0).target;
                IntervalSet matchSet = new IntervalSet();
                for (int j = interval.a; j <= interval.b; j++) {
                    Transition matchTransition = decision.transition(j).target.transition(0);
                    if (matchTransition instanceof NotSetTransition) {
                        throw new UnsupportedOperationException("Not yet implemented.");
                    }
                    IntervalSet set = matchTransition.label();
                    List<Interval> intervals = set.getIntervals();
                    int n = intervals.size();
                    for (int k = 0; k < n; k++) {
                        Interval setInterval = intervals.get(k);
                        int a = setInterval.a;
                        int b = setInterval.b;
                        if (a != -1 && b != -1) {
                            for (int v = a; v <= b; v++) {
                                if (matchSet.contains(v)) {
                                    // TODO: Token is missing (i.e. position in source will not be displayed).
                                    grammar.tool.errMgr.grammarError(ErrorType.CHARACTERS_COLLISION_IN_SET, grammar.fileName,
                                            null,
                                            CharSupport.getANTLRCharLiteralForChar(v),
                                            CharSupport.getIntervalSetEscapedString(matchSet));
                                    break;
                                }
                            }
                        }
                    }
                    matchSet.addAll(set);
                }

                Transition newTransition;
                if (matchSet.getIntervals().size() == 1) {
                    if (matchSet.size() == 1) {
                        newTransition = CodePointTransitions.createWithCodePoint(blockEndState, matchSet.getMinElement());
                    } else {
                        Interval matchInterval = matchSet.getIntervals().get(0);
                        newTransition = CodePointTransitions.createWithCodePointRange(blockEndState, matchInterval.a, matchInterval.b);
                    }
                } else {
                    newTransition = new SetTransition(blockEndState, matchSet);
                }

                
                decision.transition(interval.a).target.setTransition(0, newTransition);
                for (int j = interval.a + 1; j <= interval.b; j++) {

                    ATNState state = atn.states.get(decision.stateNumber);
                    Transition removed = decision.removeTransition(interval.a + 1);
                    
                    IntervalSet toks = atn.getExpectedTokens(decision.stateNumber, RuleContext.EMPTY);
//                    System.out.println("TOKS " + toks);
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < toks.size(); k++) {
                        sb.append(grammar.getTokenDisplayName(toks.get(k))).append(",");
                    }
                    System.out.println("TOKS " + sb);

                    IntervalSet[] lk = grammar.decisionLOOK.get(decision.decision);
                    System.out.println("LK: " + (lk == null ? "null" : Arrays.toString(lk)));
                    
                    System.out.println("REMOVE STATE " + removed + " decis " + decision + " in rule " 
                            + (rule == null ? decision.ruleIndex + "" : rule.name)
                        + " target " + removed.target + " next " + removed.target.nextTokenWithinRule
                            + " epsOnly " + decision.epsilonOnlyTransitions
                            + " nonGreedy? " + decision.nonGreedy
                            + " stateNum " + decision.stateNumber
                            + " state " + state
                            + " dfa " + dfa
                            + " " + (dfa == null ? null : dfa.toLexerString())
                    );
                    System.out.println("DEL " + grammar.decisionLOOK);
                    atn.removeState(removed.target);
                    removedStates++;
                }
            }
        }

        System.out.println("ATN optimizer removed " + removedStates + " states by collapsing sets.");
    }

    private void optimizeStates(ATN atn) {
//		System.out.println(atn.states);
        List<ATNState> compressed = new ArrayList<ATNState>();
        int i = 0; // new state number
        for (ATNState s : atn.states) {
            if (s != null) {
                compressed.add(s);
                s.stateNumber = i; // reset state number as we shift to new position
                i++;
            }
        }
        System.out.println(compressed);
        System.out.println("ATN optimizer removed " + (atn.states.size() - compressed.size()) + " null states.");
        atn.states.clear();
        atn.states.addAll(compressed);
    }

}
