package org.nemesis.data.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.nemesis.data.graph.BitSetGraph;
import org.nemesis.data.graph.ScoreImpl;
import org.nemesis.data.graph.IntGraphVisitor;
import org.nemesis.data.graph.StringGraph;
import org.nemesis.data.graph.StringGraphVisitor;

/**
 *
 * @author Tim Boudreau
 */
final class StringRuleTree implements StringGraph {

    private final String[] namesSorted;
    private final int[] ruleIndicesForSorted;
    final BitSetGraph tree;
    final String[] ruleNames;

    StringRuleTree(BitSetGraph tree, String[] ruleNames) {
        this.tree = tree;
        this.ruleNames = ruleNames;
        namesSorted = Arrays.copyOf(ruleNames, ruleNames.length);
        Arrays.sort(namesSorted);
        List<String> orig = Arrays.asList(ruleNames);
        ruleIndicesForSorted = new int[ruleNames.length];
        for (int i = 0; i < namesSorted.length; i++) {
            ruleIndicesForSorted[i] = orig.indexOf(namesSorted[i]);
        }
    }

    public void walk(StringGraphVisitor v) {
        tree.walk(new IntGraphVisitor() {
            @Override
            public void enterRule(int ruleId, int depth) {
                v.enterRule(nameOf(ruleId), depth);
            }

            @Override
            public void exitRule(int ruleId, int depth) {
                v.exitRule(nameOf(ruleId), depth);
            }
        });
    }

    public void walk(String start, StringGraphVisitor v) {
        int ix = indexOf(start);
        if (ix < 0) {
            return;
        }
        tree.walk(ix, new IntGraphVisitor() {
            @Override
            public void enterRule(int ruleId, int depth) {
                v.enterRule(nameOf(ruleId), depth);
            }

            @Override
            public void exitRule(int ruleId, int depth) {
                v.exitRule(nameOf(ruleId), depth);
            }
        });
    }

    public void walkUpwards(String start, StringGraphVisitor v) {
        int ix = indexOf(start);
        if (ix < 0) {
            return;
        }
        tree.walkUpwards(ix, new IntGraphVisitor() {
            @Override
            public void enterRule(int ruleId, int depth) {
                v.enterRule(nameOf(ruleId), depth);
            }

            @Override
            public void exitRule(int ruleId, int depth) {
                v.exitRule(nameOf(ruleId), depth);
            }
        });
    }

    @Override
    public int distance(String a, String b) {
        int ixA = indexOf(a);
        int ixB = indexOf(b);
        if (ixA < 0 || ixB < 0) {
            return Integer.MAX_VALUE;
        }
        return tree.distance(ixA, ixB);
    }

    public Set<String> disjointItems() {
        Set<Integer> all = tree.disjointItems();
        Set<String> result = new HashSet<>();
        for (int a : all) {
            result.add(nameOf(a));
        }
        return result;
    }

    public Set<String> disjunctionOfClosureOfMostCentralNodes() {
        List<Score> centrality = eigenvectorCentrality();
        double sum = 0.0;
        for (int i = 0; i < centrality.size() / 2; i++) {
            sum += centrality.get(i).score();
        }
        double avg = sum / (double) (centrality.size() / 2);
        Set<String> result = new HashSet<>(centrality.size());
        centrality.stream().filter(s -> {
            return s.score() >= avg;
        }).forEach(s -> {
            result.add(s.node());
        });
        return result;
    }

    public Set<String> disjunctionOfClosureOfHighestRankedNodes() {
        List<Score> centrality = pageRank();
        double sum = 0.0;
        for (int i = 0; i < centrality.size() / 2; i++) {
            sum += centrality.get(i).score();
        }
        double avg = sum / (double) (centrality.size() / 2);
        Set<String> result = new HashSet<>(centrality.size());
        centrality.stream().filter(s -> {
            return s.score() >= avg;
        }).forEach(s -> {
            result.add(s.node());
        });
        return result;
    }

