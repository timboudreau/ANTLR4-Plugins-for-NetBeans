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

import java.util.BitSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedRegionReferenceSetsBuilder;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.data.named.NamedSemanticRegionsBuilder;
import org.nemesis.extraction.key.NamedRegionKey;
import com.mastfrog.graph.IntGraph;
import com.mastfrog.graph.StringGraph;
import com.mastfrog.util.collections.IntSet;
import java.util.EnumMap;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import org.nemesis.data.named.ContentsChecksums;

/**
 *
 * @author Tim Boudreau
 */
class NamesAndReferencesExtractionStrategy<T extends Enum<T>> implements Hashable {

    private final Class<T> keyType;
    private final NamedRegionKey<T> namePositionKey;
    private final NamedRegionKey<T> ruleRegionKey;
    private final NameExtractionStrategy<?, T>[] nameExtractors;
    private final ReferenceExtractorPair<?>[] referenceExtractors;
    private final String scopingDelimiter;
    private static final Logger LOG = Logger.getLogger(NamesAndReferencesExtractionStrategy.class.getName());
    private final SummingFunction summer;

    @SuppressWarnings("unchecked")
    NamesAndReferencesExtractionStrategy(Class<T> keyType, NamedRegionKey<T> namePositionKey, NamedRegionKey<T> ruleRegionKey, Set<NameExtractionStrategy<?, T>> nameExtractors, Set<ReferenceExtractorPair<T>> referenceExtractors, String scopingDelimiter, SummingFunction summer) {
        this.keyType = keyType;
        assert namePositionKey != null || ruleRegionKey != null;
        this.namePositionKey = namePositionKey;
        this.ruleRegionKey = ruleRegionKey;
        this.nameExtractors = nameExtractors.toArray((NameExtractionStrategy<?, T>[]) new NameExtractionStrategy<?, ?>[nameExtractors.size()]);
        this.referenceExtractors = referenceExtractors.toArray(new ReferenceExtractorPair<?>[referenceExtractors.size()]);
        this.scopingDelimiter = scopingDelimiter;
        this.summer = summer;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(NamesAndReferencesExtractionStrategy.class.getName());
        sb.append('@').append(System.identityHashCode(this)).append('<');
        sb.append(keyType.getClass().getSimpleName()).append(':');
        sb.append("nameExtractors={");
        for (int i = 0; i < nameExtractors.length; i++) {
            sb.append(nameExtractors[i]);
            if (i != nameExtractors.length - 1) {
                sb.append(", ");
            }
        }
        if (scopingDelimiter != null) {
            sb.append("}, scopingDelimiter={").append(scopingDelimiter);
        }
        sb.append("}, referenceExtractors={");
        for (int i = 0; i < referenceExtractors.length; i++) {
            sb.append(referenceExtractors[i]);
            if (i != referenceExtractors.length - 1) {
                sb.append(", ");
            }
        }
        sb.append('}');
        return sb.append('>').toString();
    }

    void invoke(ParserRuleContext ctx, NameInfoStore store, BooleanSupplier cancelled, ToIntFunction<? super ParserRuleContext> ruleIdMapper) {
        ContentsChecksums.Builder sumBuilder = summer != null ? ContentsChecksums.builder() : null;
        RuleNameAndBoundsVisitor v = new RuleNameAndBoundsVisitor(cancelled, sumBuilder, summer, ruleIdMapper);
        ctx.accept(v);
        NamedSemanticRegions<T> names = v.namesBuilder == null ? null : v.namesBuilder.build();
        NamedSemanticRegions<T> ruleBounds = v.ruleBoundsBuilder.build();
        if (namePositionKey != null) {
            store.addNamedRegions(scopingDelimiter, namePositionKey, names);
        }
        if (ruleRegionKey != null) {
            store.addNamedRegions(scopingDelimiter, ruleRegionKey, ruleBounds);
        }
        if (v.namesBuilder != null) {
            v.namesBuilder.retrieveDuplicates((name, duplicates) -> {
                store.addDuplicateNamedRegions(namePositionKey, name, duplicates);
            });
        }
        if (v.ruleBoundsBuilder != null) {
            v.ruleBoundsBuilder.retrieveDuplicates((name, duplicates) -> {
                store.addDuplicateNamedRegions(ruleRegionKey, name, duplicates);
            });
            if (sumBuilder != null) {
                store.addChecksums(ruleRegionKey, sumBuilder.build(ruleBounds));
            }
        }

        ReferenceExtractorVisitor v1 = new ReferenceExtractorVisitor(ruleBounds);
        ctx.accept(v1);
        v1.conclude(store);
        if (ruleRegionKey != null && this.namePositionKey != null) {
            store.addNameAndBoundsKeyPair(new NameAndBoundsPair<>(ruleRegionKey, namePositionKey));
        }
        store.noteRuleIdMapping(keyType, v.ruleIdsForKinds);
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeString(keyType.getName());
        hasher.hashObject(namePositionKey);
        hasher.hashObject(ruleRegionKey);
        if (scopingDelimiter != null) {
            hasher.writeString(scopingDelimiter);
        }
        for (NameExtractionStrategy<?, ?> ne : nameExtractors) {
            hasher.hashObject(ne);
        }
        for (ReferenceExtractorPair<?> p : referenceExtractors) {
            hasher.hashObject(p);
        }
        if (summer != null) {
            hasher.hashObject(summer);
        }
    }

