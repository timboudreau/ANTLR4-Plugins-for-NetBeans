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
package org.nemesis.antlr.memory.alt;

import com.mastfrog.function.IntBiConsumer;
import com.mastfrog.util.collections.IntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.TokenStream;
import org.antlr.runtime.tree.Tree;
import org.antlr.v4.analysis.LeftRecursiveRuleAltInfo;
import org.antlr.v4.parse.ANTLRParser;
import org.antlr.v4.parse.GrammarTreeVisitor;
import org.antlr.v4.tool.Alternative;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.LabelElementPair;
import org.antlr.v4.tool.Rule;
import org.antlr.v4.tool.ast.AltAST;
import org.antlr.v4.tool.ast.GrammarAST;
import org.antlr.v4.tool.ast.GrammarRootAST;
import org.antlr.v4.tool.ast.TerminalAST;

/**
 * Extracts the Alternates and their offsets from a GrammarAST; used mainly for
 * testing that the results from analyzing a grammar with ANTLRv4Parser match
 * the alt indices that are passed to an ANTLRErrorListener's
 * <code>reportAmbiguities</code> method; needs to be public because the tests
 * of that code live in <code>antlr-live-language-editors</code>, and once
 * <code>AntlrGenerationResult</code> no longer exposes the <code>Grammar</code>
 * instance, it will be impossible to do from there. This class may be removed
 * at any time.
 * <p>
 * This little hellscape is because GrammarAST contains utter nonsense offsets
 * for some GrammarASTs such as left-recursive rules (which can have a start at
 * the beginning of the file even though they are within the file). So we have
 * to do a bunch of very fussy manipulation to massage it back into making some
 * kind of sense. Antlr's LeftRecursiveRuleRewriter supports left recursion by
 * injecting predicates into the AST, but the injected tokens just start
 * counting their index and position from zero, rather than adjusting the rest
 * of the tree contents and giving themselves correct offsets. What a mess.
 * </p>
 *
 * @author Tim Boudreau
 */
public final class AlternativesAnalyzer {

    private AlternativesAnalyzer() {
        throw new AssertionError();
    }

    private static String findLabel(Tree ast) {
        return scanForLabel(ast);
    }

    private static String scanForLabel(Tree tree) {
        if (tree instanceof GrammarAST) {
            GrammarAST g = (GrammarAST) tree;
            if (g.getAltLabel() != null) {
                return g.getAltLabel();
            }
        }
        String result = null;
        for (int i = 0; i < tree.getChildCount(); i++) {
            Tree child = tree.getChild(i);
            if (child instanceof AltAST) {
                continue;
            }
            result = scanForLabel(child);
            if (result != null) {
                break;
            }
        }
        return result;
    }

    static boolean scanForAlts(Tree ast, BiConsumer<Alternative, AltAST> c, BiConsumer<LeftRecursiveRuleAltInfo, AltAST> c1) {
        boolean foundAlts = false;
        for (int i = 0; i < ast.getChildCount(); i++) {
            Tree child = ast.getChild(i);
            foundAlts |= scanForAlts(child, c, c1);
        }
        if (!foundAlts && ast instanceof AltAST) {
            AltAST altAst = (AltAST) ast;
            if (altAst.alt != null) {
                c.accept(altAst.alt, altAst);
                return true;
            } else if (altAst.leftRecursiveAltInfo != null) {
                c1.accept(altAst.leftRecursiveAltInfo, altAst);
                return true;
            }
        }
        return foundAlts;
    }

    private static CommonToken scanForward(CommonToken from, TokenStream str) {
        // Try to gather up some tokens that should be part of the rule,
        // but which will have goofy offsets if we actually looked at the
        // syntax tree because they get rewritten by LeftRecursiveRuleRewriter
        for (; from.getType() != GrammarTreeVisitor.EOF;) {
            CommonToken next = (CommonToken) str.get(from.getTokenIndex() + 1);
            switch (next.getType()) {
                case GrammarTreeVisitor.RPAREN:
                case GrammarTreeVisitor.STAR:
                case GrammarTreeVisitor.QUESTION:
                case GrammarTreeVisitor.PLUS:
                case GrammarTreeVisitor.WS:
                    from = next;
                    break;
                default:
                    if (from.getType() == GrammarTreeVisitor.WS) {
                        from = (CommonToken) str.get(from.getTokenIndex() - 1);
                    }
                    return from;
            }
        }
        return from;
    }

