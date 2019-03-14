package org.nemesis.data.graph;

import org.nemesis.misc.utils.function.IntBiConsumer;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import static java.lang.Math.sqrt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;
import static org.nemesis.data.graph.BitSetUtils.copyOf;
import static org.nemesis.data.graph.BitSetUtils.forEach;

/**
 * A highly compact representation of a tree of rules referencing other rules as
 * an array of BitSets, one per rule.
 *
 * @author Tim Boudreau
 */
public final class BitSetGraph {

    private final BitSet[] ruleReferences;
    private final BitSet[] referencedBy;
    private final BitSet topLevel;
    private final BitSet bottomLevel;

    public BitSetGraph(BitSet[] ruleReferences, BitSet[] referencedBy) {
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
        checkConsistency();
    }

    public BitSetGraph(BitSet[] references) {
        this(references, inverseOf(references));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(int expectedSize) {
        return new Builder(expectedSize);
    }

    public static final class Builder {

        private BitSet[] outboundEdges;
        private BitSet[] inboundEdges;
        private static final int INITIAL_SIZE = 32;
        private int greatestUsed = 0;

        Builder() {
            this(INITIAL_SIZE);
        }

        Builder(int initialSize) {
            outboundEdges = new BitSet[initialSize];
            inboundEdges = new BitSet[initialSize];
            for (int i = 0; i < initialSize; i++) {
                outboundEdges[i] = new BitSet(initialSize);
                inboundEdges[i] = new BitSet(initialSize);
            }
        }

        private void ensureSize(int newIndex) {
            if (newIndex > outboundEdges.length) {
                int newSize = (((newIndex / INITIAL_SIZE) + 1) * INITIAL_SIZE) + ((newIndex % INITIAL_SIZE) + 1) + INITIAL_SIZE;
                BitSet[] newOut = Arrays.copyOf(outboundEdges, newSize);
                BitSet[] newIn = Arrays.copyOf(inboundEdges, newSize);
                for (int i = greatestUsed; i < newSize; i++) {
                    assert newIn[i] == null : "Clobbering in " + i;
                    assert newOut[i] == null : "Clobbering out " + i;
                    newIn[i] = new BitSet(newSize);
                    newOut[i] = new BitSet(newSize);
                }
                outboundEdges = newOut;
                inboundEdges = newIn;
            }
            greatestUsed = Math.max(greatestUsed, newIndex);
        }

        public Builder addEdges(int[][] items) {
            for (int[] edge : items) {
                assert edge.length == 2 : "sub-array size must be 2 not " + edge.length;
                addEdge(edge[0], edge[1]);
            }
            return this;
        }

        public Builder addEdge(int a, int b) {
            ensureSize(Math.max(a, b) + 1);
            outboundEdges[a].set(b);
            inboundEdges[b].set(a);
            return this;
        }

        public BitSetGraph build() {
            BitSet[] outs = Arrays.copyOf(outboundEdges, greatestUsed);
            BitSet[] ins = Arrays.copyOf(inboundEdges, greatestUsed);
            return new BitSetGraph(outs, ins);
        }
    }

    public boolean containsEdge(int a, int b) {
        if (a > ruleReferences.length || b > ruleReferences.length || a < 0 || b < 0) {
            return false;
        }
        boolean result = ruleReferences[a].get(b);
        assert !result || referencedBy[b].get(a) : "State inconsistent for " + a + "," + b;
        return result;
    }

    void checkConsistency() {
        boolean asserts = false;
        assert asserts = true;
        assert ruleReferences.length == referencedBy.length : "Array sizes differ";
        if (asserts) {
            for (int i = 0; i < ruleReferences.length; i++) {
                BitSet set = ruleReferences[i];
                for (int bit = set.nextSetBit(0); bit >= 0; bit = set.nextSetBit(bit + 1)) {
                    BitSet opposite = referencedBy[bit];
                    assert opposite.get(i);
                }
            }
        }
    }

    public void save(ObjectOutput out) throws IOException {
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

    public static BitSetGraph load(ObjectInput in) throws IOException, ClassNotFoundException {
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
        return new BitSetGraph(sets);
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
                        + " does not have " + ix + " set - ruleReferences: " + bitSetArrayToString(ruleReferences)
                        + " referencedBy: " + bitSetArrayToString(referencedBy);
            });
        }
        return true;
    }

    private String bitSetArrayToString(BitSet[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            BitSet b = arr[i];
            if (b.isEmpty()) {
                sb.append(i).append(": empty\n");
            } else {
                sb.append(i).append(": ").append(b).append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("BitSetTree{size=")
                .append(ruleReferences.length)
                .append(", totalCardinality=").append(totalCardinality())
                .append("}\n");

        walk((id, d) -> {
            char[] c = new char[d * 2];
            Arrays.fill(c, ' ');
            sb.append(c).append(id).append('\n');
        });
        return sb.toString();
    }

    public void walk(BitSetGraphVisitor v) {
        BitSet traversed = new BitSet();
        walk(v, topLevel, traversed, 0);
        // The top level nodes computation will miss isolated paths with
        // cycles, so ensure we've covered everything
        for (int bit = traversed.nextClearBit(0); bit >= 0 && bit < size(); bit = traversed.nextClearBit(bit + 1)) {
            walk(v, ruleReferences[bit], traversed, 0);
        }
    }

    public void walk(int startingWith, BitSetGraphVisitor v) {
        BitSet set = new BitSet();
        set.set(startingWith);
        walk(v, set, new BitSet(), 0);
    }

    private void walk(BitSetGraphVisitor v, BitSet traverse, BitSet seen, int depth) {
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

    public void walkUpwards(BitSetGraphVisitor v) {
        BitSet traversed = new BitSet();
        walkUpwards(v, topLevel, traversed, 0);
        for (int bit = traversed.previousClearBit(size() - 1); bit >= 0 && bit < size(); bit = traversed.previousClearBit(bit - 1)) {
            if (bit >= size()) {
                break;
            }
            walk(v, referencedBy[bit], traversed, 0);
        }
    }

    public void walkUpwards(int startingWith, BitSetGraphVisitor v) {
        BitSet set = new BitSet();
        set.set(startingWith);
        walkUpwards(v, set, new BitSet(), 0);
    }

    private void walkUpwards(BitSetGraphVisitor v, BitSet traverse, BitSet seen, int depth) {
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

    public void edges(IntBiConsumer bi) {
        for (int i = 0; i < size(); i++) {
            BitSet refs = this.ruleReferences[i];
            for (int bit = refs.nextSetBit(0); bit >= 0; bit = refs.nextSetBit(bit + 1)) {
                bi.accept(i, bit);
            }
        }
    }

    public PairSet allEdges() {
        PairSet set = new PairSet(size());
        edges(set::add);
        return set;
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

    /**
     * Get the minimum distance between two nodes.
     *
     * @param a One node
     * @param b Another node
     * @return A distance
     */
    public int distance(int a, int b) {
        Optional<IntPath> path = shortestPathBetween(a, b);
        if (path.isPresent()) {
            return path.get().size();
        } else {
            path = shortestPathBetween(b, a);
            if (path.isPresent()) {
                return path.get().size();
            }
            return -1;
        }
    }

    /**
     * Get the set of those nodes which exist in the closure of only one of the
     * passed list of nodes.
     *
     * @param nodes An array of nodes
     * @return A set
     */
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

    /**
     * Get the set of those nodes which exist in the closure of only one of the
     * passed list of nodes.
     *
     * @param nodes A set of nodes
     * @return A set
     */
    public BitSet closureDisjunction(BitSet nodes) {
        BitSet result = new BitSet(size());
        for (int bit = nodes.nextSetBit(0), count = 0; bit >= 0; bit = nodes.nextSetBit(bit + 1), count++) {
            if (bit >= size()) {
                break;
            }
            BitSet clos = closureOf(bit);
            if (count == 0) {
                result.or(clos);
            } else {
                result.xor(clos);
            }
        }
        return result;
    }

    /**
     * Compute the eigenvector centrality of each node in the graph - an
     * importance measure that could loosely be phrased as <i>most likely to be
     * connected <b>through</b></i> - meaning, unlike page rank, this emphasizes
     * &quot;connector&quot; nodes rather than most-linked nodes - those nodes
     * which, if removed, would break the most paths in the graph.
     *
     * @param maxIterations The maxmum number of iterations to use refining the
     * answer before assuming an optimal answer has been determined
     * @param minDiff The minimum difference in values between iterations which,
     * when reached, indicates that an optimal answer has been determined even
     * if the algorithm has refined the answer for less than
     * <code>maxIterations</code>
     * @param inEdges If true, use inbound rather than outbound edges for the
     * calculation
     * @param ignoreSelfEdges If true, do not count a node's direct cycles to
     * themselves in the score
     * @param l2norm If set, attempt to normalize the result so scores will be
     * comparable across different graphs
     * @return An array of doubles the same size as the number of nodes in the
     * graph, where the value for each node (the array index) is the score
     */
    public double[] eigenvectorCentrality(int maxIterations, double minDiff,
            boolean inEdges, boolean ignoreSelfEdges, boolean l2norm) {
        int sz = size();
        double[] unnormalized = new double[sz];
        double[] centrality = new double[sz];
        Arrays.fill(centrality, 1.0 / (double) sz);
        double diff = 0.0;
        int iter = 0;
        do {
            for (int i = 0; i < sz; i++) {
                BitSet dests = inEdges ? referencedBy[i] : neighbors(i);
                double sum = BitSetUtils.sum(dests, centrality, ignoreSelfEdges ? i : Integer.MIN_VALUE);
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
        } while (iter++ < maxIterations && diff > minDiff);
        return centrality;
    }

    private double sum(DoubleIntFunction func) {
        double result = 0.0;
        for (int i = 0; i < size(); i++) {
            result += func.apply(i);
        }
        return result;
    }

    /**
     * Rank the nodes in this graph according to the page rank algorithm, which
     * detects most-linked-to nodes in the graph.
     *
     * @param minDifference The minimum difference after an iteration, at which
     * point the algorithm should bail out and return the answer
     * @param dampingFactor The damping factor
     * @param maximumIterations The maximum number of iterations before the
     * algorithm should assume it has computed an optimal answer and bail out
     * @param normalize If true, normalize the results so they are comparable
     * across calls to this method on different graphs
     * @return An array of doubles, where the index is the node id and the value
     * is the score
     */
    public double[] pageRank(double minDifference, double dampingFactor,
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
                double inputSum = BitSetUtils.sum(referencedBy[i], j -> {
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
        } while ((difference > minDifference) && cnt < maximumIterations);
        return result;
    }

    public boolean isReachableFrom(int a, int b) {
        return closureOf(a).get(b);
    }

    /**
     * Determine if the closure of a includes b.
     *
     * @param a A node
     * @param b Another node
     * @return If b is a descendant of a
     */
    public boolean isReverseReachableFrom(int a, int b) {
        return closureOf(b).get(a);
    }

    /**
     * Get the set of parent and child nodes for a node.
     *
     * @param startingNode A node
     * @return A set of nodes
     */
    public BitSet neighbors(int startingNode) {
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

    /**
     * Get the set of nodes in the graph whose closure is not
     * shared by any other nodes.
     *
     * @return A set of nodes
     */
    public BitSet disjointItems() {
        BitSet[] unions = new BitSet[size()];
        for (int i = 0; i < unions.length; i++) {
            unions[i] = new BitSet(unions.length);
        }
        BitSet result = new BitSet(size());
        outer:
        for (int i = 0; i < unions.length; i++) {
            unions[i] = closureOf(i);
            unions[i].clear(i);
            for (int j = 0; j < unions.length; j++) {
                if (i != j) {
                    unions[i].andNot(unions[j]);
                }
                if (unions[i].cardinality() == 0) {
                }
            }
            if (!unions[i].isEmpty()) {
                result.set(i);
            }
        }
        return result;
    }

    /**
     * Determine if the passed node or a descendant of it has a cycle back to
     * itself.
     *
     * @param rule A node
     * @return True if any outbound path from this node directly or indirectly
     * recurses back to it
     */
    public boolean isRecursive(int rule) {
        return closureOf(rule).get(rule);
    }

    /**
     * Determine if a node is <i>indirectly recursive</i> - if the closure of it
     * contains a cycle back to it, not counting cycles to itself.
     *
     * @param rule A node
     * @return Whether or not it is indirectly recursive
     */
    public boolean isIndirectlyRecursive(int rule) {
        BitSet test = new BitSet(size());
        test.or(ruleReferences[rule]);
        test.clear(rule);
        closureOf(rule, test, 0);
        return test.get(rule);
    }

    /**
     * Return an array of all nodes in the graph, sorted by the size of their
     * closure (the number of distinct descendant nodes they have).
     *
     * @return A count of nodes
     */
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

    /**
     * Return an array of all nodes in the graph, sorted by the size of their
     * reverse closure (the number of distinct ancestor nodes they have).
     *
     * @return A count of nodes
     */
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

    /**
     * Determine if a direct outbound edge exits from one node to another.
     *
     * @param from The source node
     * @param to The destination node
     * @return True if the edge exists
     */
    public boolean hasOutboundEdge(int from, int to) {
        return ruleReferences[from].get(to);
    }

    /**
     * Determine if a direct inbound edge exits from one node to another.
     *
     * @param from The source node
     * @param to The destination node
     * @return True if the edge exists
     */
    public boolean hasInboundEdge(int from, int to) {
        return referencedBy[from].get(to);
    }

    /**
     * Returns the number of inbound edges a node has.
     *
     * @param rule A node
     * @return The edge count
     */
    public int inboundReferenceCount(int rule) {
        return referencedBy[rule].cardinality();
    }

    /**
     * Returns the number of outbound edges a node has.
     *
     * @param rule A node
     * @return The edge count
     */
    public int outboundReferenceCount(int rule) {
        return ruleReferences[rule].cardinality();
    }

    /**
     * Get the set of nodes which have inbound edges from the passed node.
     *
     * @param rule A node
     * @return A set
     */
    public BitSet children(int rule) {
        return ruleReferences[rule];
    }

    /**
     * Get the set of nodes which reference the passed node.
     *
     * @param rule A node
     * @return Nodes which have an outbound edge to the passed one
     */
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

    /**
     * Get the set of nodes which have no inbound edges - no ancestors.
     *
     * @return A set
     */
    public BitSet topLevelOrOrphanRules() {
        return topLevel;
    }

    /**
     * Get the set of nodes which have no outbound edges.
     *
     * @return The set of nodes which have no outbound edges
     */
    public BitSet bottomLevelRules() {
        return bottomLevel;
    }

    /**
     * Determine if a node has no inbound edges - no ancestors.
     *
     * @param rule The node
     * @return true if the passed node is not referenced by any other nodes in
     * this graph
     */
    public boolean isUnreferenced(int rule) {
        return referencedBy[rule].isEmpty();
    }

    /**
     * Get the count of nodes which have the passed node as an ancestor.
     *
     * @param rule A node
     * @return The number of nodes which have the passed node as an ancestor
     */
    public int closureSize(int rule) {
        return closureOf(rule).cardinality();
    }

    /**
     * Get the count of nodes which have the passed node as a descendant.
     *
     * @param rule A node
     * @return The number of nodes which have the passed node as a descendant
     */
    public int reverseClosureSize(int rule) {
        return reverseClosureOf(rule).cardinality();
    }

    /**
     * Get the closure of this node - the set of all nodes which have this node
     * as an ancestor.
     *
     * @param rule A node
     * @return A set
     */
    public BitSet closureOf(int rule) {
        BitSet result = new BitSet();
        closureOf(rule, result, 0);
        return result;
    }

    /**
     * Get the shortest path between two nodes in the graph. If multiple paths
     * of the shortest length exist, one will be returned, but which is
     * unspecified.
     *
     * @param src The source node
     * @param target The target node
     * @return An optional which, if non-empty, contains a path for which no
     * shorter path between the same two nodes exists
     */
    public Optional<IntPath> shortestPathBetween(int src, int target) {
        Iterator<IntPath> iter = pathsBetween(src, target).iterator();
        return iter.hasNext() ? Optional.of(iter.next()) : Optional.empty();
    }

    /**
     * Get a list of all paths between the source and target node, sorted low to
     * high by length.
     *
     * @param src The source node
     * @param target The target node
     * @return A list of paths
     */
    public List<IntPath> pathsBetween(int src, int target) {
        List<IntPath> paths = new ArrayList<>();
        IntPath base = new IntPath().add(src);
        PairSet seenPairs = new PairSet(size());
        pathsTo(src, target, base, paths, seenPairs);
        Collections.sort(paths);
        return paths;
    }

    private void pathsTo(int src, int target, IntPath base, List<? super IntPath> paths, PairSet seenPairs) {
        if (src == target) {
            paths.add(base.copy().add(target));
            return;
        }
        BitSet refs = this.ruleReferences[src];
        for (int bit = refs.nextSetBit(0); bit >= 0; bit = refs.nextSetBit(bit + 1)) {
            if (seenPairs.contains(src, bit)) {
                continue;
            }
            if (bit == target) {
                IntPath found = base.copy().add(target);
                paths.add(found);
            } else {
                pathsTo(bit, target, base.copy().add(bit), paths, seenPairs);
            }
        }
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

    /**
     * Collect the reverse closure of a rule - the set of all nodes which have
     * an outbound edge to this one or an ancestor of those nodes.
     *
     * @param rule An element in the graph
     * @return A bit set
     */
    public BitSet reverseClosureOf(int rule) {
        BitSet result = new BitSet();
        reverseClosureOf(rule, result, 0);
        return result;
    }

    /**
     * Convert this graph to a PairSet, which internally uses a single BitSet of
     * size * size bits to represent the matrix of all edges. For sparse graphs,
     * this may be a larger data structure than the original graph.
     *
     * @return A PairSet
     */
    public PairSet toPairSet() {
        PairSet result = new PairSet(size());
        for (int i = 0; i < size(); i++) {
            BitSet refs = referencedBy[i];
            for (int bit = refs.nextSetBit(0); bit >= 0; bit = refs.nextSetBit(bit + 1)) {
                result.add(bit, i);
            }
        }
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

    public StringGraph toStringGraph(String[] ruleNames) {
        return new BitSetStringGraph(this, ruleNames);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Arrays.deepHashCode(this.ruleReferences);
        return hash;
    }

    /**
     * Get the combined cardinality of all nodes in this graph - the
     * total number of edges.
     *
     * @return The edge count
     */
    public int totalCardinality() {
        int result = 0;
        for (BitSet bs : this.referencedBy) {
            result += bs.cardinality();
        }
        return result;
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
        final BitSetGraph other = (BitSetGraph) obj;
        return Arrays.deepEquals(this.ruleReferences, other.ruleReferences);
    }
}
