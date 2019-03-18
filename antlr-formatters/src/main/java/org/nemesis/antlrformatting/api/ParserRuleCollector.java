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
