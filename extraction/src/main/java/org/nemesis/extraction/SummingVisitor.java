/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.extraction;

import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 *
 * @author Tim Boudreau
 */
final class SummingVisitor extends AbstractParseTreeVisitor<Void> {

    private final SummingFunction summer;
    long sum = 0;
    private int ix = 0;
    private final IntRange<? extends IntRange<?>> constraint;

    public SummingVisitor(SummingFunction summer, int rangeStartConstraint, int rangeEndConstraint) {
        this.summer = summer;
        constraint = Range.of(rangeStartConstraint, rangeEndConstraint);
    }

    public SummingVisitor(SummingFunction summer) {
        this.summer = summer;
        constraint = null;
    }

    public long sum(ParserRuleContext ctx) {
        ctx.accept(this);
        return sum;
    }

    @Override
    public Void visitTerminal(TerminalNode node) {
        Token sym = node.getSymbol();
        if (sym != null && sym.getType() != -1 && sym.getStopIndex() >= sym.getStartIndex()) { // EOF
            if (constraint != null && constraint.contains(sym.getStartIndex())) {
                sum = summer.updateSum(sum, ix++, sym);
            } else if (constraint == null) {
                sum = summer.updateSum(sum, ix++, sym);
            }
        }
        return super.visitTerminal(node);
    }
}
