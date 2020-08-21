/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.memory.tool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.EpsilonTransition;
import org.antlr.v4.runtime.atn.RuleStartState;
import org.antlr.v4.runtime.atn.RuleStopState;
import org.antlr.v4.runtime.atn.RuleTransition;
import org.antlr.v4.runtime.atn.Transition;
import org.antlr.v4.runtime.misc.OrderedHashSet;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.Rule;

/**
 *
 * @author Tim Boudreau
 */
public class RefiningLeftRecursionDetector {

    Grammar g;
    public ATN atn;

    /**
     * Holds a list of cycles (sets of rule names).
     */
    public List<Set<Rule>> listOfRecursiveCycles = new ArrayList<>();

    /**
     * Which rule start states have we visited while looking for a single
     * left-recursion check?
     */
    Set<RuleStartState> rulesVisitedPerRuleCheck = new HashSet<>();

    public RefiningLeftRecursionDetector(Grammar g, ATN atn) {
        this.g = g;
        this.atn = atn;
    }

    public void check() {
        for (RuleStartState start : atn.ruleToStartState) {
            //System.out.print("check "+start.rule.name);
            rulesVisitedPerRuleCheck.clear();
            rulesVisitedPerRuleCheck.add(start);
            //FASerializer ser = new FASerializer(atn.g, start);
            //System.out.print(":\n"+ser+"\n");

            check(g.getRule(start.ruleIndex), start, new HashSet<ATNState>());
        }
        //System.out.println("cycles="+listOfRecursiveCycles);
        if (!listOfRecursiveCycles.isEmpty()) {
            g.tool.errMgr.leftRecursionCycles(g.fileName, listOfRecursiveCycles);
        }
    }

    /**
     * From state s, look for any transition to a rule that is currently being
     * traced. When tracing r, visitedPerRuleCheck has r initially. If you reach
     * a rule stop state, return but notify the invoking rule that the called
     * rule is nullable. This implies that invoking rule must look at follow
     * transition for that invoking state.
     *
     * The visitedStates tracks visited states within a single rule so we can
     * avoid epsilon-loop-induced infinite recursion here. Keep filling the
     * cycles in listOfRecursiveCycles and also, as a side-effect, set
     * leftRecursiveRules.
     */
    public boolean check(Rule enclosingRule, ATNState s, Set<ATNState> visitedStates) {
        boolean pushed = stack.isEmpty() || stack.peek() != enclosingRule;
        try {
            if (pushed) {
                stack.push(enclosingRule);
            }
            if (s instanceof RuleStopState) {
                return true;
            }
            if (visitedStates.contains(s)) {
                return false;
            }
            visitedStates.add(s);
            //System.out.println("visit "+s);
            int n = s.getNumberOfTransitions();
            boolean stateReachesStopState = false;
            for (int i = 0; i < n; i++) {
                Transition t = s.transition(i);
                if (t instanceof RuleTransition) {
                    RuleTransition rt = (RuleTransition) t;
                    Rule r = g.getRule(rt.ruleIndex);
                    if (rulesVisitedPerRuleCheck.contains((RuleStartState) t.target)) {
                        int tix = ((RuleTransition) t).target == null || ((RuleTransition) t).target.nextTokenWithinRule == null ? -1 :
                                t.target.nextTokenWithinRule.size() > 0 ? t.target.nextTokenWithinRule.get(0) : -1;
                        if (tix != -1) {
                            System.out.println("TIX " + tix);
                        }
//                        System.out.println("ADD TRANS " + rt + " targ " + rt.target.nextTokenWithinRule + " prec " + ((RuleTransition) t).precedence);
                        addRulesToCycle(enclosingRule, r);
                    } else {
                        // must visit if not already visited; mark target, pop when done
                        rulesVisitedPerRuleCheck.add((RuleStartState) t.target);
                        // send new visitedStates set per rule invocation
                        boolean nullable = check(r, t.target, new HashSet<ATNState>());
                        // we're back from visiting that rule
                        rulesVisitedPerRuleCheck.remove((RuleStartState) t.target);
                        if (nullable) {
                            stateReachesStopState |= check(enclosingRule, rt.followState, visitedStates);
                        }
                    }
                } else if (t.isEpsilon()) {
                    EpsilonTransition et = (EpsilonTransition) t;
                    epsilons++;
                    try {
                        stateReachesStopState |= check(enclosingRule, t.target, visitedStates);
                    } finally {
                        epsilons--;
                    }
                }
                // else ignore non-epsilon transitions
            }
            return stateReachesStopState;
        } finally {
            if (pushed) {
                stack.pop();
            }
        }
    }
    int epsilons;
    private final LinkedList<Rule> stack = new LinkedList<>();

    String stack() {
        StringBuilder sb = new StringBuilder('[');
        for (Rule r : stack) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(r.name);
        }
        return sb.append(']').toString();
    }

    /**
     * enclosingRule calls targetRule. Find the cycle containing the target and
     * add the caller. Find the cycle containing the caller and add the target.
     * If no cycles contain either, then create a new cycle.
     */
    protected void addRulesToCycle(Rule enclosingRule, Rule targetRule) {
        System.out.println("ADD TO CYCLE " + enclosingRule.name + " / " + targetRule.name + " eps " + epsilons
                + " at " + stack());
        //System.err.println("left-recursion to "+targetRule.name+" from "+enclosingRule.name);
        boolean foundCycle = false;
        for (Set<Rule> rulesInCycle : listOfRecursiveCycles) {
            // ensure both rules are in same cycle
            if (rulesInCycle.contains(targetRule)) {
                rulesInCycle.add(enclosingRule);
                foundCycle = true;
            }
            if (rulesInCycle.contains(enclosingRule)) {
                rulesInCycle.add(targetRule);
                foundCycle = true;
            }
        }
        if (!foundCycle) {
            Set<Rule> cycle = new OrderedHashSet<Rule>();
            cycle.add(targetRule);
            cycle.add(enclosingRule);
            listOfRecursiveCycles.add(cycle);
        }
    }

}
