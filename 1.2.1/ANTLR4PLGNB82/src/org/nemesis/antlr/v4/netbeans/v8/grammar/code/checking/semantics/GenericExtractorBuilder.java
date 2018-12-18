package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameExtractors;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameExtractors.NameInfoStore;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameReferenceSetKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NamedRegionKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedRegionReferenceSets;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticRegions.SemanticRegionsBuilder;

/**
 *
 * @author Tim Boudreau
 */
public class GenericExtractorBuilder<T extends ParserRuleContext> {

    private final Class<T> entryPoint;

    private static final AtomicInteger keyIds = new AtomicInteger();

    private final Set<RegionExtractorInfo<?, ?>> regionsInfo = new HashSet<>();
    private final Set<NameExtractors<?>> nameExtractors = new HashSet<>();

    public GenericExtractorBuilder(Class<T> entryPoint) {
        this.entryPoint = entryPoint;
    }

    public GenericExtractor<T> build() {
        return new GenericExtractor<>(regionsInfo, nameExtractors);
    }

    public <K extends Enum<K>> NamedRegionExtractorBuilder<K, GenericExtractorBuilder<T>> extractNamedRegions(Class<K> type) {
        return new NamedRegionExtractorBuilder<>(type, ne -> {
            nameExtractors.add(ne);
            return this;
        });
    }

    public static final class GenericExtractor<T extends ParserRuleContext> {

        private final Set<RegionExtractorInfo<?, ?>> extractors;
        private final Set<NameExtractors<?>> nameExtractors;

        GenericExtractor(Set<RegionExtractorInfo<?, ?>> extractors, Set<NameExtractors<?>> nameExtractors) {
            this.extractors = extractors;
            this.nameExtractors = nameExtractors;
        }

        public Extraction extract(T ruleNode) {
            Extraction extraction = new Extraction();
            for (RegionExtractorInfo<?, ?> r : extractors) {
                runOne(r, ruleNode, extraction);
            }
            for (NameExtractors<?> n : nameExtractors) {
                runNames(ruleNode, n, extraction);
            }
            return extraction;
        }

        private <L extends Enum<L>> void runNames(T ruleNode, NameExtractors<L> x, Extraction into) {
            x.invoke(ruleNode, into.store);
        }

        private <R> void runOne(RegionExtractorInfo<?, R> info, T ruleNode, Extraction extraction) {
            ParseTreeVisitor<SemanticRegionsBuilder<R>> v = info.createVisitor();
            SemanticRegionsBuilder<R> b = ruleNode.accept(v);
            extraction.add(info.key, b.build());
        }
    }

    public static class Extraction {

        private final Map<RegionsKey<?>, SemanticRegions<?>> regions = new HashMap<>();
        private final Map<NamedRegionExtractorBuilder.NamedRegionKey<?>, NamedSemanticRegions> nameds = new HashMap<>();
        private final Map<NamedRegionExtractorBuilder.NameReferenceSetKey<?>, NamedSemanticRegions.NamedRegionReferenceSets<?>> refs = new HashMap<>();
        private final Map<NamedRegionExtractorBuilder.NameReferenceSetKey<?>, BitSetStringGraph> graphs = new HashMap<>();

        <T> void add(RegionsKey<T> key, SemanticRegions<T> oneRegion) {
            regions.put(key, oneRegion);
        }

        public <T extends Enum<T>> NamedRegionReferenceSets<T> references(NameReferenceSetKey<T> key) {
            NamedRegionReferenceSets<?> result = refs.get(key);
            return (NamedRegionReferenceSets<T>) result;
        }

        public BitSetStringGraph referenceGraph(NameReferenceSetKey<?> key) {
            return graphs.get(key);
        }

        public <T extends Enum<T>> NamedSemanticRegions<T> namedRegions(NamedRegionKey<T> key) {
            NamedSemanticRegions<?> result = nameds.get(key);
            return result == null ? null : (NamedSemanticRegions<T>) result;
        }

        public <T extends Enum<T>> NamedSemanticRegions.NamedRegionReferenceSets<T> namedRegions(NameReferenceSetKey<T> key) {
            NamedRegionReferenceSets<?> result = refs.get(key);
            return result == null ? null : (NamedRegionReferenceSets<T>) result;
        }

        @SuppressWarnings("unchecked")
        public <T> SemanticRegions<T> regions(RegionsKey<T> key) {
            SemanticRegions<?> result = regions.get(key);
            assert key.type.equals(result.keyType());
            return (SemanticRegions<T>) result;
        }

        NameInfoStore store = new NameInfoStore() {

            @Override
            public <T extends Enum<T>> void addNamedRegions(NamedRegionExtractorBuilder.NamedRegionKey<T> key, NamedSemanticRegions<T> regions) {
                nameds.put(key, regions);
            }

            @Override
            public <T extends Enum<T>> void addReferences(NamedRegionExtractorBuilder.NameReferenceSetKey<T> key, NamedSemanticRegions.NamedRegionReferenceSets<T> regions) {
                refs.put(key, regions);
            }

            @Override
            public <T extends Enum<T>> void addReferenceGraph(NameReferenceSetKey<T> refSetKey, BitSetStringGraph stringGraph) {
                graphs.put(refSetKey, stringGraph);
            }

        };
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

        public RegionExtractionBuilder<EntryPointType, RuleType, RegionKeyType> whereAncestorRuleType(Class<? extends ParserRuleContext> type) {
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

        public GenericExtractorBuilder<EntryPointType> recordingRegionsUnder(RegionsKey<RegionKeyType> key) {
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
        private final String name;

        private RegionsKey(Class<T> type, String name) {
            this.name = name;
            this.type = type;
        }

        private RegionsKey(Class<T> type) {
            this.type = type;
            this.name = null;
        }

        public static <T> RegionsKey<T> create(Class<T> type, String name) {
            return new RegionsKey<>(type, name == null ? type.getSimpleName() : name);
        }

        public static <T> RegionsKey<T> create(Class<T> type) {
            return create(type, null);
        }

        public Class<T> type() {
            return type;
        }

        public String name() {
            return name;
        }

        public String toString() {
            String nm = type.getSimpleName();
            return nm.equals(name) ? nm : name + ":" + nm;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.type);
            hash = 37 * hash + Objects.hashCode(this.name);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RegionsKey<?> other = (RegionsKey<?>) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            return true;
        }
    }
}
