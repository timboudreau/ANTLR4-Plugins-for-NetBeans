package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.RegionsKey;

/**
 *
 * @author Tim Boudreau
 */
public final class RegionExtractionBuilder<EntryPointType extends ParserRuleContext, RegionKeyType> {

    private final ExtractorBuilder<EntryPointType> bldr;
    private final RegionsKey<RegionKeyType> key;

    RegionExtractionBuilder(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key) {
        this.bldr = bldr;
        this.key = key;
    }

    public <T extends ParserRuleContext> RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, T> whenRuleType(Class<T> type) {
        return new RegionExtractionBuilderForOneRuleType<>(bldr, key, type);
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
         * Simple-ish use-case: Record the bounds of all rules of RuleType,
         * with the value provided by the passed function.
         *
         * @return The outer builder
         */
        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingBoundsFromRuleAndKeyWith(Function<RuleType, RegionKeyType> func) {
            return extractingKeyAndBoundsWith((rule, c) -> {
                RegionKeyType k = func.apply(rule);
                c.accept(k, new int[]{rule.getStart().getStartIndex(), rule.getStop().getStopIndex() + 1});
            });
        }

        /**
         * Simple use-case: Record the bounds of all rules of RuleType, with
         * a null value (useful for things like block delimiters such as
         * braces or parentheses).
         *
         * @return The outer builder
         */
        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingBoundsFromRule() {
            return extractingKeyAndBoundsWith(RegionExtractionBuilderForOneRuleType::extractBounds);
        }

        private static <RuleType extends ParserRuleContext, R> void extractBounds(RuleType rule, BiConsumer<R, int[]> c) {
            c.accept(null, new int[]{rule.getStart().getStartIndex(), rule.getStop().getStopIndex() + 1});
        }

        private static <RuleType extends ParserRuleContext, R> void extractBounds(RuleType rule, R key, BiConsumer<R, int[]> c) {
            c.accept(key, new int[]{rule.getStart().getStartIndex(), rule.getStop().getStopIndex() + 1});
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingBoundsFromRuleUsingKey(RegionKeyType key) {
            return extractingKeyAndBoundsWith((rule, c) -> {
                c.accept(key, new int[]{rule.getStart().getStartIndex(), rule.getStop().getStopIndex() + 1});
            });
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromTokenWith(BiConsumer<RuleType, BiConsumer<RegionKeyType, Token>> consumer) {
            extractors.add(new RegionExtractionStrategy<>(ruleType, ancestorQualifier, consumer, RegionExtractType.TOKEN));
            return finish();
        }

        /**
         * Extract a region, returning raw offsets. Note: Antlr stop indices
         * are the index <i>of</i> the last character, while we deal in
         * exclusive ends - i.e. if you get this from a Token, you want
         * <code>stopIndex() + 1</code>.
         *
         * @param consumer A consumer
         * @return a finishable builder
         */
        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyAndBoundsWith(BiConsumer<RuleType, BiConsumer<RegionKeyType, int[]>> consumer) {
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
