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

import com.mastfrog.function.throwing.ThrowingSupplier;
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
import org.netbeans.modules.parsing.spi.ParseException;
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
 * Subclass this (or annotate one or more NameReferenceSetKeys with
 * <code>&#064;InstantRename</code> and let the annotation processor do it for
 * you) to make it work.
 *
 * @author Tim Boudreau
 */
public class InstantRenameAction extends BaseAction {

    private final NameReferenceSetKey<?> key;
    private static final Logger LOG = Logger.getLogger(InstantRenameAction.class.getName());
    public static final String ACTION_NAME = "in-place-refactoring";

    static {
        LOG.setLevel(Level.SEVERE);
    }

    protected InstantRenameAction(NameReferenceSetKey<?> key) {
        super(ACTION_NAME, MAGIC_POSITION_RESET | UNDO_MERGE_RESET); //NOI18N
        this.key = key;
    }

    protected static HighlightsLayerFactory highlightsFactory() {
        return new HighlightsLayerFactoryImpl();
    }

    @Messages({
        ACTION_NAME + "=Rename",
        "InstantRenameDenied=Cannot perform rename here",
        "scanning-in-progress=Scanning In Progress"
    })
    @Override
    public void actionPerformed(ActionEvent evt, final JTextComponent target) {
        try {
            final int caret = target.getCaretPosition();
            BaseDocument document = Utilities.getDocument(target);
            final String ident = Utilities.getIdentifier(document, caret);
            System.out.println("IDENTIFIER: '" + ident + "'");
            if (ident == null) {
                System.out.println("NO IDENT");
                Utilities.setStatusBoldText(target, Bundle.InstantRenameDenied());
                return;
            }

            if (IndexingManager.getDefault().isIndexing()) {
                Utilities.setStatusBoldText(target, Bundle.scanning_in_progress());
                return;
            }
            Source js = Source.create(target.getDocument());
            if (js == null) {
                System.out.println("NO SOURCE");
                return;
            }
            ModificationChecker checker = new ModificationChecker(document);
            try {
                System.out.println("call with pp disabled");
                NbAntlrUtils.withPostProcessingDisabledThrowing(new InstantRenameImplementation(key, checker, js, caret, target, document));
                System.out.println("done");
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Refactoring", ex);
            } finally {
                checker.detach();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Refactoring", e);
        }
    }

    private static final class ModificationChecker implements DocumentListener {

        private final AtomicInteger changed = new AtomicInteger();
        private final Document doc;

        public ModificationChecker(Document doc) {
            this.doc = doc;
            doc.addDocumentListener(this);
        }

        void detach() {
            doc.removeDocumentListener(this);
        }

        void set(int val) {
            changed.set(val);
        }

        int get() {
            return changed.get();
        }

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
    }

    @Override
    protected Class<?> getShortDescriptionBundleClass() {
        return InstantRenameAction.class;
    }

    private static void doFullRename(EditorCookie ec, Node n) {
        InstanceContent ic = new InstanceContent();
        ic.add(ec);
        ic.add(n);

        Lookup actionContext = new AbstractLookup(ic);

        Action a = RefactoringActionsFactory.renameAction().createContextAwareInstance(actionContext);
        a.actionPerformed(RefactoringActionsFactory.DEFAULT_EVENT);
    }

    private static class InstantRenameImplementation extends UserTask implements ThrowingSupplier<Iterable<? extends IntRange>>, Runnable {

        private final NameReferenceSetKey<?> key;
        private final ModificationChecker checker;
        private final Source source;
        private final int caret;
        private final JTextComponent target;
        private BadLocationException thrown;
        private final BaseDocument baseDoc;
        private Iterable<? extends IntRange> regions;

        public InstantRenameImplementation(NameReferenceSetKey<?> key, ModificationChecker checker, Source js, int caret, JTextComponent target, BaseDocument baseDoc) {
            this.key = key;
            this.checker = checker;
            this.source = js;
            this.caret = caret;
            this.target = target;
            this.baseDoc = baseDoc;
        }

        @Override
        public void run() {
            try {
                // writers are now locked out, check mod flag:
                if (checker.get() == 0) {
                    // sanity check the regions against snaphost size, see #227890; OffsetRange contains document positions.
                    // if no document change happened, then offsets must be correct and within doc bounds.
                    int maxLen = baseDoc.getLength();
                    for (IntRange r : regions) {
                        if (r.start() >= maxLen || r.end() >= maxLen) {
                            throw new IllegalArgumentException("Bad OffsetRange provided: " + r + ", docLen=" + maxLen);
                        }
                    }
                    InstantRenamePerformer.performInstantRename(target, regions, caret);
                    // don't loop even if there's a modification
                    checker.set(2);
                }
            } catch (BadLocationException ex) {
                thrown = ex;
            }
        }

        private void rethrowIfThrown() throws BadLocationException {
            BadLocationException ble = thrown;
            thrown = null;
            if (ble != null) {
                throw ble;
            }
        }

        @Override
        public Iterable<? extends IntRange> get() throws Exception {
            do {
                checker.set(0);
                findRegions();
                System.out.println("parse task got " + regions);
                if (regions != null) {
                    final BaseDocument baseDoc = (BaseDocument) target.getDocument();
                    baseDoc.render(this);
                    rethrowIfThrown();
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
            } while (checker.get() == 1);
            return null;
        }

        Iterable<? extends IntRange> findRegions() throws ParseException {
            ParserManager.parse(Collections.singleton(source), this);
            return regions;
        }

        @Override
        public void run(ResultIterator resultIterator) throws Exception {
            System.out.println("do parse");
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
                    System.out.println("no caret named region - give up");
                    return;
                }
                String ident = caretNamedRegion.name();
                if (referent == null) {
                    referent = names.regionFor(ident);
                }
                if (referent == null) {
                    System.out.println("  no referent for " + ident);
                    return;
                }
                NamedRegionReferenceSet<?> referencesToName = refs.references(ident);
                LOG.log(Level.FINE, "Inplace rename {0} reparse {1} using {2} in"
                        + " region {3}",
                        new Object[]{
                            ident,
                            extraction.source(),
                            key,
                            caretNamedRegion
                        });
                System.out.println("RENAMING '" + ident + "'");
                Iterable<? extends IntRange> regions = referencesToName;
                Set<IntRange> all = new HashSet<>();
                regions.forEach(all::add);
                all.add(referent);
                System.out.println("FOUND REGIONS: " + regions);
                this.regions = all;
            } else {
                System.out.println("wrong parser result type " + res);
            }
        }
    }
}
