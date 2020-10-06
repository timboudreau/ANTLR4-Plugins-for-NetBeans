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
package org.nemesis.antlr.live.language.ambig;

import com.mastfrog.function.TriConsumer;
import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Wraps a parse tree with a stringified version of that tree, for generating
 * tree diffs without relying on the tree's equals method.
 */
final class PT {

    private final String stringValue;
    private final ParseTree parseTree;

    PT(String stringValue, ParseTree parseTree) {
        this.stringValue = stringValue;
        this.parseTree = parseTree;
    }

    static PT of(String s) {
        return new PT(s, null);
    }

    static PT noTree() {
        return of("-");
    }

    public ParseTree tree() {
        return parseTree;
    }

    public String toString() {
        return stringValue;
    }

    public String treeText() {
        return parseTree.getText();
    }

    public boolean isTerminal() {
        return parseTree instanceof TerminalNode;
    }

    public int ruleIndex() {
        return parseTree instanceof ParserRuleContext ? ((ParserRuleContext) parseTree).getRuleIndex()
                : -1;
    }

    public int lastTokenType() {
        if (parseTree instanceof TerminalNode) {
            TerminalNode tn = (TerminalNode) parseTree;
            return tn.getSymbol().getType();
        } else if (parseTree instanceof ParserRuleContext) {
            ParserRuleContext prc = (ParserRuleContext) parseTree;
            Token last = prc.getStop();
            if (last == null) {
                last = prc.getStart();
            }
            if (last != null) {
                return last.getType();
            }
        } else if (parseTree instanceof ErrorNode) {
            ErrorNode en = (ErrorNode) parseTree;
            Token t = en.getSymbol();
            return t == null ? -3 : t.getType();
        }
        return -4;
    }

    @Override
    public boolean equals(Object o) {
        return o == null ? false : o.getClass() == PT.class ? ((PT) o).stringValue.equals(stringValue) : false;
    }

    @Override
    public int hashCode() {
        return 31891 * stringValue.hashCode();
    }

    static Map<PT, List<List<PT>>> diffPaths(List<List<List<PT>>> all, AmbiguityAnalyzer.STV stv) {
        Set<List<PT>> joined = new HashSet<>();
        all.forEach(oneAltSets -> {
            System.out.println(" SETS A: " + oneAltSets);
            oneAltSets.forEach(subs -> {
                System.out.println("  SETS B " + subs);
                joined.add(subs);
            });
        });
//        for (int i = 0; i < all.size(); i++) {
//            List<List<PT>> oneAlternativesPaths = all.get(i);
//            for (int j = 0; j < oneAlternativesPaths.size(); j++) {
//                List<PT> items = oneAlternativesPaths.get(j);
//                joined.add(items);
//            }
//        }
        System.out.println(all.size() + " paths reduced to " + joined.size());
        Map<PT, List<List<PT>>> byTail = new HashMap<>();
        for (List<PT> ptl : joined) {
            List<List<PT>> l = byTail.computeIfAbsent(ptl.get(ptl.size() - 1), _ignored -> new ArrayList<>());
            l.add(ptl);
        }
        System.out.println("  by tail " + byTail.size());
        Set<PT> toRemove = new HashSet<>();
        for (Map.Entry<PT, List<List<PT>>> e : byTail.entrySet()) {
            if (e.getValue().size() <= 1) {
                toRemove.add(e.getKey());
            } else {
                stv.status("\n\nPaths to " + e.getKey(), -1, -1);
                System.out.println("PATHS TO " + e.getKey());
                for (List<PT> lp : e.getValue()) {
                    System.out.println(" * " + Strings.join(" -> ", lp));
                    stv.status("\n" + " * " + Strings.join(" -> ", lp), -1, -1);
                }
            }
        }
        System.out.println("  will remove " + toRemove);
        if (toRemove.size() < byTail.size()) {
            for (PT rem : toRemove) {
                byTail.remove(rem);
            }
        }
        return byTail;
    }

    public static List<List<PT>> allPaths(ParserRuleContext ctx, String[] ruleNames, Vocabulary vocab, int startIndex, int stopIndex) {
        List<List<PT>> result = new ArrayList<>();
        List<PT> curr = new ArrayList<>();
        allPaths(ctx, ruleNames, vocab, curr, result, startIndex, stopIndex);
        return result;
    }

