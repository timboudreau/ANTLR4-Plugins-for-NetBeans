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

import com.mastfrog.util.collections.CollectionUtils;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.misc.Triple;
import org.antlr.v4.tool.ErrorType;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.LexerGrammar;
import org.antlr.v4.tool.Rule;
import org.antlr.v4.tool.ast.AltAST;
import org.antlr.v4.tool.ast.GrammarAST;
import org.antlr.v4.tool.ast.PlusBlockAST;
import org.antlr.v4.tool.ast.RuleAST;
import org.nemesis.antlr.memory.tool.ext.EpsilonRuleInfo;
import org.nemesis.antlr.memory.tool.ext.ProblematicEbnfInfo;

/**
 * Analyzes grammars to root-cause rules which have an element that can match
 * the empty string in their closure. This involves running the same logic Antlr
 * does to determine that situation, but with instrumentation to capture the
 * rules being processed and identify the culprit EBNF. The heavy lifting is
 * done by TrackingLL1Analyzer, which recursively pushes what it's processing
 * into the current PendingEmission; if, on exit, such an EBNF has been
 * encountered, then we have complete information about where and what it was,
 * and PendingEmissions are emitted as errors with their tag set to an
 * EpsilonRuleInfo which hint providers can get and use to suggest replacement
 * text.
 *
 * @author Tim Boudreau
 */
public final class EpsilonAnalysis {

    org.antlr.runtime.Token currentToken;
    String currentRuleName;
    final List<PendingEmission> pendingEmissions = new ArrayList<>(24);
    private final List<EpsilonRuleInfo> items = new ArrayList<>(16);
    final Map<String, LinkedList<ProblematicEbnfInfo>> problemInfos = CollectionUtils.supplierMap(LinkedList::new);

    private EpsilonAnalysis() {
        // do nothing
    }

    public static List<EpsilonRuleInfo> analyze(Grammar g) {
        EpsilonAnalysis state = new EpsilonAnalysis();
        if (g instanceof LexerGrammar) {
            LexerGrammar lg = (LexerGrammar) g;
            LexerEmptyStringAnalyzer lana = new LexerEmptyStringAnalyzer(lg, state);
            lana.createATN();
        } else {
            ParserEmptyStringAnalyzer ana = new ParserEmptyStringAnalyzer(g, state);
            ana.createATN();
            if (g.implicitLexer != null) {
                LexerEmptyStringAnalyzer lana = new LexerEmptyStringAnalyzer(g.implicitLexer, state);
                lana.createATN();
            }
        }
        return state.items;
    }

    void withCurrentTokenAndRule(org.antlr.runtime.Token token, String rule, Runnable r) {
        org.antlr.runtime.Token oldToken = currentToken;
        String oldRule = currentRuleName;
        currentRuleName = rule;
        currentToken = token;
        try {
            r.run();
        } finally {
            currentRuleName = oldRule;
            currentToken = oldToken;
//            if (!pendingEmissions.isEmpty()) {
//                System.out.println("  DISCARDING SOME PENDING EMISSIONS: " + pendingEmissions);
//            }
            pendingEmissions.clear();
        }
    }

    EpsilonAnalysis addPendingEmission(PendingEmission emission) {
        pendingEmissions.add(emission);
        return this;
    }

    void emit(ErrorType type, String inRule) {
        LinkedList<ProblematicEbnfInfo> l = null;
        outer:
        for (PendingEmission pe : pendingEmissions) {
            for (String rp : pe.path) {
                l = problemInfos.get(rp);
                if (!l.isEmpty()) {
                    break outer;
                }
            }
        }
        if (l.isEmpty()) {
//            System.out.println("NOTHING TO EMIT IN " + inRule + " for " + type);
            return;
        }
        //        assert !l.isEmpty() : "Did not find target info in " + pendingEmissions
        //                + " and " + problemInfos;
//        System.out.println("EMIT WITH " + l.size() + " RULES: " + l);
//        System.out.println("PE SIZE " + pendingEmissions.size());
        for (PendingEmission pe : pendingEmissions) {
            ProblematicEbnfInfo problem = l.pop();
//            System.out.println("  POPPED " + problem);
            EpsilonRuleInfo info = pe.toInfo(type, problem);
            items.add(info);
//            System.out.println("EMIT " + info);
        }
//        pendingEmissions.clear();
    }

