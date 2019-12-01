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
package org.nemesis.antlr.memory.tool.epsilon;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.v4.automata.ATNOptimizer;
import org.antlr.v4.automata.ParserATNFactory;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNState;
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

    private final EpsilonAnalysis anaState;

    MatchEmptyStringAnalyzer(Grammar g, EpsilonAnalysis anaState) {
        super(g);
        this.anaState = anaState;
    }

    @Override
    public ATN createATN() {
        _createATN(g.rules.values());
        assert atn.maxTokenType == g.getMaxTokenType();
        addRuleFollowLinks();
        addEOFTransitionToStartRules();
        ATNOptimizer.optimize(g, atn);
        Consumer<LinkedList<LookInfo>> onEpsilon = anaState.onEpsilon(g);
        for (Triple<Rule, ATNState, ATNState> pair : preventEpsilonClosureBlocks) {
            TrackingLL1Analyzer analyzer = new TrackingLL1Analyzer(atn, onEpsilon);
            ATNState blkStart = pair.b;
            ATNState blkStop = pair.c;
            anaState.withCurrentTokenAndRule(((GrammarAST) pair.a.ast.getChild(0)).getToken(), pair.a.name, () -> {
                IntervalSet lookahead = analyzer.LOOK(blkStart, blkStop, null);
                if (lookahead.contains(org.antlr.v4.runtime.Token.EPSILON)) {
                    ErrorType errorType = pair.a instanceof LeftRecursiveRule ? ErrorType.EPSILON_LR_FOLLOW : ErrorType.EPSILON_CLOSURE;
                    anaState.emit(errorType, pair.a.name);
                }
            });
        }

        optionalCheck:
        for (Triple<Rule, ATNState, ATNState> pair : preventEpsilonOptionalBlocks) {
            for (int i = 0; i < pair.b.getNumberOfTransitions(); i++) {
                ATNState startState = pair.b.transition(i).target;
                if (startState == pair.c) {
                    continue;
                }
                anaState.withCurrentTokenAndRule(((GrammarAST) pair.a.ast.getChild(0)).getToken(), pair.a.name, () -> {
                    TrackingLL1Analyzer analyzer = new TrackingLL1Analyzer(atn, onEpsilon);
                    if (analyzer.LOOK(startState, pair.c, null).contains(org.antlr.v4.runtime.Token.EPSILON)) {
                        anaState.emit(ErrorType.EPSILON_OPTIONAL, pair.a.name);
                    }
                });
            }
        }
        return atn;
    }

    static String s2s(ATNState state) {
        if (state == null) {
            return "<null>";
        }
        Class<?> type = state.getClass();
        StringBuilder sb = new StringBuilder(type.getSimpleName()).append('(')
                .append(state).append(' ') //                .append("rule ").append(this.g.getRule(state.ruleIndex).name).append(' ')
                ;
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

    static String toS(GrammarAST ast, CommonTree par) {
        StringBuilder sb = new StringBuilder(96);
        sb.append(par.getClass().getSimpleName()).append('(');
        int ps = par.getTokenStartIndex();
        int pe = par.getTokenStopIndex() + 1;
        for (int i = ps; i < pe; i++) {
            CommonToken ct = (CommonToken) ast.g.originalTokenStream.get(i);
            sb.append(ct.getText());
            if (i != pe - 1) {
                sb.append(' ');
            }
        }
        return sb.append(' ').toString();
    }

    @Override
    public Handle optional(GrammarAST optAST, Handle blk) {
        return wrap(optAST, () -> super.optional(optAST, blk));
    }

    @Override
    public Handle plus(GrammarAST plusAST, Handle blk) {
        return wrap(plusAST, () -> super.plus(plusAST, blk));
    }

    @Override
    public Handle star(GrammarAST starAST, Handle elem) {
        return wrap(starAST, () -> super.star(starAST, elem));
    }

    private Handle wrap(GrammarAST ast, Supplier<Handle> hs) {
        BiConsumer<Changed, Triple<Rule, ATNState, ATNState>> cap
                = anaState.astInfoCapturer(ast);
        return wrap(hs, cap);
    }

    private Handle wrap(Supplier<Handle> hs,
            BiConsumer<Changed, Triple<Rule, ATNState, ATNState>> onChange) {
        int oldOs = preventEpsilonOptionalBlocks.size();
        int oldCs = preventEpsilonClosureBlocks.size();
        Handle result = hs.get();
        int newOs = preventEpsilonOptionalBlocks.size();
        int newCs = preventEpsilonClosureBlocks.size();

        Changed change = Changed.of(oldOs, newOs, oldCs, newCs);
        if (change.isChanged()) {
            Triple<Rule, ATNState, ATNState> val = change.get(preventEpsilonClosureBlocks, preventEpsilonOptionalBlocks);
            onChange.accept(change, val);
        }
        return result;
    }
}
