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
package org.nemesis.antlrformatting.api;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.data.SemanticRegions;

/**
 *
 * @author Tim Boudreau
 */
final class ParserRuleCollector {

    private final EverythingTokenStream stream;

    ParserRuleCollector(EverythingTokenStream stream) {
        this.stream = stream;
    }

    IntFunction<Set<Integer>> visit(RuleNode node) {
        V v = new V();
        node.accept(v);
        return new RulesFinder(v.bldr.build());
    }

    private static class RulesFinder implements IntFunction<Set<Integer>> {

        private final SemanticRegions<Integer> regions;

        RulesFinder(SemanticRegions<Integer> regions) {
            this.regions = regions;
        }

        @Override
        public Set<Integer> apply(int pos) {
            Set<Integer> vals = new HashSet<>();
            regions.keysAtPoint(pos, vals);
            return vals;
        }
    }

    final class V extends AbstractParseTreeVisitor<Void> {

        SemanticRegions.SemanticRegionsBuilder<Integer> bldr = SemanticRegions.builder(Integer.class);

        @Override
        public Void visitChildren(RuleNode node) {
            int ruleIndex = node.getRuleContext().getRuleIndex();
            Interval startAndEndTokens = node.getSourceInterval();

            ModalToken start = stream.get(startAndEndTokens.a);
            ModalToken end = stream.get(startAndEndTokens.b);
            int startOffset = start.getStartIndex();
            int endOffset = end.getStopIndex() + 1;
            if (startOffset < endOffset) {
                bldr.add(ruleIndex, startOffset, endOffset);
            }
            return super.visitChildren(node);
        }
    }
}
