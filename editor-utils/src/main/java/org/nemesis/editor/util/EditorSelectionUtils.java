/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.editor.util;

import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.range.RangeRelation;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.editor.caret.CaretInfo;
import org.netbeans.api.editor.caret.CaretMoveContext;
import org.netbeans.api.editor.caret.EditorCaret;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.text.PositionBounds;
import org.openide.util.Exceptions;
import org.openide.windows.TopComponent;

/**
 * Handles scrolling the nearest available editor, or opending a new one and
 * scrolling it, to a particular location.
 *
 * @author Tim Boudreau
 */
public final class EditorSelectionUtils {

    private EditorSelectionUtils() {
        throw new AssertionError();
    }

    public static boolean navigateTo(Path path,
            IntRange<? extends IntRange> range) throws BadLocationException, IOException {
        FileObject grammarFile = FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
        if (grammarFile == null) {
            return false;
        }
        return navigateTo(grammarFile, range);
    }

    public static boolean navigateTo(FileObject file,
            IntRange<? extends IntRange> range) throws BadLocationException, IOException {
        return navigateTo(DataObject.find(file), range);
    }

    @SuppressWarnings("deprecation")
    public static boolean navigateTo(DataObject dob,
            IntRange<? extends IntRange> range) throws BadLocationException, IOException {
        EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
        Document grammarDocument = ck.openDocument();
        openAndSelectRange(grammarDocument, range);
        return true;
    }

    public static void openAndSelectRange(Document doc, int start,
            int end) throws BadLocationException {
        openAndSelectRange(doc, Range.ofCoordinates(start, end));
    }

    private static <R extends IntRange<? extends R>> R constrainWithinDocument(@NonNull R range, @NonNull Document doc) {
        if (range instanceof PositionRange) {
            // Positions will automatically be adjusted, so no need to test it
            return range;
        }
        IntRange docSpan = Range.ofCoordinates(0, doc.getLength());
        RangeRelation rel = docSpan.relationTo(range);
        switch (rel) {
            case CONTAINS:
            case EQUAL:
                // ok
                break;
            case BEFORE:
                range = range.newRange(0, 0);
                break;
            case AFTER:
                range = range.newRange(range.stop(), range.stop());
                break;
            case CONTAINED:
                range = range.newRange(docSpan.start(), docSpan.size());
                break;
            case STRADDLES_END:
                range = range.newRange(range.start(), docSpan.stop() - docSpan.start());
                break;
            case STRADDLES_START:
                range = range.newRange(0, range.end());
                break;
        }
        return range;
    }

    public static void openAndSelectRange(@NonNull PositionBounds bounds) throws BadLocationException {
        openAndSelectRange(bounds.getBegin().getCloneableEditorSupport().getDocument(),
                PositionFactory.toPositionRange(bounds));
    }

    public static void openAndSelectRange(@NonNull Document doc,
            IntRange<? extends IntRange> range) throws BadLocationException {
        // Use EditorRegistry, as it includes the preview editor and
        // we don't want to open a separate document if we don't have to
        JTextComponent editor = EditorRegistry.findComponent(doc);
        TopComponent tc = null;
        if (editor != null) {
            tc = NbEditorUtilities.getOuterTopComponent(editor);
        }
        if (editor == null || tc == null) {
            openUsingNewEditor(doc, range);
        } else {
            openUsingComponent(editor, tc, range);
        }
    }

    private static void openUsingComponent(@NonNull JTextComponent comp, TopComponent outermost,
            IntRange<? extends IntRange> range) throws BadLocationException {
        selectRange(comp, range);
        if (TopComponent.getRegistry().getActivated() != outermost) {
            outermost.requestActive();
        }
        centerTextRegion(comp, range);
        comp.requestFocus();
    }

