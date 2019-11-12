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

import java.util.Arrays;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;

/**
 *
 * @author Tim Boudreau
 */
final class RegionExtractionStrategy<KeyType, RuleType extends RuleNode, TType> implements Hashable {

    final Class<RuleType> ruleType;
    final Predicate<RuleNode> ancestorQualifier;
    final BiPredicate<RuleType, BiPredicate<KeyType, TType>> extractor;
    private final RegionExtractType ttype;
    final Predicate<ParseTree> qualifier;

    RegionExtractionStrategy(Class<RuleType> ruleType, Predicate<RuleNode> ancestorQualifier,
            BiPredicate<RuleType, BiPredicate<KeyType, TType>> tok, RegionExtractType ttype,
            Predicate<ParseTree> qualifier) {
        this.ruleType = ruleType;
        this.ancestorQualifier = ancestorQualifier;
        this.extractor = tok;
        this.ttype = ttype;
        this.qualifier = qualifier;
    }

    public boolean extract(RuleType rule, BiPredicate<KeyType, int[]> c) {
        // DO THE CHECKSUM HERE
        BiPredicate<KeyType, TType> wrapped = ttype.wrap(c);
        return extractor.test(rule, wrapped);
    }

    public boolean extract(RuleType rule, BiPredicate<KeyType, int[]> c, SummingFunction summer, LongConsumer sums) {
        if (summer == null) {
            BiPredicate<KeyType, TType> wrapped = ttype.wrap(c);
            return extractor.test(rule, wrapped);
        } else {
            BiPredicate<RuleType, BiPredicate<KeyType, int[]>> wrappedExt
                    = SumConsumerAdapter.<RuleType, KeyType>wrap(this::extract, summer, sums);
            return wrappedExt.test(rule, c);
        }
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeString(ruleType.getName());
        hasher.hashObject(ancestorQualifier);
        if (qualifier != null) {
            hasher.hashObject(qualifier);
        }
        hasher.hashObject(extractor);
        hasher.writeInt(ttype.ordinal());
    }

    static <KeyType> RegionExtractionStrategy<KeyType, ? super ParserRuleContext, ParserRuleContext> forRuleIds(
            Predicate<RuleNode> ancestorQualifier, int[] ids, Function<ParserRuleContext, KeyType> cvt, Predicate<ParseTree> targetQualifier) {
        return new RegionExtractionStrategy<>(ParserRuleContext.class,
                ancestorQualifier,
                ids.length == 1 ? new SingleIdFilter(ids[0], cvt) : new IdListFilter(ids, cvt),
                RegionExtractType.PARSER_RULE_CONTEXT, targetQualifier);
    }

    interface SumConsumer<KeyType> {

        void accept(KeyType keyType, ParserRuleContext ctx, boolean hasSum, long sum);
    }

    private static class IdListFilter<KeyType> implements BiPredicate<ParserRuleContext, BiPredicate<KeyType, ParserRuleContext>>, Hashable {

        private final int[] ids;
        private final Function<ParserRuleContext, KeyType> cvt;

        IdListFilter(int[] ids, Function<ParserRuleContext, KeyType> cvt) {
            this.ids = ids;
            this.cvt = cvt;
        }

        @Override
        public boolean test(ParserRuleContext rn, BiPredicate<KeyType, ParserRuleContext> cons) {
            int ix = rn.getRuleIndex();
            if (Arrays.binarySearch(ids, ix) >= 0) {
                KeyType kt = cvt.apply(rn);
                if (kt != null) {
                    return cons.test(kt, rn);
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return "IdListFilter(" + Arrays.toString(ids) + " " + cvt + ")";
        }
    }

    private static class SingleIdFilter<KeyType> implements BiPredicate<ParserRuleContext, BiPredicate<KeyType, ParserRuleContext>>, Hashable {

        private final int id;
        private final Function<ParserRuleContext, KeyType> cvt;

        SingleIdFilter(int id, Function<ParserRuleContext, KeyType> cvt) {
            this.id = id;
            this.cvt = cvt;
        }

        @Override
        public boolean test(ParserRuleContext rn, BiPredicate<KeyType, ParserRuleContext> cons) {
            int ix = rn.getRuleIndex();
            if (id == ix) {
                KeyType kt = cvt.apply(rn);
                if (kt != null) {
                    return cons.test(kt, rn);
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return "SingleIdFilter(" + id + " " + cvt + ")";
        }
    }
}