    @Override
    public List<Score> eigenvectorCentrality() {
        double[] centrality = tree.eigenvectorCentrality(400, 0.000001, false, true, true);
        List<Score> result = new ArrayList<>(centrality.length);
        for (int i = 0; i < centrality.length; i++) {
            result.add(new ScoreImpl(centrality[i], i, nameOf(i)));
        }
        Collections.sort(result);
        return result;
    }

    @Override
    public List<Score> pageRank() {
        double[] centrality = tree.pageRank(0.0000000000000004, 0.00000000001, 1000, true);
        List<Score> result = new ArrayList<>(centrality.length);
        for (int i = 0; i < centrality.length; i++) {
            result.add(new ScoreImpl(centrality[i], i, nameOf(i)));
        }
        Collections.sort(result);
        return result;
    }


    public List<String> byClosureSize() {
        int[] cs = tree.byClosureSize();
        List<String> result = new ArrayList<>(cs.length);
        for (int i = 0; i < cs.length; i++) {
            result.add(nameOf(cs[i]));
        }
        return result;
    }

    public List<String> byReverseClosureSize() {
        int[] cs = tree.byReverseClosureSize();
        List<String> result = new ArrayList<>(cs.length);
        for (int i = 0; i < cs.length; i++) {
            result.add(nameOf(cs[i]));
        }
        return result;
    }

    @Override
    public Set<String> parents(String rule) {
        int ix = indexOf(rule);
        if (ix == -1) {
            return Collections.emptySet();
        }
        return collect(tree.parents(ix));
    }

    @Override
    public Set<String> children(String rule) {
        int ix = indexOf(rule);
        if (ix == -1) {
            return Collections.emptySet();
        }
        return collect(tree.children(ix));
    }

    public Set<String> edgeStrings() {
        Set<String> result = new TreeSet<>();
        for (int[] pair : tree.edges()) {
            result.add(nameOf(pair[0]) + ":" + nameOf(pair[1]));
        }
        return result;
    }

    private int indexOf(String name) {
        int ix = Arrays.binarySearch(namesSorted, name);
        if (ix < 0) {
//            throw new IllegalArgumentException("Rule name '" + name + "' not in " + Arrays.toString(namesSorted));
            return -1;
        }
        return ruleIndicesForSorted[ix];
    }

    private String nameOf(int index) {
        return ruleNames[index];
    }

    private Set<String> collect(BitSet set) {
        int count = set.cardinality();
        if (count == 0) {
            return Collections.emptySet();
        }
        Set<String> into = new HashSet<>(count);
        collect(set, into);
        return into;
    }

    private void collect(BitSet set, Set<String> into) {
        for (int bit = set.nextSetBit(0); bit >= 0; bit = set.nextSetBit(bit + 1)) {
            into.add(nameOf(bit));
        }
    }

    @Override
    public int inboundReferenceCount(String rule) {
        int ix = indexOf(rule);
        return ix < 0 ? 0 : tree.inboundReferenceCount(ix);
    }

    @Override
    public int outboundReferenceCount(String rule) {
        int ix = indexOf(rule);
        return ix < 0 ? 0 : tree.outboundReferenceCount(ix);
    }

    @Override
    public Set<String> topLevelOrOrphanRules() {
        return collect(tree.topLevelOrOrphanRules());
    }

    @Override
    public Set<String> bottomLevelRules() {
        return collect(tree.bottomLevelRules());
    }

    @Override
    public boolean isUnreferenced(String rule) {
        int ix = indexOf(rule);
        return ix < 0 ? true : tree.isUnreferenced(ix);
    }

    @Override
    public int closureSize(String rule) {
        int ix = indexOf(rule);
        return ix < 0 ? 0 : tree.closureSize(ix);
    }

    @Override
    public int reverseClosureSize(String rule) {
        int ix = indexOf(rule);
        return ix < 0 ? 0 : tree.reverseClosureSize(ix);
    }

    @Override
    public Set<String> reverseClosureOf(String rule) {
        int ix = indexOf(rule);
        return ix < 0 ? Collections.emptySet() 
                : collect(tree.reverseClosureOf(ix));
    }

    @Override
    public Set<String> closureOf(String rule) {
        int ix = indexOf(rule);
        return ix < 0 ? Collections.emptySet() 
                : collect(tree.closureOf(ix));
    }

}
