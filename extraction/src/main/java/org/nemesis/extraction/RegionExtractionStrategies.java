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

import java.util.Iterator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;
import org.nemesis.data.SemanticRegions;
import org.nemesis.extraction.key.RegionsKey;

/**
 *
 * @author Tim Boudreau
 */
final class RegionExtractionStrategies<RegionKeyType> implements Hashable {

    final RegionsKey<RegionKeyType> key;
    final Set<? extends RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors;
    final Set<? extends TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors;

    private static final Logger LOG = Logger.getLogger(RegionExtractionStrategies.class.getName());

    RegionExtractionStrategies(RegionsKey<RegionKeyType> key, Set<? extends RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors, Set<? extends TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors) {
        this.key = key;
        this.extractors = extractors;
        this.tokenExtractors = tokenExtractors;
        LOG.log(Level.FINE, "Create a region extractors strategy for {0} "
                + "with {1} token extractors and {2} rule extractors",
                new Object[]{key, tokenExtractors.size(), extractors.size()});
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(RegionExtractionStrategies.class.getName());
        sb.append('@').append(System.identityHashCode(this)).append(":");
        if (tokenExtractors != null && !tokenExtractors.isEmpty()) {
            sb.append("tokens=[");
            Iterator<? extends TokenRegionExtractionStrategy<RegionKeyType>> it;
            for (it = tokenExtractors.iterator(); it.hasNext();) {
                TokenRegionExtractionStrategy<?> t = it.next();
                sb.append(t);
            }
            sb.append(']');
            it = null;
            if (extractors != null && !extractors.isEmpty()) {
                sb.append(", ");
            }
        }
        if (extractors != null && !extractors.isEmpty()) {
            sb.append("rules=[");
            Iterator<? extends RegionExtractionStrategy<RegionKeyType, ?, ?>> it;
            for (it = extractors.iterator(); it.hasNext();) {
                RegionExtractionStrategy<RegionKeyType, ?, ?> e = it.next();
                sb.append(e);
                if (it.hasNext()) {
                    sb.append(", ");
                }
            }
            it = null;
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeInt(190019943);
        key.hashInto(hasher);
        for (RegionExtractionStrategy<?, ?, ?> e : extractors) {
            hasher.hashObject(e);
        }
        if (tokenExtractors != null) {
            for (TokenRegionExtractionStrategy s : tokenExtractors) {
                hasher.hashObject(s);
            }
        }
    }

    boolean runTokenExtrationStrategies(Consumer<SemanticRegions<RegionKeyType>> tokenRegions, Iterable<? extends Token> tokens, BooleanSupplier cancelled) {
        LOG.log(Level.FINER, "Run token extractions on {0} on stream {1}", new Object[]{this, tokens});
        if (tokenExtractors == null || tokenExtractors.isEmpty()) {
            LOG.log(Level.FINER, "No token extractors in {0}", this);
            return false;
        } else {
            LOG.log(Level.FINEST, "Will run {0} extractors for {1}", new Object[]{tokenExtractors.size(), this});
        }
        SemanticRegions<RegionKeyType> result = null;
        for (TokenRegionExtractionStrategy<RegionKeyType> strategy : tokenExtractors) {
            if (cancelled.getAsBoolean()) {
                LOG.log(Level.FINE, "Token extraction cancelled on {0}", this);
                return false;
            }
            SemanticRegions<RegionKeyType> curr = strategy.scan(tokens, cancelled);
            LOG.log(Level.FINEST, "Strategy {0} found {1} items", new Object[]{strategy, curr == null ? -1 : curr.size()});
            if (result == null || result.isEmpty()) {
                result = curr;
            } else if (!curr.isEmpty()) {
                SemanticRegions<RegionKeyType> nue = result.combineWith(curr);
                LOG.log(Level.FINEST, "Merged {0} and {1} to get {2}", new Object[]{result, curr, nue});
                result = nue;
            }
        }
        if (result != null && !result.isEmpty()) {
            LOG.log(Level.FINEST, "Passing result to {0} - {1}", new Object[]{tokenRegions, result});
            tokenRegions.accept(result);
        }
        return result != null && !result.isEmpty();
    }

    ParseTreeVisitor<Void> createVisitor(BiConsumer<RegionKeyType, int[]> c, BooleanSupplier cancelled) {
        return new V<>(key.type(), c, extractors, cancelled);
    }

    static class V<RegionKeyType> extends AbstractParseTreeVisitor<Void> {

        private final BiConsumer<RegionKeyType, int[]> c;
        private final RegionExtractionStrategy<?, ?, ?>[] extractors;
        private final int[] activatedCount;
        private final BooleanSupplier cancelled;

        V(Class<RegionKeyType> keyType, BiConsumer<RegionKeyType, int[]> c, Set<? extends RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors, BooleanSupplier cancelled) {
            this.c = c;
            this.cancelled = cancelled;
            this.extractors = extractors.toArray(new RegionExtractionStrategy<?, ?, ?>[extractors.size()]);
            this.activatedCount = new int[this.extractors.length];
            for (int i = 0; i < this.extractors.length; i++) {
                if (this.extractors[i].ancestorQualifier == null) {
                    activatedCount[i] = 1;
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Void visitChildren(RuleNode node) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            boolean[] scratch = new boolean[extractors.length];
            for (int i = 0; i < scratch.length; i++) {
                RegionExtractionStrategy<RegionKeyType, ?, ?> e = (RegionExtractionStrategy<RegionKeyType, ?, ?>) extractors[i];
                if (e.ancestorQualifier != null) {
                    if (e.ancestorQualifier.test(node)) {
                        activatedCount[i]++;
                        scratch[i] = true;
                    }
                }
                if (activatedCount[i] > 0) {
                    runOne(node, e);
                }
            }
            super.visitChildren(node);
            for (int i = 0; i < scratch.length; i++) {
                if (scratch[i]) {
                    activatedCount[i]--;
                }
            }
            return null;
        }

        private <RuleType extends RuleNode, TType> void runOne(RuleNode node, RegionExtractionStrategy<RegionKeyType, RuleType, TType> e) {
            if (e.ruleType.isInstance(node)) {
                doRunOne(e.ruleType.cast(node), e);
            }
        }

        private <RuleType extends RuleNode, TType> void doRunOne(RuleType node, RegionExtractionStrategy<RegionKeyType, RuleType, TType> e) {
            e.extract(node, c);
        }
    }
}
