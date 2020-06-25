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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import static org.nemesis.antlr.refactoring.AbstractRefactoringContext.escapeHtml;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.editor.utils.DocumentOperation;
import org.nemesis.editor.utils.DocumentOperator;
import org.nemesis.editor.utils.DocumentProcessor;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.localizers.api.Localizers;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.refactoring.spi.RefactoringElementImplementation;
import org.netbeans.modules.refactoring.spi.SimpleRefactoringElementImplementation;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.PositionBounds;
import org.openide.text.PositionRef;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Tim Boudreau
 */
public class ReplaceRanges extends SimpleRefactoringElementImplementation {

    private final List<PositionBounds> bounds;
    private final PositionBounds span;
    private final String newText;
    private final String oldText;
    private final StyledDocument document;
    private final Lookup fileLookup;
    private final Map<PositionBounds, Boolean> disabled;
    private final Object[] moreContents;
    private final String fileText;
    private final FileObject file;
    private Lookup lookup;
    private final ExtractionKey<?> key;
    private boolean enabled = true;
    private int status = NORMAL;
    private final AtomicBoolean performed;

    ReplaceRanges(ExtractionKey<?> key, String oldText, String newText, FileObject file, String fileText,
            PositionBounds span, List<PositionBounds> items, StyledDocument document, Lookup lkp,
            Map<PositionBounds, Boolean> disabled,
            AtomicBoolean performed,
            Object... moreContents) {
        this.newText = newText;
        this.file = file;
        this.key = key;
        this.oldText = oldText;
        this.fileText = fileText;
        this.document = document;
        this.fileLookup = lkp;
        this.disabled = disabled;
        this.performed = performed;
        this.moreContents = moreContents;
        this.span = span;
        this.bounds = items;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ReplaceRanges(");
        sb.append(file.getNameExt()).append(" '").append(oldText).append("' -> '").append(newText).append("' ");
        for (int i = 0; i < moreContents.length; i++) {
            Object o = moreContents[i];
            if (o instanceof IntRange<?>) {
                IntRange<?> ir = (IntRange<?>) o;
                sb.append(ir.start()).append(':').append(ir.end());
                if (i != moreContents.length - 1) {
                    sb.append(",");
                }
            }
        }
        return sb.append(')').toString();
    }

    private static String textOf(FileObject file, StyledDocument nullOrDoc) throws IOException {
        if (nullOrDoc != null) {
            return DocumentUtilities.getText(nullOrDoc).toString();
        } else {
            return file.asText();
        }
    }

    public static void create(ExtractionKey<?> key, FileObject fo, List<? extends IntRange<? extends IntRange<?>>> ranges, String oldText, String newText, Consumer<RefactoringElementImplementation> c) throws IOException, BadLocationException {
        if (ranges.isEmpty()) {
            return;
        }
        DataObject dob = DataObject.find(fo);
        Lookup lkp = dob.getLookup();
        CloneableEditorSupport supp = lkp.lookup(CloneableEditorSupport.class);
        List<IntRange<? extends IntRange<?>>> items = new ArrayList<>(ranges);
        Collections.sort(items);
        StyledDocument doc = supp.getDocument();
        String origFileText = textOf(fo, doc);
        List<PositionBounds> bounds = new ArrayList<>();
        PositionRef first = null;
        int lastPos = 0;
        List<RefactoringElementImplementation> children = new ArrayList<>(ranges.size());
        Map<PositionBounds, Boolean> disabled = new IdentityHashMap<>();
        Object[] lookupContents = new Object[]{key, newText};
        AtomicBoolean performed = new AtomicBoolean();
        for (int i = 0; i < ranges.size(); i++) {
            IntRange<? extends IntRange<?>> range = items.get(i);
            PositionRef begin = supp.createPositionRef(range.start(), Position.Bias.Forward);
            if (first == null) {
                first = begin;
            }
            // In the case of being passed nested SemanticRegions, we actually can be
            // passed ranges ending before the end of a previous one, which are inside
            // the previous one
            lastPos = Math.max(lastPos, range.end());
            PositionRef end = supp.createPositionRef(range.end(), Position.Bias.Backward);
            PositionBounds bds = new PositionBounds(begin, end);
            bounds.add(bds);
            children.add(new RangeChild(key, range, bds, fo, origFileText, newText, disabled, performed, oldText));
        }
        PositionRef last = supp.createPositionRef(lastPos, Position.Bias.Backward);
        PositionBounds span = new PositionBounds(first, last);
        ReplaceRanges result = new ReplaceRanges(key, oldText, newText, fo,
                origFileText, span, bounds, doc, lkp, disabled, performed,
                lookupContents);
        c.accept(result);
        children.forEach(c);
    }

    private DocumentProcessor<Void, IOException> op(String text, Map<PositionBounds, Boolean> enablement) {
        // JDK 8 javac does not like a lambda here
        return new DocumentProcessor<Void, IOException>() {
            @Override
            public Void get() throws IOException, BadLocationException {
                for (PositionBounds pb : bounds) {
                    if (!enablement.containsKey(pb)) {
                        pb.setText(text);
                    }
                }
                return null;
            }
        };
    }

    private Map<PositionBounds, Boolean> snapshotAtPerform;

    @Override
    public void performChange() {
        if (!performed.compareAndSet(false, true)) {
            throw new IllegalStateException("performChange() called twice without "
                    + "intervening undo");
        }
        try {
            snapshotAtPerform = new IdentityHashMap<>(disabled);
            if (document != null) {
                DocumentOperation<Void, IOException> op = DocumentOperator.NON_JUMP_REENTRANT_UPDATE_DOCUMENT.operateOn(document);
                op.operate(op(newText, snapshotAtPerform));
            } else {
                op(newText, snapshotAtPerform).get();
            }
        } catch (IOException | BadLocationException ioe) {
            throw new IllegalStateException("Failed refactoring " + file.getNameExt(), ioe);
        }
    }

    @Override
    public void undoChange() {
        if (!performed.compareAndSet(true, false)) {
            throw new IllegalStateException("undoChange() called twice without "
                    + "intervening undo");
        }
        try {
            if (document != null) {
                DocumentOperation<Void, IOException> op = DocumentOperator.NON_JUMP_REENTRANT_UPDATE_DOCUMENT.operateOn(document);
                op.operate(op(oldText, snapshotAtPerform));
            } else {
                op(oldText, snapshotAtPerform).get();
            }
        } catch (IOException | BadLocationException ioe) {
            throw new IllegalStateException("Failed refactoring " + file.getNameExt(), ioe);
        }
    }

    @Override
    public FileObject getParentFile() {
        return file;
    }

    @Override
    public PositionBounds getPosition() {
        return span;
    }

    @Override
    public Lookup getLookup() {
        return lookup == null
                ? lookup = AbstractRefactoringContext.lookupFrom(fileLookup, moreContents)
                : lookup;
    }

    @Override
    protected String getNewFileContent() {
        if (performed.get()) {
            return fileText;
        }
        List<PositionBounds> reverseSorted = new ArrayList<>(bounds);
        Collections.sort(reverseSorted, (a, b) -> {
            return Integer.compare(a.getBegin().getOffset(), b.getBegin().getOffset());
        });
        StringBuilder text = new StringBuilder(fileText);
        for (PositionBounds pb : reverseSorted) {
            text.replace(pb.getBegin().getOffset(), pb.getEnd().getOffset(), newText);
        }
        return text.toString();
    }

    @Override
    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @NbBundle.Messages(value = {
        "# {0} - itemCount",
        "# {1} - the item type",
        "# {2} - originalName",
        "# {3} - newName",
        "replaceTitle=Replace {0} occurrences of {1} ''{2}'' with ''{3}''"})
    @Override
    public String getText() {
        String keyLoc = Localizers.displayName(key);
        return Bundle.replaceTitle(bounds.size(), keyLoc, oldText, newText);
    }

    private Object keyObject(IntRange<?> range) {
        if (range == null) {
            return null;
        }
        if (range instanceof AttributedForeignNameReference<?, ?, ?, ?>) {
            AttributedForeignNameReference<?, ?, ?, ?> attr = (AttributedForeignNameReference<?, ?, ?, ?>) range;
            return ((AttributedForeignNameReference<?, ?, ?, ?>) range).element().kind();
        } else if (range instanceof NamedSemanticRegion<?>) {
            return ((NamedSemanticRegion<?>) range).kind();
        } else if (range instanceof NamedSemanticRegionReference<?>) {
            return ((NamedSemanticRegionReference<?>) range).kind();
        } else if (range instanceof SingletonEncounters.SingletonEncounter<?>) {
            return ((SingletonEncounters.SingletonEncounter) range).get();
        }
        return null;
    }

    @NbBundle.Messages(value = {
        "# {0} - occurrences",
        "# {1} - elementType",
        "# {2} - name",
        "replaceName=Replace original and {0} uses of <i>{1}</i> <b>{2}</b>"})
    @Override
    public String getDisplayText() {
        Object ko = keyObject(getLookup().lookup(IntRange.class));
        return Bundle.replaceName(bounds.size(),
                Localizers.displayName(ko == null ? key.type() : ko), oldText);
    }

    /**
     * A dummy child so the UI can display elements for individual changes.
     */
    static final class RangeChild extends SimpleRefactoringElementImplementation {

        private final ExtractionKey<?> key;
        private final IntRange<?> range;
        private final PositionBounds bounds;
        private final FileObject file;
        private boolean dead;
        private final String origText;
        private final String newName;
        private final Map<PositionBounds, Boolean> disabledItems;
        private final AtomicBoolean performed;
        private final String oldName;

        public RangeChild(ExtractionKey<?> key, IntRange<?> range,
                PositionBounds bounds, FileObject file, String origText,
                String newName, Map<PositionBounds, Boolean> disabled,
                AtomicBoolean performed, String origName) {
            this.key = key;
            this.range = range;
            this.bounds = bounds;
            this.file = file;
            this.origText = origText;
            this.newName = newName;
            this.disabledItems = disabled;
            this.performed = performed;
            this.oldName = origName;
        }

        @Override
        public boolean isEnabled() {
            return !disabledItems.containsKey(bounds);
        }

        @Override
        public void setEnabled(boolean enabled) {
            if (enabled) {
                disabledItems.remove(bounds);
            } else {
                disabledItems.put(bounds, Boolean.TRUE);
            }
        }

        @Override
        protected String getNewFileContent() {
            if (!isEnabled() || performed.get()) {
                return origText;
            }
            StringBuilder sb = new StringBuilder(origText);
            sb.replace(range.start(), range.end(), newName);
            return sb.toString();
        }

        @Override
        @Messages({
            "# {0} - type",
            "# {1} - start",
            "# {2} - end",
            "oneChangeText=At {0}:{1}",
            "# {0} - type",
            "# {1} - start",
            "# {2} - end",
            "invalidChange=Invalidated {0} at {1}:{2}"
        })
        public String getText() {
            try {
                return performed.get() ? newName : bounds.getText();
            } catch (BadLocationException | IOException ex) {
                dead = true;
                return Bundle.invalidChange(escapeHtml(Localizers.displayName(range)), range.start(), range.end());
            }
        }

        private Object keyObject() {
            if (range instanceof AttributedForeignNameReference<?, ?, ?, ?>) {
                AttributedForeignNameReference<?, ?, ?, ?> attr = (AttributedForeignNameReference<?, ?, ?, ?>) range;
                return attr.element().kind();
            } else if (range instanceof NamedSemanticRegion<?>) {
                return ((NamedSemanticRegion<?>) range).kind();
            } else if (range instanceof NamedSemanticRegionReference<?>) {
                return ((NamedSemanticRegionReference<?>) range).kind();
            } else if (range instanceof SingletonEncounters.SingletonEncounter<?>) {
                return ((SingletonEncounters.SingletonEncounter) range).get();
            }
            return range;
        }

        @Override
        @Messages({
            "# {0} - reference type",
            "# {1} - type of thing",
            "# {2} - name",
            "# {3} - file name",
            "# {4} - line",
            "# {5} - offsetInLine",
            "simple_replacement=<html>Replace <i>{1} {0}</i> <b>{2}</b> in {3} <font color=\"!controlShadow\">at {4}:{5}</font>",
            "# {0} - reference type",
            "# {1} - type of thing",
            "# {2} - name",
            "# {3} - file name",
            "# {4} - line",
            "# {5} - offsetInLine",
            "simple_ref_replacement=Replace reference to <i>{1} {0}</i> <b>{2}</b> in {3} <font color=\"!controlShadow\">at {4}:{5}</font>"
        })
        public String getDisplayText() {
//            Object ko = keyObject();
//            if (!(ko instanceof NamedSemanticRegion<?>) && !(ko instanceof NamedSemanticRegionReference<?>)) {
//                System.out.println("KEY OBJECT " + ko.getClass().getName() + ": " + ko);
//            }
            String localizedKeyName = escapeHtml(Localizers.displayName(key));
            String localizedObjectName = escapeHtml(Localizers.displayName(keyObject()));
            String theName = this.oldName;
            String fileName = file.getNameExt();
            boolean isReference = range instanceof NamedSemanticRegionReference<?>;
            int start = range.start();
            int end = range.end();
            try {
                start = bounds.getBegin().getLine();
                end = bounds.getEnd().getColumn();
            } catch (Exception ex) {
                dead = true;
            }
            return isReference
                    ? Bundle.simple_replacement(localizedKeyName, localizedObjectName, theName, fileName, start, end)
                    : Bundle.simple_ref_replacement(localizedKeyName, localizedObjectName, theName, fileName, start, end);
        }

        @Override
        public void performChange() {
            // do nothing
        }

        @Override
        public Lookup getLookup() {
            return Lookups.fixed(key, range, newName);
        }

        @Override
        public FileObject getParentFile() {
            return file;
        }

        @Override
        public PositionBounds getPosition() {
            return bounds;
        }

        @Override
        public int getStatus() {
            return dead ? RefactoringElementImplementation.WARNING
                    : super.getStatus();
        }
    }
}
