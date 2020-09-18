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
package org.nemesis.antlr.error.highlighting.hints;

import org.nemesis.antlr.error.highlighting.hints.util.EditorAttributesFinder;
import com.mastfrog.function.state.Bool;
import org.nemesis.antlr.error.highlighting.spi.AntlrHintGenerator;
import com.mastfrog.graph.StringGraph;
import com.mastfrog.range.IntRange;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.extractiontypes.GrammarType;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.error.highlighting.HintsAndErrorsExtractors;
import static org.nemesis.antlr.error.highlighting.hints.util.RuleNamingConvention.findGrammarType;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.refactoring.usages.ImportersFinder;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.antlr.spi.language.highlighting.HighlightConsumer;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.graph.hetero.BitSetHeteroObjectGraph;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.ops.DocumentOperator;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.key.ExtractionKey;
import org.netbeans.api.editor.settings.AttributesUtilities;
import static org.netbeans.api.editor.settings.EditorStyleConstants.WaveUnderlineColor;
import org.netbeans.modules.refactoring.api.Problem;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({
    "# {0} - Rule name",
    "unusedRule=Unused rule ''{0}''",
    "# {0} - Rule name",
    "deleteRule=Delete rule ''{0}''",
    "# {0} - Rule name",
    "# {1} - Top rule",
    "# {2} - Path",
    "notReachableRule=''{0}'' is used by ''{2}'' but not reachable from ''{1}''. Delete?",
    "# {0} - Rule name",
    "# {1} - Path",
    "deleteRuleAndClosure=Delete ''{0}'' and its closure {1}",
    "# {0} - Rule name",
    "deleteUnusedRule=Unused rule ''{0}''. Delete?",
    "# {0} - Rule name",
    "notReachableFromRoot=Not reachable from top-level rule: ''{0}''",
    "# {0} - Rule name",
    "# {1} - reachableFrom",
    "# {2} - root",
    "# {3} - Path",
    "deleteRuleAndClosureIndirect=Rule ''{0}'' is reachable from ''{1}'' but not from ''{2}''. Delete ''{2}'' and its closure ''{3}''?",
    "# {0} - Rule name",
    "# {1} - reachableFromCount",
    "# {2} - root",
    "deleteRuleAndClosureIndirectLong=Rule ''{0}'' is reachable from {1} rules, but not from ''{2}''. Delete ''{2}'' and its closure?",
    "# {0} - Rule name",
    "# {1} - DependersCount",
    "# {2} - topRuleName",
    "notReachableRuleLong=''{0}'' is used by {1} rules but not reachable from ''{2}''. Delete?"
})

@ServiceProvider(service = AntlrHintGenerator.class)
public final class UnusedRuleHintGenerator extends AntlrHintGenerator {

    private final EditorAttributesFinder finder = new EditorAttributesFinder();
    private static final AttributeSet DEFAULT_UNUSED_ATTRIBUTES = AttributesUtilities.createImmutable(WaveUnderlineColor, Color.BLACK);

