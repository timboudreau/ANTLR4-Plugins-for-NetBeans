/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
 */
package org.nemesis.parse.recorder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import org.antlr.v4.runtime.TokenStream;

/**
 *
 * @author Tim Boudreau
 */
class ParseTranscriberImpl implements ParseTranscriber {

    final TokenStream str;
    private final Map<Integer, List<Evt>> eventsForTokenIndex = new HashMap<>();

    public ParseTranscriberImpl(TokenStream str) {
        this.str = str;
    }

    public void visitEvents(BiConsumer<Integer, List<Evt>> c) {
        List<Integer> keys = new ArrayList<>(eventsForTokenIndex.keySet());
        Collections.sort(keys);
        for (Integer k : keys) {
            c.accept(k, eventsForTokenIndex.get(k));
        }
    }

    private List<Evt> events(int index) {
        List<Evt> result = eventsForTokenIndex.get(index);
        if (result == null) {
            result = new ArrayList<>(5);
            eventsForTokenIndex.put(index, result);
        }
        return result;
    }

    @Override
    public void ruleStop(int ruleIndex, int stateNumber, boolean epsilonOnlyTransitions, int tokenIndex) {
        events(tokenIndex).add(new RuleStopEvent(ruleIndex, stateNumber, epsilonOnlyTransitions));
    }

    @Override
    public void enterRule(int ruleIndex, int tokenIndex) {
        events(tokenIndex).add(new EnterRuleEvent(ruleIndex));
    }

    @Override
    public void decision(int decision, int stateNumber, int ruleIndex, boolean epsilonOnly, int[] nextTokenWithinRule, int tokenIndex) {
        events(tokenIndex).add(new DecisionEvent(decision, stateNumber, ruleIndex, epsilonOnly, nextTokenWithinRule));
    }

    @Override
    public void state(int stateNumber, boolean epsilonOnly, int ruleIndex, int[] nextTokenWithinRule, int tokenIndex) {
        events(tokenIndex).add(new StateEvent(stateNumber, epsilonOnly, ruleIndex, nextTokenWithinRule));
    }

    @Override
    public void exitRule(int tokenIndex) {
        events(tokenIndex).add(new ExitRuleEvent());
    }

    @Override
    public void recurse(int state, int ruleIndex, int precedence, int index) {
    }

    public enum Events {
        ENTER_RULE, EXIT_RULE, DECISION, STATE, RULE_STOP, RECURSE
    }

    public static abstract class Evt implements Comparable<Evt> {

        private static final AtomicLong IDS = new AtomicLong(Long.MIN_VALUE);
        public final Events kind;
        private final long id = IDS.getAndIncrement();

        public static long base() {
            return IDS.get();
        }

        public Evt(Events kind) {
            this.kind = kind;
        }

        public int id(long base) {
            return (int) (id - base);
        }

        @Override
        public int compareTo(Evt o) {
            return Long.compare(id, o.id);
        }
    }

    public static class EnterRuleEvent extends Evt {

        public final int ruleIndex;

        EnterRuleEvent(int ruleIndex) {
            super(Events.ENTER_RULE);
            this.ruleIndex = ruleIndex;
        }

        @Override
        public String toString() {
            return "EnterRuleEvent{" + "ruleIndex=" + ruleIndex + '}';
        }
    }

    public static class ExitRuleEvent extends Evt {

        public ExitRuleEvent() {
            super(Events.EXIT_RULE);
        }

        @Override
        public String toString() {
            return "ExitRuleEvent{" + '}';
        }
    }

    public static final class RuleStopEvent extends Evt {

        public final int ruleIndex;
        public final int stateNumber;
        public final boolean epsilonOnlyTransitions;

        RuleStopEvent(int ruleIndex, int stateNumber, boolean epsilonOnlyTransitions) {
            super(Events.RULE_STOP);
            this.ruleIndex = ruleIndex;
            this.stateNumber = stateNumber;
            this.epsilonOnlyTransitions = epsilonOnlyTransitions;
        }

        @Override
        public String toString() {
            return "RuleStopEvent{" + "ruleIndex=" + ruleIndex
                    + ", stateNumber=" + stateNumber
                    + ", epsilonOnlyTransitions=" + epsilonOnlyTransitions + '}';
        }

    }

    public static final class DecisionEvent extends Evt {

        public final int decision;
        public final int stateNumber;
        public final int ruleIndex;
        public final boolean epsilonOnly;
        public final int[] nextTokenWithinRule;

        DecisionEvent(int decision, int stateNumber, int ruleIndex, boolean epsilonOnly, int[] nextTokenWithinRule) {
            super(Events.DECISION);
            this.decision = decision;
            this.stateNumber = stateNumber;
            this.ruleIndex = ruleIndex;
            this.epsilonOnly = epsilonOnly;
            this.nextTokenWithinRule = nextTokenWithinRule;
        }

        @Override
        public String toString() {
            return "DecisionEvent{" + "decision=" + decision + ", stateNumber="
                    + stateNumber + ", ruleIndex=" + ruleIndex + ", epsilonOnly="
                    + epsilonOnly + ", nextTokenWithinRule="
                    + Arrays.toString(nextTokenWithinRule) + '}';
        }
    }

    public static final class StateEvent extends Evt {

        public final int stateNumber;
        public final boolean epsilonOnly;
        public final int ruleIndex;
        public final int[] nextTokenWithinRule;

        StateEvent(int stateNumber, boolean epsilonOnly, int ruleIndex, int[] nextTokenWithinRule) {
            super(Events.STATE);
            this.stateNumber = stateNumber;
            this.epsilonOnly = epsilonOnly;
            this.ruleIndex = ruleIndex;
            this.nextTokenWithinRule = nextTokenWithinRule;
        }

        @Override
        public String toString() {
            return "StateEvent{" + "stateNumber=" + stateNumber
                    + ", epsilonOnly=" + epsilonOnly + ", ruleIndex="
                    + ruleIndex + ", nextTokenWithinRule="
                    + Arrays.toString(nextTokenWithinRule) + '}';
        }
    }
}
