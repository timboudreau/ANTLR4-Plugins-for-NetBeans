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

import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;
import org.nemesis.extraction.key.SingletonKey;

/**
 * Represents a group of strategies for extracting singletons using various rule
 * nodes.
 *
 * @author Tim Boudreau
 */
final class SingletonExtractionStrategies<KeyType> implements Hashable {

    final SingletonKey<KeyType> key;
    final Set<SingletonExtractionStrategy<KeyType, ?>> infos;

    SingletonExtractionStrategies(SingletonKey<KeyType> key, Set<SingletonExtractionStrategy<KeyType, ?>> infos) {
        this.key = key;
        this.infos = new HashSet<>(infos);
    }

    void addAll(Set<SingletonExtractionStrategy<KeyType, ?>> all) {
        for (SingletonExtractionStrategy<KeyType, ?> so : all) {
            infos.add(so);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(key).append("<");
        for (Iterator<SingletonExtractionStrategy<KeyType, ?>> it = infos.iterator(); it.hasNext();) {
            SingletonExtractionStrategy<KeyType, ?> i = it.next();
            sb.append(i);
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.append('>').toString();
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.hashObject(key);
        for (SingletonExtractionStrategy i : infos) {
            hasher.hashObject(i);
        }
    }

    public SingletonEncounters<KeyType> extract(ParserRuleContext node, BooleanSupplier cancelled) {
        SingleVisitor<KeyType> v = new SingleVisitor<>(infos, cancelled);
        node.accept(v);
        return v.encounters;
    }

    static final class SingleVisitor<KeyType> extends AbstractParseTreeVisitor<Void> {

        private final SingletonExtractionStrategy<KeyType, ?>[] infos;
        private final int[] activations;
        private final SingletonEncounters<KeyType> encounters = new SingletonEncounters<>();
        private final BooleanSupplier cancelled;

        @SuppressWarnings("unchecked")
        SingleVisitor(Set<SingletonExtractionStrategy<KeyType, ?>> infos, BooleanSupplier cancelled) {
            this.cancelled = cancelled;
            this.infos = infos.toArray((SingletonExtractionStrategy<KeyType, ?>[]) Array.newInstance(SingletonExtractionStrategy.class, infos.size()));
            activations = new int[this.infos.length];
            for (int i = 0; i < this.infos.length; i++) {
                SingletonExtractionStrategy<?, ?> info = this.infos[i];
                if (info.ancestorQualifier == null) {
                    activations[i] = 1;
                }
            }
        }

        @Override
        public Void visitChildren(RuleNode node) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            if (node instanceof ParserRuleContext) {
                visitRule((ParserRuleContext) node);
            } else {
                super.visitChildren(node);
            }
            return null;
        }

        private <R extends ParserRuleContext> void runOne(SingletonExtractionStrategy<KeyType, R> extractor, ParserRuleContext ctx) {
            if (extractor.ruleType.isInstance(ctx)) {
                doRunOne(extractor, extractor.ruleType.cast(ctx));
            }
        }

        private <R extends ParserRuleContext> void doRunOne(SingletonExtractionStrategy<KeyType, R> extractor, R ctx) {
            KeyType found = extractor.extractor.apply(ctx);
            if (found != null) {
                encounters.add(found, ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1, extractor.ruleType);
            }
        }

        private void visitRule(ParserRuleContext rule) {
            boolean[] scratch = new boolean[infos.length];
            boolean anyActivated = false;
            for (int i = 0; i < infos.length; i++) {
                if (infos[i].ancestorQualifier != null) {
                    if (infos[i].ancestorQualifier.test(rule)) {
                        activations[i]++;
                        scratch[i] = true;
                        anyActivated = true;
                    }
                }
                if (activations[i] > 0) {
                    runOne(infos[i], rule);
                }
            }
            super.visitChildren(rule);
            if (anyActivated) {
                for (int i = 0; i < infos.length; i++) {
                    if (scratch[i]) {
                        activations[i]--;
                    }
                }
            }
        }
    }

}
