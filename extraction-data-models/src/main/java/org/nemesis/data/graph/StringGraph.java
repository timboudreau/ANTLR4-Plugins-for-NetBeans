package org.nemesis.data.graph;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Set;
import static org.nemesis.data.graph.BitSetStringGraph.sanityCheckArray;

/**
 *
 * @author Tim Boudreau
 */
public interface StringGraph {

    /**
     * Get a list of the nodes in the graph sorted by the size of their closure.
     *
     * @return A list of string node names
     */
    List<String> byClosureSize();

    /**
     * Get a list of the nodes in the graph sorted by the size of their reverse
     * closure (all paths to the top of the graph from this node).
     *
     * @return A list of string node names
     */
    List<String> byReverseClosureSize();

    /**
     * For logging convenience, get a list of strings that identify the edges
     * present in this graph.
     *
     * @return
     */
    Set<String> edgeStrings();

    /**
     * Get the set of strings which are immediate parent nodes to a given one
     * (if the graph is cyclic, this may include the node name passed).
     *
     * @param rule A node name
     * @return The set of parents
     */
    Set<String> parents(String rule);

    /**
     * Get the set of string node names which are immediate child nodes of a
     * given one (if the graph is cyclic, this may include the node name
     * passed).
     *
     *
     * @param rule The node name
     * @return A set of child node names
     */
    Set<String> children(String rule);

    /**
     * Count the number of nodes that have outbound edges to the passed node.
     * Will be zero if it has none.
     *
     * @param rule The node name
     * @return A count of edges
     */
    int inboundReferenceCount(String rule);

    /**
     * Count the number of nodes this node has outbound edges to.
     *
     * @param rule The node name
     * @return A count of edges
     */
    int outboundReferenceCount(String rule);

    /**
     * Get the set of nodes which have no inbound edges.
     *
     * @return A set of node names
     */
    Set<String> topLevelOrOrphanRules();

    /**
     * Get the set of nodes which have no outbound edges.
     *
     * @return A set of nodes
     */
    Set<String> bottomLevelRules();

    /**
     * Returns true if this node has no inbound edges.
     *
     * @param rule A node
     * @return Whether or not it has inbound edges
     */
    boolean isUnreferenced(String rule);

    /**
     * Determine the size of the closure of this node, following outbound edges
     * to the bottom of the graph, traversing each node once.
     *
     * @param rule A node
     * @return The size of its closure
     */
    int closureSize(String rule);

    /**
     * Determine the size of the inverse closure of this node, following inbound
     * edges to the top of the graph, traversing each node once.
     *
     * @param rule
     * @return
     */
    int reverseClosureSize(String rule);

    /**
     * Get the reverse closure of a node in the graph - all nodes which have an
     * outbound edge to this node, and all nodes which have an outbound edge to
     * one of those, and so forth, to the top of the graph.
     *
     * @param rule A node name
     * @return A set of nodes
     */
    Set<String> reverseClosureOf(String rule);

    /**
     * Get the closure of this node in the graph - all nodes which are reachable
     * following outbound edges from this node and its descendants.
     *
     * @param rule A node name
     * @return A set of nodes
     */
    Set<String> closureOf(String rule);

    // Original implementation does not support these and is
    // kept for testing changes to this one, so provide default
    // implementations of these methods
    /**
     * Walk the tree of rules in some order, such that each rule is only visited
     * once.
     *
     * @param v A visitor
     */
    default void walk(StringGraphVisitor v) {
        throw new UnsupportedOperationException();
    }

    /**
     * Walk the tree of rule definitions and rule references in some order,
     * starting from the passed starting rule.
     *
     * @param startingWith The starting rule
     * @param v A visitor
     */
    default void walk(String startingWith, StringGraphVisitor v) {
        throw new UnsupportedOperationException();
    }

    /**
     * Walk the antecedents of a rule.
     *
     * @param startingWith The starting rule
     * @param v A visitor
     */
    default void walkUpwards(String startingWith, StringGraphVisitor v) {
        throw new UnsupportedOperationException();
    }

    /**
     * Get the distance along the shortest path between two rule.
     *
     * @param a One rule
     * @param b Another rule
     * @return the distance
     */
    default int distance(String a, String b) {
        throw new UnsupportedOperationException();
    }

    /**
     * Compute the eigenvector centrality - "most likely to be connected
     * *through*" - score for each rule. This finds nodes which are most
     * critical - connectors - in the rule graph.
     *
     * @return
     */
    default List<Score> eigenvectorCentrality() {
        throw new UnsupportedOperationException();
    }

    /**
     * Compute the pagerank score of every node in the graph.
     *
     * @return
     */
    default List<Score> pageRank() {
        throw new UnsupportedOperationException();
    }

    /**
     * This requires some explaining - it is used to pick the initial set of
     * colorings that are active - attempting to find rules that are likely to
     * be ones someone would want flagged.
     *
     * The algorithm is this: Rank the nodes according to their pagerank. That
     * typically gets important but common grammar elements such as "expression"
     * or "arguments". Take the top third of those nodes - these will be the
     * most connected to nodes. Then take the closure of each of those - the set
     * of rules it or rules it calls touches, and xor them to get the
     * disjunction of their closures. That gives you those rules which are
     * called indirectly or directly by *one* of the highly ranked nodes, but
     * none of the others. These have a decent chance of being unique things one
     * would like distinct syntax highlighting for.
     *
     * @return
     */
    default Set<String> disjunctionOfClosureOfHighestRankedNodes() {
        throw new UnsupportedOperationException();
    }

    /**
     * Create a graph from the passed bit set graph, and a pre-sorted array of
     * unique strings where integer nodes in the passed graph correspond to
     * offsets within the array. The array must be sorted, not have duplicates,
     * and have a matching number of elements for the unique node ids in the
     * tree. If assertions are on, asserts will check that these invariants
     * hold.
     *
     * @param graph A graph
     * @param sortedArray An array of strings matching the requirements stated
     * above
     * @return A graph of strings which wraps the original graph
     */
    public static StringGraph create(BitSetGraph graph, String[] sortedArray) {
        assert sanityCheckArray(sortedArray);
        return new BitSetStringGraph(graph, sortedArray);
    }

    /**
     * Optimized serialization support.
     *
     * @param out The output
     * @throws IOException If something goes wrong
     */
    public void save(ObjectOutput out) throws IOException;

    public static StringGraph load(ObjectInput in) throws IOException, ClassNotFoundException {
        return BitSetStringGraph.load(in);
    }

    /**
     * Implementation over strings of the scores as returned by page rank and
     * eigenvector centrality algorithms.
     */
    interface Score extends Comparable<Score> {

        /**
         * The name of graph node
         *
         * @return The name
         */
        public String node();

        /**
         * The score of the graph node, relative to others.
         *
         * @return A score
         */
        public double score();

        /**
         * The integer index of the node in the underlying graph.
         *
         * @return
         */
        public int ruleIndex();

        /**
         * Compares scores, sorting <i>higher</i> scores to the top.
         *
         * @param o Another score
         * @return a comparison
         */
        @Override
        public default int compareTo(Score o) {
            if (o == this) {
                return 0;
            }
            double a = score();
            double b = o.score();
            return a > b ? -1 : a == b ? 0 : 1;
        }
    }
}
