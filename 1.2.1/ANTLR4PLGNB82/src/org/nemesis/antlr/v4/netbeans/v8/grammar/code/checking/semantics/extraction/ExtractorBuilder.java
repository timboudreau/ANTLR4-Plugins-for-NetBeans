package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.RegionsKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.SingletonKey;

/**
 *
 * @author Tim Boudreau
 */
public class ExtractorBuilder<T extends ParserRuleContext> {

    private final Class<T> documentRootType;

    private final Set<RegionExtractionStrategies<?>> regionsInfo2 = new HashSet<>();
    private final Set<NamesAndReferencesExtractionStrategy<?>> nameExtractors = new HashSet<>();
    private final Map<SingletonKey<?>, SingletonExtractionStrategies<?>> singles = new HashMap<>();

    ExtractorBuilder(Class<T> entryPoint) {
        this.documentRootType = entryPoint;
    }

    public Extractor<T> build() {
        return new Extractor<>(documentRootType, nameExtractors, regionsInfo2, singles);
    }

    /**
     * Extract named regions, which may each be assigned an enum value of the passed enum
     * type.
     *
     * @param <K> The enum type
     * @param type The enum type
     * @return A builder
     */
    public <K extends Enum<K>> NamedRegionExtractorBuilder<K, ExtractorBuilder<T>> extractNamedRegions(Class<K> type) {
        return new NamedRegionExtractorBuilder<>(type, ne -> {
            nameExtractors.add(ne);
            return this;
        });
    }

    void addRegionEx(RegionExtractionStrategies<?> info) {
        regionsInfo2.add(info);
    }

    /**
     * Extract <i>singleton data</i> from a source, retrievable using the passed key.
     * This is data which should occur exactly <i>once</i> in a source file, if present.
     * You can retrieve a {@link SingletonEncounters} instance from the resulting
     * {@link Extraction} which handles the case that the data in question is actually found
     * more than once.
     *
     * @param <KeyType> The key type
     * @param key The key
     * @return A builder
     */
    public <KeyType> SingletonExtractionBuilder<T, KeyType> extractingSingletonUnder(SingletonKey<KeyType> key) {
        return new SingletonExtractionBuilder<>(this, key);
    }

    /**
     * Extract unnamed, but possibly nested, semantic regions which use the passed key type
     * for data associated with the region.
     * 
     * @param <KeyType> The key type
     * @param key The key to use for retrieval
     * @return A builder
     */
    public <KeyType> RegionExtractionBuilderWithKey<T, KeyType> extractingRegionsUnder(RegionsKey<KeyType> key) {
        return new RegionExtractionBuilderWithKey<>(this, key);
    }

    public static class SingletonExtractionBuilder<T extends ParserRuleContext, KeyType> {

        final ExtractorBuilder<T> bldr;
        final SingletonKey<KeyType> key;
        Predicate<RuleNode> ancestorQualifier;
        final Set<SingletonExtractionStrategy<KeyType, ?>> set;

        SingletonExtractionBuilder(ExtractorBuilder<T> bldr, SingletonKey<KeyType> key, Set<SingletonExtractionStrategy<KeyType, ?>> set) {
            this.set = set;
            this.bldr = bldr;
            this.key = key;
        }

        SingletonExtractionBuilder(ExtractorBuilder<T> bldr, SingletonKey<KeyType> key) {
            this.bldr = bldr;
            this.key = key;
            this.set = new HashSet<>(2);
        }

        public SingletonExtractionBuilder<T, KeyType> whenAncestorIs(Class<? extends RuleNode> ancestorType) {
            if (ancestorQualifier == null) {
                ancestorQualifier = new QualifierPredicate(ancestorType);
            } else {
                ancestorQualifier = ancestorQualifier.or(new QualifierPredicate(ancestorType));
            }
            return this;
        }

