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
package org.nemesis.antlr.refactoring;

import com.mastfrog.range.IntRange;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.data.named.NamedRegionReferenceSet;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.api.indexing.IndexingManager;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.refactoring.api.ui.RefactoringActionsFactory;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 *
 * @author Tim Boudreau
 */
public class InstantRenameAction extends BaseAction {

    private final NameReferenceSetKey<?> key;
    private static final Logger LOG = Logger.getLogger(InstantRenameAction.class.getName());

    static {
        LOG.setLevel(Level.SEVERE);
    }

    protected InstantRenameAction(NameReferenceSetKey<?> key) {
        super("in-place-refactoring", MAGIC_POSITION_RESET | UNDO_MERGE_RESET); //NOI18N
        this.key = key;
    }

    protected static HighlightsLayerFactory highlightsFactory() {
        return new HighlightsLayerFactoryImpl();
    }

    @Messages({
        "in-place-refactoring=Rename",
        "InstantRenameDenied=Cannot perform rename here",
        "scanning-in-progress=Scanning In Progress"
    })
    @Override
    public void actionPerformed(ActionEvent evt, final JTextComponent target) {
        try {
            final int caret = target.getCaretPosition();
            final String ident = Utilities.getIdentifier(Utilities.getDocument(target), caret);
            System.out.println("IDENTIFIER: '" + ident + "'");
            if (ident == null) {
                Utilities.setStatusBoldText(target, Bundle.InstantRenameDenied());
                return;
            }

            if (IndexingManager.getDefault().isIndexing()) {
                Utilities.setStatusBoldText(target, Bundle.scanning_in_progress());
                return;
            }
            Source js = Source.create(target.getDocument());
            if (js == null) {
                return;
            }

            final Iterable<IntRange>[] changePoints = new Iterable[1];
            final AtomicInteger changed = new AtomicInteger(0);

            DocumentListener dl = new DocumentListener() {

                @Override
                public void insertUpdate(DocumentEvent e) {
                    changed.compareAndSet(0, 1);
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    changed.compareAndSet(0, 1);
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    // ignore attr changes
                }

            };
            target.getDocument().addDocumentListener(dl);

            final InstantRenamer[] renamer = new InstantRenamer[1];
            try {

                NbAntlrUtils.withPostProcessingDisabledThrowing(() -> {
                    do {
                        changed.set(0);
                        ParserManager.parse(
                                Collections.<Source>singleton(js),
                                new UserTask() {
                            public @Override
                            void run(ResultIterator resultIterator) throws Exception {

                                Parser.Result res = resultIterator.getParserResult();
                                if (res instanceof ExtractionParserResult) {
                                    Extraction extraction = ((ExtractionParserResult) res).extraction();
                                    NamedRegionReferenceSets<?> refs = extraction.references(key);
                                    NamedSemanticRegions<?> names = extraction.namedRegions(key.referencing());
                                    NamedSemanticRegion<?> caretNamedRegion = refs.at(caret);
                                    NamedSemanticRegion<?> referent = null;
                                    if (caretNamedRegion == null) {
                                        caretNamedRegion = referent = names.at(caret);
                                    }
                                    if (caretNamedRegion == null) {
                                        return;
                                    }
                                    String ident = caretNamedRegion.name();
                                    System.out.println("RENAMING '" + ident + "'");
                                    if (referent == null) {
                                        referent = names.regionFor(ident);
                                    }
                                    if (referent == null) {
                                        return;
                                    }
                                    NamedRegionReferenceSet<?> referencesToName = refs.references(ident);
                                    Iterable<? extends IntRange> regions = referencesToName;
//                                Iterable<? extends IntRange> orig = Collections.singleton(referent);
                                    Set<IntRange> all = new HashSet<>();
                                    regions.forEach(all::add);
                                    all.add(referent);
//                                Iterable<? extends IntRange> merged = CollectionUtils.concatenate(orig, regions);
                                    changePoints[0] = all;
                                }

                                /*
                            Map<String, Parser.Result> embeddedResults = new HashMap<>();
                            outer:
                            for (;;) {
                                final Result parserResult = resultIterator.getParserResult();
                                if (parserResult == null) {
                                    return;
                                }
                                embeddedResults.put(parserResult.getSnapshot().getMimeType(),
                                        resultIterator.getParserResult());
                                String inheritedType = parserResult.getSnapshot().getMimePath().getInheritedType();
                                if (inheritedType != null) {
                                    embeddedResults.put(inheritedType, resultIterator.getParserResult());
                                }
                                Iterable<Embedding> embeddings = resultIterator.getEmbeddings();
                                for (Embedding e : embeddings) {
                                    if (e.containsOriginalOffset(caret)) {
                                        resultIterator = resultIterator.getResultIterator(e);
                                        continue outer;
                                    }
                                }
                                break;
                            }

                            BaseDocument baseDoc = (BaseDocument) target.getDocument();
                            List<Language> list = LanguageRegistry.getInstance().getEmbeddedLanguages(baseDoc, caret);
                            for (Language language : list) {
                                if (language.getInstantRenamer() != null) {
                                    //the parser result matching with the language is just
                                    //mimetype based, it doesn't take mimepath into account,
                                    //which I belive is ok here.
                                    Parser.Result result = embeddedResults.get(language.getMimeType());
                                    if (!(result instanceof ParserResult)) {
                                        return;
                                    }
                                    ParserResult parserResult = (ParserResult) result;

                                    renamer[0] = language.getInstantRenamer();
                                    assert renamer[0] != null;

                                    String[] descRetValue = new String[1];

                                    if (!renamer[0].isRenameAllowed(parserResult, caret, descRetValue)) {
                                        return;
                                    }

                                    Set<OffsetRange> regions = renamer[0].getRenameRegions(parserResult, caret);

                                    if ((regions != null) && (regions.size() > 0)) {
                                        changePoints[0] = regions;
                                    }

                                    break; //the for break
                                }
                            }
                                 */
                            }
                        }
                        );

                        if (changePoints[0] != null) {
                            final BadLocationException[] exc = new BadLocationException[1];
                            final BaseDocument baseDoc = (BaseDocument) target.getDocument();
                            baseDoc.render(new Runnable() {
                                public void run() {
                                    try {
                                        // writers are now locked out, check mod flag:
                                        if (changed.get() == 0) {
                                            // sanity check the regions against snaphost size, see #227890; OffsetRange contains document positions.
                                            // if no document change happened, then offsets must be correct and within doc bounds.
                                            int maxLen = baseDoc.getLength();
                                            for (IntRange r : changePoints[0]) {
                                                if (r.start() >= maxLen || r.end() >= maxLen) {
                                                    throw new IllegalArgumentException("Bad OffsetRange provided by " + renamer[0] + ": " + r + ", docLen=" + maxLen);
                                                }
                                            }
                                            doInstantRename(changePoints[0], target, caret);
                                            // don't loop even if there's a modification
                                            changed.set(2);
                                        }
                                    } catch (BadLocationException ex) {
                                        exc[0] = ex;
                                    }
                                }
                            });
                            if (exc[0] != null) {
                                throw exc[0];
                            }
                        } else {
                            Document doc = target.getDocument();
                            FileObject fo = NbEditorUtilities.getFileObject(doc);
                            if (fo != null) {
                                DataObject dob = DataObject.find(fo);
                                EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                                if (ck != null) {
                                    doFullRename(ck, dob.getNodeDelegate());
                                }
                            }
                            break;
                        }
                    } while (changed.get() == 1);

                    return null;
                });
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Refactoring", ex);
            } finally {
                target.getDocument().removeDocumentListener(dl);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Refactoring", e);
        }
    }

    @Override
    protected Class getShortDescriptionBundleClass() {
        return InstantRenameAction.class;
    }

    void doInstantRename(Iterable<IntRange> changePoints, JTextComponent target, int caret) throws BadLocationException {
        InstantRenamePerformer.performInstantRename(target, changePoints, caret);
    }

    private void doFullRename(EditorCookie ec, Node n) {
        InstanceContent ic = new InstanceContent();
        ic.add(ec);
        ic.add(n);

        Lookup actionContext = new AbstractLookup(ic);

        Action a = RefactoringActionsFactory.renameAction().createContextAwareInstance(actionContext);
        a.actionPerformed(RefactoringActionsFactory.DEFAULT_EVENT);
    }

}
