/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary;

/**
 *
 * @author Tim Boudreau
 */
public interface RuleElement extends RuleComponent {

    int getStartOffset();

    int getEndOffset();

    String getRuleID();

    RuleElementKind kind();

}
