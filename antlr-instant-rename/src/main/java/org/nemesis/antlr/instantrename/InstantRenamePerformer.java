/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.nemesis.antlr.instantrename;

import com.mastfrog.range.IntRange;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.Position.Bias;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;
import org.nemesis.antlr.instantrename.impl.RenamePerformer;
import org.nemesis.antlr.refactoring.common.RefactoringActionsBridge;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.BaseKit;
import org.netbeans.lib.editor.util.swing.MutablePositionRegion;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.NbDocument;

/**
 * Borrowed this code with modifications from NetBeans' common scripting
 * language project, which borrowed it from the circa-JDK 5 / NetBeans 6
 * Retouche use-javac-internally project.
 *
 * This file is originally from Retouche, the Java Support infrastructure in
 * NetBeans. I have modified the file as little as possible to make merging
 * Retouche fixes back as simple as possible.
 *
 * @author Jan Lahoda, Tim Boudreau
 */
final class InstantRenamePerformer implements DocumentListener, KeyListener, RenamePerformer {

    private SyncDocumentRegion region;
    private final BaseDocument doc;
    private final JTextComponent target;

    private AttributeSet attribs = null;
    private AttributeSet attribsLeft = null;
    private AttributeSet attribsRight = null;
    private AttributeSet attribsMiddle = null;
    private AttributeSet attribsAll = null;

    private AttributeSet attribsSlave = null;
    private AttributeSet attribsSlaveLeft = null;
    private AttributeSet attribsSlaveRight = null;
    private AttributeSet attribsSlaveMiddle = null;
    private AttributeSet attribsSlaveAll = null;
    private final String origText;
    private String lastText;
    private volatile boolean inSync;

    private static final Logger LOG = Logger.getLogger(InstantRenamePerformer.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }
    private final FindItemsResult<?, ?, ?, ?> result;

    @SuppressWarnings("LeakingThisInConstructor")
    private InstantRenamePerformer(JTextComponent target, FindItemsResult<?, ?, ?, ?> result, int caretOffset, String origText) throws BadLocationException {
        this.target = target;
        this.result = result;
        this.origText = lastText = origText;
        doc = (BaseDocument) target.getDocument();
        MutablePositionRegion mainRegion = null;
        List<MutablePositionRegion> regions = new ArrayList<MutablePositionRegion>();

        for (IntRange h : result) {
            Position start = NbDocument.createPosition(doc, h.start(), Bias.Backward);
            Position end = NbDocument.createPosition(doc, h.end(), Bias.Forward);
            MutablePositionRegion current = new MutablePositionRegion(start, end);
            if (isIn(current, caretOffset)) {
                mainRegion = current;
            } else {
                regions.add(current);
            }
        }

        if (mainRegion == null) {
            LOG.log(Level.WARNING, "No highlight contains the caret ({0}; highlights={1})",
                    new Object[]{caretOffset, result}); //NOI18N
            // Attempt to use another region - pick the one closest to the caret
            if (regions.size() > 0) {
                mainRegion = regions.get(0);
                int mainDistance = Integer.MAX_VALUE;
                for (MutablePositionRegion r : regions) {
                    int distance = caretOffset < r.getStartOffset() ? (r.getStartOffset() - caretOffset) : (caretOffset - r.getEndOffset());
                    if (distance < mainDistance) {
                        mainRegion = r;
                        mainDistance = distance;
                    }
                }
            } else {
                origText = null;
                return;
            }
        }

        regions.add(0, mainRegion);

        region = new SyncDocumentRegion(doc, regions);

        doc.addPostModificationDocumentListener(this);
        UndoableEdit undo = new CancelInstantRenameUndoableEdit(this);
        for (UndoableEditListener l : doc.getUndoableEditListeners()) {
            l.undoableEditHappened(new UndoableEditEvent(doc, undo));
        }

        target.addKeyListener(this);

        target.putClientProperty("NetBeansEditor.navigateBoundaries", mainRegion); // NOI18N
        target.putClientProperty(InstantRenamePerformer.class, this);

        requestRepaint();

        target.select(mainRegion.getStartOffset(), mainRegion.getEndOffset());
        sendUndoableEdit(doc, CloneableEditorSupport.BEGIN_COMMIT_GROUP);
        // ensure when a real refactoring is run, all performers are cleaned up
        RefactoringActionsBridge.beforeAnyRefactoring(this);
    }

    private static InstantRenamePerformer getPerformerFromComponent(JTextComponent target) {
        return (InstantRenamePerformer) target.getClientProperty(InstantRenamePerformer.class);
    }

    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public static void performInstantRename(JTextComponent target, FindItemsResult<?, ?, ?, ?> result, int caretOffset, String origText) throws BadLocationException {
        //check if there is already an instant rename action in progress
        InstantRenamePerformer performer = getPerformerFromComponent(target);
        if (performer != null) {
            //cancel the old one
            performer.release(true);
        }

        new InstantRenamePerformer(target, result, caretOffset, origText);
    }

    private boolean isIn(MutablePositionRegion region, int caretOffset) {
        return region.getStartOffset() <= caretOffset && caretOffset <= region.getEndOffset();
    }

