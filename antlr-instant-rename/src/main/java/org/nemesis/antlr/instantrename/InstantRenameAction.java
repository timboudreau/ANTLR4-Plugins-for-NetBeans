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
package org.nemesis.antlr.instantrename;

import com.mastfrog.function.throwing.ThrowingSupplier;
import com.mastfrog.range.IntRange;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.instantrename.impl.RenameQueryResultTrampoline;
import org.nemesis.antlr.refactoring.common.RefactoringActionsBridge;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.data.IndexAddressable;
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
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle.Messages;

/**
 * Subclass this (or annotate one or more NameReferenceSetKeys with
 * <code>&#064;InstantRename</code> and let the annotation processor do it for
 * you) to make it work.
 *
 * @author Tim Boudreau
 */
public final class InstantRenameAction extends BaseAction {

    static final Logger LOG = Logger.getLogger(InstantRenameAction.class.getName());
    public static final String ACTION_NAME = "in-place-refactoring";
    private final List<? extends InstantRenameProcessorEntry<?, ?, ?, ?>> entries;

    protected InstantRenameAction(List<? extends InstantRenameProcessorEntry<?, ?, ?, ?>> entries) {
        super(ACTION_NAME, MAGIC_POSITION_RESET | UNDO_MERGE_RESET); //NOI18N
        this.entries = entries;
    }

    protected InstantRenameAction(InstantRenameProcessorEntry<?, ?, ?, ?>... entries) {
        this(Arrays.asList(entries));
    }

    public static InstantRenameActionBuilder builder() {
        return new InstantRenameActionBuilder();
    }

    public static HighlightsLayerFactory highlightsFactory() {
        return new HighlightsLayerFactoryImpl();
    }

    @Override
    protected final boolean asynchonous() {
        return false;
    }

    @Messages({
        ACTION_NAME + "=Rename",
        "InstantRenameDenied=Cannot perform rename here",
        "readOnlyFile=Read-only file",
        "scanning-in-progress=Scanning In Progress"
    })
    @Override
    public final void actionPerformed(ActionEvent evt, final JTextComponent target) {
        try {
            final int caret = target.getCaretPosition();
            BaseDocument document = Utilities.getDocument(target);
            final String ident = Utilities.getIdentifier(document, caret);
            if (ident == null) {
                Utilities.setStatusBoldText(target,
                        Bundle.InstantRenameDenied());
                return;
            }

            if (IndexingManager.getDefault().isIndexing()) {
                Utilities.setStatusBoldText(target,
                        Bundle.scanning_in_progress());
                return;
            }

            FileObject fo = NbEditorUtilities.getFileObject(document);
            if (fo != null && !fo.canWrite()) {
                Utilities.setStatusBoldText(target,
                        Bundle.readOnlyFile());
                return;
            }

            Source source = Source.create(target.getDocument());
            if (source == null) {
                return;
            }
            ModificationState checker = new ModificationState(document);
            try {
                NbAntlrUtils.withPostProcessingDisabledThrowing(
                        new RegionsFinder(entries, checker, source, caret,
                                target, document, ident));
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

    private static class RegionsFinder extends UserTask implements ThrowingSupplier<Iterable<? extends IntRange>>, Runnable {

        private final ModificationState checker;
        private final Source source;
        private final int caret;
        private final JTextComponent target;
        private BadLocationException thrown;
        private final BaseDocument baseDoc;
        private FindItemsResult<?, ?, ?, ?> result;
        private final List<? extends InstantRenameProcessorEntry<?, ?, ?, ?>> entries;
        private final String ident;

        RegionsFinder(List<? extends InstantRenameProcessorEntry<?, ?, ?, ?>> entries,
                ModificationState checker, Source js, int caret, JTextComponent target,
                BaseDocument baseDoc, String ident) {
            this.entries = entries;
            this.checker = checker;
            this.source = js;
            this.caret = caret;
            this.target = target;
            this.baseDoc = baseDoc;
            this.ident = ident;
        }

        @Override
        public void run() {
            try {
                // writers are now locked out, check mod flag:
                if (checker.isUnmodified()) {
                    // sanity check the regions against snaphost size, see #227890; OffsetRange contains document positions.
                    // if no document change happened, then offsets must be correct and within doc bounds.
                    int maxLen = baseDoc.getLength();
                    for (IntRange r : result) {
                        if (r.start() >= maxLen || r.end() >= maxLen) {
                            throw new IllegalArgumentException("Bad OffsetRange provided: " + r + ", docLen=" + maxLen);
                        }
                    }
                    InstantRenamePerformer.performInstantRename(target, result, caret, ident);
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
                if (result != null && !result.isNotFound() && result.isInplaceProceed()) {
                    final BaseDocument baseDoc = (BaseDocument) target.getDocument();
                    baseDoc.render(this);
                    rethrowIfThrown();
                } else if (result == null || result.isUseRefactoring()) {
                    Document doc = target.getDocument();
                    FileObject fo = NbEditorUtilities.getFileObject(doc);
                    if (fo != null) {
                        RefactoringActionsBridge.invokeFullRename(fo, baseDoc, target, caret);
                    }
                    break;
                }
            } while (checker.modificationIsInProgress());
            return result == null ? Collections.emptySet() : result.ranges();
        }

        void findRegions() throws ParseException {
            ParserManager.parse(Collections.singleton(source), this);
        }

        @Override
        public void run(ResultIterator resultIterator) throws Exception {
            Parser.Result res = resultIterator.getParserResult();
            if (res instanceof ExtractionParserResult) {
                Extraction extraction = ((ExtractionParserResult) res).extraction();
                FindItemsResult<?, ?, ?, ?> fir = null;
                for (InstantRenameProcessorEntry<?, ?, ?, ?> entry : entries) {
                    fir = entry.find(extraction, baseDoc, caret, ident);
                    if (!fir.isNotFound()) {
                        break;
                    }
                }
                if (fir == null || fir.isNotFound()) {
                    // If we found nothing to rename, see if there is an unknown
                    // reference at this spot, and if so, trigger the refactoring
                    // API which may be able to attribute and resolve it
                    for (NameReferenceSetKey<?> refkey : extraction.referenceKeys()) {
                        IndexAddressable unknowns = extraction.unknowns(refkey);
                        if (unknowns != null && unknowns.at(caret) != null) {
                            fir = new FindItemsResult<>(Collections.emptySet(),
                                    RenameQueryResultTrampoline.createUseRefactoringResult());
                        }
                    }
                }
                if (fir == null) {
                    fir = new FindItemsResult<>(Collections.emptySet(), RenameQueryResultTrampoline.createNothingFoundResult());
                }
                result = fir;
            } else {
                LOG.log(Level.WARNING, "Called with wrong parser result type: {0}", res);
            }
        }
    }

    private static final class ModificationState implements DocumentListener {

        private final AtomicInteger changed = new AtomicInteger();
        private final Document doc;

        public ModificationState(Document doc) {
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
