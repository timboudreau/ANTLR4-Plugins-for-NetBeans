package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.List;
import java.util.Set;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleVisitor;

/**
 *
 * @author Tim Boudreau
 */
public interface RuleTree {

    List<String> byClosureSize();

    List<String> byReverseClosureSize();

    Set<String> edgeStrings();

    Set<String> parents(String rule);

    Set<String> children(String rule);

    int inboundReferenceCount(String rule);

    int outboundReferenceCount(String rule);

    Set<String> topLevelOrOrphanRules();

    Set<String> bottomLevelRules();

    boolean isUnreferenced(String rule);

    int closureSize(String rule);

    int reverseClosureSize(String rule);

    Set<String> reverseClosureOf(String rule);

    Set<String> closureOf(String rule);

    // Original implementation does not support these and is
    // kept for testing changes to this one, so provide default
    // implementations of these methods

    /**
     * Walk the tree of rules in some order, such that each rule
     * is only visited once.
     * @param v A visitor
     */
    default void walk(RuleVisitor v) {
        throw new UnsupportedOperationException();
    }

    /**
     * Walk the tree of rule definitions and rule references in
     * some order, starting from the passed starting rule.
     *
     * @param startingWith The starting rule
     * @param v A visitor
     */
    default void walk(String startingWith, RuleVisitor v) {
        throw new UnsupportedOperationException();
    }

    /**
     * Walk the antecedents of a rule.
     *
     * @param startingWith The starting rule
     * @param v A visitor
     */
    default void walkUpwards(String startingWith, RuleVisitor v) {
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

    interface Score extends Comparable<Score> {

        public String node();

        public double score();

        public int ruleIndex();

        @Override
        public default int compareTo(Score o) {
            double a = score();
            double b = o.score();
            return a > b ? -1 : a == b ? 0 : 1;
        }
    }
}