    @Override
    public void markSynced() {
        inSync = true;
    }

    public synchronized void insertUpdate(DocumentEvent e) {
        if (inSync) {
            return;
        }

        inSync = true;
        sync();
        inSync = false;
        requestRepaint();
    }

    public synchronized void removeUpdate(DocumentEvent e) {
        if (inSync) {
            return;
        }

        //#89997: do not sync the regions for the "remove" part of replace selection,
        //as the consequent insert may use incorrect offset, and the regions will be synced
        //after the insert anyway.
        if (doc.getProperty(BaseKit.DOC_REPLACE_SELECTION_PROPERTY) != null) {
            return;
        }

        inSync = true;
        sync();
        inSync = false;
        requestRepaint();
    }

    private void sync() {
        region.sync(0);
        notifyParticipant();
    }

    private void notifyParticipant() {
        if (result.isPassChanges()) {
            String text = region.getFirstRegionText();
            if (!lastText.equals(text)) {
                try {
                    result.onNameUpdated(lastText, (StyledDocument) doc, text);
                } finally {
                    lastText = text;
                }
            }
        }
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
    }

    private boolean isFirstCharacter() {
        int selStart = target.getSelectionStart();
        int selEnd = target.getSelectionEnd();
        if (selStart != selEnd) {
            if (selStart == region.getFirstRegionStartOffset()) {
                return true;
            }
        } else {
            if (selStart == region.getFirstRegionStartOffset() || region.getFirstRegionLength() == 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        boolean initial = isFirstCharacter();
        boolean suppressed = !result.test(initial, e.getKeyChar());
//        System.out.println("Key '" + e.getKeyChar() + "' initial " + initial + " suppress? " + suppressed);
        if (suppressed) {
            e.consume();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        boolean isEscape = e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0;
        boolean isEnter = e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0;
        if (isEscape || isEnter) {
            release(false);
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    void undo() {
        region.setText(origText);
    }

    public void release(boolean cancellation) {
        if (RefactoringActionsBridge.unregisterBeforeAnyRefactoring(this)) {
            sendUndoableEdit(doc, CloneableEditorSupport.END_COMMIT_GROUP);
            String newText = region.getFirstRegionText();
            boolean modified = !origText.equals(newText);
            if (!modified) {
                result.onCancelled();
            } else {
                result.onNameUpdated(origText, (StyledDocument) doc, newText);
                result.onRename(origText, newText, this::undo);
            }
            target.putClientProperty("NetBeansEditor.navigateBoundaries", null); // NOI18N
            target.putClientProperty(InstantRenamePerformer.class, null);
            doc.removePostModificationDocumentListener(this);
            target.removeKeyListener(this);
            region = null;
            requestRepaint();
        }
    }

    private void requestRepaint() {
        if (region == null) {
            OffsetsBag bag = getHighlightsBag(doc);
            bag.clear();
        } else {
            int count = region.getRegionCount();
            if (count > 0) {
                ensureAttribs();
                OffsetsBag nue = new OffsetsBag(doc);
                for (int i = 0; i < count; i++) {
                    int startOffset = region.getRegion(i).getStartOffset();
                    int endOffset = region.getRegion(i).getEndOffset();
                    int size = region.getRegion(i).getLength();
                    if (size == 1) {
                        nue.addHighlight(startOffset, endOffset, i == 0 ? attribsAll : attribsSlaveAll);
                    } else if (size > 1) {
                        nue.addHighlight(startOffset, startOffset + 1, i == 0 ? attribsLeft : attribsSlaveLeft);
                        nue.addHighlight(endOffset - 1, endOffset, i == 0 ? attribsRight : attribsSlaveRight);
                        if (size > 2) {
                            nue.addHighlight(startOffset + 1, endOffset - 1, i == 0 ? attribsMiddle : attribsSlaveMiddle);
                        }
                    }
                }
                OffsetsBag bag = getHighlightsBag(doc);
                bag.setHighlights(nue);
            }
        }
    }

//    private static final AttributeSet defaultSyncedTextBlocksHighlight = AttributesUtilities.createImmutable(StyleConstants.Background, new Color(138, 191, 236));
    private static final AttributeSet defaultSyncedTextBlocksHighlight
            = AttributesUtilities.createImmutable(StyleConstants.Foreground, Color.red);

    private static AttributeSet getSyncedTextBlocksHighlight(String name) {
        FontColorSettings fcs = MimeLookup.getLookup(MimePath.EMPTY).lookup(FontColorSettings.class);
        AttributeSet as = fcs != null ? fcs.getFontColors(name) : null;
        as = as == null ? defaultSyncedTextBlocksHighlight : as;
        System.out.println("Sync'd block highlight: " + as);
        return as;
    }

    private static AttributeSet createAttribs(Object... keyValuePairs) {
        assert keyValuePairs.length % 2 == 0 : "There must be even number of prameters. "
                + "They are key-value pairs of attributes that will be inserted into the set.";

        List<Object> list = new ArrayList<Object>();

        for (int i = keyValuePairs.length / 2 - 1; i >= 0; i--) {
            Object attrKey = keyValuePairs[2 * i];
            Object attrValue = keyValuePairs[2 * i + 1];

            if (attrKey != null && attrValue != null) {
                list.add(attrKey);
                list.add(attrValue);
            }
        }

        return AttributesUtilities.createImmutable(list.toArray());
    }

    public static OffsetsBag getHighlightsBag(Document doc) {
        OffsetsBag bag = (OffsetsBag) doc.getProperty(InstantRenamePerformer.class);

        if (bag == null) {
            doc.putProperty(InstantRenamePerformer.class, bag = new OffsetsBag(doc));

//            Object stream = doc.getProperty(StreamDescriptionProperty);
//            if (stream instanceof DataObject) {
//                stream = ((DataObject) stream).getPrimaryFile();
//            }
//
//            if (stream instanceof FileObject) {
//                Logger.getLogger("TIMER").log(Level.FINE, "Instant Rename Highlights Bag", new Object[]{(FileObject) stream, bag}); //NOI18N
//            }
        }

        return bag;
    }

    void ensureAttribs() {
        if (attribs == null) {
            // read the attributes for the master region
            attribs = getSyncedTextBlocksHighlight("synchronized-text-blocks-ext"); //NOI18N
            Color foreground = (Color) attribs.getAttribute(StyleConstants.Foreground);
            Color background = (Color) attribs.getAttribute(StyleConstants.Background);
            attribsLeft = createAttribs(
                    StyleConstants.Background, background,
                    EditorStyleConstants.LeftBorderLineColor, foreground,
                    EditorStyleConstants.TopBorderLineColor, foreground,
                    EditorStyleConstants.BottomBorderLineColor, foreground
            );
            attribsRight = createAttribs(
                    StyleConstants.Background, background,
                    EditorStyleConstants.RightBorderLineColor, foreground,
                    EditorStyleConstants.TopBorderLineColor, foreground,
                    EditorStyleConstants.BottomBorderLineColor, foreground
            );
            attribsMiddle = createAttribs(
                    StyleConstants.Background, background,
                    EditorStyleConstants.TopBorderLineColor, foreground,
                    EditorStyleConstants.BottomBorderLineColor, foreground
            );
            attribsAll = createAttribs(
                    StyleConstants.Background, background,
                    EditorStyleConstants.LeftBorderLineColor, foreground,
                    EditorStyleConstants.RightBorderLineColor, foreground,
                    EditorStyleConstants.TopBorderLineColor, foreground,
                    EditorStyleConstants.BottomBorderLineColor, foreground
            );
            // read the attributes for the slave regions
            attribsSlave = getSyncedTextBlocksHighlight("synchronized-text-blocks-ext-slave"); //NOI18N
            Color slaveForeground = (Color) attribsSlave.getAttribute(StyleConstants.Foreground);
            Color slaveBackground = (Color) attribsSlave.getAttribute(StyleConstants.Background);
            attribsSlaveLeft = createAttribs(
                    StyleConstants.Background, slaveBackground,
                    EditorStyleConstants.LeftBorderLineColor, slaveForeground,
                    EditorStyleConstants.TopBorderLineColor, slaveForeground,
                    EditorStyleConstants.BottomBorderLineColor, slaveForeground
            );
            attribsSlaveRight = createAttribs(
                    StyleConstants.Background, slaveBackground,
                    EditorStyleConstants.RightBorderLineColor, slaveForeground,
                    EditorStyleConstants.TopBorderLineColor, slaveForeground,
                    EditorStyleConstants.BottomBorderLineColor, slaveForeground
            );
            attribsSlaveMiddle = createAttribs(
                    StyleConstants.Background, slaveBackground,
                    EditorStyleConstants.TopBorderLineColor, slaveForeground,
                    EditorStyleConstants.BottomBorderLineColor, slaveForeground
            );
            attribsSlaveAll = createAttribs(
                    StyleConstants.Background, slaveBackground,
                    EditorStyleConstants.LeftBorderLineColor, slaveForeground,
                    EditorStyleConstants.RightBorderLineColor, slaveForeground,
                    EditorStyleConstants.TopBorderLineColor, slaveForeground,
                    EditorStyleConstants.BottomBorderLineColor, slaveForeground
            );
        }
    }

    private static void sendUndoableEdit(Document d, UndoableEdit ue) {
        if (d instanceof AbstractDocument) {
            UndoableEditListener[] uels = ((AbstractDocument) d).getUndoableEditListeners();
            UndoableEditEvent ev = new UndoableEditEvent(d, ue);
            for (UndoableEditListener uel : uels) {
                uel.undoableEditHappened(ev);
            }
        }
    }

    private static class CancelInstantRenameUndoableEdit extends AbstractUndoableEdit {

        private final Reference<InstantRenamePerformer> performer;

        public CancelInstantRenameUndoableEdit(InstantRenamePerformer performer) {
            this.performer = new WeakReference<InstantRenamePerformer>(performer);
        }

        @Override
        public boolean isSignificant() {
            return false;
        }

        @Override
        public void undo() throws CannotUndoException {
            InstantRenamePerformer perf = performer.get();
            if (perf != null) {
                perf.release(true);
            }
        }
    }
}