        public SingletonExtractionBuilder<T, KeyType> whenAncestorMatches(Predicate<RuleNode> qualifier) {
            if (ancestorQualifier == null) {
                ancestorQualifier = qualifier;
            } else {
                ancestorQualifier = ancestorQualifier.or(qualifier);
            }
            return this;
        }

        public <R extends ParserRuleContext> SingletonExtractionBuilderWithRule<T, KeyType, R> using(Class<R> ruleType) {
            return new SingletonExtractionBuilderWithRule<>(bldr, key, ancestorQualifier, ruleType, set);
        }
    }

    public static final class FinishableSingletonExtractorBuilder<T extends ParserRuleContext, KeyType> {

        final ExtractorBuilder<T> bldr;
        final SingletonKey<KeyType> key;
        final Set<SingletonExtractionStrategy<KeyType, ?>> set;

        FinishableSingletonExtractorBuilder(ExtractorBuilder<T> bldr, SingletonKey<KeyType> key, Set<SingletonExtractionStrategy<KeyType, ?>> set) {
            this.bldr = bldr;
            this.key = key;
            this.set = set;
        }

        public ExtractorBuilder<T> finishObjectExtraction() {
            return bldr.addSingletons(key, set);
        }

        public SingletonExtractionBuilder<T, KeyType> whenAncestorIs(Class<? extends RuleNode> ancestorType) {
            SingletonExtractionBuilder<T, KeyType> result = new SingletonExtractionBuilder<>(bldr, key, set);
            result.ancestorQualifier = new QualifierPredicate(ancestorType);
            return result;
        }

        public SingletonExtractionBuilder<T, KeyType> whenAncestorMatches(Predicate<RuleNode> qualifier) {
            SingletonExtractionBuilder<T, KeyType> result = new SingletonExtractionBuilder<>(bldr, key, set);
            result.ancestorQualifier = qualifier;
            return result;
        }

        public <R extends ParserRuleContext> SingletonExtractionBuilderWithRule<T, KeyType, R> using(Class<R> ruleType) {
            return new SingletonExtractionBuilderWithRule<>(bldr, key, null, ruleType, set);
        }
    }

    public static final class SingletonExtractionBuilderWithRule<T extends ParserRuleContext, KeyType, R extends ParserRuleContext> {

        final ExtractorBuilder<T> bldr;
        final SingletonKey<KeyType> key;
        final Predicate<RuleNode> ancestorQualifier;
        final Class<R> ruleType;
        final Set<SingletonExtractionStrategy<KeyType, ?>> set;

        SingletonExtractionBuilderWithRule(ExtractorBuilder<T> bldr,
                SingletonKey<KeyType> key,
                Predicate<RuleNode> ancestorQualifier, Class<R> ruleType, Set<SingletonExtractionStrategy<KeyType, ?>> set) {
            this.bldr = bldr;
            this.key = key;
            this.ruleType = ruleType;
            this.ancestorQualifier = ancestorQualifier;
            this.set = set;
        }

        public FinishableSingletonExtractorBuilder<T, KeyType> extractingObjectWith(Function<R, KeyType> func) {
            SingletonExtractionStrategy<KeyType, R> info = new SingletonExtractionStrategy<>(key, ancestorQualifier, ruleType, func);
            set.add(info);
            return new FinishableSingletonExtractorBuilder<>(bldr, key, set);
        }
    }

    private <KeyType> ExtractorBuilder<T> addSingletons(SingletonKey<KeyType> key, Set<SingletonExtractionStrategy<KeyType, ?>> single) {
        if (singles.containsKey(key)) {
            SingletonExtractionStrategies<KeyType> infos = (SingletonExtractionStrategies<KeyType>) singles.get(key);
            infos.addAll(single);
            return this;
        }
        SingletonExtractionStrategies<KeyType> infos = new SingletonExtractionStrategies<>(key, single);
        singles.put(key, infos);
        return this;
    }

    public static final class RegionExtractionBuilder<EntryPointType extends ParserRuleContext> {

