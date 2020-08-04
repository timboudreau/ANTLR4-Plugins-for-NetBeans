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
package org.nemesis.antlr.file.antlrrefactoring;

import com.mastfrog.function.state.Obj;
import com.mastfrog.range.IntRange;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import static org.nemesis.antlr.file.AntlrKeys.RULE_BOUNDS;
import org.nemesis.antlr.refactoring.AbstractRefactoringContext;
import org.nemesis.antlr.refactoring.ReplaceRanges;
import org.nemesis.antlr.refactoring.usages.SimpleUsagesFinder;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.spi.RefactoringElementsBag;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;
import org.openide.text.PositionRef;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
public class InlineRefactoringPlugin extends AbstractRefactoringContext implements RefactoringPlugin {

    private static final Logger LOG = Logger.getLogger(InlineRefactoringPlugin.class.getName());
    private final PositionBounds bounds;
    private Extraction extraction;
    private final AbstractRefactoring refactoring;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private NamedSemanticRegion<RuleTypes> region;
    private FileObject file;

    public InlineRefactoringPlugin(AbstractRefactoring refactoring, Extraction extraction, PositionBounds bounds) {
        this.refactoring = refactoring;
        this.extraction = extraction;
        this.bounds = bounds;
        LOG.log(Level.FINE, "Create an InlinePlugin for {0} at {1}", new Object[]{extraction.source(), bounds});
    }

    @Override
    @NbBundle.Messages(value = "noDocument=No Document")
    public Problem preCheck() {
        // Get the document
        Optional<Document> doc = extraction.source().lookup(Document.class);
        if (doc.isPresent()) {
            // Create position refs so we can create the PositionBounds we
            // will use for replacement offsets that survive subsequent edits
            PositionRef start = bounds.getBegin();
            PositionRef end = bounds.getEnd();
            int startPos = start.getOffset();
            int endPos = end.getOffset();
            Document d = doc.get();
            int st = Math.max(0, startPos - 1);
            Obj<Problem> prob = Obj.create();
            // Do this under the document lock
            d.render(() -> {
                int en = Math.min(d.getLength(), endPos + 1);
                try {
                    String txt = d.getText(st, en - st);
                    if (Strings.isBlank(txt)) {
                        // Use createProblem so that we don't wind up with
                        // black on black text
                        prob.set(createProblem(true, Bundle.inWhitespace()));
                    }
                } catch (BadLocationException ex) {
                    prob.set(AbstractRefactoringContext.toProblem(ex, true));
                }
            });
            if (prob.isSet()) {
                return prob.get();
            }
            // We may have multiple requests to parse the same file within
            // the usages search, so use inParsingContext() to cache results
            // for things we have already parsed to only do each file once
            return inParsingContext(() -> {
                Extraction extraction = this.extraction;
                // First check the simple case - it is a rule in the same file
                NamedSemanticRegions<RuleTypes> allRules = extraction.namedRegions(AntlrKeys.RULE_NAMES);
                int pos = bounds.getBegin().getOffset();
                // Find the rule name at the caret position, if we are on one
                region = allRules.at(pos);
                if (region == null) {
                    // If that's null, maybe we are on a reference to a rule within this file
                    NamedRegionReferenceSets<RuleTypes> refs = extraction.references(AntlrKeys.RULE_NAME_REFERENCES);
                    // See what rule within this file we are referencing
                    NamedSemanticRegionReference<RuleTypes> refRegion = refs.at(pos);
                    if (refRegion != null) {
                        region = refRegion.referencing();
                        LOG.log(Level.FINEST, "No region at {0} refs finds {1}", new Object[]{pos, region});
                    }
                }
                // If we still don't have a region, then we may be on a referance to a
                // rule in another file - attribute the extraction so we can get hold of
                // references to foreign rules
                if (region == null) {
                    // If that is null, check if we're on a resolvable reference to a rule in another
                    // file, and if so, use the extraction of that file
                    SemanticRegion<UnknownNameReference<RuleTypes>> unk = extraction.unknowns(AntlrKeys.RULE_NAME_REFERENCES).at(pos);
                    if (unk != null) {
                        Attributions<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolved
                                = extraction.resolveAll(AntlrKeys.RULE_NAME_REFERENCES);
                        SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes>> attributed = resolved.attributed().at(pos);
                        if (attributed != null) {
                            AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> reference
                                    = attributed.key();
                            // We may be in a completely different file for the actual target rule, so
                            // reset the extraction field here to the extraction of the actual target
                            // file
                            extraction = reference.attributedTo();
                            this.extraction = extraction;
                            // Get hold of the region in the foreign file
                            region = reference.element();
                            LOG.log(Level.FINEST, "No ref, attribution finds {0}", region);
                        }
                    }
                }
                // If we didn't find a region after all that, we are not going to,
                // so bail out
                if (region == null) {
                    this.region = null;
                    return createProblem(true, Bundle.noRule());
                }
                // Make sure a file is resolvable
                Optional<FileObject> fileOpt = extraction.source().lookup(FileObject.class);
                if (!fileOpt.isPresent()) {
                    return createProblem(true, Bundle.noFile());
                }
                this.file = fileOpt.get();
                refactoring.getContext().add(fileOpt.get());
                // If there is not a project, we're not going to be able to
                // resolve things reliably
                Project proj = FileOwnerQuery.getOwner(fileOpt.get());
                if (proj == null) {
                    return createProblem(true, Bundle.noProject());
                }
                // We currently (probably) have a reference to the name, and we need a reference to the entire body
                // of the rule, so we can grab its entire content
                NamedSemanticRegions<RuleTypes> ruleBodies = extraction.namedRegions(AntlrKeys.RULE_BOUNDS);
                if (ruleBodies == allRules) {
                    // a sanity check that should never be triggered
                    throw new AssertionError("Wrong collection returned for " + AntlrKeys.RULE_BOUNDS + ": " + ruleBodies);
                }
                int rix = ruleBodies.indexOf(region.name());
                this.region = ruleBodies.forIndex(rix);
                refactoring.getContext().add(region);
                LOG.log(Level.FINE, "Proceed with region {0} in {1}", new Object[]{region, extraction.source()});
                return null;
            });
        }
        return createProblem(true, Bundle.noDocument());
    }