    class ReferenceExtractorVisitor extends AbstractParseTreeVisitor<Void> {

        int[] lengths;
        int[][] activations;
        int unknownCount = 0;
        //            Set<NameAndOffsets> unknown = new HashSet<>();
        SemanticRegions.SemanticRegionsBuilder<UnknownNameReference<T>> unknown = SemanticRegions.builder(UnknownNameReference.class);
        ReferenceExtractionStrategy<?, ?>[][] infos;
        private final NamedSemanticRegions<T> regions;
        NamedRegionReferenceSetsBuilder<T>[] refs;
        private final BitSet[][] references;
        private final BitSet[][] reverseReferences;

        @SuppressWarnings("unchecked")
        ReferenceExtractorVisitor(NamedSemanticRegions<T> regions) {
            activations = new int[referenceExtractors.length][];
            lengths = new int[referenceExtractors.length];
            references = new BitSet[referenceExtractors.length][regions.size()];
            reverseReferences = new BitSet[referenceExtractors.length][regions.size()];
            infos = new ReferenceExtractionStrategy[referenceExtractors.length][];
            refs = (NamedRegionReferenceSetsBuilder<T>[]) new NamedRegionReferenceSetsBuilder<?>[referenceExtractors.length];
            for (int i = 0; i < referenceExtractors.length; i++) {
                ReferenceExtractionStrategy[] ex = referenceExtractors[i].referenceExtractors.toArray(new ReferenceExtractionStrategy[referenceExtractors[i].referenceExtractors.size()]);
                lengths[i] = ex.length;
                infos[i] = ex;
                activations[i] = new int[ex.length];
                refs[i] = regions.newReferenceSetsBuilder();
                for (int j = 0; j < ex.length; j++) {
                    if (ex[i].ancestorQualifier == null) {
                        activations[i][j] = 1;
                    }
                }
                for (int j = 0; j < regions.size(); j++) {
                    references[i][j] = new BitSet(regions.size());
                    reverseReferences[i][j] = new BitSet(regions.size());
                }
            }
            this.regions = regions;
        }

        @SuppressWarnings("unchecked")
        void conclude(NameInfoStore store) {
            for (int i = 0; i < referenceExtractors.length; i++) {
                ReferenceExtractorPair r = referenceExtractors[i];
                store.addReferences(r.refSetKey, refs[i].build());
                IntGraph graph = IntGraph.create(reverseReferences[i], references[i]);
                StringGraph stringGraph = StringGraph.create(graph, regions.nameArray());
                store.addReferenceGraph(r.refSetKey, stringGraph);
                store.addUnknownReferences(r.refSetKey, unknown.build());
            }
        }

        private <L extends ParserRuleContext> NameAndOffsets doRunOne(ReferenceExtractionStrategy<L, ?> ext, L nd) {
            return ext.offsetsExtractor.apply(nd);
        }

