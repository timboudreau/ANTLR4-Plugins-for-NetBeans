package org.nemesis.extraction;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
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
    final BiConsumer<RuleType, BiConsumer<KeyType, TType>> extractor;
    private final RegionExtractType ttype;

    RegionExtractionStrategy(Class<RuleType> ruleType, Predicate<RuleNode> ancestorQualifier,
            BiConsumer<RuleType, BiConsumer<KeyType, TType>> tok, RegionExtractType ttype) {
        this.ruleType = ruleType;
        this.ancestorQualifier = ancestorQualifier;
        this.extractor = tok;
        this.ttype = ttype;
    }

    public void extract(RuleType rule, BiConsumer<KeyType, int[]> c) {
        extractor.accept(rule, ttype.wrap(c));
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeString(ruleType.getName());
        hasher.hashObject(ancestorQualifier);
        hasher.hashObject(extractor);
        hasher.writeInt(ttype.ordinal());
    }

    static <KeyType> RegionExtractionStrategy<KeyType, ? super ParserRuleContext, ParserRuleContext> forRuleIds(
            Predicate<RuleNode> ancestorQualifier, int[] ids, Function<ParserRuleContext, KeyType> cvt) {
        BiConsumer<ParserRuleContext, BiConsumer<KeyType, ParserRuleContext>> bc = (rn, cons) -> {
            int ix = rn.getRuleIndex();
            if (Arrays.binarySearch(ids, ix) >= 0) {
                KeyType kt = cvt.apply(rn);
                if (kt != null) {
                    cons.accept(kt, rn);
                }
            }
        };
        return new RegionExtractionStrategy<>(ParserRuleContext.class, ancestorQualifier, bc, RegionExtractType.PARSER_RULE_CONTEXT);
    }

    static class PRC<KeyType> implements BiConsumer<ParserRuleContext, BiConsumer<KeyType, ParserRuleContext>>, Hashable {

        private final Function<ParserRuleContext, KeyType> cvt;
        private final int[] ruleIds;

        public PRC(Function<ParserRuleContext, KeyType> func, int[] ruleIds) {
            this.cvt = func;
            this.ruleIds = ruleIds;
        }

        @Override
        public void accept(ParserRuleContext ruleNode, BiConsumer<KeyType, ParserRuleContext> cons) {
            int ix = ruleNode.getRuleIndex();
            if (Arrays.binarySearch(ruleIds, ix) >= 0) {
                KeyType kt = cvt.apply(ruleNode);
                if (kt != null) {
                    cons.accept(kt, ruleNode);
                }
            }
        }

        @Override
        public void hashInto(Hasher hasher) {
            for (int val : ruleIds) {
                hasher.writeInt(val);
            }
            hasher.hashObject(cvt);
        }

    }
}