    Consumer<LinkedList<LookInfo>> onEpsilon(Grammar g) {
        return (stack) -> {
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
                int stopRuleIndex = info.stopState == null ? info.state.ruleIndex : info.stopState.ruleIndex;
                Rule startRule = g.getRule(ruleIndex);
                Rule stopRule = g.getRule(stopRuleIndex);
                PendingEmission pe = new PendingEmission(startRule.g, currentToken, currentRuleName, startRule, stopRule, path);
                addPendingEmission(pe);
                break;
            }
        };
    }

    BiConsumer<Changed, Triple<Rule, ATNState, ATNState>> astInfoCapturer(GrammarAST ast) {
//        System.out.println("\n*****************\nCAPTURE " + toS(ast, ast));
        return (Changed ch, Triple<Rule, ATNState, ATNState> tr) -> {
            if (ast instanceof PlusBlockAST) {
                // cannot capture empty string, skip
                return;
            }
            CommonTree par = ast;
            CommonTree p = ast;
            while (p != null) {
                p = (CommonTree) p.getParent();
//                System.out.println("TRY PARENT " + toS(ast, par));
                if (p instanceof AltAST) {
                    par = p;
                    break;
                }
                if (p instanceof RuleAST || p instanceof GrammarAST) {
//                    System.out.println("  encountered " + p.getClass().getSimpleName() + " - bail");
                    return;
                }
            }
            if (par == null) {
//                System.out.println("  backed out - bail");
                return;
            }

            // The method name here is inaccurate - it is not the start
            // offset of the token, it is the INDEX of the token in
            // the token stream
            int start = par.getTokenStartIndex();
            int end = ast.getTokenStopIndex();

            List<String> tokenParts = new ArrayList<>((end - start) + 1);

            int startOffset = -1;
            int endOffset = -1;
            int startLine = -1;
            int endLine = -1;
            int startOffsetInLine = -1;
            int endOffsetInLine = -1;
            for (int i = start; i <= end; i++) {
                org.antlr.runtime.Token tok = ast.g.originalTokenStream.get(i);
                if (startLine == -1) {
                    startLine = tok.getLine();
                    startOffsetInLine = tok.getCharPositionInLine();
                    startOffset = ((CommonToken) tok).getStartIndex();
                }
                endLine = tok.getLine();
                endOffset = ((CommonToken) tok).getStopIndex() + 1;
                String txt = tok.getText();
                int length;
                if (tok instanceof CommonToken) {
                    CommonToken ct = (CommonToken) tok;
                    length = (ct.getStopIndex() - ct.getStartIndex()) + 1;
                } else {
                    length = txt == null ? 0 : txt.length();
                }
                endOffsetInLine = tok.getCharPositionInLine() + length + 1;
//                System.out.println("TOK '" + txt + "' ");
                txt = txt.trim();
                if (!txt.isEmpty()) {
                    tokenParts.add(txt);
                }
            }
//            CommonTree t = ast;
//            while (t != null) {
//                t = (CommonTree) t.getParent();
//            }

            CommonTree curr = ast;
            while (!(curr instanceof RuleAST) && curr != null) {
                curr = (CommonTree) curr.getParent();
            }
            String ruleName = curr instanceof RuleAST
                    ? ((RuleAST) curr).getRuleName()
                    : "<unnamed>";

            ProblematicEbnfInfo info = new ProblematicEbnfInfo(startOffset,
                    endOffset, tokenParts,
                    startLine, startOffsetInLine, endLine, endOffsetInLine);
            problemInfos.get(ruleName).add(info);
//            System.out.println("**************end capture");
        };
    }
}
