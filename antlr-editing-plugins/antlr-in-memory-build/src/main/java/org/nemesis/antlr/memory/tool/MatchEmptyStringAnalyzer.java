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
package org.nemesis.antlr.memory.tool;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.antlr.v4.automata.ATNOptimizer;
import org.antlr.v4.automata.ParserATNFactory;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNConfig;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.BlockEndState;
import org.antlr.v4.runtime.atn.LL1Analyzer;
import org.antlr.v4.runtime.atn.PredictionContext;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.misc.Triple;
import org.antlr.v4.tool.ErrorType;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.LeftRecursiveRule;
import org.antlr.v4.tool.Rule;
import org.antlr.v4.tool.ast.GrammarAST;

/**
 * Antlr's default error reporting simply tells you "this rule directly or
 * indirectly calls some rule and somewhere down in the bowels of your grammar
 * something can match the empty string" which is useless for actually figuring
 * out the problem is. This class does the same analysis to get there, but keeps
 * track of what it was looking at when the problem was encountered.
 *
 * @author Tim Boudreau
 */
class MatchEmptyStringAnalyzer extends ParserATNFactory {

    private org.antlr.runtime.Token currentToken;
    private String currentRuleName;
    private final List<PendingEmission> pendingEmissions = new ArrayList<>();
    private final List<EpsilonRuleInfo> items = new ArrayList<>(3);

    MatchEmptyStringAnalyzer(Grammar g) {
        super(g);
    }

    public List<EpsilonRuleInfo> analyze() {
        createATN();
//        System.out.println("FOUND " + items.size() + " EPSILON ITEMS");
        return items;
    }

    void emit(ErrorType type) {
//        System.out.println("  EMIT " + type.name() + " with " + pendingEmissions.size() + "emissions");
        for (PendingEmission pe : pendingEmissions) {
            items.add(pe.toInfo(type));
        }
        pendingEmissions.clear();
    }

    private void onEpsilon(LinkedList<LookInfo> stack) {
//        System.out.println("ON EPSILON WITH ");
        /*
        StringBuilder statePath = new StringBuilder();
        for (LookInfo li : stack) {
            System.out.println("  - " + li);
            if (statePath.length() > 0) {
                statePath.append(" / ");
            }
            String rl = g.getRule(li.state.ruleIndex).name;
            IntervalSet iset = li.state.nextTokenWithinRule;
            statePath.append(li.state.stateNumber).append('-')
                    .append(rl).append('-')
                    .append(li.state.getClass().getSimpleName());
            if (li.state instanceof LoopEndState) {
                LoopEndState les = (LoopEndState) li.state;
                statePath.append(" loop=").append(s2s(les.loopBackState));
            }
            if (iset != null) {
                if (iset.size() > 0) {
                    statePath.append(" (nexts: ");
                    for (int i = 0; i < iset.size(); i++) {
                        int tokNum = iset.get(i);
                        if (tokNum >= 0) {
                            org.antlr.runtime.CommonToken tok = (org.antlr.runtime.CommonToken) g.originalGrammar.tokenStream.get(tokNum);
                            statePath.append(tok).append(' ');
                        }
                    }
                    statePath.append(')');
                }
            }
        }
        System.out.println("STATE PATH: " + statePath);
        System.out.println("\n");
         */
        List<String> path = new ArrayList<>(stack.size());
        for (LookInfo info : stack) {
            if (info.state == null) {
                continue;
            }
            int ix = info.state.ruleIndex;
            if (ix >= 0 && ix <= g.rules.size()) {
                path.add(g.getRule(info.state.ruleIndex).name);
            }
        }
        for (LookInfo info : stack) {
            int ruleIndex = info.state.ruleIndex;
            int stopRuleIndex = info.stopState.ruleIndex;
            Rule startRule = g.getRule(ruleIndex);
            Rule stopRule = g.getRule(stopRuleIndex);
            PendingEmission pe = new PendingEmission(startRule.g, currentToken, currentRuleName, startRule, stopRule, path);
            pendingEmissions.add(pe);
            break;
        }
    }