        private final ExtractorBuilder<EntryPointType> bldr;

        RegionExtractionBuilder(ExtractorBuilder<EntryPointType> bldr) {
            this.bldr = bldr;
        }

        public <T> RegionExtractionBuilderWithKey<EntryPointType, T> recordingRegionsUnder(RegionsKey<T> key) {
            return new RegionExtractionBuilderWithKey<>(bldr, key);
        }
    }

    public static final class RegionExtractionBuilderWithKey<EntryPointType extends ParserRuleContext, RegionKeyType> {

        private final ExtractorBuilder<EntryPointType> bldr;
        private final RegionsKey<RegionKeyType> key;

        RegionExtractionBuilderWithKey(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key) {
            this.bldr = bldr;
            this.key = key;
        }

        public <T extends ParserRuleContext> RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, T> whenRuleType(Class<T> type) {
            return new RegionExtractionBuilderForOneRuleType<>(bldr, key, type);
        }
    }

    public static final class RegionExtractionBuilderForOneRuleType<EntryPointType extends ParserRuleContext, RegionKeyType, RuleType extends ParserRuleContext> {

        private final ExtractorBuilder<EntryPointType> bldr;
        private final RegionsKey<RegionKeyType> key;
        private final Class<RuleType> ruleType;
        private Predicate<RuleNode> ancestorQualifier;
        private final Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors = new HashSet<>();

        RegionExtractionBuilderForOneRuleType(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, Class<RuleType> ruleType, Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> set) {
            this(bldr, key, ruleType);
            this.extractors.addAll(set);
        }

        RegionExtractionBuilderForOneRuleType(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, Class<RuleType> ruleType) {
            this.bldr = bldr;
            this.key = key;
            this.ruleType = ruleType;
        }

        public RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, RuleType> whenAncestorMatches(Predicate<RuleNode> pred) {
            if (ancestorQualifier == null) {
                ancestorQualifier = pred;
            } else {
                ancestorQualifier = ancestorQualifier.or(pred);
            }
            return this;
        }

        public RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, RuleType> whenAncestorRuleOf(Class<? extends RuleNode> type) {
            return whenAncestorMatches(new QualifierPredicate(type));
        }

        private FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> finish() {
            return new FinishableRegionExtractorBuilder<>(bldr, key, extractors);
        }

        /**
         * Simple-ish use-case: Record the bounds of all rules of RuleType, with
         * the value provided by the passed function.
         *
         * @return The outer builder
         */
        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingBoundsFromRuleAndKeyWith(Function<RuleType, RegionKeyType> func) {
            return extractingKeyAndBoundsFromWith((rule, c) -> {
                RegionKeyType k = func.apply(rule);
                c.accept(k, new int[]{
                    rule.getStart().getStartIndex(),
                    rule.getStop().getStopIndex() + 1
                });
            });
        }

