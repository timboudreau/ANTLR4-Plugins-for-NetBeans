package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.Hashable;

/**
 *
 * @author Tim Boudreau
 */
final class RegionExtractionStrategy<KeyType, RuleType extends RuleNode, TType> implements Hashable {

    final Class<RuleType> ruleType;
    final Predicate<RuleNode> ancestorQualifier;
    final BiConsumer<RuleType, BiConsumer<KeyType, TType>> extractor;
    private final ExtractorBuilder.RegionExtractType ttype;

    RegionExtractionStrategy(Class<RuleType> ruleType, Predicate<RuleNode> ancestorQualifier, BiConsumer<RuleType, BiConsumer<KeyType, TType>> tok, ExtractorBuilder.RegionExtractType ttype) {
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
}
