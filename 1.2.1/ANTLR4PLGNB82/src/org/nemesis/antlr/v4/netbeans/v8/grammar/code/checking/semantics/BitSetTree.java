package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import static java.lang.Math.sqrt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * A highly compact representation of a tree of rules referencing other rules as
 * an array of BitSets, one per rule.
 *
 * @author Tim Boudreau
 */
final class BitSetTree {

    private final BitSet[] ruleReferences;
    private final BitSet[] referencedBy;
    private final BitSet topLevel;
    private final BitSet bottomLevel;

    public BitSetTree(BitSet[] ruleReferences, BitSet[] referencedBy) {
        assert sanityCheck(ruleReferences, referencedBy);
        this.ruleReferences = ruleReferences;
        this.referencedBy = referencedBy;
        BitSet ruleReferencesKeys = keySet(ruleReferences);
        BitSet referencedByKeys = keySet(referencedBy);
        topLevel = new BitSet(ruleReferences.length);
        bottomLevel = new BitSet(referencedBy.length);
        topLevel.or(ruleReferencesKeys);
        bottomLevel.or(referencedByKeys);
        topLevel.andNot(referencedByKeys);
        bottomLevel.andNot(ruleReferencesKeys);
    }

    public BitSetTree(BitSet[] references) {
        this(references, inverseOf(references));
    }

    void save(ObjectOutput out) throws IOException {
        out.writeInt(1); // version
        out.writeInt(ruleReferences.length);
        for (int i = 0; i < ruleReferences.length; i++) {
            if (ruleReferences[i].cardinality() > 0) {
                out.writeObject(ruleReferences[i].toByteArray());
            } else {
                out.writeObject(null);
            }
        }
        out.flush();
    }

    static BitSetTree load(ObjectInput in) throws IOException, ClassNotFoundException {
        int ver = in.readInt();
        if (ver != 1) {
            throw new IOException("Unsupoorted version " + ver);
        }
        int count = in.readInt();
        BitSet[] sets = new BitSet[count];
        for (int i = 0; i < sets.length; i++) {
            byte[] vals = (byte[]) in.readObject();
            if (vals == null) {
                sets[i] = new BitSet(0);
            } else {
                sets[i] = BitSet.valueOf(vals);
            }
        }
        return new BitSetTree(sets);
    }

    private static BitSet[] inverseOf(BitSet[] ruleReferences) {
        int size = ruleReferences.length;
        BitSet empty = null;
        BitSet[] reverseUsages = new BitSet[size];
        for (int i = 0; i < size; i++) {
            if (ruleReferences[i] == null) {
                if (empty == null) {
                    empty = new BitSet(0);
                }
                ruleReferences[i] = empty;
            }
            for (int j = 0; j < size; j++) {
                BitSet b = ruleReferences[j];
                if (b != null) {
                    if (b.get(i)) {
                        if (reverseUsages[i] == null) {
                            reverseUsages[i] = new BitSet(size);
                        }
                        reverseUsages[i].set(j);
                    }
                }
            }
            if (reverseUsages[i] == null) {
                if (empty == null) {
                    empty = new BitSet(0);
                }
                reverseUsages[i] = empty;
            }
        }
        return reverseUsages;
    }

    private boolean sanityCheck(BitSet[] ruleReferences, BitSet[] referencedBy) {
        boolean asserts = false;
        assert asserts = true;
        if (!asserts) {
            return true;
        }
        assert ruleReferences.length == referencedBy.length : "BitSet array lengths do not match: "
                + ruleReferences.length + " and " + referencedBy.length;
        for (int i = 0; i < ruleReferences.length; i++) {
            int ix = i;
            forEach(ruleReferences[i], bit -> {
                BitSet reverse = referencedBy[bit];
                assert reverse.get(ix) : "ruleReferences[" + ix + "] says it "
                        + "references " + bit + " but referencedBy[" + bit + "]"
                        + " does not have " + ix + " set";
            });
        }
        return true;
    }

    interface IntRuleVisitor {

        void enterRule(int ruleId, int depth);

        void exitRule(int ruleId, int depth);
    }

    public String toString() {
        return "BitSetTree{size=" + ruleReferences.length + "}";
    }

    public void walk(IntRuleVisitor v) {
        walk(v, topLevel, new BitSet(), 0);
    }

    public void walk(int startingWith, IntRuleVisitor v) {
        BitSet set = new BitSet();
        set.set(startingWith);
        walk(v, set, new BitSet(), 0);
    }

    private void walk(IntRuleVisitor v, BitSet traverse, BitSet seen, int depth) {
        BitSet refs = traverse;
        for (int bit = refs.nextSetBit(0); bit >= 0; bit = refs.nextSetBit(bit + 1)) {
            if (!seen.get(bit)) {
                seen.set(bit);
                v.enterRule(bit, depth);
                walk(v, ruleReferences[bit], seen, depth + 1);
                v.exitRule(bit, depth);
            }
        }
    }

