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
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.ContentsChecksums;
import org.nemesis.extraction.key.RegionsKey;

/**
 *
 * @author Tim Boudreau
 */
final class RegionExtractionStrategies<RegionKeyType> implements Hashable {

    final RegionsKey<RegionKeyType> key;
    final Set<? extends RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors;
    final Set<? extends TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors;
    final SummingFunction summer;
    private static final Logger LOG = Logger.getLogger(RegionExtractionStrategies.class.getName());

    RegionExtractionStrategies(RegionsKey<RegionKeyType> key, Set<? extends RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors, Set<? extends TokenRegionExtractionStrategy<RegionKeyType>> tokenExtractors, SummingFunction summer) {
        this.key = key;
        this.extractors = extractors;
        this.tokenExtractors = tokenExtractors;
        this.summer = summer;
        LOG.log(Level.FINE, "Create a region extractors strategy for {0} "
                + "with {1} token extractors and {2} rule extractors summer {3}",
                new Object[]{key, tokenExtractors.size(), extractors.size(), summer});
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
        if (summer != null) {
            summer.hashInto(hasher);
        }
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

    V createVisitor(BiPredicate<RegionKeyType, int[]> c, BooleanSupplier cancelled) {
        return new V<>(key.type(), c, extractors, cancelled, summer);
    }

    ContentsChecksums<SemanticRegion<RegionKeyType>> retrieveChecksums(V<RegionKeyType> v, SemanticRegions<RegionKeyType> regions) {
        if (v.sumsBuilder == null) {
            return ContentsChecksums.empty();
        }
        ContentsChecksums<SemanticRegion<RegionKeyType>> result = v.sumsBuilder.build(regions);
        return result;
    }

    static class V<RegionKeyType> extends AbstractParseTreeVisitor<Void> {

        private final BiPredicate<RegionKeyType, int[]> consumer;
        private final RegionExtractionStrategy<?, ?, ?>[] extractors;
        private final int[] activatedCount;
        private final BooleanSupplier cancelled;
        private final SummingFunction summer;
        final ContentsChecksums.Builder sumsBuilder;

        V(Class<RegionKeyType> keyType, BiPredicate<RegionKeyType, int[]> consumer,
                Set<? extends RegionExtractionStrategy<RegionKeyType, ?, ?>> extractors,
                BooleanSupplier cancelled,
                SummingFunction summer) {
            this.cancelled = cancelled;
            this.extractors = extractors.toArray(new RegionExtractionStrategy<?, ?, ?>[extractors.size()]);
            this.summer = summer;
            this.activatedCount = new int[this.extractors.length];
            for (int i = 0; i < this.extractors.length; i++) {
                if (this.extractors[i].ancestorQualifier == null) {
                    activatedCount[i] = 1;
                }
            }
            if (summer == null) {
                this.consumer = consumer;
                this.sumsBuilder = null;
            } else {
                sumsBuilder = ContentsChecksums.builder();
                this.consumer = new SumsWrapper<>(consumer, summer, sumsBuilder);
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
                    runOne(node, e, i);
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

        private <RuleType extends RuleNode, TType> void runOne(RuleNode node, RegionExtractionStrategy<RegionKeyType, RuleType, TType> e, int index) {
            if (e.ruleType.isInstance(node)) {
                if (e.qualifier != null && !e.qualifier.test(node)) {
                    return;
                }
                doRunOne(e.ruleType.cast(node), e, index);
            }
        }

        private <RuleType extends RuleNode, TType> void doRunOne(RuleType node,
                RegionExtractionStrategy<RegionKeyType, RuleType, TType> e, int index) {
            if (summer == null) {
                e.extract(node, consumer);
            } else {
                if (node instanceof ParserRuleContext) {
                    // Could be written more cleanly, but this is performance-sensitive
                    ((SumsWrapper<?>) consumer).node = ((ParserRuleContext) node);
                    e.extract(node, consumer);
                    ((SumsWrapper<?>) consumer).node = null;
                } else {
                    e.extract(node, consumer);
                }
            }
        }

        static class SumsWrapper<RegionKeyType> implements BiPredicate<RegionKeyType, int[]> {

            private final BiPredicate<RegionKeyType, int[]> delegate;
            private final SummingFunction summer;
            private final ContentsChecksums.Builder sumsBuilder;
            ParserRuleContext node;

            public SumsWrapper(BiPredicate<RegionKeyType, int[]> delegate, SummingFunction summer, ContentsChecksums.Builder sumsBuilder) {
                this.delegate = delegate;
                this.summer = summer;
                this.sumsBuilder = sumsBuilder;
            }

            @Override
            public boolean test(RegionKeyType key, int[] bounds) {
                boolean result = delegate.test(key, bounds);
                // Here we need to actually KNOW if something was added and how many -
                // as in, if it was a duplicate, don't add an entry

                // Hmm, maybe get before/after size on the regions being built?
                if (result) {
                    if (bounds != null && bounds.length >= 2 && bounds[1] > bounds[0] && node instanceof ParserRuleContext) {
                        long sum = summer.sum(node, bounds[0], bounds[1]);
                        LOG.log(Level.FINEST, "Checksum for {0}:{1} is {2}",
                                new Object[]{bounds[0], bounds[1], sum});
                        sumsBuilder.add(sum);
                    } else {
                        sumsBuilder.add(0);
                    }
                }
                return result;
            }
        }
    }
}
