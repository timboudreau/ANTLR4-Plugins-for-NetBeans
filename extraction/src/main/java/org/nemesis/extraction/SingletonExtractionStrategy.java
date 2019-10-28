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

import com.mastfrog.function.TriConsumer;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;
import org.nemesis.extraction.key.SingletonKey;

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
    final BiConsumer<R, TriConsumer<KeyType, Integer, Integer>> consumer;

    SingletonExtractionStrategy(SingletonKey<KeyType> key, Predicate<RuleNode> ancestorQualifier, Class<R> ruleType, Function<R, KeyType> extractor, BiConsumer<R, TriConsumer<KeyType, Integer, Integer>> consumer) {
        this.key = key;
        this.ancestorQualifier = ancestorQualifier;
        this.ruleType = ruleType;
        this.extractor = extractor;
        this.consumer = consumer;
        assert (consumer == null) != (extractor == null);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(ruleType.getSimpleName());
        if (ancestorQualifier != null) {
            sb.append("qual=").append(ancestorQualifier).append(", ");
        }
        sb.append(extractor);
        return sb.toString();
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
        return Objects.equals(this.extractor, other.extractor);
    }

}
