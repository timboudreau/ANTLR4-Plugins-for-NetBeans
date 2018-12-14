package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticRegions.SemanticRegionsBuilder;

/**
 *
 * @author Tim Boudreau
 */
public class GenericExtractorBuilder<T extends ParserRuleContext> {

    private final Class<T> entryPoint;

    private static final AtomicInteger keyIds = new AtomicInteger();

    private final Set<RegionExtractorInfo<?,?>> regionsInfo = new HashSet<>();

    public GenericExtractorBuilder(Class<T> entryPoint) {
        this.entryPoint = entryPoint;
    }

    public GenericExtractor<T> build() {
        return new GenericExtractor<>(regionsInfo);
    }

    public static final class GenericExtractor<T extends ParserRuleContext> {

        private final Set<RegionExtractorInfo<?, ?>> extractors;

        GenericExtractor(Set<RegionExtractorInfo<?, ?>> extractors) {
            this.extractors = extractors;
        }

        public Extraction extract(T ruleNode) {
            Extraction extraction = new Extraction();
            for (RegionExtractorInfo<?, ?> r : extractors) {
                runOne(r, ruleNode, extraction);
            }
            return extraction;
        }

        private <R> void runOne(RegionExtractorInfo<?, R> info, T ruleNode, Extraction extraction) {
            ParseTreeVisitor<SemanticRegionsBuilder<R>> v = info.createVisitor();
            SemanticRegionsBuilder<R> b = ruleNode.accept(v);
            extraction.add(info.key, b.build());
        }
    }

    public static class Extraction {

        private final Map<RegionsKey<?>, SemanticRegions<?>> regions = new HashMap<>();

        <T> void add(RegionsKey<T> key, SemanticRegions<T> oneRegion) {
            regions.put(key, oneRegion);
        }

        @SuppressWarnings("unchecked")
        public <T> SemanticRegions<T> regions(RegionsKey<T> key) {
            SemanticRegions<?> result = regions.get(key);
            assert key.type.equals(result.keyType());
            return (SemanticRegions<T>) result;
        }
    }

    public <RuleType extends ParserRuleContext> RegionExtractionBuilder<T, RuleType, Void> extractRegionsFor(Class<RuleType> rt) {
        return new RegionExtractionBuilder<>(this, rt, Void.class);
    }

    public static class RegionExtractionBuilder<EntryPointType extends ParserRuleContext, RuleType extends ParserRuleContext, RegionKeyType> {

        private final Class<RuleType> targetRule;
        private final Class<RegionKeyType> regionKeys;
        private final Function<RuleType, RegionKeyType> extractor;
        private Predicate<RuleType> matcher;
        private GenericExtractorBuilder<EntryPointType> bldr;
        private final Set<Class<? extends ParserRuleContext>> whenIn = new HashSet<>();
        private String name;

        RegionExtractionBuilder(GenericExtractorBuilder<EntryPointType> bldr, Class<RuleType> targetRule, Class<RegionKeyType> regionKeys) {
            this.targetRule = targetRule;
            this.regionKeys = regionKeys;
            extractor = null;
            this.bldr = bldr;
        }

        RegionExtractionBuilder(RegionExtractionBuilder<EntryPointType, RuleType, ?> orig, Class<RegionKeyType> newRegionKeyType, Function<RuleType, RegionKeyType> extractor) {
            this.targetRule = orig.targetRule;
            this.regionKeys = newRegionKeyType;
            this.extractor = extractor;
            this.matcher = orig.matcher;
            this.bldr = orig.bldr;
            this.whenIn.addAll(orig.whenIn);
            this.name = orig.name;
        }

        public RegionExtractionBuilder<EntryPointType, RuleType, RegionKeyType> whenMatching(Predicate<RuleType> pred) {
            this.matcher = pred;
            return this;
        }

        public RegionExtractionBuilder<EntryPointType, RuleType, RegionKeyType> whenInsideRuleTypes(Class<? extends ParserRuleContext> type) {
            whenIn.add(type);
            return this;
        }

        public <T> RegionExtractionBuilder<EntryPointType, RuleType, T> derivingKey(Class<T> type, Function<RuleType, T> extractor) {
            return new RegionExtractionBuilder<>(this, type, extractor);
        }

        public RegionExtractionBuilder<EntryPointType, RuleType, RegionKeyType> named(String name) {
            this.name = name;
            return this;
        }

        public GenericExtractorBuilder<EntryPointType> build(Consumer<RegionsKey<RegionKeyType>> keyConsumer) {
            RegionsKey<RegionKeyType> key = new RegionsKey<>(regionKeys, name);
            keyConsumer.accept(key);
            RegionExtractorInfo<RuleType, RegionKeyType> info = new RegionExtractorInfo<>(key, targetRule, regionKeys, extractor, matcher, whenIn);
            bldr.regionsInfo.add(info);
            return bldr;
        }
    }

