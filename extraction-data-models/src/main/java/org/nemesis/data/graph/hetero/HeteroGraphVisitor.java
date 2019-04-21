/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.data.graph.hetero;

/**
 *
 * @author Tim Boudreau
 */
public interface HeteroGraphVisitor<T, R> {

    void enterFirst(T ruleId, int depth);

    void enterSecond(R ruleId, int depth);

    default void exitFirst(T ruleId, int depth) {
    }

    default void exitSecond(R ruleId, int depth) {
    }

}
