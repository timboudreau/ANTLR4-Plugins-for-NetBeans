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
