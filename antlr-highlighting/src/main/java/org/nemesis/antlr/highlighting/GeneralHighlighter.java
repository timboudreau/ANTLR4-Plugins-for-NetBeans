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
package org.nemesis.antlr.highlighting;

import java.awt.EventQueue;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.spi.language.highlighting.AbstractHighlighter;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.lib.editor.util.swing.DocumentListenerPriority;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

/**
 * Base class for highlighters that takes care of most of the boilerplate of
 * listening and re-running as needed.
 *
 * @author Tim Boudreau
 */
abstract class GeneralHighlighter<T> extends AbstractHighlighter {

    protected final Document doc;
    protected final Logger LOG = Logger.getLogger(getClass().getName());
    private final AntlrHighlighter implementation;

    GeneralHighlighter(Context ctx, int refreshDelay, AntlrHighlighter implementation) {
        super(ctx, implementation.mergeHighlights());
        doc = ctx.getDocument();
        this.implementation = implementation;
        log(Level.FINE, "Create for {0} with {1}", doc, implementation);
    }

    @Override
    protected final void activated(FileObject file, Document doc) {
        activated(ctx.getComponent(), doc);
        scheduleRefresh();
    }

    @Override
    protected final void deactivated(FileObject file, Document doc) {
        deactivated(ctx.getComponent(), doc);
    }

    @Override
    public String toString() {
        return "GeneralHighlighter{" + implementation + "}";
    }

    final void log(Level level, String msg, Object... args) {
        LOG.log(level, msg, args);
    }

    protected final void refresh(Document doc, T argument, Extraction ext, OffsetsBag bag) {
        Integer caret = argument instanceof Integer ? (Integer) argument : null;
        implementation.refresh(doc, ext, bag, caret);
    }

    /**
     * Called when the component is made visible; attach listeners here.
     *
     * @param pane The text pane
     * @param doc The document
     */
    protected void activated(JTextComponent pane, Document doc) {
        // do nothing
    }

    /**
     * Called when the component is hidden, removed or loses its parent.
     *
     * @param pane The text pane
     * @param doc The document
     */
    protected void deactivated(JTextComponent pane, Document doc) {
        // do nothing
    }

    protected final void scheduleRefresh() {
        if (isActive()) {
            Document doc = ctx.getDocument();
            if (doc != null) {
                EventQueue.invokeLater(() -> {
                    ParseCoalescer.getDefault().enqueueParse(doc, this);
                });
            }
        }
    }

    protected T getArgument() {
        return null;
    }

    /**
     * Override in, for example, caret triggered highlighters that should not be
     * active when there is a selection, and set the argument type to Integer,
     * and in <code>getArgument()</code> fetch or return cached caret positions.
     *
     *
     * @param argument The argument from <code>getArgument()</code>
     * @return true if highlighting should be performed
     */
    protected boolean shouldProceed(T argument) {
        return true;
    }

    final void refresh(Document doc, Extraction ext) {
        T arg = getArgument();
        if (shouldProceed(arg)) {
            refresh(doc, ext, arg);
        }
    }

    private void refresh(Document doc, Extraction semantics, T argument) {
        LOG.log(Level.FINEST, "{0} update highlights for {1}",
                new Object[]{doc, this});
        updateHighlights(brandNewBag -> {
            refresh(doc, argument, semantics, brandNewBag);
            return true;
        });
    }

    static final class DocumentOriented<T> extends GeneralHighlighter<T>
            implements DocumentListener, LookupListener, Runnable {

        private Lookup.Result<FontColorSettings> res;
        private AtomicBoolean pendingRefresh = new AtomicBoolean();

        @SuppressWarnings("LeakingThisInConstructor")
        public DocumentOriented(Context ctx, int refreshDelay, AntlrHighlighter implementation) {
            super(ctx, refreshDelay, implementation);
        }

        @Override
        protected void activated(JTextComponent pane, Document doc) {
            DocumentUtilities.addDocumentListener(doc, this, DocumentListenerPriority.AFTER_CARET_UPDATE);
            res = MimeLookup.getLookup(NbEditorUtilities.getMimeType(doc)).lookupResult(FontColorSettings.class);
            res.addLookupListener(this);
            res.allInstances();
            if (pane != null) {
                enqueue();
            }
        }

        @Override
        protected void deactivated(JTextComponent pane, Document doc) {
            DocumentUtilities.removeDocumentListener(doc, this, DocumentListenerPriority.AFTER_CARET_UPDATE);
            Lookup.Result<FontColorSettings> r = res;
            res = null;
            if (r != null) {
                r.removeLookupListener(this);
            }
        }

        @Override
        public final void insertUpdate(DocumentEvent e) {
            enqueue();
        }

        @Override
        public final void removeUpdate(DocumentEvent e) {
            enqueue();
        }

        @Override
        public final void changedUpdate(DocumentEvent e) {
            // do nothing
        }

        @Override
        public void resultChanged(LookupEvent ev) {
            enqueue();
        }

        void enqueue() {
            // This ensures that we don't do our reparse BEFORE
            // the current key/document event has been processed and
            // wind up parsing the text of the file prior to the
            // edit the user is doing right now
            if (pendingRefresh.compareAndSet(false, true)) {
                EventQueue.invokeLater(this);
            }
        }

        public void run() {
            if (pendingRefresh.compareAndSet(true, false)) {
                scheduleRefresh();
            }
        }
    }

    static final class CaretOriented extends GeneralHighlighter<Integer> implements CaretListener {

        private Reference<JTextComponent> component;
        private static final int NO_COMPONENT = -1;
        private static final int HAS_SELECTION = -2;
        private static final int NO_CARET = -3;
        private static final int UNINITIALIZED = -4;
        private static final int INACTIVE = -5;

        public CaretOriented(Context ctx, int refreshDelay, AntlrHighlighter implementation) {
            super(ctx, refreshDelay, implementation);
        }

        @Override
        protected void activated(JTextComponent pane, Document doc) {
            if (pane != null) {
                component = new WeakReference<>(pane);
                pane.addCaretListener(this);
            }
        }

        @Override
        protected void deactivated(JTextComponent pane, Document doc) {
            if (pane != null) {
                pane.removeCaretListener(this);
            }
        }

        @Override
        protected Integer getArgument() {
            if (!isActive()) {
                return INACTIVE;
            }
            if (component == null) {
                return UNINITIALIZED;
            }
            JTextComponent comp = component.get();
            if (comp == null) {
                return NO_COMPONENT;
            }
            Caret caret = comp.getCaret();
            if (caret == null) {
                // avoid
                // java.lang.NullPointerException
                // at java.desktop/javax.swing.text.JTextComponent.getSelectionStart(JTextComponent.java:1825)
                return NO_CARET;
            }
            int start = comp.getSelectionStart();
            int end = comp.getSelectionEnd();
            if (start == end) {
                return end;
            }
            return HAS_SELECTION;
        }

        @Override
        protected boolean shouldProceed(Integer argument) {
            switch (argument) {
                case UNINITIALIZED:
                case NO_CARET:
                case NO_COMPONENT:
                case HAS_SELECTION:
                case INACTIVE:
                    return false;
            }
            return argument >= 0;
        }

        @Override
        public void caretUpdate(CaretEvent e) {
            scheduleRefresh();
        }
    }
}