    private static CommonToken scanBackward(CommonToken from, TokenStream str) {
        for (; from.getTokenIndex() > 0;) {
            CommonToken prev = (CommonToken) str.get(from.getTokenIndex() - 1);
            if ("(".equals(prev.getText())) {
                from = prev;
                continue;
            }
            switch (prev.getType()) {
                case GrammarTreeVisitor.LPAREN:
                case GrammarTreeVisitor.NOT:
                case GrammarTreeVisitor.WS:
                case GrammarTreeVisitor.EPSILON:
                case GrammarTreeVisitor.LABEL:
                    from = prev;
                    break;
                default:
                    // omit leading whitespace
                    if (from.getType() == GrammarTreeVisitor.WS) {
                        from = (CommonToken) str.get(from.getTokenIndex() + 1);
                    }
                    return from;
            }
        }
        return from;
    }

    private static boolean withAlternativeBounds(Alternative alt, AltAST altAst,
            TokenStream str, IntBiConsumer startStop) {

        int min = -1;
        int max = -1;

        if (alt.labelDefs != null) {
            for (Map.Entry<String, List<LabelElementPair>> e : alt.labelDefs.entrySet()) {
                for (LabelElementPair le : e.getValue()) {
                    CommonToken ct = (CommonToken) le.label.token;
                    CommonToken a = scanBackward(ct, str);
                    CommonToken b = scanForward(ct, str);
                    min = min == -1 ? ct.getStartIndex() : Math.min(a.getStartIndex(), min);
                    max = max == -1 ? ct.getStopIndex() : Math.max(b.getStopIndex(), max);
                }
            }
        }

        if (alt.ruleRefsInActions != null) {
            for (Map.Entry<String, List<GrammarAST>> e : alt.ruleRefsInActions.entrySet()) {
                for (GrammarAST gast : e.getValue()) {
                    int first = gast.getTokenStartIndex();
                    int last = gast.getTokenStopIndex();
                    CommonToken firstToken = (CommonToken) str.get(first);
                    CommonToken lastToken = first == last ? firstToken : (CommonToken) str.get(last);
                    firstToken = scanBackward(firstToken, str);
                    lastToken = scanBackward(lastToken, str);
                    min = min == -1 ? firstToken.getStartIndex() : Math.min(firstToken.getStartIndex(), min);
                    max = max == -1 ? lastToken.getStopIndex() : Math.max(lastToken.getStopIndex(), max);
                }
            }
        }
        if (alt.tokenRefsInActions != null) {
            for (Map.Entry<String, List<GrammarAST>> e : alt.tokenRefsInActions.entrySet()) {
                for (GrammarAST gast : e.getValue()) {
                    int first = gast.getTokenStartIndex();
                    int last = gast.getTokenStopIndex();
                    CommonToken firstToken = (CommonToken) str.get(first);
                    CommonToken lastToken = first == last ? firstToken : (CommonToken) str.get(last);
                    firstToken = scanBackward(firstToken, str);
                    lastToken = scanBackward(lastToken, str);
                    min = min == -1 ? firstToken.getStartIndex() : Math.min(firstToken.getStartIndex(), min);
                    max = max == -1 ? lastToken.getStopIndex() : Math.max(lastToken.getStopIndex(), max);
                }
            }
        }

        if (alt.tokenRefs != null) {
            for (Map.Entry<String, List<TerminalAST>> e : alt.tokenRefs.entrySet()) {
                for (TerminalAST te : e.getValue()) {
                    CommonToken ct = (CommonToken) te.token;
                    CommonToken a = scanBackward(ct, str);
                    CommonToken b = scanForward(ct, str);
                    min = min == -1 ? ct.getStartIndex() : Math.min(a.getStartIndex(), min);
                    max = max == -1 ? ct.getStopIndex() : Math.max(b.getStopIndex(), max);
                }
            }
        }
        if (alt.ruleRefs != null) {
            for (Map.Entry<String, List<GrammarAST>> e : alt.ruleRefs.entrySet()) {
                for (GrammarAST gast : e.getValue()) {
                    int first = gast.getTokenStartIndex();
                    int last = gast.getTokenStopIndex();
                    CommonToken firstToken = (CommonToken) str.get(first);
                    CommonToken lastToken = first == last ? firstToken : (CommonToken) str.get(last);
                    firstToken = scanBackward(firstToken, str);
                    lastToken = scanBackward(lastToken, str);
                    min = min == -1 ? firstToken.getStartIndex() : Math.min(firstToken.getStartIndex(), min);
                    max = max == -1 ? lastToken.getStopIndex() : Math.max(lastToken.getStopIndex(), max);
                }
            }
        }
        if (min != -1 && max != -1) {
            AltAST ast = altAst;
            if (ast.altLabel != null) {
                max = Math.max(max, ((CommonToken) ast.altLabel.token).getStopIndex());
            } else if (altAst.leftRecursiveAltInfo != null && altAst.leftRecursiveAltInfo.altLabel != null) {
                max = Math.max(max, ((CommonToken) altAst.leftRecursiveAltInfo.altAST.altLabel.token).getStopIndex());
            } else if (altAst.leftRecursiveAltInfo != null && altAst.leftRecursiveAltInfo.originalAltAST != null && altAst.leftRecursiveAltInfo.originalAltAST.altLabel != null) {
                max = Math.max(max, ((CommonToken) altAst.leftRecursiveAltInfo.originalAltAST.altLabel.token).getStopIndex());
            }
            startStop.accept(min, max);
            return true;
        }
        return false;
    }

