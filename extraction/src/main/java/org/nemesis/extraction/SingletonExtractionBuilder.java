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
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.extraction.key.SingletonKey;

/**
 *
 * @author Tim Boudreau
 */
public class SingletonExtractionBuilder<T extends ParserRuleContext, KeyType> {

    final ExtractorBuilder<T> bldr;
    final SingletonKey<KeyType> key;
    Predicate<RuleNode> ancestorQualifier;
    final Set<SingletonExtractionStrategy<KeyType, ?>> set;

    SingletonExtractionBuilder(ExtractorBuilder<T> bldr, SingletonKey<KeyType> key, Set<SingletonExtractionStrategy<KeyType, ?>> set) {
        this.set = set;
        this.bldr = bldr;
        this.key = key;
    }

    SingletonExtractionBuilder(ExtractorBuilder<T> bldr, SingletonKey<KeyType> key) {
        this.bldr = bldr;
        this.key = key;
        this.set = new HashSet<>(2);
    }

    public SingletonExtractionBuilder<T, KeyType> whenAncestorIs(Class<? extends RuleNode> ancestorType) {
        if (ancestorQualifier == null) {
            ancestorQualifier = new QualifierPredicate(ancestorType);
        } else {
            ancestorQualifier = ancestorQualifier.or(new QualifierPredicate(ancestorType));
        }
        return this;
    }

    public SingletonExtractionBuilder<T, KeyType> whenAncestorMatches(Predicate<RuleNode> qualifier) {
        if (ancestorQualifier == null) {
            ancestorQualifier = qualifier;
        } else {
            ancestorQualifier = ancestorQualifier.or(qualifier);
        }
        return this;
    }

    public <R extends ParserRuleContext> SingletonExtractionBuilderWithRule<T, KeyType, R> using(Class<R> ruleType) {
        return new SingletonExtractionBuilderWithRule<>(bldr, key, ancestorQualifier, ruleType, set);
    }

    public static final class FinishableSingletonExtractorBuilder<T extends ParserRuleContext, KeyType> {

        final ExtractorBuilder<T> bldr;
        final SingletonKey<KeyType> key;
        final Set<SingletonExtractionStrategy<KeyType, ?>> set;

        FinishableSingletonExtractorBuilder(ExtractorBuilder<T> bldr, SingletonKey<KeyType> key, Set<SingletonExtractionStrategy<KeyType, ?>> set) {
            this.bldr = bldr;
            this.key = key;
            this.set = set;
        }

        public ExtractorBuilder<T> finishObjectExtraction() {
            return bldr.addSingletons(key, set);
        }

        public SingletonExtractionBuilder<T, KeyType> whenAncestorIs(Class<? extends RuleNode> ancestorType) {
            SingletonExtractionBuilder<T, KeyType> result = new SingletonExtractionBuilder<>(bldr, key, set);
            result.ancestorQualifier = new QualifierPredicate(ancestorType);
            return result;
        }

        public SingletonExtractionBuilder<T, KeyType> whenAncestorMatches(Predicate<RuleNode> qualifier) {
            SingletonExtractionBuilder<T, KeyType> result = new SingletonExtractionBuilder<>(bldr, key, set);
            result.ancestorQualifier = qualifier;
            return result;
        }

        public <R extends ParserRuleContext> SingletonExtractionBuilderWithRule<T, KeyType, R> using(Class<R> ruleType) {
            return new SingletonExtractionBuilderWithRule<>(bldr, key, null, ruleType, set);
        }
    }

    public static final class SingletonExtractionBuilderWithRule<T extends ParserRuleContext, KeyType, R extends ParserRuleContext> {

        final ExtractorBuilder<T> bldr;
        final SingletonKey<KeyType> key;
        final Predicate<RuleNode> ancestorQualifier;
        final Class<R> ruleType;
        final Set<SingletonExtractionStrategy<KeyType, ?>> set;

        SingletonExtractionBuilderWithRule(ExtractorBuilder<T> bldr, SingletonKey<KeyType> key, Predicate<RuleNode> ancestorQualifier, Class<R> ruleType, Set<SingletonExtractionStrategy<KeyType, ?>> set) {
            this.bldr = bldr;
            this.key = key;
            this.ruleType = ruleType;
            this.ancestorQualifier = ancestorQualifier;
            this.set = set;
        }

        public FinishableSingletonExtractorBuilder<T, KeyType> extractingObjectWith(Function<R, KeyType> func) {
            SingletonExtractionStrategy<KeyType, R> info = new SingletonExtractionStrategy<>(key, ancestorQualifier, ruleType, func, null);
            set.add(info);
            return new FinishableSingletonExtractorBuilder<>(bldr, key, set);
        }

        public FinishableSingletonExtractorBuilder<T, KeyType> extractionObjectAndBoundsWith(BiConsumer<R, TriConsumer<KeyType, Integer, Integer>> consumer) {
            SingletonExtractionStrategy<KeyType, R> info = new SingletonExtractionStrategy<>(key, ancestorQualifier, ruleType, null, notNull("consumer", consumer));
            set.add(info);
            return new FinishableSingletonExtractorBuilder<>(bldr, key, set);
        }
    }
}
