package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.SingletonKey;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.Hashable;

/**
 * Information an extractor uses to extract singletons.
 *
 * @author Tim Boudreau
 */
final class SingletonExtractionStrategy<KeyType, R extends ParserRuleContext> implements Hashable {

    final SingletonKey<KeyType> key;
    final Predicate<RuleNode> ancestorQualifier;
    final Class<R> ruleType;
    final Function<R, KeyType> extractor;

    SingletonExtractionStrategy(SingletonKey<KeyType> key, Predicate<RuleNode> ancestorQualifier, Class<R> ruleType, Function<R, KeyType> extractor) {
        this.key = key;
        this.ancestorQualifier = ancestorQualifier;
        this.ruleType = ruleType;
        this.extractor = extractor;
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.hashObject(key);
        hasher.hashObject(ancestorQualifier);
        hasher.writeString(ruleType.getName());
        hasher.hashObject(extractor);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.key);
        hash = 79 * hash + Objects.hashCode(this.ancestorQualifier);
        hash = 79 * hash + Objects.hashCode(this.ruleType);
        hash = 79 * hash + Objects.hashCode(this.extractor);
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
        final SingletonExtractionStrategy<?, ?> other = (SingletonExtractionStrategy<?, ?>) obj;
        if (!Objects.equals(this.key, other.key)) {
            return false;
        }
        if (!Objects.equals(this.ancestorQualifier, other.ancestorQualifier)) {
            return false;
        }
        if (!Objects.equals(this.ruleType, other.ruleType)) {
            return false;
        }
        if (!Objects.equals(this.extractor, other.extractor)) {
            return false;
        }
        return true;
    }

}