    public static AlternativesInfo collectAlternativesOffsets(Grammar grammar) {
        assert grammar != null : "Null grammar";
        switch (grammar.getType()) {
            case ANTLRParser.PARSER:
            case ANTLRParser.COMBINED:
                break;
            default:
                return null;
        }

        GrammarRootAST rootAst = grammar.ast;
        assert rootAst != null : "Grammar has no ast";
        rootAst.freshenParentAndChildIndexesDeeply();
        int size = Math.max(16, grammar.rules.size() * 2);

        List<AltInfo> infos = new ArrayList<>(size);

        TokenStream str = rootAst.tokenStream;
        for (int i = 0; i < grammar.rules.size(); i++) {
            Rule rule = grammar.getRule(i);

            scanForAlts(rule.ast, (alt, altAst) -> {
                withAlternativeBounds(alt, altAst, str, (min, max) -> {
                    int altNum = alt.altNum;
                    String label = findLabel(altAst);
                    if (altAst.leftRecursiveAltInfo != null) {
                        if (label == null) {
                            label = altAst.leftRecursiveAltInfo.altLabel;
                        }
                        altNum = altAst.leftRecursiveAltInfo.altNum;
                    }
                    if (altNum == -1) {
                        return;
                    }
                    RuleAlt ruleAlt = new RuleAlt(altNum, label, rule.name);
                    infos.add(new AltInfo(ruleAlt, min, max + 1, label, altNum));
                });
            }, (LeftRecursiveRuleAltInfo lrai, AltAST ast) -> {
                int first = lrai.originalAltAST.getTokenStartIndex();
                int last = lrai.originalAltAST.getTokenStopIndex();

                if (ast.altLabel != null) {
                    last = Math.max(last, ((CommonToken) ast.altLabel.token).getTokenIndex());
                } else if (lrai.originalAltAST.altLabel != null) {
                    last = Math.max(last, ((CommonToken) lrai.originalAltAST.altLabel.token).getTokenIndex());
                }

                CommonToken firstToken = (CommonToken) str.get(first);
                firstToken = scanBackward(firstToken, str);
                CommonToken lastToken = first == last ? firstToken : (CommonToken) str.get(last);
                lastToken = scanForward(lastToken, str);
                int six = firstToken.getStartIndex();
                int eix = lastToken.getStopIndex();
                int altNum = lrai.altNum;
                String label = lrai.altLabel;
                RuleAlt ruleAlt = new RuleAlt(altNum, label, rule.name);
                infos.add(new AltInfo(ruleAlt, six, eix + 1, label, lrai.altNum));
            });
        }
        if (infos.isEmpty()) {
            return null;
        } else {
            // Sigh.  Antlr's tree rewrites also recorders the sequence in
            // which alternatives are encountered by the parser (but not necessarily
            // their alt indices), so we have to cache all of them and sort to
            // be able to add them in sorted (which is needed for constructing
            // a SemanticRegions).
            Collections.sort(infos);
            IntList starts = IntList.create(size);
            IntList ends = IntList.create(size);
            List<RuleAlt> altInfos = new ArrayList<>(size);
            for (AltInfo ai : infos) {
                starts.add(ai.start);
                ends.add(ai.end);
                altInfos.add(ai.ra);
            }
            return new AlternativesInfo(starts, ends, altInfos);

        }
    }

    static class AltInfo implements Comparable<AltInfo> {

        final RuleAlt ra;
        final int start;
        final int end;
        final String label;

        public AltInfo(RuleAlt ra, int start, int end, String label, int altNumber) {
            this.ra = ra;
            this.start = start;
            this.end = end;
            this.label = label;
        }

        @Override
        public int compareTo(AltInfo o) {
            int result;
            if (ra.rule.equals(o.ra.rule)) {
                result = Integer.compare(ra.altIndex, o.ra.altIndex);
            } else {
                result = Integer.compare(start, o.start);
                if (result == 0) {
                    result = -Integer.compare(end, o.end);
                }
            }
            return result;
        }

    }
}