    @SuppressWarnings("null")
    private static void allPaths(ParseTree ctx, String[] ruleNames, Vocabulary vocab, List<PT> curr, List<List<PT>> into, int startIndex, int stopIndex) {
        allPaths(ctx, ruleNames, vocab, curr, into, startIndex, stopIndex, true);
    }

    private static void allPaths(ParseTree ctx, String[] ruleNames, Vocabulary vocab, List<PT> curr, List<List<PT>> into, int startIndex, int stopIndex, boolean filter) {
        if (ctx instanceof ParserRuleContext) {
            ParserRuleContext prc = (ParserRuleContext) ctx;
            String ruleName = ruleNames[prc.getRuleIndex()];
            curr.add(new PT(ruleName, ctx));
        } else if (ctx instanceof TerminalNode) {
            TerminalNode tn = (TerminalNode) ctx;
            String tokName = vocab.getSymbolicName(tn.getSymbol().getType());
            curr.add(new PT(tokName, ctx));
        } else {
            curr.add(new PT(ctx == null ? "null" : ctx.getClass().getSimpleName(), ctx));
        }
        int ct = ctx == null ? 0 : ctx.getChildCount();
        switch (ct) {
            case 0:
                into.add(new ArrayList<>(curr));
                break;
            case 1:
                allPaths(ctx.getChild(0), ruleNames, vocab, curr, into, startIndex, stopIndex);
                break;
            default:
                for (int i = 0; i < ct; i++) {
                    ParseTree ch = ctx.getChild(i);
                    if (filter && Interval.of(startIndex, stopIndex).properlyContains(ch.getSourceInterval())) {
                        allPaths(ch, ruleNames, vocab, new ArrayList<>(curr), into, startIndex, stopIndex);
                    }
                    if (into.isEmpty() && filter) {
                        allPaths(ctx, ruleNames, vocab, curr, into, startIndex, stopIndex, false);
                    }
                }
        }
    }

    public static boolean hasCommonalities(List<List<PT>> paths) {
        if (paths.size() <= 1) {
            return false;
        }
        int minLength = Integer.MAX_VALUE;
        for (List<PT> l : paths) {
            minLength = Math.min(minLength, l.size());
        }
        if (minLength == 0) {
            return false;
        }
        boolean hasHeadOrTail = true;
        PT test = null;
        for (List<PT> p : paths) {
            if (test == null) {
                test = p.get(0);
            } else {
                if (!test.equals(p.get(0))) {
                    hasHeadOrTail = false;
                    break;
                }
            }
        }
        if (!hasHeadOrTail) {
            hasHeadOrTail = true;
            test = null;
            for (List<PT> p : paths) {
                PT pp = p.get(p.size() - 1);
                if (test == null) {
                    test = pp;
                } else if (!test.equals(pp)) {
                    hasHeadOrTail = false;
                    break;
                }
            }
        }
        return hasHeadOrTail;
    }

    public static void commonalities(List<List<PT>> paths, TriConsumer<List<PT>, List<List<PT>>, List<PT>> c) {
        List<PT> commonHead = new LinkedList<>();
        int minLength = Integer.MAX_VALUE;
        for (List<PT> l : paths) {
            minLength = Math.min(minLength, l.size());
        }
        for (int i = 0; i < minLength; i++) {
            boolean same = true;
            PT test = null;
            for (List<PT> each : paths) {
                if (test == null) {
                    test = each.get(i);
                } else {
                    if (!test.equals(each.get(i))) {
                        same = false;
                        break;
                    }
                }
            }
            if (same) {
                commonHead.add(paths.get(0).get(i));
            } else {
                break;
            }
        }

        List<PT> commonTail = new LinkedList<>();
        for (int i = 0; i < minLength; i++) {
            boolean same = true;
            PT curr = null;
            for (List<PT> each : paths) {
                int last = each.size() - (i + 1);
                PT test = each.get(last);
                if (curr == null) {
                    curr = test;
                } else {
                    if (!curr.equals(test)) {
                        same = false;
                        break;
                    }
                }
            }
            if (same) {
                commonTail.add(0, curr);
            } else {
                break;
            }
        }
        List<List<PT>> differences = new ArrayList<>();
        int start = commonHead.size();
        for (List<PT> each : paths) {
            int end = each.size() - commonTail.size();
            if (end < start) {
                end = each.size();
            }
            List<PT> sub = each.subList(start, end);
            differences.add(sub);
        }
        c.accept(commonHead, differences, commonTail);
    }
}