        private <L extends ParserRuleContext> NameAndOffsets runOne(ReferenceExtractionStrategy<L, ?> ext, RuleNode nd) {
            return doRunOne(ext, ext.ruleType.cast(nd));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Void visitChildren(RuleNode node) {
            boolean[][] activeScratch = new boolean[referenceExtractors.length][];
            for (int i = 0; i < lengths.length; i++) {
                activeScratch[i] = new boolean[lengths[i]];
                for (int j = 0; j < lengths[i]; j++) {
                    ReferenceExtractionStrategy<?, ?> info = infos[i][j];
                    if (info.ancestorQualifier != null) {
                        if (info.ancestorQualifier.test(node)) {
                            activeScratch[i][j] = true;
                            activations[i][j]++;
                        }
                    }
                    if (activations[i][j] > 0 && info.ruleType.isInstance(node)) {
                        NameAndOffsets referenceOffsets = runOne(info, node);
                        if (referenceOffsets != null) {
                            if (regions.contains(referenceOffsets.name)) {
                                refs[i].addReference(referenceOffsets.name, referenceOffsets.start, referenceOffsets.end);
                                NamedSemanticRegion<T> containedBy = regions.index().regionAt(referenceOffsets.start);
                                int referencedIndex = regions.indexOf(referenceOffsets.name);
                                if (containedBy != null && referencedIndex != -1) {
                                    assert containedBy.contains(referenceOffsets.start) : "Index returned bogus result for position " + referenceOffsets.start + ": " + containedBy + " from " + regions.index() + "; code:\n" + regions.toCode();
                                    int referenceIndex = containedBy.index();
                                    references[i][referencedIndex].set(referenceIndex);
                                    reverseReferences[i][referenceIndex].set(referencedIndex);
                                }
                            } else {
                                System.out.println("ADD AN UNKNOWN: " + referenceOffsets.name + " @ " + referenceOffsets.start + ":" + referenceOffsets.end);
                                T kind = referenceOffsets instanceof NamedRegionData<?> && ((NamedRegionData<?>) referenceOffsets).kind != null ? (T) ((NamedRegionData<?>) referenceOffsets).kind : null;
                                if (kind != null) {
                                    unknown.add(new UnknownNameReferenceImpl(kind, referenceOffsets.start, referenceOffsets.end, referenceOffsets.name, unknownCount++), referenceOffsets.start, referenceOffsets.end);
                                } else {
                                    unknown.add(new UnknownNameReferenceImpl(regions.kindType(), referenceOffsets.start, referenceOffsets.end, referenceOffsets.name, unknownCount++), referenceOffsets.start, referenceOffsets.end);
                                }
                            }
                        }
                    }
                }
            }
            super.visitChildren(node);
            for (int i = 0; i < lengths.length; i++) {
                for (int j = 0; j < lengths[i]; j++) {
                    if (activeScratch[i][j]) {
                        activations[i][j]--;
                    }
                }
            }
            return null;
        }
    }

    class RuleNameAndBoundsVisitor extends AbstractParseTreeVisitor<Void> {

        // This is all old-school, array-based, 1990s programming for a reason
        // - this code runs thousands of times, potentially every time a key
        // is pressed.  It needs to be fast and low-allocation more than it
        // needs to be pretty.
        private final NamedSemanticRegionsBuilder<T> namesBuilder;
        private final NamedSemanticRegionsBuilder<T> ruleBoundsBuilder;
        private final int[] activations;
        private final BooleanSupplier cancelled;
        final LinkedList<String>[] nameStacks;
        private final ContentsChecksums.Builder sums;
        private final SummingFunction summer;
        private final ToIntFunction<? super ParserRuleContext> ruleIdMapper;
        private final EnumMap<T, IntSet> ruleIdsForKinds = new EnumMap<>(keyType);

        @SuppressWarnings("unchecked")
        RuleNameAndBoundsVisitor(BooleanSupplier cancelled, ContentsChecksums.Builder sums, SummingFunction summer, ToIntFunction<? super ParserRuleContext> ruleIdMapper) {
            this.cancelled = cancelled;
            this.sums = sums;
            this.summer = summer;
            this.ruleIdMapper = ruleIdMapper;
            activations = new int[nameExtractors.length];
            nameStacks = (LinkedList<String>[]) new LinkedList<?>[nameExtractors.length];
            for (int i = 0; i < activations.length; i++) {
                if (nameExtractors[i].ancestorQualifier == null) {
                    activations[i] = 1;
                }
                nameStacks[i] = new LinkedList<>();
            }
            if (namePositionKey != null) {
                namesBuilder = NamedSemanticRegions.builder(keyType);
            } else {
                namesBuilder = null;
            }
            ruleBoundsBuilder = NamedSemanticRegions.builder(keyType);
            assert namesBuilder != null || ruleBoundsBuilder != null;
        }

        @Override
        public Void visitChildren(RuleNode node) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            if (node instanceof ParserRuleContext) {
                try {
                    // Try not to wreak complete havoc with the rest of
                    // extraction
                    onVisit((ParserRuleContext) node);
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, "Exception visiting "
                            + node.getText()
                            + " (" + node.getClass().getSimpleName() + ")", ex);
                }
            } else {
                super.visitChildren(node);
            }
            return null;
        }

