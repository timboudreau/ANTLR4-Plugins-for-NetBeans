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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
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
final class PT implements Comparable<PT> {

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

    @Override
    public int compareTo(PT o) {
        return toString().compareTo(toString());
    }

    boolean isRuleTree() {
        return parseTree instanceof ParserRuleContext;
    }

    int altNumber() {
        if (isRuleTree()) {
            ParserRuleContext prc = (ParserRuleContext) parseTree;
            return prc.getAltNumber();
        }
        return -1;
    }

    int invokingState() {
        if (isRuleTree()) {
            ParserRuleContext prc = (ParserRuleContext) parseTree;
            return prc.invokingState;
        }
        return -1;
    }

    String text() {
        if (parseTree == null) {
            return stringValue;
        }
        return parseTree.getText();
    }

    public String toString() {
        return stringValue;
    }

    public String toString(boolean includeAlt) {
        if (includeAlt) {
            int an = altNumber();
            return an >= 0 ? stringValue + ":" + an + ":" + invokingState() : stringValue;

        }
        return toString();
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
        if (o == null || o.getClass() != PT.class) {
            return false;
        } else if (o == this) {
            return true;
        }
        PT other = (PT) o;
        boolean ha = isRuleTree();
        boolean oha = other.isRuleTree();
        if (!ha == oha) {
            return false;
        }
        if (ha) {
            return invokingState() == other.invokingState() && other.stringValue.equals(stringValue);
        } else {
            return other.stringValue.equals(stringValue);
        }
    }

    @Override
    public int hashCode() {
        return 31891 * stringValue.hashCode()
                + (altNumber() * 83269);
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
//        System.out.println(all.size() + " paths reduced to " + joined.size());
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
        allPaths(ctx, ruleNames, vocab, curr, into, startIndex, stopIndex, true, 0);
    }

    private static void allPaths(ParseTree ctx, String[] ruleNames, Vocabulary vocab, List<PT> curr, List<List<PT>> into, int startIndex, int stopIndex, boolean filter, int depth) {
        if (ctx instanceof ParserRuleContext) {
            ParserRuleContext prc = (ParserRuleContext) ctx;
            String ruleName = ruleNames[prc.getRuleIndex()];
            add(new PT(ruleName, ctx), curr);
        } else if (ctx instanceof TerminalNode) {
            TerminalNode tn = (TerminalNode) ctx;
            String tokName = vocab.getSymbolicName(tn.getSymbol().getType());
            add(new PT(tokName, ctx), curr);
        } else {
            add(new PT(ctx == null ? "null" : ctx.getClass().getSimpleName(), ctx), curr);
        }
        int ct = ctx == null ? 0 : ctx.getChildCount();
        switch (ct) {
            case 0:
                into.add(new ArrayList<>(curr));
                break;
            case 1:
                allPaths(ctx.getChild(0), ruleNames, vocab, curr, into, startIndex, stopIndex, filter, depth + 1);
                break;
            default:
                for (int i = 0; i < ct; i++) {
                    ParseTree ch = ctx.getChild(i);
                    if (filter && Interval.of(startIndex, stopIndex).properlyContains(ch.getSourceInterval())) {
                        allPaths(ch, ruleNames, vocab, new ArrayList<>(curr), into, startIndex, stopIndex, true, depth + 1);
                    }
                    if (depth == 0 && into.isEmpty() && filter) {
                        allPaths(ctx, ruleNames, vocab, curr, into, startIndex, stopIndex, false, depth + 1);
                    }
                }
        }
        if (depth == 0 && into.isEmpty() && filter) {
            allPaths(ctx, ruleNames, vocab, curr, into, startIndex, stopIndex, false, depth);
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

    private static void subsplit(List<List<PT>> paths, Consumer<List<List<PT>>> c) {
        List<List<PT>> set = new ArrayList<>(new HashSet<>(paths));
        if (set.size() == 1) {
            c.accept(new ArrayList<>(set));
            return;
        }
        List<List<PT>> heads = new ArrayList<>(paths.size());
        int max = 0;
        for (List<PT> l : paths) {
            max = Math.max(l.size(), max);
            heads.add(new ArrayList<>(l.size()));
        }
        int headCount = paths.size();
        for (int i = 0; i < max; i++) {
            for (int j = 0; j < set.size(); j++) {
                List<PT> curr = set.get(j);
                List<PT> head = heads.get(j);
                if (i < curr.size()) {
                    head.add(curr.get(i));
                }
            }
            int newHeadCount = new HashSet<>(heads).size();
            if (i == 0 && newHeadCount == paths.size()) {
                c.accept(set);
                return;
            } else if (i == 0) {
                headCount = newHeadCount;
            } else if (newHeadCount > headCount) {
                Set<List<PT>> uniqueHeads = new HashSet<>();
                for (int j = 0; j < heads.size(); j++) {
                    List<PT> curr = set.get(j);
                    List<PT> head = heads.get(j);
                    if (i < curr.size()) {
                        head.remove(head.size() - 1);
                    }
                    uniqueHeads.add(head);
                }
                System.out.println("HAVE " + uniqueHeads.size() + " heads: " + uniqueHeads);
                if (uniqueHeads.size() > 1) {
                    for (List<PT> uh : uniqueHeads) {
                        List<List<PT>> nue = new LinkedList<>();
                        for (Iterator<List<PT>> it = set.iterator(); it.hasNext();) {
                            List<PT> curr = it.next();
                            if (startsWith(uh, curr)) {
                                nue.add(curr);
                                it.remove();
                            }
                        }
                        if (!nue.isEmpty()) {
                            c.accept(nue);
                        }
                    }
                    return;
                }
            }
        }
        c.accept(set);
    }

    private static <T> boolean startsWith(List<T> head, List<T> list) {
        if (head.isEmpty()) {
            return true;
        }
        if (list.isEmpty()) {
            return false;
        }
        if (list.size() < head.size()) {
            return false;
        }
        for (int i = 0; i < head.size(); i++) {
            T a = head.get(i);
            T b = list.get(i);
            if (!Objects.equals(a, b)) {
                return false;
            }
        }
        return true;
    }

    public static void commonalities(List<List<PT>> paths, TriConsumer<List<PT>, List<List<PT>>, List<PT>> c) {
        subsplit(paths, subpaths -> doCommonalities(subpaths, c));
    }

    private static void doCommonalities(List<List<PT>> paths, TriConsumer<List<PT>, List<List<PT>>, List<PT>> c) {
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
                add(paths.get(0).get(i), commonHead);
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
                if (commonTail.isEmpty() || !(curr.equals(commonTail.get(commonTail.size() - 1)))) {
                    commonTail.add(0, curr);
                }
            } else {
                break;
            }
        }
        Set<List<PT>> differences = new HashSet<>();
        int start = commonHead.size();
        for (List<PT> each : paths) {
            int end = each.size() - commonTail.size();
            if (end < start) {
                end = each.size();
            }
            List<PT> sub = each.subList(start, end);
            differences.add(sub);
        }
        c.accept(commonHead, new ArrayList<>(differences), commonTail);
    }

