/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.EnumSet;
import java.util.Set;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;

/**
 *
 * @author Tim Boudreau
 */
public enum EbnfProperty {
    STAR, QUESTION, PLUS;

    static Set<EbnfProperty> forSuffix(ANTLRv4Parser.EbnfSuffixContext ctx) {
        Set<EbnfProperty> result = EnumSet.noneOf(EbnfProperty.class);
        if (ctx.PLUS() != null) {
            result.add(PLUS);
        }
        if (ctx.STAR() != null) {
            result.add(STAR);
        }
        if (ctx.QUESTION() != null && ctx.QUESTION().size() > 0) {
            //                System.out.println("question; " + ctx.QUESTION());
            result.add(QUESTION);
        }
        return result;
    }

}