        private void onVisit(ParserRuleContext node) {
            boolean[] activationScratch = new boolean[nameExtractors.length];
            for (int i = 0; i < nameExtractors.length; i++) {
                if (nameExtractors[i].ancestorQualifier != null) {
                    if (nameExtractors[i].ancestorQualifier.test(node)) {
                        activationScratch[i] = true;
                        activations[i]++;
                    }
                }
            }
            try {
                String[] foundNames = scopingDelimiter == null ? null : new String[nameExtractors.length];
                boolean anyFoundNames = false;
                for (int i = 0; i < nameExtractors.length; i++) {
                    if (activations[i] > 0) {
                        String[] nm = runOne(node, nameExtractors[i], i);
                        if (nm != null && scopingDelimiter != null && foundNames[i] == null) {
                            foundNames[i] = nm[0];
                            if (scopingDelimiter != null) {
                                nameStacks[i].push(nm[1]);
                                anyFoundNames = true;
                            }
                        }
                    }
                }
                super.visitChildren(node);
                if (anyFoundNames) {
                    for (int i = 0; i < nameExtractors.length; i++) {
                        if (foundNames[i] != null) {
                            String popped = nameStacks[i].pop();
                        }
                    }
                }
            } finally {
                for (int i = 0; i < activationScratch.length; i++) {
                    if (activationScratch[i]) {
                        activations[i]--;
                    }
                }
            }
        }

        private <R extends ParserRuleContext> String[] runOne(ParserRuleContext node, NameExtractionStrategy<R, T> nameExtractor, int nameExtractorIndex) {
            if (nameExtractor.type.isInstance(node)) {
                return doRunOne(nameExtractor.type.cast(node), nameExtractor, nameExtractorIndex);
            }
            return null;
        }

        private <R extends ParserRuleContext> void mapKindToRuleId(R ruleNode, NamedRegionData<T> nm) {
            int ruleId = ruleNode.getRuleIndex();  //ruleIdMapper.applyAsInt(ruleNode);
//            System.out.println("RuleIDMapper gets " + ruleId + " real ruleId "
//                    + ruleNode.getRuleIndex() + " for " + nm.name + " " + nm.kind
//                    + " " + nm.start + ":" + nm.end + " for "
//                    + ruleNode.getClass().getSimpleName());
            if (ruleId >= 0) {
                IntSet set = ruleIdsForKinds.get(nm.kind);
                if (set == null) {
                    set = IntSet.create(40);
                    ruleIdsForKinds.put(nm.kind, set);
                }
//                System.out.println("  add to set for " + nm.kind + " " + set);
                set.add(ruleId);
            }
        }

        private <R extends ParserRuleContext> String[] doRunOne(R node, NameExtractionStrategy<R, T> e, int nameExtractorIndex) {
            String[] result = new String[2];
            int count = e.find(node, (NamedRegionData<T> nm, TerminalNode tn) -> {
                // If we are iterating TerminalNodes, tn will be non-null; otherwise
                // it will be null and we are doing single extraction - this is so we can,
                // for example, in an ANTLR grammar for ANTLR, create token names and
                // references from an import tokens statement where there is no rule
                // definition, but we should not point the definition position for all
                // of the names to the same spot
                boolean added = false;
                if (nm != null) {
                    if (nm.kind != null) {
                        mapKindToRuleId(node, nm);
                    }
                    String name = nm.name(nameStacks == null ? null : nameStacks[nameExtractorIndex], scopingDelimiter);
                    result[0] = name;
                    result[1] = nm.name();
                    if (namesBuilder != null) {
                        // XXX, the names extractor actually needs to return the name AND the offsets of the name
                        // use the same code we use for finding the reference
                        added = namesBuilder.add(name, nm.kind, nm.start, nm.end);
                    }
                    if ((added || namesBuilder == null) && ruleBoundsBuilder != null) {
                        if (tn == null) {
                            added = ruleBoundsBuilder.add(name, nm.kind, node.start.getStartIndex(), node.stop.getStopIndex() + 1);
                            if (added) {
                                sum(node);
                            }
                            return added;
                        } else {
                            Token tok = tn.getSymbol();
                            added = ruleBoundsBuilder.add(name, nm.kind, tok.getStartIndex(), tok.getStopIndex() + 1);
                            if (added) {
                                sum(node);
                            }
                            return added;
                        }
                    }
                }
                return added;
            });
            return result;
        }

        private void sum(ParserRuleContext ctx) {
            if (summer == null) {
                return;
            }
            this.sums.add(summer.sum(ctx));
        }
    }
}