    @Override
    protected boolean generate(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate, Fixes fixes, Document doc,
            PositionFactory positions, HighlightConsumer highlights) throws BadLocationException {
        if (res == null || res.mainGrammar == null || res.mainGrammar.isLexer()) {
            // XXX should use usages finder for lexer rules
            return false;
        }
        Bool unusedHighlighted = Bool.create();
        NamedSemanticRegions<RuleTypes> rules = extraction.namedRegions(AntlrKeys.RULE_NAMES);
        if (!rules.isEmpty()) {
            StringGraph graph = extraction.referenceGraph(AntlrKeys.RULE_NAME_REFERENCES);
            if (graph == null) {
                // In an undo operation, the extraction nmay have been disposed
                // Should fix this never to return null
                return false;
            }
            NamedSemanticRegions<RuleTypes> ruleBounds = extraction.namedRegions(AntlrKeys.RULE_BOUNDS);
            NamedSemanticRegion<RuleTypes> firstRule = rules.index().first();
            String firstRuleName = firstRule.name();
            Set<String> seen = new HashSet<>();
            // Flag orphan nodes for deletion hints
            Set<String> orphans = new LinkedHashSet<>(graph.topLevelOrOrphanNodes());

            // Find any nodes that have skip or channel directives
            // and omit them from orphans - they are used to route
            // comments and whitespace out of the parse tree, but
            // will look like they are unused
            SemanticRegions<HintsAndErrorsExtractors.ChannelOrSkipInfo> skippedAndSimilar
                    = extraction.regions(HintsAndErrorsExtractors.CHSKIP);
            // Build a graph that cross-references the rule that contains a
            // skip or channel directive with the named rule that contains
            // it - rules that contain a skip or channel directive will have
            // an edge to that directive, so we just need to iterate our
            // rules and exclude the parents
            BitSetHeteroObjectGraph<NamedSemanticRegion<RuleTypes>, SemanticRegion<HintsAndErrorsExtractors.ChannelOrSkipInfo>, ?, SemanticRegions<HintsAndErrorsExtractors.ChannelOrSkipInfo>> hetero
                    = ruleBounds.crossReference(skippedAndSimilar);

            Bool hasImporters = Bool.create();
            for (SemanticRegion<HintsAndErrorsExtractors.ChannelOrSkipInfo> s : skippedAndSimilar) {
                // Find the owners and remove them
                for (NamedSemanticRegion<RuleTypes> parent : hetero.rightSlice().parents(s)) {
                    orphans.remove(parent.name());
                }
            }
            // Weed out all orphan rules that are unused but are imported by another grammar
            Optional<FileObject> of = extraction.source().lookup(FileObject.class);
            if (of.isPresent()) {
                ImportersFinder imf = ImportersFinder.forFile(of.get());
                Problem p = imf.usagesOf(() -> false, of.get(), AntlrKeys.IMPORTS,
                        (IntRange<? extends IntRange<?>> a, String grammarName, FileObject importer, ExtractionKey<?> importerKey, Extraction importerExtraction) -> {
                            if (importer.equals(of.get())) {
                                // self import?
                                return null;
                            }
                            SemanticRegions<UnknownNameReference<RuleTypes>> unknowns = importerExtraction.unknowns(AntlrKeys.RULE_NAME_REFERENCES);
                            // XXX could attribute and be sure we're talking about the right references
                            for (SemanticRegion<UnknownNameReference<RuleTypes>> r : unknowns) {
                                String name = r.key().name();
                                orphans.remove(name);
                            }
                            hasImporters.set();
                            return null;
                        });
                if (p != null && p.isFatal()) {
                    LOG.log(Level.WARNING, "Failed searching for importers of {0}: {1}", new Object[]{of, p.getMessage()});
                    return false;
                } else if (p != null) {
                    LOG.log(Level.WARNING, p.getMessage());
                }
            }

            // For parser grammars, the first rule / entry point will always have no
            // usages but should not be considered for removal
            GrammarType type = findGrammarType(extraction);
            switch (type) {
                case COMBINED:
                case PARSER:
                case UNDEFINED:
                    orphans.remove(firstRuleName);
            }
            // If we have no importers, ensure the first rule name is not considered
            // hasImporters.ifUntrue(() -> orphans.remove(firstRuleName));

            for (String name : orphans) {
                String errId = "orphan-" + name;
                if (fixes.isUsedErrorId(errId)) {
                    continue;
                }
                String msg = Bundle.unusedRule(name);
                NamedSemanticRegion<RuleTypes> reg = rules.regionFor(name);
                NamedSemanticRegion<RuleTypes> bounds = ruleBounds.regionFor(name);
                PositionRange rng = growIfSurroundedByNewlines(positions.range(bounds));

                highlights.addHighlight(reg.start(), reg.end(),
                        finder.find(() -> DEFAULT_UNUSED_ATTRIBUTES, "unused"));
                unusedHighlighted.set();
                fixes.addWarning(errId, rng, msg, fixen -> {
                    // Offer to delete just that rule:
                    if (graph.inboundReferenceCount(name) == 0) {
                        fixen.addFix(Bundle.deleteUnusedRule(name), bag -> {
                            bag.delete(rng);
                        });
                    }

                    Set<String> deletableClosure = findDeletableClosureOfOrphan(name, graph);
                    if (!deletableClosure.isEmpty() && !(deletableClosure.size() == 1 && deletableClosure.iterator().next().equals(name))) {
                        // We can also delete the closure of this rule wherever no other
                        // rules reference it
                        Set<PositionRange> closureRanges = new HashSet<>();
                        Map<String, PositionRange> positionRangeForName = new HashMap<>();
                        // We will need to prune some rules out of the closure if they
                        // would still be referenced by others, as those would become
                        // syntax errors
                        for (String cl : deletableClosure) {
                            PositionRange r = positions.range(ruleBounds.regionFor(cl));
                            closureRanges.add(r);
                            positionRangeForName.put(cl, r);
                        }
                        if (closureRanges.size() > 0) {
                            // Don't put the name of the rule in its closure
                            deletableClosure.remove(name);
                            String closureString
                                    = "<i>"
                                    + Strings.join(", ",
                                            CollectionUtils.reversed(
                                                    new ArrayList<>(deletableClosure)));
                            fixen.addFix(Bundle.deleteRuleAndClosure(name,
                                    elide(closureString)), bag -> {
                                for (PositionRange r : closureRanges) {
                                    bag.delete(growIfSurroundedByNewlines(r));
                                }
                            });
                            for (String cl : deletableClosure) {
                                String topMsg = Bundle.notReachableFromRoot(firstRuleName);
                                fixes.ifUnusedErrorId("nr-" + cl, () -> {

                                    fixes.addWarning("nr-" + cl, positionRangeForName.get(cl), topMsg, fxn -> {
                                        String reachableMsg;
                                        if (deletableClosure.size() > 2) {
                                            reachableMsg
                                                    = Bundle.deleteRuleAndClosureIndirectLong(cl,
                                                            name, deletableClosure.size());
                                        } else {
                                            reachableMsg = Bundle.deleteRuleAndClosureIndirect(cl,
                                                    name, firstRuleName, elide(closureString));
                                        }
                                        fxn.addFix(reachableMsg, bag -> {
                                            for (PositionRange r : closureRanges) {
                                                bag.delete(growIfSurroundedByNewlines(r));
                                            }
                                        });
                                    });
                                });
                            }
                            seen.add(name);
                        }
                    }
                });

                Set<String> closure = graph.closureOf(name);
                for (String node : closure) {
                    if (seen.contains(node)) {
                        continue;
                    }
                    seen.add(node);
                    // XXX at some point, note if there is a channels() or skip
                    // directive and don't offer to delete those
                    Set<String> revClosure = graph.reverseClosureOf(node);
                    if (!revClosure.contains(firstRuleName)) {
                        String rc = Strings.join(":", revClosure);
                        NamedSemanticRegion<RuleTypes> subBounds
                                = ruleBounds.regionFor(node);
                        PositionRange subPb = growIfSurroundedByNewlines(positions.range(subBounds));
                        NamedSemanticRegion<RuleTypes> nameBounds = rules.regionFor(node);
                        unusedHighlighted.set();
                        highlights.addHighlight(nameBounds.start(), nameBounds.end(),
                                finder.find(() -> DEFAULT_UNUSED_ATTRIBUTES, "unused"));

                        Set<String> deletableClosure = findDeletableClosureOfOrphan(node, graph);
                        deletableClosure.add(node);
                        fixes.ifUnusedErrorId("nr-" + node, () -> {
                            String hintMsg = deletableClosure.size() > 2
                                    ? Bundle.notReachableRuleLong(node, deletableClosure.size(), rc)
                                    : Bundle.notReachableRule(node, firstRuleName, rc);

                            fixes.addWarning("nr-" + node, subBounds,
                                    hintMsg, fixen -> {
                                        fixen.addFix(Bundle.notReachableRule(node, firstRuleName,
                                                rc), bag -> {
                                                    bag.delete(subPb);
                                                });
                                    });
                        });
                        Supplier<String> closureStringSuppler = () -> {
                            return "<i>" + elide(
                                    Strings.join(", ",
                                            CollectionUtils.reversed(
                                                    new ArrayList<>(deletableClosure)))) + "</i>";
                        };
                        for (String cl : deletableClosure) {
//                            if (!node.equals(cl)) {
                            PositionRange namePosition = positions.range(rules.regionFor(cl));
                            fixes.ifUnusedErrorId("nr-" + node, () -> {
                                fixes.addWarning(namePosition, closureStringSuppler.get(), fixen -> {
                                    Set<PositionRange> toDelete = new TreeSet<>();
                                    for (String s : deletableClosure) {
                                        toDelete.add(positions.range(ruleBounds.regionFor(s)));
                                    }
                                    fixen.addFix(closureStringSuppler, bag -> {
                                        for (PositionRange delete : toDelete) {
                                            bag.delete(growIfSurroundedByNewlines(delete));
                                        }
                                    });
                                });
                            });
//                            }
                        }
                    }
                }
            }
        }
        return unusedHighlighted.get();
    }