    public static void selectRange(@NonNull JTextComponent comp,
            IntRange<? extends IntRange> range) throws BadLocationException {
        range = constrainWithinDocument(range, comp.getDocument());
        PositionRange posRange;
        if (range instanceof PositionRange) {
            posRange = (PositionRange) range;
        } else {
            PositionFactory factory = PositionFactory.forDocument(comp.getDocument());
            posRange = factory.range(range);
        }
        Caret caret = comp.getCaret();
        if (caret instanceof EditorCaret) {
            EditorCaret ec = (EditorCaret) caret;
            ec.moveCarets((CaretMoveContext context) -> {
                CaretInfo info = ec.getLastCaret();
                context.setDotAndMark(info, posRange.startPosition(),
                        Position.Bias.Backward, posRange.endPosition(),
                        Position.Bias.Forward);
            });
        } else {
            comp.setSelectionStart(range.start());
            comp.setSelectionEnd(range.end());
        }
    }

    @SuppressWarnings("deprecation")
    public static void centerTextRegion(JTextComponent comp, IntRange<? extends IntRange> range) {
        try {
            // Center the rectangle, or it can wind up half way
            // off the bottom of the screen.
            Rectangle visibleBounds = comp.getVisibleRect();
            int cy = visibleBounds.height / 2;
            Rectangle characterBounds = comp.getUI().modelToView(comp, range.start());
            characterBounds.add(comp.getUI().modelToView(comp, range.end()));
            characterBounds.x = 0;
            cy -= characterBounds.height;
            characterBounds.y -= cy;
            characterBounds.height += cy;
            // Swing components *will* do negative scroll, and the
            // effect is not a good one
            characterBounds.y = Math.max(0, characterBounds.y);

            comp.scrollRectToVisible(characterBounds);
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private static void openUsingNewEditor(Document doc, IntRange<? extends IntRange> select) throws BadLocationException {
        DataObject dob = NbEditorUtilities.getDataObject(doc);
        EditorCookie.Observable obs = dob.getLookup().lookup(EditorCookie.Observable.class);
        JEditorPane[] panes = obs == null ? null : obs.getOpenedPanes();
        if (panes != null && panes.length > 0) {
            TopComponent tc = NbEditorUtilities.getOuterTopComponent(panes[0]);
            if (tc != null) {
                openUsingComponent(panes[0], tc, select);
                return;
            }
        } else {
            if (obs != null) {
                obs.addPropertyChangeListener(new SelectRegionWhenOpened(obs, select));
            }
        }
        LineDocument ld = LineDocumentUtils.asRequired(doc, LineDocument.class);
        int lineStart = LineDocumentUtils.getLineStart(ld, select.start());
        int column = select.start() - lineStart;
        Line ln = NbEditorUtilities.getLine(doc, select.start(), false);
        ln.show(Line.ShowOpenType.REUSE_NEW, Line.ShowVisibilityType.FOCUS, column);
        if (obs == null) {
            EventQueue.invokeLater(() -> {
                try {
                    JTextComponent comp = EditorRegistry.findComponent(doc);
                    selectRange(comp, select);
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
        }
    }

    private static class SelectRegionWhenOpened implements PropertyChangeListener, Runnable {

        private final long created = System.currentTimeMillis();
        private final EditorCookie.Observable obs;
        private volatile JTextComponent comp;
        private final IntRange<? extends IntRange> toSelect;

        public SelectRegionWhenOpened(EditorCookie.Observable obs, IntRange<? extends IntRange> toSelect) {
            this.obs = obs;
            this.toSelect = toSelect;
        }

        boolean tooOld() {
            // For some reason, no component opened when requested, and this
            // listener just hung around for a long time
            return System.currentTimeMillis() - created > 40000;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (EditorCookie.Observable.PROP_OPENED_PANES.equals(evt.getPropertyName())) {
                try {
                    if (!tooOld()) {
                        JTextComponent[] comps = (JTextComponent[]) evt.getNewValue();
                        if (comps != null && comps.length > 0) {
                            this.comp = comps[0];
                            EventQueue.invokeLater(this);
                        }
                    }
                } finally {
                    obs.removePropertyChangeListener(this);
                }
            }
        }

        @Override
        public void run() {
            try {
                selectRange(comp, toSelect);
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }
}