    @Override
    public ATN createATN() {
        _createATN(g.rules.values());
        assert atn.maxTokenType == g.getMaxTokenType();
        addRuleFollowLinks();
        addEOFTransitionToStartRules();
        ATNOptimizer.optimize(g, atn);
        for (Triple<Rule, ATNState, ATNState> pair : preventEpsilonClosureBlocks) {
            TrackingLL1Analyzer analyzer = new TrackingLL1Analyzer(atn);
            ATNState blkStart = pair.b;
            ATNState blkStop = pair.c;
            currentToken = ((GrammarAST) pair.a.ast.getChild(0)).getToken();
            currentRuleName = pair.a.name;
            IntervalSet lookahead = analyzer.LOOK(blkStart, blkStop, null);
            if (lookahead.contains(org.antlr.v4.runtime.Token.EPSILON)) {
                ErrorType errorType = pair.a instanceof LeftRecursiveRule ? ErrorType.EPSILON_LR_FOLLOW : ErrorType.EPSILON_CLOSURE;
                emit(errorType);
            }
            pendingEmissions.clear();
        }

        optionalCheck:
        for (Triple<Rule, ATNState, ATNState> pair : preventEpsilonOptionalBlocks) {
            for (int i = 0; i < pair.b.getNumberOfTransitions(); i++) {
                ATNState startState = pair.b.transition(i).target;
                if (startState == pair.c) {
                    continue;
                }

                currentToken = ((GrammarAST) pair.a.ast.getChild(0)).getToken();
                currentRuleName = pair.a.name;
                TrackingLL1Analyzer analyzer = new TrackingLL1Analyzer(atn);
                if (analyzer.LOOK(startState, pair.c, null).contains(org.antlr.v4.runtime.Token.EPSILON)) {
                    emit(ErrorType.EPSILON_OPTIONAL);
                    continue optionalCheck;
                }
                pendingEmissions.clear();
            }
        }
        return atn;
    }

    final class TrackingLL1Analyzer extends LL1Analyzer {

        private final LinkedList<LookInfo> lookInfo = new LinkedList<>();

        public TrackingLL1Analyzer(ATN atn) {
            super(atn);
        }

        private int epsilonCount;

        protected void _LOOK(ATNState s,
                ATNState stopState,
                PredictionContext ctx,
                IntervalSet look,
                Set<ATNConfig> lookBusy,
                BitSet calledRuleStack,
                boolean seeThruPreds, boolean addEOF) {
            LookInfo info = new LookInfo(s, stopState, ctx);
            lookInfo.push(info);
            try {
                boolean hadEpsilon = look.contains(Token.EPSILON);
                boolean epsilonAdded = false;
                int oldEpsilonCount = epsilonCount;
                super._LOOK(s, stopState, ctx, look, lookBusy, calledRuleStack, seeThruPreds, addEOF);
                boolean hasEpsilon = look.contains(Token.EPSILON);
                epsilonAdded = !hadEpsilon && hasEpsilon;
                if (epsilonAdded && oldEpsilonCount == epsilonCount && !(s instanceof BlockEndState)) {
                    onEpsilon(lookInfo);
                    epsilonCount++;
                }
            } finally {
                lookInfo.pop();
            }
        }
    }

    String s2s(ATNState state) {
        if (state == null) {
            return "<null>";
        }
        Class<?> type = state.getClass();
        StringBuilder sb = new StringBuilder(type.getSimpleName()).append('(')
                .append(state).append(' ')
                .append("rule ").append(this.g.getRule(state.ruleIndex).name).append(' ');
        while (type != Object.class) {
            Field[] fields = type.getFields();
            for (Field f : fields) {
                if ((f.getModifiers() & Modifier.PUBLIC) != 0 && (f.getModifiers() & Modifier.STATIC) == 0) {
                    try {
                        Object o = f.get(state);
                        if (o instanceof ATN || o == null) {
                            continue;
                        }
                        sb.append(f.getName()).append('=').append(o)
                                //                                .append(" <").append(o == null ? "" : o.getClass().getSimpleName())
                                //                                .append('>')
                                .append(' ');
                    } catch (IllegalArgumentException | IllegalAccessException ex) {
                    }
                }
            }
            type = type.getSuperclass();
        }

        return sb.toString();
    }

