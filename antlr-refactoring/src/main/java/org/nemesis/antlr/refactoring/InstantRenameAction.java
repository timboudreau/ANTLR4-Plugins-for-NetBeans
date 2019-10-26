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
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;

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
        LOG.setLevel(Level.ALL);
    }

    protected InstantRenameAction(NameReferenceSetKey<?> key) {
        super(ACTION_NAME, MAGIC_POSITION_RESET | UNDO_MERGE_RESET); //NOI18N
        this.key = key;
    }

    protected static HighlightsLayerFactory highlightsFactory() {
        return new HighlightsLayerFactoryImpl();
    }

    @Override
    protected final boolean asynchonous() {
        return false;
    }

    @Messages({
        ACTION_NAME + "=Rename",
        "InstantRenameDenied=Cannot perform rename here",
        "scanning-in-progress=Scanning In Progress"
    })
    @Override
    public final void actionPerformed(ActionEvent evt, final JTextComponent target) {
        try {
            final int caret = target.getCaretPosition();
            BaseDocument document = Utilities.getDocument(target);
            final String ident = Utilities.getIdentifier(document, caret);
            if (ident == null) {
                Utilities.setStatusBoldText(target, Bundle.InstantRenameDenied());
                return;
            }

            if (IndexingManager.getDefault().isIndexing()) {
                Utilities.setStatusBoldText(target, Bundle.scanning_in_progress());
                return;
            }
            Source source = Source.create(target.getDocument());
            if (source == null) {
                return;
            }
            ModificationNoticer checker = new ModificationNoticer(document);
            try {
                NbAntlrUtils.withPostProcessingDisabledThrowing(new RegionsFinder(key, checker, source, caret, target, document));
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Refactoring", ex);
            } finally {
                checker.detach();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Refactoring", e);
        }
    }

    @Override
    protected final Class<?> getShortDescriptionBundleClass() {
        return InstantRenameAction.class;
    }

    private static void doFullRename(EditorCookie ec, Node n) {
        Action a = RefactoringActionsFactory.renameAction()
                .createContextAwareInstance(Lookups.fixed(ec, n));
        a.actionPerformed(RefactoringActionsFactory.DEFAULT_EVENT);
    }

    private static class RegionsFinder extends UserTask implements ThrowingSupplier<Iterable<? extends IntRange>>, Runnable {

        private final NameReferenceSetKey<?> key;
        private final ModificationNoticer checker;
        private final Source source;
        private final int caret;
        private final JTextComponent target;
        private BadLocationException thrown;
        private final BaseDocument baseDoc;
        private Iterable<? extends IntRange> regions;

        public RegionsFinder(NameReferenceSetKey<?> key, ModificationNoticer checker, Source js, int caret, JTextComponent target, BaseDocument baseDoc) {
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
                if (checker.isUnmodified()) {
                    // sanity check the regions against snaphost size, see #227890; OffsetRange contains document positions.
                    // if no document change happened, then offsets must be correct and within doc bounds.
                    int maxLen = baseDoc.getLength();
                    for (IntRange r : regions) {
                        if (r.start() >= maxLen || r.end() >= maxLen) {
                            throw new IllegalArgumentException("Bad OffsetRange provided: " + r + ", docLen=" + maxLen);
                        }
                    }
                    InstantRenamePerformer.performInstantRename(target, regions, caret, null);
                    // don't loop even if there's a modification
                    checker.setHandedOff();
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
                checker.setUnmodified();
                findRegions();
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
            } while (checker.modificationIsInProgress());
            return regions;
        }

        Iterable<? extends IntRange> findRegions() throws ParseException {
            ParserManager.parse(Collections.singleton(source), this);
            return regions;
        }

        @Override
        public void run(ResultIterator resultIterator) throws Exception {
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
                if (referent == null) {
                    referent = names.regionFor(ident);
                }
                if (referent == null) {
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
                Iterable<? extends IntRange> regions = referencesToName;
                Set<IntRange> all = new HashSet<>();
                regions.forEach(all::add);
                all.add(referent);
                this.regions = all;
            } else {
                LOG.log(Level.WARNING, "Called with wrong parser result type: {0}", res);
            }
        }
    }

    private static final class ModificationNoticer implements DocumentListener {

        private final AtomicInteger changed = new AtomicInteger();
        private final Document doc;

        public ModificationNoticer(Document doc) {
            this.doc = doc;
            doc.addDocumentListener(this);
        }

        boolean isUnmodified() {
            return changed.get() == 0;
        }

        boolean modificationIsInProgress() {
            return changed.get() == 1;
        }

        void detach() {
            doc.removeDocumentListener(this);
        }

        void set(int val) {
            changed.set(val);
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

        private void setUnmodified() {
            changed.set(0);
        }

        private void setHandedOff() {
            changed.set(2);
        }
    }
}
