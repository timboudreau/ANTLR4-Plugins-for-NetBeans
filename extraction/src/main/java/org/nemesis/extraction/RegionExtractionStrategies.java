package org.nemesis.extraction;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;
import org.nemesis.data.SemanticRegions;
import org.nemesis.extraction.key.RegionsKey;

/**
 *
 * @author Tim Boudreau
 */
final class RegionExtractionStrategies<RegionKeyType> implements Hashable {

    final RegionsKey<RegionKeyType> key;
    final Set<? extends RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors;
    final Set<? extends TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors;

    RegionExtractionStrategies(RegionsKey<RegionKeyType> key, Set<? extends RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors, Set<? extends TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors) {
        this.key = key;
        this.extractors = extractors;
        this.tokenExtractors = tokenExtractors;
        System.out.println("CREATE A REGION EXTRACTION STRATEGIES WITH " + tokenExtractors.size());
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeInt(190019943);
        key.hashInto(hasher);
        for (RegionExtractionStrategy<?, ?, ?> e : extractors) {
            hasher.hashObject(e);
        }
        if (tokenExtractors != null) {
            for (TokenRegionExtractionStrategy s : tokenExtractors) {
                hasher.hashObject(s);
            }
        }
    }

    boolean runTokenExtrationStrategies(Consumer<SemanticRegions<RegionKeyType>> tokenRegions, Supplier<? extends TokenStream> streamSupplier) {
        System.out.println("run token extractions");
        TokenStream stream = streamSupplier.get();
        int oldIndex = stream.index();
        if (tokenExtractors == null || tokenExtractors.isEmpty()) {
            System.out.println("no token extractors");
            return false;
        }
        SemanticRegions<RegionKeyType> result = null;
        for (TokenRegionExtractionStrategy<RegionKeyType> strategy : tokenExtractors) {
            System.out.println("run one strategy " + strategy);
            stream.seek(0);
            SemanticRegions<RegionKeyType> curr = strategy.scan(stream);
            System.out.println("scan got " + curr);
            if (result == null) {
                result = curr;
            } else if (!curr.isEmpty()) {
                SemanticRegions<RegionKeyType> nue = result.combineWith(curr);
                System.out.println("COMBINE " + result + "\nWITH:" + curr + "\nTO GET: " + nue);
                result = nue;
            }
        }
        if (result != null && !result.isEmpty()) {
            System.out.println("HAVE A RESULT - " + result);
            tokenRegions.accept(result);
        }
        stream.seek(Math.max(0, oldIndex));
        return result != null && !result.isEmpty();
    }

    ParseTreeVisitor<Void> createVisitor(BiConsumer<RegionKeyType, int[]> c) {
        return new V<>(key.type(), c, extractors);
    }

    static class V<RegionKeyType> extends AbstractParseTreeVisitor<Void> {

        private final BiConsumer<RegionKeyType, int[]> c;
        private final RegionExtractionStrategy<?, ?, ?>[] extractors;
        private final int[] activatedCount;

        V(Class<RegionKeyType> keyType, BiConsumer<RegionKeyType, int[]> c, Set<? extends RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors) {
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
