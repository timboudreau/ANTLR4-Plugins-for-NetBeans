package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.util.Set;
import java.util.function.BiConsumer;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.Hashable;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.RegionsKey;

/**
 *
 * @author Tim Boudreau
 */
final class RegionExtractionStrategies<RegionKeyType> implements Hashable {

    final RegionsKey<RegionKeyType> key;
    final Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors;

    RegionExtractionStrategies(RegionsKey<RegionKeyType> key, Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors) {
        this.key = key;
        this.extractors = extractors;
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.hashObject(key);
        for (RegionExtractionStrategy<?, ?, ?> e : extractors) {
            hasher.hashObject(e);
        }
    }

    ParseTreeVisitor<Void> createVisitor(BiConsumer<RegionKeyType, int[]> c) {
        return new V<RegionKeyType>(key.type(), c, extractors);
    }

    static class V<RegionKeyType> extends AbstractParseTreeVisitor<Void> {

        private final BiConsumer<RegionKeyType, int[]> c;
        private final RegionExtractionStrategy<?, ?, ?>[] extractors;
        private final int[] activatedCount;

        V(Class<RegionKeyType> keyType, BiConsumer<RegionKeyType, int[]> c, Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors) {
            this.c = c;
            this.extractors = extractors.toArray(new RegionExtractionStrategy<?, ?, ?>[extractors.size()]);
            this.activatedCount = new int[this.extractors.length];
            for (int i = 0; i < this.extractors.length; i++) {
                if (this.extractors[i].ancestorQualifier == null) {
                    activatedCount[i] = 1;
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Void visitChildren(RuleNode node) {
            boolean[] scratch = new boolean[extractors.length];
            for (int i = 0; i < scratch.length; i++) {
                RegionExtractionStrategy<RegionKeyType, ?, ?> e = (RegionExtractionStrategy<RegionKeyType, ?, ?>) extractors[i];
                if (e.ancestorQualifier != null) {
                    if (e.ancestorQualifier.test(node)) {
                        activatedCount[i]++;
                        scratch[i] = true;
                    }
                }
                if (activatedCount[i] > 0) {
                    runOne(node, e);
                }
            }
            super.visitChildren(node);
            for (int i = 0; i < scratch.length; i++) {
                if (scratch[i]) {
                    activatedCount[i]--;
                }
            }
            return null;
        }

        private <RuleType extends RuleNode, TType> void runOne(RuleNode node, RegionExtractionStrategy<RegionKeyType, RuleType, TType> e) {
            if (e.ruleType.isInstance(node)) {
                doRunOne(e.ruleType.cast(node), e);
            }
        }

        private <RuleType extends RuleNode, TType> void doRunOne(RuleType node, RegionExtractionStrategy<RegionKeyType, RuleType, TType> e) {
            e.extract(node, c);
        }
    }

}
