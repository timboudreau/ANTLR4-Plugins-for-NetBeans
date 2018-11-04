/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary;

import org.netbeans.modules.csl.api.OffsetRange;

/**
 *
 * @author Tim Boudreau
 */
public interface RuleComponent extends Comparable<RuleComponent> {

    public int getStartOffset();

    public int getEndOffset();

    default OffsetRange toOffsetRange() {
        return new OffsetRange(getStartOffset(), getEndOffset());
    }

    default boolean overlaps(int pos) {
        return getStartOffset() >= pos && getEndOffset() < pos;
    }

    default boolean contains(RuleElement el) {
        return el.getStartOffset() >= getStartOffset()
                && el.getEndOffset() <= getEndOffset();
    }

    @Override
    default int compareTo(RuleComponent o) {
        int a = getStartOffset();
        int b = o.getStartOffset();
        if (a == b) {
            a = getEndOffset();
            b = o.getEndOffset();
            return a > b ? 1 : a == b ? 0 : -1;
        }
        return a == b ? 0 : a > b ? 1 : -1;
    }
}