    public void walkUpwards(IntRuleVisitor v) {
        walkUpwards(v, topLevel, new BitSet(), 0);
    }

    public void walkUpwards(int startingWith, IntRuleVisitor v) {
        BitSet set = new BitSet();
        set.set(startingWith);
        walkUpwards(v, set, new BitSet(), 0);
    }

    private void walkUpwards(IntRuleVisitor v, BitSet traverse, BitSet seen, int depth) {
        BitSet refs = traverse;
        for (int bit = refs.nextSetBit(0); bit >= 0; bit = refs.nextSetBit(bit + 1)) {
            if (!seen.get(bit)) {
                seen.set(bit);
                v.enterRule(bit, depth);
                walk(v, referencedBy[bit], seen, depth + 1);
                v.exitRule(bit, depth);
            }
        }
    }

    public int size() {
        return ruleReferences.length;
    }

    public BitSet closureDisjunction(int a, int b) {
        if (a == b) {
            return new BitSet();
        }
        BitSet ca = closureOf(a);
        BitSet cb = closureOf(b);
        ca.xor(cb);
        return ca;
    }

    public BitSet closureUnion(int a, int b) {
        if (a == b) {
            return new BitSet();
        }
        BitSet ca = closureOf(a);
        BitSet cb = closureOf(b);
        ca.or(cb);
        return ca;
    }

