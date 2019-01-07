package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.util.BitSet;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.Hashable;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.SemanticRegions;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.graph.BitSetGraph;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.graph.BitSetStringGraph;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedRegionReferenceSetsBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedSemanticRegions;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedSemanticRegionsBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.NamedRegionKey;

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

    @SuppressWarnings("unchecked")
    NamesAndReferencesExtractionStrategy(Class<T> keyType, NamedRegionKey<T> namePositionKey, NamedRegionKey<T> ruleRegionKey, Set<NameExtractionStrategy<?, T>> nameExtractors, Set<ReferenceExtractorPair<T>> referenceExtractors) {
        this.keyType = keyType;
        assert namePositionKey != null || ruleRegionKey != null;
        this.namePositionKey = namePositionKey;
        this.ruleRegionKey = ruleRegionKey;
        this.nameExtractors = nameExtractors.toArray((NameExtractionStrategy<?, T>[]) new NameExtractionStrategy<?, ?>[nameExtractors.size()]);
        this.referenceExtractors = referenceExtractors.toArray(new ReferenceExtractorPair<?>[referenceExtractors.size()]);
    }

    void invoke(ParserRuleContext ctx, NameInfoStore store) {
        RuleNameAndBoundsVisitor v = new RuleNameAndBoundsVisitor();
        ctx.accept(v);
        NamedSemanticRegions<T> names = v.namesBuilder == null ? null : v.namesBuilder.build();
        NamedSemanticRegions<T> ruleBounds = v.ruleBoundsBuilder.build();
        if (namePositionKey != null) {
            store.addNamedRegions(namePositionKey, names);
        }
        if (ruleRegionKey != null) {
            store.addNamedRegions(ruleRegionKey, ruleBounds);
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
        }
        ReferenceExtractorVisitor v1 = new ReferenceExtractorVisitor(ruleBounds);
        ctx.accept(v1);
        v1.conclude(store);
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeString(keyType.getName());
        hasher.hashObject(namePositionKey);
        hasher.hashObject(ruleRegionKey);
        for (NameExtractionStrategy<?, ?> ne : nameExtractors) {
            hasher.hashObject(ne);
        }
        for (ReferenceExtractorPair<?> p : referenceExtractors) {
            hasher.hashObject(p);
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
                BitSetGraph graph = new BitSetGraph(reverseReferences[i], references[i]);
                BitSetStringGraph stringGraph = BitSetStringGraph.create(graph, regions.nameArray());
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
                                    //                                        System.out.println("REGION AT " + referenceOffsets.start + " IS " + containedBy.start() + ":" + containedBy.end() + " - " + containedBy.name());
                                    assert containedBy.containsPosition(referenceOffsets.start) : "Index returned bogus result for position " + referenceOffsets.start + ": " + containedBy + " from " + regions.index() + "; code:\n" + regions.toCode();
                                    //                                        System.out.println("ENCOUNTERED " + referenceOffsets + " index " + referencedIndex + " inside " + containedBy.name() + " index " + containedBy.index() + " in " + regions);
                                    int referenceIndex = containedBy.index();
                                    references[i][referencedIndex].set(referenceIndex);
                                    reverseReferences[i][referenceIndex].set(referencedIndex);
                                }
                            } else {
                                T kind = referenceOffsets instanceof NamedRegionData<?> && ((NamedRegionData<?>) referenceOffsets).kind != null ? (T) ((NamedRegionData<?>) referenceOffsets).kind : null;
                                unknown.add(new UnknownNameReferenceImpl(kind, referenceOffsets.start, referenceOffsets.end, referenceOffsets.name, unknownCount++), referenceOffsets.start, referenceOffsets.end);
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

        private final NamedSemanticRegionsBuilder<T> namesBuilder;
        private final NamedSemanticRegionsBuilder<T> ruleBoundsBuilder;
        private final int[] activations;

        RuleNameAndBoundsVisitor() {
            activations = new int[nameExtractors.length];
            for (int i = 0; i < activations.length; i++) {
                if (nameExtractors[i].ancestorQualifier == null) {
                    activations[i] = 1;
                }
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
            if (node instanceof ParserRuleContext) {
                onVisit((ParserRuleContext) node);
            } else {
                super.visitChildren(node);
            }
            return null;
        }

        private void onVisit(ParserRuleContext node) {
            boolean[] activationScratch = new boolean[nameExtractors.length];
            for (int i = 0; i < nameExtractors.length; i++) {
                if (nameExtractors[i].ancestorQualifier != null) {
                    activationScratch[i] = nameExtractors[i].ancestorQualifier.test(node);
                    activations[i]++;
                }
            }
            try {
                for (int i = 0; i < nameExtractors.length; i++) {
                    if (activations[i] > 0) {
                        runOne(node, nameExtractors[i]);
                    }
                }
                super.visitChildren(node);
            } finally {
                for (int i = 0; i < activationScratch.length; i++) {
                    if (activationScratch[i]) {
                        activations[i]--;
                    }
                }
            }
        }

        private <R extends ParserRuleContext> void runOne(ParserRuleContext node, NameExtractionStrategy<R, T> nameExtractor) {
            if (nameExtractor.type.isInstance(node)) {
                doRunOne(nameExtractor.type.cast(node), nameExtractor);
            }
        }

        private <R extends ParserRuleContext> void doRunOne(R node, NameExtractionStrategy<R, T> e) {
            e.find(node, (NamedRegionData<T> nm, TerminalNode tn) -> {
                // If we are iterating TerminalNodes, tn will be non-null; otherwise
                // it will be null and we are doing single extraction - this is so we can,
                // for example, in an ANTLR grammar for ANTLR, create token names and
                // references from an import tokens statement where there is no rule
                // definition, but we should not point the definition position for all
                // of the names to the same spot
                if (nm != null) {
                    if (namesBuilder != null) {
                        // XXX, the names extractor actually needs to return the name AND the offsets of the name
                        // use the same code we use for finding the reference
                        namesBuilder.add(nm.name, nm.kind, nm.start, nm.end);
                    }
                    if (ruleBoundsBuilder != null) {
                        if (tn == null) {
                            ruleBoundsBuilder.add(nm.name, nm.kind, node.start.getStartIndex(), node.stop.getStopIndex() + 1);
                        } else {
                            Token tok = tn.getSymbol();
                            ruleBoundsBuilder.add(nm.name, nm.kind, tok.getStartIndex(), tok.getStopIndex() + 1);
                        }
                    }
                }
            });
        }
    }
}
