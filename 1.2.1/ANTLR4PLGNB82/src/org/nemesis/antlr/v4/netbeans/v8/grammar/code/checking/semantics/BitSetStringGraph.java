/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.BitSetTree.IntRuleVisitor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleTree.Score;

/**
 *
 * @author Tim Boudreau
 */
public class BitSetStringGraph {

    private final BitSetTree tree;
    private final String[] items;

    BitSetStringGraph(BitSetTree tree, String[] sortedArray) {
        this.tree = tree;
        this.items = sortedArray;
    }

    Set<String> toSet(BitSet bits) {
        if (indexedImpl == null) {
            indexedImpl = new IndexedImpl();
        }
        return new BitSetSet<>(indexedImpl, bits);
    }

    Set<String> newSet() {
        return toSet(new BitSet(items.length));
    }

    private transient IndexedImpl indexedImpl;

    void save(ObjectOutput out) throws IOException {
        out.writeInt(1);
        out.writeObject(items);
        tree.save(out);
    }

    static BitSetStringGraph load(ObjectInput in) throws IOException, ClassNotFoundException {
        int v = in.readInt();
        if (v != 1) {
            throw new IOException("Unsupoorted version " + v);
        }
        String[] sortedArray = (String[]) in.readObject();
        BitSetTree tree = BitSetTree.load(in);
        return new BitSetStringGraph(tree, sortedArray);
    }

    class IndexedImpl implements Indexed<String> {

        List<String> list = Arrays.asList(items);

        @Override
        public int indexOf(Object o) {
            return list.indexOf(o);
        }

        @Override
        public String get(int index) {
            return nameOf(index);
        }

        @Override
        public int size() {
            return items.length;
        }
    }

    public void walk(RuleVisitor v) {
        tree.walk(new IntRuleVisitor() {
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

    public void walk(String start, RuleVisitor v) {
        int ix = indexOf(start);
        if (ix < 0) {
            return;
        }
        tree.walk(ix, new IntRuleVisitor() {
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

    public void walkUpwards(String start, RuleVisitor v) {
        int ix = indexOf(start);
        if (ix < 0) {
            return;
        }
        tree.walkUpwards(ix, new IntRuleVisitor() {
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

    public List<Score> eigenvectorCentrality() {
        double[] centrality = tree.eigenvectorCentrality(400, 0.000001, false, true, true);
        List<Score> result = new ArrayList<>(centrality.length);
        for (int i = 0; i < centrality.length; i++) {
            result.add(new ScoreImpl(centrality[i], i, nameOf(i)));
        }
        Collections.sort(result);
        return result;
    }

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

    public Set<String> parents(String node) {
        int ix = indexOf(node);
        if (ix == -1) {
            return Collections.emptySet();
        }
        return collect(tree.parents(ix));
    }

    public Set<String> children(String node) {
        int ix = indexOf(node);
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
        int ix = Arrays.binarySearch(items, name);
        if (ix < 0) {
            return -1;
        }
        return ix;
    }

    private String nameOf(int index) {
        return items[index];
    }

    private Set<String> collect(BitSet set) {
        int count = set.cardinality();
        if (count == 0) {
            return Collections.emptySet();
        }
        return toSet(set);
    }

    public int inboundReferenceCount(String node) {
        int ix = indexOf(node);
        return ix < 0 ? 0 : tree.inboundReferenceCount(ix);
    }

    public int outboundReferenceCount(String node) {
        int ix = indexOf(node);
        return ix < 0 ? 0 : tree.outboundReferenceCount(ix);
    }

    public Set<String> topLevelOrOrphanNodes() {
        return collect(tree.topLevelOrOrphanRules());
    }

    public Set<String> bottomLevelNodes() {
        return collect(tree.bottomLevelRules());
    }

    public boolean isUnreferenced(String node) {
        int ix = indexOf(node);
        return ix < 0 ? true : tree.isUnreferenced(ix);
    }

    public int closureSize(String node) {
        int ix = indexOf(node);
        return ix < 0 ? 0 : tree.closureSize(ix);
    }

    public int reverseClosureSize(String node) {
        int ix = indexOf(node);
        return ix < 0 ? 0 : tree.reverseClosureSize(ix);
    }

    public Set<String> reverseClosureOf(String node) {
        int ix = indexOf(node);
        return ix < 0 ? Collections.emptySet()
                : collect(tree.reverseClosureOf(ix));
    }

    public Set<String> closureOf(String node) {
        int ix = indexOf(node);
        return ix < 0 ? Collections.emptySet()
                : collect(tree.closureOf(ix));
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(tree.toString()).append(":");
        walk((String rule, int depth) -> {
            char[] c = new char[depth * 2];
            Arrays.fill(c, ' ');
            sb.append(c).append(rule).append('\n');
        });
        sb.append("Tops: ").append(tree.topLevelOrOrphanRules());
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.tree);
        hash = 79 * hash + Arrays.deepHashCode(this.items);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BitSetStringGraph other = (BitSetStringGraph) obj;
        if (!Objects.equals(this.tree, other.tree)) {
            return false;
        }
        if (!Arrays.deepEquals(this.items, other.items)) {
            return false;
        }
        return true;
    }
}