    public int distance(int a, int b) {
        if (a == b) {
            return 0;
        }
        int down = distanceDown(a, b, Integer.MAX_VALUE, 1);
        if (down == 1) {
            return down;
        }
        int up = distanceUp(a, b, Integer.MAX_VALUE, 1);
        if (up == Integer.MAX_VALUE && down == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (up == down) {
            return up;
        }
        if (up < down) {
            return -up;
        } else {
            return down;
        }
    }

    private int distanceDown(int from, int to, int currResult, int depth) {
        BitSet refs = ruleReferences[to];
        for (int bit = refs.nextSetBit(0); bit >= 0; bit = refs.nextSetBit(bit + 1)) {
            if (bit == to) {
                return Math.min(currResult, depth);
            }
            int found = distanceDown(from, bit, currResult, depth + 1);
            if (found < currResult) {
                currResult = found;
            }
        }
        return currResult;
    }

    private int distanceUp(int from, int to, int currResult, int depth) {
        BitSet refs = ruleReferences[to];
        for (int bit = refs.nextSetBit(0); bit >= 0; bit = refs.nextSetBit(bit + 1)) {
            if (bit == to) {
                return Math.min(currResult, depth);
            }
            int found = distanceDown(from, bit, currResult, depth + 1);
            if (found < currResult) {
                currResult = found;
            }
        }
        return currResult;
    }

    public BitSet closureDisjunction(int... nodes) {
        BitSet result = new BitSet(size());
        for (int i = 0; i < nodes.length; i++) {
            BitSet clos = closureOf(nodes[i]);
            if (i == 0) {
                result.or(clos);
            } else {
                result.xor(clos);
            }
        }
        return result;
    }

    public double[] eigenvectorCentrality(int maxIterations, double maxDiff,
            boolean inEdges, boolean ignoreSelfEdges, boolean l2norm) {
        int sz = size();
        double[] unnormalized = new double[sz];
        double[] centrality = new double[sz];
        Arrays.fill(centrality, 1.0 / (double) sz);
        double diff = 0.0;
        int iter = 0;
        do {
            for (int i = 0; i < sz; i++) {
                BitSet dests = inEdges ? referencedBy[i] : reachableFrom(i);
                double sum = sum(dests, centrality, ignoreSelfEdges ? i : Integer.MIN_VALUE);
                unnormalized[i] = sum;
                double s;
                if (l2norm) {
                    double l2sum = 0.0;
                    for (int j = 0; j < sz; j++) {
                        l2sum += unnormalized[j] * unnormalized[j];
                    }
                    s = (l2sum == 0.0) ? 1.0 : 1 / sqrt(l2sum);
                } else {
                    double l1sum = 0.0;
                    for (int j = 0; j < sz; j++) {
                        l1sum += unnormalized[j];
                    }
                    s = l1sum == 0.0 ? 1.0 : 1 / l1sum;
                }
                diff = 0.0;
                for (int j = 0; j < sz; j++) {
                    double val = unnormalized[j] * s;
                    diff += Math.abs(centrality[j] - val);
                    centrality[j] = val;
                }
            }
        } while (iter++ < maxIterations && diff > maxDiff);
        return centrality;
    }

    private double sum(DoubleIntFunction func) {
        double result = 0.0;
        for (int i = 0; i < size(); i++) {
            result += func.apply(i);
        }
        return result;
    }

    public double[] pageRank(double maxError, double dampingFactor,
            int maximumIterations, boolean normalize) {
        double difference;
        int cnt = 0;
        double n = size();
        double[] result = new double[size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = 1d / n;
        }
        do {
            difference = 0.0;
            double danglingFactor = 0;
            if (normalize) {
                danglingFactor = dampingFactor / n * sum(i -> {
                    if (children(i).cardinality() == 0) {
                        return result[i];
                    }
                    return 0.0;
                });
            }
            for (int i = 0; i < size(); i++) {
                double inputSum = sum(referencedBy[i], j -> {
                    double outDegree = children(j).cardinality();
                    if (outDegree != 0) {
                        return result[j] / outDegree;
                    }
                    return 0.0;
                });
                double val = (1.0 - dampingFactor) / n
                        + dampingFactor * inputSum + danglingFactor;
                difference += Math.abs(val - result[i]);
                if (result[i] < val) {
                    result[i] = val;
                }
            }
            cnt++;
//            System.out.println("PR ITER " + cnt + " difference " + difference + " maxError " + maxError);
        } while ((difference > maxError) && cnt < maximumIterations);
        return result;
    }

    private double sum(BitSet set, DoubleIntFunction f) {
        double result = 0.0;
        for (int bit = set.nextSetBit(0); bit >= 0; bit = set.nextSetBit(bit + 1)) {
            result += f.apply(bit);
        }
        return result;
    }

    private double sum(BitSet set, double[] values, int ifNot) {
        double result = 0.0;
        for (int bit = set.nextSetBit(0); bit >= 0; bit = set.nextSetBit(bit + 1)) {
            if (bit != ifNot) {
                result += values[bit];
            }
        }
        return result;
    }

    private void forEach(BitSet set, IntConsumer cons) {
        for (int bit = set.nextSetBit(0); bit >= 0; bit = set.nextSetBit(bit + 1)) {
            cons.accept(bit);
        }
    }

    public BitSet reachableFrom(int startingNode) {
        BitSet result = copyOf(referencedBy[startingNode]);
        result.or(ruleReferences[startingNode]);
        return result;
    }

    public void depthFirstSearch(int startingNode, boolean up, IntConsumer cons) {
        depthFirstSearch(startingNode, up, cons, new BitSet());
    }

    public void breadthFirstSearch(int startingNode, boolean up, IntConsumer cons) {
        breadthFirstSearch(startingNode, up, cons, new BitSet());
    }

    private void breadthFirstSearch(int startingNode, boolean up, IntConsumer cons, BitSet traversed) {
        BitSet dests = up ? referencedBy[startingNode] : ruleReferences[startingNode];
        boolean any = false;
        for (int bit = dests.nextSetBit(0); bit >= 0; bit = dests.nextSetBit(bit + 1)) {
            if (!traversed.get(bit)) {
                cons.accept(bit);
                any = true;
            }
        }
        if (!any) {
            return;
        }
        for (int bit = dests.nextSetBit(0); bit >= 0; bit = dests.nextSetBit(bit + 1)) {
            if (!traversed.get(bit)) {
                breadthFirstSearch(bit, up, cons, traversed);
                traversed.set(bit);
            }
        }
    }

    private void depthFirstSearch(int startingNode, boolean up, IntConsumer cons, BitSet traversed) {
        BitSet dests = up ? referencedBy[startingNode] : ruleReferences[startingNode];
        boolean any = false;
        for (int bit = dests.nextSetBit(0); bit >= 0; bit = dests.nextSetBit(bit + 1)) {
            if (!traversed.get(bit)) {
                depthFirstSearch(bit, up, cons, traversed);
                any = true;
            }
        }
        if (!any) {
            return;
        }
        for (int bit = dests.nextSetBit(0); bit >= 0; bit = dests.nextSetBit(bit + 1)) {
            if (!traversed.get(bit)) {
                traversed.set(bit);
                cons.accept(bit);
            }
        }
    }

    private BitSet copyOf(BitSet set) {
        BitSet nue = new BitSet(size());
        nue.or(set);
        return nue;
    }

    public Set<Integer> disjointItems() {
        BitSet[] unions = new BitSet[size()];
        for (int i = 0; i < unions.length; i++) {
            unions[i] = new BitSet(unions.length);
        }
        Set<Integer> result = new HashSet<>();
        outer:
        for (int i = 0; i < unions.length; i++) {
            unions[i] = closureOf(i);
            unions[i].clear(i);
            for (int j = 0; j < unions.length; j++) {
                if (i != j) {
                    unions[i].andNot(unions[j]);
                }
                if (unions[i].cardinality() == 0) {
                    continue;
                }
            }
            if (!unions[i].isEmpty()) {
                result.add(i);
            }
        }
        return result;
    }

    public boolean isRecursive(int rule) {
        BitSet closure = closureOf(rule);
        return closure.get(rule);
    }

    public boolean isIndirectlyRecursive(int rule) {
        BitSet test = new BitSet(size());
        test.or(ruleReferences[rule]);
        test.clear(rule);
        closureOf(rule, test, 0);
        return test.get(rule);
    }

    public int[] byClosureSize() {
        Integer[] result = new Integer[ruleReferences.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = i;
        }
        int[] cache = new int[result.length];
        Arrays.sort(result, (a, b) -> {
            int sizeA = cache[a] == -1 ? cache[a] = closureSize(a) : cache[a];
            int sizeB = cache[b] == -1 ? cache[b] = closureSize(b) : cache[b];
            return sizeA == sizeB ? 0 : sizeA > sizeB ? 1 : -1;
        });
        int[] res = new int[result.length];
        for (int i = 0; i < res.length; i++) {
            res[i] = result[i];
        }
        return res;
    }

    public int[] byReverseClosureSize() {
        Integer[] result = new Integer[ruleReferences.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = i;
        }
        int[] cache = new int[ruleReferences.length];
        Arrays.fill(cache, -1);
        Arrays.sort(result, (a, b) -> {
            int sizeA = cache[a] == -1 ? cache[a] = reverseClosureSize(a) : cache[a];
            int sizeB = cache[b] == -1 ? cache[b] = reverseClosureSize(b) : cache[b];
            return sizeA == sizeB ? 0 : sizeA > sizeB ? 1 : -1;
        });
        int[] res = new int[result.length];
        for (int i = 0; i < res.length; i++) {
            res[i] = result[i];
        }
        return res;
    }

    public List<int[]> edges() {
        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < ruleReferences.length; i++) {
            BitSet refs = ruleReferences[i];
            for (int bit = refs.nextSetBit(0); bit >= 0; bit = refs.nextSetBit(bit + 1)) {
                result.add(new int[]{i, bit});
            }
        }
        return result;
    }