    static final class ExtractPredicate<T extends ParserRuleContext, R> {
        private final Class<T> ruleType;
        private final Function<T, R> keyDerivationFunction;

        public ExtractPredicate(Class<T> ruleType, Function<T, R> keyDerivationFunction) {
            this.ruleType = ruleType;
            this.keyDerivationFunction = keyDerivationFunction;
        }

        public Result<T> invoke(ParserRuleContext rule) {
            if (ruleType.isInstance(rule)) {
                return new Result(_invoke(ruleType.cast(rule)), true);
            }
            return null;
        }

        private R _invoke(T rule) {
            return keyDerivationFunction == null ? null : keyDerivationFunction.apply(rule);
        }

        static class Result<T> {
            private final boolean wasInvoked;
            private final T key;
            Result() {
                this(null, false);
            }

            Result(T key, boolean wasInvoked) {
                this.wasInvoked = wasInvoked;
                this.key = key;
            }
        }
    }

    static final class RegionExtractorInfo<RuleType extends ParserRuleContext, RegionKeyType> {

        private final RegionsKey<RegionKeyType> key;
        private final Class<RuleType> targetRule;
        private final Class<RegionKeyType> regionKeys;
        private final Function<RuleType, RegionKeyType> extractor;
        private final Predicate<RuleType> matcher;
        private final Set<Class<? extends ParserRuleContext>> whenIn = new HashSet<>();

        public RegionExtractorInfo(RegionsKey<RegionKeyType> key, Class<RuleType> targetRule, Class<RegionKeyType> regionKeys, Function<RuleType, RegionKeyType> extractor, Predicate<RuleType> matcher, Set<Class<? extends ParserRuleContext>> whenIn) {
            this.key = key;
            this.targetRule = targetRule;
            this.regionKeys = regionKeys;
            this.extractor = extractor;
            this.matcher = matcher;
            this.whenIn.addAll(whenIn);
        }

        public ParseTreeVisitor<SemanticRegions.SemanticRegionsBuilder<RegionKeyType>> createVisitor() {
            return new V();
        }

        class V extends AbstractParseTreeVisitor<SemanticRegions.SemanticRegionsBuilder<RegionKeyType>> {

            private final SemanticRegionsBuilder<RegionKeyType> bldr = SemanticRegions.builder(regionKeys);

            private int activationCount;

            public V() {
            }

            @Override
            protected SemanticRegionsBuilder<RegionKeyType> defaultResult() {
                return bldr;
            }

            @Override
            public SemanticRegionsBuilder<RegionKeyType> visitChildren(RuleNode node) {
                boolean wasActivated = false;
                if (!whenIn.isEmpty()) {
                    for (Class<? extends ParserRuleContext> type : whenIn) {
                        if (type.isInstance(node)) {
                            wasActivated = true;
                            activationCount++;
                            break;
                        }
                    }
                }
                boolean active = whenIn.isEmpty() || activationCount > 0;
                try {
                    if (active && targetRule.isInstance(node)) {
                        RegionKeyType rkey = null;
                        if (extractor != null) {
                            rkey = extractor.apply(targetRule.cast(node));
                        }
                        RuleType rt = targetRule.cast(node);
                        boolean shouldAdd = true;
                        if (matcher != null) {
                            shouldAdd = matcher.test(rt);
                        }
                        if (shouldAdd) {
                            bldr.add(rkey, rt.start.getStartIndex(), rt.stop.getStopIndex() + 1);
                        }
                    }
                    super.visitChildren(node);
                } finally {
                    if (wasActivated) {
                        activationCount--;
                    }
                }
                return bldr;
            }
        }
    }

    public static final class RegionsKey<T> {

        private final Class<T> type;
        private final int id = keyIds.getAndIncrement();
        private final String name;

        private RegionsKey(Class<T> type, String name) {
            this.name = name;
            this.type = type;
        }

        private RegionsKey(Class<T> type) {
            this.type = type;
            this.name = null;
        }

        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RegionsKey<?> && ((RegionsKey<?>) o).id == id;
        }

        @Override
        public int hashCode() {
            return 7 * id;
        }

        @Override
        public String toString() {
            return name == null ? id + ":" + type.getSimpleName() : id + ":" + type.getSimpleName() + ":" + name;
        }
    }

    public static final class OffsetsKey<T> {

        private final Class<T> type;
        private final int id = keyIds.getAndIncrement();

        private OffsetsKey(Class<T> type) {
            this.type = type;
        }

        public boolean equals(Object o) {
            return o instanceof OffsetsKey<?> && ((OffsetsKey<?>) o).id == id;
        }

        public int hashCode() {
            return 7 * id;
        }

        public String toString() {
            return id + ":" + type.getSimpleName();
        }
    }

}