    private PositionRange growIfSurroundedByNewlines(PositionRange range) throws BadLocationException {
        Document doc = range.document();
        return DocumentOperator.<PositionRange, RuntimeException>runAtomic((StyledDocument) doc, () -> {
            if (range.isEmpty()) {
                return range;
            }
            PositionRange result = range;
            int len = doc.getLength();
            Segment seg = new Segment();
            int start = range.start();
            int retrieveLength = range.size();
            boolean canHavePrev = start > 0;
            boolean canHaveNext = range.end() < len;
            if (canHavePrev) {
                start--;
                retrieveLength++;
            }
            if (canHaveNext) {
                retrieveLength++;
            }
            doc.getText(start, retrieveLength, seg);
            boolean expandBack = canHavePrev && seg.charAt(0) == '\n';
            boolean expandFwd = canHaveNext && seg.charAt(seg.length() - 1) == '\n';
            int newStart = range.start();
            int newEnd = range.end();
            if (expandFwd) {
                newStart--;
            }
            if (expandBack) {
                newEnd++;
            }
            // XXX something wrong with one of the PositionRange impls
//            result = PositionFactory.forDocument(range.document()).range(newStart, range.startBias(), newEnd - newStart, range.endBias());
            result = result.newRange(newStart, newEnd - newStart);
            assert result.start() == newStart : "wrong start " + result.start() + " expected "
                    + newStart + " original range " + range + " new range " + result
                    + " expandFwd? " + expandFwd + " expandBwd? " + expandBack;
            assert result.end() == newEnd;
            return result;
        });
    }

    private Set<String> findDeletableClosureOfOrphan(String orphan, StringGraph usageGraph) {
        Set<String> closure = usageGraph.closureOf(orphan);
        Set<String> toDelete = new LinkedHashSet<>(closure);
        toDelete.add(orphan);
        for (String child : closure) {
            toDelete.addAll(usageGraph.closureOf(child));
        }
        Set<String> stillInUse = new HashSet<>();
        for (String del : toDelete) {
            Set<String> rev = usageGraph.reverseClosureOf(del);
            if (!toDelete.containsAll(rev)) {
                stillInUse.add(del);
            }
        }
        toDelete.removeAll(stillInUse);
        return toDelete;
    }

    private String elide(String s) {
        if (s.length() > 28) {
            return s.substring(0, 28) + "\u2026"; // ellipsis
        }
        return Escaper.BASIC_HTML.escape(s);
    }
}