    public int inboundReferenceCount(int rule) {
        return referencedBy[rule].cardinality();
    }

    public int outboundReferenceCount(int rule) {
        return ruleReferences[rule].cardinality();
    }

    public BitSet children(int rule) {
        return ruleReferences[rule];
    }

    public BitSet parents(int rule) {
        return referencedBy[rule];
    }

    private static BitSet keySet(BitSet[] bits) {
        BitSet nue = new BitSet(bits.length);
        for (int i = 0; i < bits.length; i++) {
            if (bits[i].cardinality() > 0) {
                nue.set(i);
            }
        }
        return nue;
    }

    public BitSet topLevelOrOrphanRules() {
        return topLevel;
    }

    public BitSet bottomLevelRules() {
        return bottomLevel;
    }

    public boolean isUnreferenced(int rule) {
        return referencedBy[rule].isEmpty();
    }

    public int closureSize(int rule) {
        return closureOf(rule).cardinality();
    }

    public int reverseClosureSize(int rule) {
        return reverseClosureOf(rule).cardinality();
    }

    public BitSet closureOf(int rule) {
        BitSet result = new BitSet();
        closureOf(rule, result, 0);
        return result;
    }

    private void closureOf(int rule, BitSet into, int depth) {
        if (into.get(rule)) {
            return;
        }
        if (depth > 0) {
            into.set(rule);
        }
        BitSet refs = ruleReferences[rule];
        for (int bit = refs.nextSetBit(0); bit >= 0; bit = refs.nextSetBit(bit + 1)) {
            if (bit != rule /* && !into.get(bit) */) {
                closureOf(bit, into, depth + 1);
            }
            into.set(bit);
        }
    }

    public BitSet reverseClosureOf(int rule) {
        BitSet result = new BitSet();
        reverseClosureOf(rule, result, 0);
        return result;
    }

    private void reverseClosureOf(int rule, BitSet into, int depth) {
        if (into.get(rule)) {
            return;
        }
        if (depth > 0) {
            into.set(rule);
        }
        BitSet refs = referencedBy[rule];
        for (int bit = refs.nextSetBit(0); bit >= 0; bit = refs.nextSetBit(bit + 1)) {
            if (bit != rule /* && !into.get(bit) */) {
                reverseClosureOf(bit, into, depth + 1);
            }
            into.set(bit);
        }
    }

    public RuleTree strings(String[] ruleNames) {
        return new StringRuleTree(this, ruleNames);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Arrays.deepHashCode(this.ruleReferences);
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
        final BitSetTree other = (BitSetTree) obj;
        return Arrays.deepEquals(this.ruleReferences, other.ruleReferences);
    }
}
