package org.nemesis.extraction;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.data.Hashable;
import org.nemesis.extraction.key.RegionsKey;

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

    public TokenRegionExtractorBuilder<EntryPointType, RegionKeyType> whenTokenTypeMatches(IntPredicate tokenTypeMatcher) {
        return new TokenRegionExtractorBuilder<>(bldr, key, tokenTypeMatcher);
    }

    public TokenRegionExtractorBuilder<EntryPointType, RegionKeyType> whenTokenTypeMatches(int tokenType, int... moreTypes) {
        return whenTokenTypeMatches(new MultiIntPredicate(tokenType, moreTypes));
    }

    public static final class TokenRegionExtractorBuilder<EntryPointType extends ParserRuleContext, RegionKeyType> {

        private final ExtractorBuilder<EntryPointType> bldr;
        private final RegionsKey<RegionKeyType> key;
        private final IntPredicate tokenTypeMatcher;
        private final Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors = new HashSet<>();
        private final Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors = new HashSet<>();
        private Predicate<? super Token> filter;

        TokenRegionExtractorBuilder(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, IntPredicate tokenTypeMatcher, Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors, Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors) {
            this(bldr, key, tokenTypeMatcher);
            this.extractors.addAll(extractors);
            this.tokenExtractors.addAll(tokenExtractors);
        }

        TokenRegionExtractorBuilder(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, IntPredicate tokenTypeMatcher) {
            this.bldr = bldr;
            this.key = key;
            this.tokenTypeMatcher = tokenTypeMatcher;
        }

        public TokenRegionExtractorBuilder filteringTokensWith(Predicate<? super Token> filter) {
            this.filter = filter;
            return this;
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> derivingKeyWith(Function<Token, RegionKeyType> func) {
            tokenExtractors.add(new TokenRegionExtractionStrategy<>(key.type(), tokenTypeMatcher, func, filter));
            return new FinishableRegionExtractorBuilder<>(bldr, key, extractors, tokenExtractors);
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> usingKey(RegionKeyType fixedKey) {
            return derivingKeyWith(new FixedKey<>(fixedKey));
        }

        static final class FixedKey<T> implements Function<Token, T>, Hashable {

            private final T key;

            public FixedKey(T key) {
                this.key = key;
            }

            @Override
            public T apply(Token t) {
                return key;
            }

            @Override
            public String toString() {
                return Objects.toString(key);
            }

            @Override
            public void hashInto(Hasher hasher) {
                hasher.hashObject(key);
            }
        }

    }

    public static final class RegionExtractionBuilderForOneRuleType<EntryPointType extends ParserRuleContext, RegionKeyType, RuleType extends ParserRuleContext> {

        private final ExtractorBuilder<EntryPointType> bldr;
        private final RegionsKey<RegionKeyType> key;
        private final Class<RuleType> ruleType;
        private Predicate<RuleNode> ancestorQualifier;
        private final Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors = new HashSet<>();
        private final Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors = new HashSet<>();

        RegionExtractionBuilderForOneRuleType(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, Class<RuleType> ruleType, Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> set, Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors) {
            this(bldr, key, ruleType);
            this.extractors.addAll(set);
            this.tokenExtractors.addAll(tokenExtractors);
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
            return new FinishableRegionExtractorBuilder<>(bldr, key, extractors, tokenExtractors);
        }

        /**
         * Simple-ish use-case: Record the bounds of all rules of RuleType, with
         * the value provided by the passed function.
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
         * Simple use-case: Record the bounds of all rules of RuleType, with a
         * null value (useful for things like block delimiters such as braces or
         * parentheses).
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
         * Extract a region, returning raw offsets. Note: Antlr stop indices are
         * the index <i>of</i> the last character, while we deal in exclusive
         * ends - i.e. if you get this from a Token, you want
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
        private final Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors = new HashSet<>();

        FinishableRegionExtractorBuilder(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> set, Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors) {
            this.bldr = bldr;
            this.key = key;
            this.extractors.addAll(set);
            this.tokenExtractors.addAll(tokenExtractors);
        }

        public <T extends ParserRuleContext> RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, T> whenRuleType(Class<T> type) {
            return new RegionExtractionBuilderForOneRuleType<>(bldr, key, type, extractors, tokenExtractors);
        }

        public TokenRegionExtractorBuilder<EntryPointType, RegionKeyType> whenTokenTypeMatches(IntPredicate tokenTypeMatcher) {
            return new TokenRegionExtractorBuilder<>(bldr, key, tokenTypeMatcher, extractors, tokenExtractors);
        }

        public TokenRegionExtractorBuilder<EntryPointType, RegionKeyType> whenTokenTypeMatches(int tokenType, int... moreTypes) {
            IntPredicate tokenTypeMatcher = new MultiIntPredicate(tokenType, moreTypes);
            return new TokenRegionExtractorBuilder<>(bldr, key, tokenTypeMatcher, extractors, tokenExtractors);
        }

        public ExtractorBuilder<EntryPointType> finishRegionExtractor() {
            assert !extractors.isEmpty() || !tokenExtractors.isEmpty();
            bldr.addRegionEx(new RegionExtractionStrategies<>(key, extractors, tokenExtractors));
            return bldr;
        }
    }

    private static final class MultiIntPredicate implements IntPredicate {

        private final int[] values;

        MultiIntPredicate(int first, int... more) {
            values = Arrays.copyOf(more, more.length + 1);
            values[values.length - 1] = first;
            Arrays.sort(values);
        }

        @Override
        public boolean test(int value) {
            return Arrays.binarySearch(values, value) >= 0;
        }

        @Override
        public String toString() {
            return "tokenType(" + Arrays.toString(values) + ")";
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 53 * hash + Arrays.hashCode(this.values);
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
            final MultiIntPredicate other = (MultiIntPredicate) obj;
            return Arrays.equals(this.values, other.values);
        }
    }
}
