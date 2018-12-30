package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.graph;

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