    @Override
    @NbBundle.Messages(value = {"noRule=No rule here", "noFile=No file present", "noProject=Not in a project", "noRefs=No References to this Rule", "nothingToDo=Nothing to do"})
    public Problem checkParameters() {
        return null;
    }

    @Override
    @NbBundle.Messages(value = "inWhitespace=No rule name here.")
    public Problem fastCheckParameters() {
        return null;
    }

    @Override
    public void cancelRequest() {
        cancelled.set(true);
    }

    private Problem reparse() {
        Optional<Document> docOpt = extraction.source().lookup(Document.class);
        assert docOpt.isPresent();
        Document doc = docOpt.get();
        try {
            extraction = AbstractRefactoringContext.parse(doc);
            NamedSemanticRegions<RuleTypes> bodies = extraction.namedRegions(RULE_BOUNDS);
            region = bodies.regionFor(region.name());
        } catch (Exception ex) {
            return AbstractRefactoringContext.toProblem(ex);
        }
        return null;
    }

    @Override
    public Problem prepare(RefactoringElementsBag reb) {
        LOG.log(Level.FINE, "Prepare {0}", file);
        return inParsingContext(() -> {
            // Make sure preCheck() was really called
            if (region == null) {
                Problem result = preCheck();
                if (result != null && result.isFatal()) {
                    return result;
                }
            }
            // Double check that we have some region to work with
            if (region == null) {
                return createProblem(true, Bundle.nothingToDo());
            }
            // Now force a reparse and re-find the thing we are renaming -
            // if the Refactor dialog with its Refactor button has been
            // sitting around while the file was edited, it is out-of-date.
            // If it has not been modified, the infrastructure will give us
            // the same result we had before, so in the common case, this is
            // low cost
            Problem result = reparse();
            if (result != null && result.isFatal()) {
                return result;
            }

            // Another sanity check that should always pass
            Optional<Document> extDoc = extraction.source().lookup(Document.class);
            if (!extDoc.isPresent()) {
                return createProblem(true, Bundle.noDocument());
            }
            Document doc = extDoc.get();
            // RuleBodyExtractor will extract JUST the body of the rule, eliding
            // elements such as labels which would either be risky or wrong in
            // embedded content.  If we have to make such elisions the user
            // will be warned with a non-fatal problem.
            RuleBodyExtractor.RuleBodyExtractionResult extractBodyResult;
            try {
                extractBodyResult = RuleBodyExtractor.extractRuleBody(region.name(), doc);
            } catch (IOException ex) {
                return AbstractRefactoringContext.toProblem(ex);
            }
            result = chainProblems(result, extractBodyResult.problem);
            // IF serious failure, bail.  It may just be a warning about having
            // to make changes in the inlined code
            if (result != null && result.isFatal()) {
                return result;
            }
            // We map files to the set of regions in them that are replaced by
            // the inlined code
            Map<FileObject, List<IntRange<? extends IntRange<?>>>> ranges = CollectionUtils.supplierMap(ArrayList::new);
            // Go find all the usages of the rule name we need to replace
            SimpleUsagesFinder<RuleTypes> sim = new SimpleUsagesFinder<>(AntlrKeys.RULE_NAME_REFERENCES);
            Problem p2 = sim.findUsages(cancelled::get, file, bounds.getBegin().getOffset(), extraction, (range, name, file, key, extraction2) -> {
                ranges.get(file).add(range);
                return null;
            });
            // double check that there are no fatal problems
            result = chainProblems(result, p2);
            if (result == null || !result.isFatal()) {
                try {
                    NamedSemanticRegion<RuleTypes> rangeToDelete = region;
                    // If the rule we're deleted is surrounded by newlines, don't leave
                    // them behind creating a bunch of blank lines in the grammar where
                    // the inlined rule was
                    if (extractBodyResult.whitespaceCharsUpToAndIncludingPrecedingNewline > 0) {
                        rangeToDelete = rangeToDelete.withStart(rangeToDelete.start() - extractBodyResult.whitespaceCharsUpToAndIncludingPrecedingNewline);
                    }
                    if (extractBodyResult.whitespaceCharsUpToAndIncludingSubsequentNewline > 0) {
                        rangeToDelete = rangeToDelete.withEnd(rangeToDelete.end() + extractBodyResult.whitespaceCharsUpToAndIncludingSubsequentNewline);
                    }
                    // Add our first change to the refactoring elements bag - replacing the original
                    // rule with the empty string
                    ReplaceRanges.create(AntlrKeys.RULE_BOUNDS, file, Collections.singletonList(rangeToDelete), extractBodyResult.fullRuleText, "", el -> {
                        reb.addFileChange(refactoring, el);
                    });
                } catch (IOException | BadLocationException ex) {
                    LOG.log(Level.INFO, "Exception adding delete rule change for " + region.name(), ex);
                    return AbstractRefactoringContext.toProblem(ex, true);
                }
            } else {
                return result;
            }
            Problem[] pres = new Problem[]{result};
            // Now add a replacement for each use to the bag
            for (Map.Entry<FileObject, List<IntRange<? extends IntRange<?>>>> e : ranges.entrySet()) {
                try {
                    // If we have a situation such as "head=someRule" and we would replace it with
                    // "head=(This? That)", that cannot have the label "head=" and be legal Antlr -
                    // so move the start of the region to replace backwards to include the label
                    // declaration and warn the user:
                    List<IntRange<? extends IntRange<?>>> adjustedRanges = new ArrayList<>();
                    // If we are inlining a rule that only referenced one other rule, we don't
                    // need to worry about eliding labels, so skip this
                    if (extractBodyResult.atomCount > 1) {
                        for (IntRange<? extends IntRange<?>> r1 : e.getValue()) {
                            ReplacementBoundsFinder.ReplacementBoundsResult res = ReplacementBoundsFinder.adjustReplacementBounds(file, r1);
                            if (res.problem != null) {
                                pres[0] = chainProblems(pres[0], res.problem);
                            }
                            adjustedRanges.add(res.range);
                        }
                    } else {
                        adjustedRanges.addAll(e.getValue());
                    }
                    // Now add all the changes to a ReplaceRanges refactoring element
                    ReplaceRanges.create(AntlrKeys.RULE_NAME_REFERENCES, e.getKey(), adjustedRanges, region.name(), extractBodyResult.text.toString(), el -> {
                        // We get a callback for each change to each file
                        Problem p = reb.addFileChange(refactoring, el);
                        pres[0] = AbstractRefactoringContext.chainProblems(pres[0], p);
                    });
                } catch (IOException | BadLocationException ex) {
                    pres[0] = chainProblems(pres[0], AbstractRefactoringContext.toProblem(ex, true));
                }
            }
            LOG.log(Level.FINEST, "Will refactor {0}", ranges);
            return pres[0];
        });
    }

}
