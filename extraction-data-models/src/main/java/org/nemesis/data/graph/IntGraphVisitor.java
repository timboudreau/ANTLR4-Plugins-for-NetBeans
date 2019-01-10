package org.nemesis.data.graph;

/**
 * Visitor interface for BitSetGraph, a graph which maps integer
 * nodes to other integer nodes.
 *
 * @author Tim Boudreau
 */
public interface IntGraphVisitor {

    void enterRule(int ruleId, int depth);

    default void exitRule(int ruleId, int depth) {
    }

}
