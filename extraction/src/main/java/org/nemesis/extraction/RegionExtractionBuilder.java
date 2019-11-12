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

import com.mastfrog.predicates.integer.IntPredicates;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.tree.ParseTree;
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
    private SummingFunction summer;

    RegionExtractionBuilder(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key) {
        this.bldr = bldr;
        this.key = key;
    }

    /**
     * Sum the tokens in each matched rule using the default summing function,
     * computing sums only for tokens on channel 0.
     *
     * @return this
     */
    public RegionExtractionBuilder<EntryPointType, RegionKeyType> summingTokens() {
        return summingTokensWith(SummingFunction.createDefault().filterChannel(0));
    }

    /**
     * Sum the tokens in each matched rule using the passed summing function,
     * computing sums only for tokens on channel 0.
     *
     * @return this
     */
    public RegionExtractionBuilder<EntryPointType, RegionKeyType> summingTokensWith(SummingFunction summer) {
        if (this.summer != null) {
            throw new IllegalStateException("Summer already set to " + summer);
        }
        this.summer = summer;
        return this;
    }

    /**
     * Sum the tokens in each matched rule using the passed summing function,
     * computing sums only for tokens on channel 0.
     *
     * @return this
     */
    public RegionExtractionBuilder<EntryPointType, RegionKeyType> summingTokensFor(Vocabulary voc) {
        return summingTokensWith(SummingFunction.createDefault(voc.getMaxTokenType()).filterChannel(0));
    }

    public <T extends ParserRuleContext> RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, T> whenRuleType(Class<T> type) {
        return new RegionExtractionBuilderForOneRuleType<>(bldr, key, type, summer);
    }

    public <T extends ParserRuleContext> RegionExtractionBuilderForRuleIds<EntryPointType, RegionKeyType> whenRuleIdIn(int... types) {
        return new RegionExtractionBuilderForRuleIds<>(bldr, key, types, summer);
    }

    public TokenRegionExtractorBuilder<EntryPointType, RegionKeyType> whenTokenTypeMatches(IntPredicate tokenTypeMatcher) {
        return new TokenRegionExtractorBuilder<>(bldr, key, tokenTypeMatcher, summer);
    }

    public TokenRegionExtractorBuilder<EntryPointType, RegionKeyType> whenTokenTypeMatches(int tokenType, int... moreTypes) {
        return whenTokenTypeMatches(IntPredicates.anyOf(tokenType, moreTypes));
    }

    public static final class TokenRegionExtractorBuilder<EntryPointType extends ParserRuleContext, RegionKeyType> {

        private final ExtractorBuilder<EntryPointType> bldr;
        private final RegionsKey<RegionKeyType> key;
        private final IntPredicate tokenTypeMatcher;
        private final Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors = new HashSet<>();
        private final Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors = new HashSet<>();
        private Predicate<? super Token> filter;
        private SummingFunction summer;

        TokenRegionExtractorBuilder(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, IntPredicate tokenTypeMatcher, Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors, Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors, SummingFunction summer) {
            this(bldr, key, tokenTypeMatcher, summer);
            this.extractors.addAll(extractors);
            this.tokenExtractors.addAll(tokenExtractors);
            this.summer = summer;
        }

        TokenRegionExtractorBuilder(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, IntPredicate tokenTypeMatcher, SummingFunction summer) {
            this.bldr = bldr;
            this.key = key;
            this.tokenTypeMatcher = tokenTypeMatcher;
            this.summer = summer;
        }

        public TokenRegionExtractorBuilder filteringTokensWith(Predicate<? super Token> filter) {
            this.filter = filter;
            return this;
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> derivingKeyWith(Function<Token, RegionKeyType> func) {
            tokenExtractors.add(new TokenRegionExtractionStrategy<>(key.type(), tokenTypeMatcher, func, filter));
            return new FinishableRegionExtractorBuilder<>(bldr, key, extractors, tokenExtractors, summer);
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> usingKey(RegionKeyType fixedKey) {
            return derivingKeyWith(new FixedKey<>(fixedKey));
        }

        static final class FixedKey<T> implements Function<Token, T>, Hashable {

            private final T key;

            FixedKey(T key) {
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

    /**
     * Builder for the use case of extracting the region covered by a rule, by
     * ID.
     *
     * @param <EntryPointType>
     * @param <RegionKeyType>
     */
    public static final class RegionExtractionBuilderForRuleIds<EntryPointType extends ParserRuleContext, RegionKeyType> {

        private final ExtractorBuilder<EntryPointType> bldr;
        private final RegionsKey<RegionKeyType> key;
        private final int[] ruleIds;
        private Predicate<RuleNode> ancestorQualifier;
        private final Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors = new HashSet<>();
        private final Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors = new HashSet<>();
        private final SummingFunction summer;
        private Predicate<ParseTree> targetQualifier;

        RegionExtractionBuilderForRuleIds(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, int[] ruleIds, Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> set, Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors, SummingFunction summer) {
            this(bldr, key, ruleIds, summer);
            this.extractors.addAll(set);
            this.tokenExtractors.addAll(tokenExtractors);
        }

        RegionExtractionBuilderForRuleIds(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, int[] ruleIds, SummingFunction summer) {
            this.bldr = bldr;
            this.key = key;
            this.ruleIds = Arrays.copyOf(ruleIds, ruleIds.length);
            Arrays.sort(ruleIds);
            this.summer = summer;
        }

        public RegionExtractionBuilderForRuleIds<EntryPointType, RegionKeyType> whenAncestorMatches(Predicate<RuleNode> pred) {
            if (ancestorQualifier == null) {
                ancestorQualifier = pred;
            } else {
                ancestorQualifier = ancestorQualifier.or(pred);
            }
            return this;
        }

        public RuleParentPredicate.Builder<RegionExtractionBuilderForRuleIds<EntryPointType, RegionKeyType>>
                whereRuleHierarchy() {
            return RuleParentPredicate.builder(rpp -> {
                return whereRuleMatches(rpp);
            });
        }

        public RegionExtractionBuilderForRuleIds<EntryPointType, RegionKeyType> whereRuleMatches(Predicate<ParseTree> pred) {
            if (this.targetQualifier != null) {
                this.targetQualifier = this.targetQualifier.or(pred);
            } else {
                this.targetQualifier = pred;
            }
            return this;
        }

        public RegionExtractionBuilderForRuleIds<EntryPointType, RegionKeyType> whenAncestorRuleOf(Class<? extends RuleNode> type) {
            return whenAncestorMatches(new QualifierPredicate(type));
        }

        private FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> finish() {
            return new FinishableRegionExtractorBuilder<>(bldr, key, extractors, tokenExtractors, summer);
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyWith(Function<ParserRuleContext, RegionKeyType> func) {
            extractors.add(RegionExtractionStrategy.forRuleIds(ancestorQualifier, ruleIds, func, targetQualifier));
            return finish();
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyWith(RegionKeyType type) {
            extractors.add(RegionExtractionStrategy.forRuleIds(ancestorQualifier, ruleIds, new FixedKey<>(type), targetQualifier));
            return finish();
        }

        static class FixedKey<RegionKeyType> implements Function<ParserRuleContext, RegionKeyType>, Hashable {

            private final RegionKeyType type;

            public FixedKey(RegionKeyType type) {
                this.type = type;
            }

            @Override
            public RegionKeyType apply(ParserRuleContext t) {
                return type;
            }

            @Override
            public void hashInto(Hasher hasher) {
                hasher.writeString(getClass().getName());
                hasher.writeString(type == null ? null : type.getClass().getName());
                hasher.hashObject(type);
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
        private final SummingFunction summer;
        private Predicate<ParseTree> targetQualifier;

        RegionExtractionBuilderForOneRuleType(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, Class<RuleType> ruleType, Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> set, Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors, SummingFunction summer) {
            this(bldr, key, ruleType, summer);
            this.extractors.addAll(set);
            this.tokenExtractors.addAll(tokenExtractors);
        }

        RegionExtractionBuilderForOneRuleType(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, Class<RuleType> ruleType, SummingFunction summer) {
            this.bldr = bldr;
            this.key = key;
            this.ruleType = ruleType;
            this.summer = summer;
        }

        public RuleParentPredicate.Builder<RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, RuleType>>
                whereRuleHierarchy() {
            return RuleParentPredicate.builder(rpp -> {
                return whereRuleMatches(rpp);
            });
        }

        public RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, RuleType> whereRuleMatches(Predicate<ParseTree> pred) {
            if (this.targetQualifier != null) {
                this.targetQualifier = this.targetQualifier.or(pred);
            } else {
                this.targetQualifier = pred;
            }
            return this;
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
            return new FinishableRegionExtractorBuilder<>(bldr, key, extractors, tokenExtractors, summer);
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
                return c.test(k, new int[]{rule.getStart().getStartIndex(), rule.getStop().getStopIndex() + 1});
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

        private static <RuleType extends ParserRuleContext, R> boolean extractBounds(RuleType rule, BiPredicate<R, int[]> c) {
            return c.test(null, new int[]{rule.getStart().getStartIndex(), rule.getStop().getStopIndex() + 1});
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingBoundsFromRuleUsingKey(RegionKeyType key) {
            return extractingKeyAndBoundsWith((rule, c) -> {
                return c.test(key, new int[]{rule.getStart().getStartIndex(), rule.getStop().getStopIndex() + 1});
            });
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromTokenWith(
                BiPredicate<RuleType, BiPredicate<RegionKeyType, Token>> consumer) {
            extractors.add(new RegionExtractionStrategy<>(ruleType, ancestorQualifier, consumer, RegionExtractType.TOKEN, targetQualifier));
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
        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyAndBoundsWith(BiPredicate<RuleType, BiPredicate<RegionKeyType, int[]>> consumer) {
            extractors.add(new RegionExtractionStrategy<>(ruleType, ancestorQualifier, consumer, RegionExtractType.INT_ARRAY, targetQualifier));
            return finish();
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromRuleWith(BiPredicate<RuleType, BiPredicate<RegionKeyType, ParserRuleContext>> consumer) {
            extractors.add(new RegionExtractionStrategy<>(ruleType, ancestorQualifier, consumer, RegionExtractType.PARSER_RULE_CONTEXT, targetQualifier));
            return finish();
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromTerminalNodeWith(BiPredicate<RuleType, BiPredicate<RegionKeyType, TerminalNode>> consumer) {
            extractors.add(new RegionExtractionStrategy<>(ruleType, ancestorQualifier, consumer, RegionExtractType.TERMINAL_NODE, targetQualifier));
            return finish();
        }

        public FinishableRegionExtractorBuilder<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromTerminalNodeList(BiPredicate<RuleType, BiPredicate<RegionKeyType, List<TerminalNode>>> consumer) {
            extractors.add(new RegionExtractionStrategy<>(ruleType, ancestorQualifier, consumer, RegionExtractType.TERMINAL_NODE_LIST, targetQualifier));
            return finish();
        }
    }

    public static final class FinishableRegionExtractorBuilder<EntryPointType extends ParserRuleContext, RegionKeyType> {

        private final ExtractorBuilder<EntryPointType> bldr;
        private final RegionsKey<RegionKeyType> key;
        private final Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors = new HashSet<>();
        private final Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors = new HashSet<>();
        private final SummingFunction summer;

        FinishableRegionExtractorBuilder(ExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, Set<RegionExtractionStrategy<RegionKeyType, ?, ?>> set, Set<TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors, SummingFunction summer) {
            this.bldr = bldr;
            this.key = key;
            this.extractors.addAll(set);
            this.tokenExtractors.addAll(tokenExtractors);
            this.summer = summer;
        }

        public <T extends ParserRuleContext> RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, T> whenRuleType(Class<T> type) {
            return new RegionExtractionBuilderForOneRuleType<>(bldr, key, type, extractors, tokenExtractors, summer);
        }

        public TokenRegionExtractorBuilder<EntryPointType, RegionKeyType> whenTokenTypeMatches(IntPredicate tokenTypeMatcher) {
            return new TokenRegionExtractorBuilder<>(bldr, key, tokenTypeMatcher, extractors, tokenExtractors, summer);
        }

        public TokenRegionExtractorBuilder<EntryPointType, RegionKeyType> whenTokenTypeMatches(int tokenType, int... moreTypes) {
            IntPredicate tokenTypeMatcher = new MultiIntPredicate(tokenType, moreTypes);
            return new TokenRegionExtractorBuilder<>(bldr, key, tokenTypeMatcher, extractors, tokenExtractors, summer);
        }

        public ExtractorBuilder<EntryPointType> finishRegionExtractor() {
            assert !extractors.isEmpty() || !tokenExtractors.isEmpty();
            bldr.addRegionEx(new RegionExtractionStrategies<>(key, extractors, tokenExtractors, summer));
            return bldr;
        }
    }

    private static final class MultiIntPredicate implements IntPredicate, Hashable {

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
        public void hashInto(Hasher hasher) {
            hasher.writeInt(-1013);
            hasher.writeIntArray(values);
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
