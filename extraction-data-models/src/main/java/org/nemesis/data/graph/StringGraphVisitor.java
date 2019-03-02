package org.nemesis.data.graph;

/**
 * Visitor interface for traversing rule trees. The visitor will visit every
 * node of the graph in some order starting from the top level nodes, such that
 * every node is visited exactly once.
 * <p>
 * If a node has inbound edges from more than one other node, it is unspecified
 * which parent the visitor will be invoked as a child of.
 * </p>
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface StringGraphVisitor {

    /**
     * Enter a rule; subsequent calls to enterRule before a call to exitRule
     * means the subsequent rules are referenced by the previous one. EnterRule
     * will be called exactly once for each rule, starting from top-level and
     * orphan rules which have no antecedents. For rules referenced by
     * descendants, the order they are passed to enterRule is
     * implementation-depenedent - they will appear nested under some rule that
     * calls them, but not more than one.
     *
     * @param rule The node
     * @param depth The depth of this node in the tree, in the traversal pattern
     * being used.
     */
    void enterRule(String rule, int depth);

    /**
     * Called when the last child of a node has been visited.
     *
     * @param node The node
     * @param depth The depth of this node in the tree in the traversal pattern
     * being used
     */
    default void exitRule(String rule, int depth) {
    }

}