        /**
         * Simple use-case: Record the bounds of all rules of RuleType, with a
         * null value (useful for things like block delimiters such as braces or
         * parentheses).
         *
         * @return The outer builder
         */
        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingBoundsFromRule() {
            return extractingKeyAndBoundsFromWith((rule, c) -> {
                c.accept(null, new int[]{
                    rule.getStart().getStartIndex(),
                    rule.getStop().getStopIndex() + 1
                });
            });
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingBoundsFromRuleUsingKey(RegionKeyType key) {
            return extractingKeyAndBoundsFromWith((rule, c) -> {
                c.accept(key, new int[]{
                    rule.getStart().getStartIndex(),
                    rule.getStop().getStopIndex() + 1
                });
            });
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromTokenWith(BiConsumer<RuleType, BiConsumer<RegionKeyType, Token>> consumer) {
            extractors.add(new RegionExtractionStrategy<>(ruleType, ancestorQualifier, consumer, RegionExtractType.TOKEN));
            return finish();
        }

        /**
         * Extract a region, returning raw offsets.  Note:  Antlr stop indices are the index <i>of</i> the
         * last character, while we deal in exclusive ends - i.e. if you get this from a Token, you want
         * <code>stopIndex() + 1</code>.
         *
         * @param consumer A consumer
         * @return a finishable builder
         */
        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromWith(BiConsumer<RuleType, BiConsumer<RegionKeyType, int[]>> consumer) {
            extractors.add(new RegionExtractionStrategy<>(ruleType, ancestorQualifier, consumer, RegionExtractType.INT_ARRAY));
            return finish();
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromRuleWith(BiConsumer<RuleType, BiConsumer<RegionKeyType, ParserRuleContext>> consumer) {
            extractors.add(new RegionExtractionStrategy<>(ruleType, ancestorQualifier, consumer, RegionExtractType.PARSER_RULE_CONTEXT));
            return finish();
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromTerminalNodeWith(BiConsumer<RuleType, BiConsumer<RegionKeyType, TerminalNode>> consumer) {
            extractors.add(new RegionExtractionStrategy<>(ruleType, ancestorQualifier, consumer, RegionExtractType.TERMINAL_NODE));
            return finish();
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromTerminalNodeList(BiConsumer<RuleType, BiConsumer<RegionKeyType, List<TerminalNode>>> consumer) {
            extractors.add(new RegionExtractionStrategy<>(ruleType, ancestorQualifier, consumer, RegionExtractType.TERMINAL_NODE_LIST));
            return finish();
        }
    }

    enum RegionExtractType implements Function<Object, int[]> {
        TOKEN,
        TERMINAL_NODE,
        INT_ARRAY,
        PARSER_RULE_CONTEXT,
        TERMINAL_NODE_LIST;

        public <K, TType> BiConsumer<K, TType> wrap(BiConsumer<K, int[]> c) {
            return (K t, TType u) -> {
                if (RegionExtractType.this == TERMINAL_NODE_LIST && u instanceof List<?>) {
                    List<TerminalNode> tns = (List<TerminalNode>) u;
                    for (TerminalNode n : tns) {
                        c.accept(t, TERMINAL_NODE.apply(u));
                    }
                } else {
                    c.accept(t, apply(u));
                }
            };
        }

        @Override
        public int[] apply(Object t) {
            if (t == null) {
                return null;
            }
            switch (this) {
                case TOKEN:
                    Token tok = (Token) t;
                    return new int[]{tok.getStartIndex(), tok.getStopIndex() + 1};
                case TERMINAL_NODE:
                    return TOKEN.apply(((TerminalNode) t).getSymbol());
                case PARSER_RULE_CONTEXT:
                    ParserRuleContext rule = (ParserRuleContext) t;
                    return new int[]{rule.getStart().getStartIndex(), rule.getStop().getStopIndex() + 1};
                case INT_ARRAY:
                    int[] val = (int[]) t;
                    if (val.length != 2) {
                        throw new IllegalArgumentException("Array must have two elements: " + Arrays.toString(val));
                    }
                    return val;
                default:
                    throw new AssertionError(this);
            }
        }
    }

    public static final class FinishableRegionExtractorBuilder<EntryPointType extends ParserRuleContext, RegionKeyType> {

        private final ExtractorBuilder<EntryPointType> bldr;
        private final RegionsKey<RegionKeyType> key;
        private final Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors = new HashSet<>();

        FinishableRegionExtractorBuilder(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> set) {
            this.bldr = bldr;
            this.key = key;
            this.extractors.addAll(set);
        }

        public <T extends ParserRuleContext> RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, T> whenRuleType(Class<T> type) {
            return new RegionExtractionBuilderForOneRuleType<>(bldr, key, type);
        }

        public ExtractorBuilder<EntryPointType> finishRegionExtractor() {
            assert !extractors.isEmpty();
            bldr.addRegionEx(new RegionExtractionStrategies<>(key, extractors));
            return bldr;
        }
    }
}