    final class LookInfo {

        final ATNState state;
        final ATNState stopState;
        final PredictionContext ctx;

        public LookInfo(ATNState state, ATNState stopState, PredictionContext ctx) {
            this.state = state;
            this.stopState = stopState;
            this.ctx = ctx;
        }

        @Override
        public String toString() {
            return "LookInfo{" + "state=" + s2s(state) + ", stopState=" + s2s(stopState) + ", ctx=" + ctx + '}';
        }
    }

    static class PendingEmission {

        private final Grammar grammar;
        private final org.antlr.runtime.Token token;
        private final String ruleName;
        private final Rule startRule;
        private final Rule stopRule;
        private final List<String> path;

        public PendingEmission(Grammar grammar, org.antlr.runtime.Token token, String ruleName, Rule startRule,
                Rule stopRule, List<String> path) {
            this.grammar = grammar;
            this.token = token;
            this.ruleName = ruleName;
            this.startRule = startRule;
            this.stopRule = stopRule;
            this.path = path;
        }

        public EpsilonRuleInfo toInfo(ErrorType errorType) {
            int ruleStartingToken = startRule.ast.getTokenStartIndex();
            org.antlr.runtime.CommonToken startRuleStartToken = (org.antlr.runtime.CommonToken) startRule.g.originalTokenStream.get(ruleStartingToken);

            int ruleLine = startRule.ast.getLine();
            int ruleLineOffset = startRule.ast.getCharPositionInLine();

            String altLabel = startRule.ast.getAltLabel();

            org.antlr.runtime.CommonToken tk = (org.antlr.runtime.CommonToken) token;
            int startRuleStart = startRuleStartToken.getStartIndex();
            int startRuleEnd = startRuleStartToken.getStopIndex() + 1; //startRuleStopToken.getStopIndex() + 1;

            int victimStart = tk.getStartIndex();
            int victimEnd = tk.getStopIndex() + 1;

            return new EpsilonRuleInfo(grammar.name, errorType, startRule.name,
                    startRuleStart, startRuleEnd,
                    ruleLine, ruleLineOffset,
                    startRule.ast.isLexerRule(), ruleName,
                    tk.getTokenIndex(),
                    victimStart, victimEnd,
                    token.getLine(),
                    token.getCharPositionInLine(), path, altLabel);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("EpsilonRuleInfo(");
            int ruleStart = startRule.ast.getTokenStartIndex();
            int ruleEnd = startRule.ast.getTokenStopIndex() + 1;
            int ruleLine = startRule.ast.getLine();
            int ruleLineOffset = startRule.ast.getCharPositionInLine();
            org.antlr.runtime.Token startRuleStartToken = startRule.g.originalTokenStream.get(ruleStart);
            org.antlr.runtime.Token startRuleStopToken = startRule.g.originalTokenStream.get(ruleEnd);

            sb.append(grammar.name).append(' ')
                    .append(" startRuleName ").append(startRule.name)
                    .append(" startRuleLine ").append(startRule.ast.getLine())
                    .append(" startRuleStart ").append(ruleStart)
                    .append(" startRuleEnd ").append(ruleEnd)
                    .append(" startToken ").append(startRuleStartToken)
                    .append(" stopRuleName ").append(stopRule.name)
                    .append(" stopRuleLine ").append(stopRule.ast.getLine())
                    .append(" stopRuleStart ").append(stopRule.ast.getTokenStartIndex())
                    .append(" stopRuleStop").append(stopRule.ast.getTokenStartIndex())
                    .append(" stopToken ").append(startRuleStopToken)
                    .append(" token ").append(token)
                    .append(" lexRule ").append(startRule.ast.isLexerRule())
                    .append(" ruleName ").append(ruleName)
                    .append(" ruleLine ").append(ruleLine)
                    .append(" ruleLineOffset ").append(ruleLineOffset);

            return sb.append(')').toString();
        }
    }
}