    private static void add(PT pt, List<PT> list) {
        if (!list.isEmpty() && pt.equals(list.get(list.size() - 1))) {
            return;
        }
        list.add(pt);
    }
    /*
    static ObjectGraph<PT> graph(List<List<PT>> pts) {
        SortedSet<PT> all = new TreeSet<>();
        for (List<PT> l : pts) {
            all.addAll(l);
        }
        List<PT> sorted = new ArrayList<>(all);
        IntGraphBuilder igb = IntGraph.builder(sorted.size());
        for (List<PT> l : pts) {
            for (int i = 1; i < l.size(); i++) {
                PT prev = l.get(i - 1);
                PT curr = l.get(i);
                igb.addEdge(sorted.indexOf(prev), sorted.indexOf(curr));
            }
        }
        return igb.build().toObjectGraph(sorted);
    }

    public static void commonalities2(List<List<PT>> paths, TriConsumer<List<PT>, List<List<PT>>, List<PT>> c) {
        Set<PT> all = new TreeSet<>();
        int maxLen = 0;
        for (List<PT> l : paths) {
            all.addAll(l);
            maxLen = Math.max(maxLen, l.size());
        }
        PT[] sorted = all.toArray(new PT[all.size()]);
        int[][] grid = new int[paths.size()][maxLen * 2];
        IntIntMap rowEnds = IntIntMap.create(all.size());

        for (int i = 0; i < paths.size(); i++) {
            List<PT> l = paths.get(i);
            int[] row = grid[i];
            Arrays.fill(row, -1);
            for (int j = 0; j < l.size(); j++) {
                PT pt = l.get(j);
                int ix = Arrays.binarySearch(sorted, pt);
                row[j] = ix;
            }
            rowEnds.put(i, l.size());
        }
    }

    private static void align(IntIntMap endOfRow, int[][] grid) {
        int max = max(endOfRow);
        for (int i = 0; i < grid.length; i++) {
            int len = endOfRow.get(i);
            for (int j = 0; j < grid.length; j++) {
                if (i == j) {
                    continue;
                }
                
            }
        }
    }

    private static int max(IntIntMap map) {
        int max = 0;
        for (int i = 0; i < map.size(); i++) {
            max = Math.max(max, map.valueAt(i));
        }
        return max;
    }

    private int indexInRow(int from, int[] within, int to, int of) {
        for (int i = from; i < to; i++) {
            int val = within[i];
            if (val == of) {
                return i;
            }
        }
        return -1;
    }

    private static void shiftRight(int[] row, int from, int to) {
        if (from == to) {
            return;
        }
        int len = to - from;
        System.arraycopy(row, from, row, to, len);
        for (int i = from; i < to; i++) {
            row[i] = -1;
        }
    }

    private static void shiftLeft(int[] row, int from, int to) {
        if (from == to) {
            return;
        }
        int len = from - to;
        System.arraycopy(row, from, row, to, len);
        Arrays.fill(row, to, to + len, -1);

    }
//    public static void commonalities2(List<List<PT>> paths, TriConsumer<List<PT>, List<List<PT>>, List<PT>> c) {
//        ObjectGraph<PT> graph = graph(paths);
//        Set<PT> bottoms = new TreeSet<>(graph.bottomLevelNodes());
//        Map<PT, Set<PT>> reverseClosures = new HashMap<>();
//        int maxClosure = 0;
//        for (PT pt : bottoms) {
//            Set<PT> reverseClosure = graph.reverseClosureOf(pt);
//            maxClosure = Math.max(reverseClosure.size(), maxClosure);
//            reverseClosures.put(pt, reverseClosure);
//        }
//        List<PT> bottomList = new ArrayList<>(bottoms);
//        int[][] positions = new int[bottoms.size()][maxClosure * 2];
//        for (int i = 0; i < bottomList.size(); i++) {
//            int[] posForBottom = positions[i];
//            Arrays.fill(posForBottom, -1);
//            PT pt = bottomList.get(i);
//
//        }
//    }

    private static void populateWithParents() {


}*/
}
