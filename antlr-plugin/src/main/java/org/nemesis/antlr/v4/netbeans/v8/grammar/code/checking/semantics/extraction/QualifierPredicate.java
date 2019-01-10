package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.util.Objects;
import java.util.function.Predicate;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;

/**
 *
 * @author Tim Boudreau
 */
final class QualifierPredicate implements Predicate<RuleNode>, Hashable {

    private final Class<? extends RuleNode> qualifyingType;

    QualifierPredicate(Class<? extends RuleNode> qualifyingType) {
        this.qualifyingType = qualifyingType;
    }

    @Override
    public boolean test(RuleNode t) {
        return qualifyingType.isInstance(t);
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeString("QP");
        hasher.writeString(qualifyingType.getName());
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(this.qualifyingType);
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
        final QualifierPredicate other = (QualifierPredicate) obj;
        return Objects.equals(this.qualifyingType, other.qualifyingType);
    }

}
