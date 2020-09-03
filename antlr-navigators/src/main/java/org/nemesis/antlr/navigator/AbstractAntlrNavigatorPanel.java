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
package org.nemesis.antlr.navigator;

import com.mastfrog.function.state.Obj;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.extraction.Extraction;
import org.netbeans.spi.navigator.NavigatorPanel;
import org.openide.awt.QuickSearch;
import org.openide.cookies.EditorCookie;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.LookupListener;
import org.openide.util.RequestProcessor;
import org.openide.util.Task;
import org.openide.util.TaskListener;
import org.openide.util.Utilities;
import org.openide.windows.TopComponent;

/**
 * Takes care of the general plumbing of managing a navigator panel *correctly*
 * - specifically solving the problem of the empty panel when rapidly switching
 * between files, due to a cancelled job to populate a panel completing after a
 * new one has been started and completed, and its contents. Uses an
 * AtomicInteger to ensure only the most current request to populate the panel
 * gets to actually update it.
 *
 * @author Tim Boudreau
 */
abstract class AbstractAntlrNavigatorPanel<R, C extends JComponent & ComponentIsActiveChecker> implements NavigatorPanel {

    private JComponent component;
    protected C list;
    /**
     * current context to work on
     */
    protected Lookup.Result<EditorCookie> editorCookieContext;
    protected Lookup.Result<DataObject> dataObjectContext;
    protected FileListener fileListener;
    /**
     * A sadly common problem in Navigator panel implementations is sometimes
     * getting an empty panel or a panel showing the wrong file's contents. That
     * happens because model building work is asynchronous, so rapidly changing
     * between two files causes one model to be built, then another, and the
     * second one completes first and then gets clobbered by the first one. So,
     * each lookup change increments an AtomicInteger; the new value propagates
     * through all the model building and setting code and is preserved in the
     * ListModel instance. If at any point the value being passed around does
     * not match the value of this AtomicInteger, the work is aborted rather
     * than finishing building the model or updating the ui.
     */
    protected final AtomicInteger changeCount = new AtomicInteger();
    /**
     * listener to context changes
     */
    private LookupListener editorCookieListener;
    protected AtomicReference<RequestProcessor.Task> fut = new AtomicReference<>();
    protected static final RequestProcessor threadPool = new RequestProcessor("antlr-navigator", 1, false);
    protected static final int DELAY = 500;

    protected abstract C createComponent();

    protected boolean isCurrent(int change) {
        return changeCount.get() == change;
    }

    protected void onBeforeCreateComponent() {

    }

    private void attachSearch(C component) {

    }

    @Override
    public JComponent getComponent() {
        if (component == null) {
            onBeforeCreateComponent();
            list = createComponent();
            JScrollPane scrollPane = new JScrollPane(list);
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(scrollPane, BorderLayout.CENTER);

            if (this instanceof SearchableNavigatorPanel) {
                attachQuickSearch(panel, (SearchableNavigatorPanel) this);
            }
            component = panel;
            // The usual Swing border-buildup fixes
            Border empty = BorderFactory.createEmptyBorder();
            scrollPane.setBorder(empty);
            scrollPane.setViewportBorder(empty);
            list.setBorder(empty);
            onAfterCreateComponent(list);
        }
        return component;
    }

    protected final void attachQuickSearch(JPanel panel, SearchableNavigatorPanel search) {
        CB callback = new CB((SearchableNavigatorPanel) this);
        QuickSearch qs = QuickSearch.attach(panel, BorderLayout.NORTH, callback);
        if (search.isSearchAlwaysVisible()) {
            qs.setAlwaysShown(true);
        } else {
            list.getActionMap().put("-antlr-navigator-quicksearch", new AbstractAction() {
                public void actionPerformed(ActionEvent ae) {
                    qs.setAlwaysShown(true);
                    callback.onNextConclusion(() -> {
                        qs.setAlwaysShown(false);
                    });
                }
            });
            KeyStroke stroke;
            if (Utilities.isMac()) {
                stroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK, true);
            } else {
                stroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.ALT_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK, true);
            }
            list.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(stroke, "-antlr-navigator-quicksearch");
        }
    }

    protected void onAfterCreateComponent(C component) {

    }

    static class CB implements QuickSearch.Callback {

        private String searchText;
        private Runnable stateRestorer;
        private final SearchableNavigatorPanel nav;
        private Runnable onNextConclusion;
        private String lastCompletionPrefix;
        private String lastCompletionResult;
        private int lastSearchMatch;

        public CB(SearchableNavigatorPanel nav) {
            this.nav = nav;
        }

        void onNextConclusion(Runnable run) {
            onNextConclusion = run;
        }

        private void done(boolean accept) {
            accept &= searchText != null;
            if (accept) {
                nav.commitSearch(searchText);
            } else {
                if (stateRestorer != null) {
                    stateRestorer.run();
                }
            }
            searchText = null;
            stateRestorer = null;
            if (onNextConclusion != null) {
                onNextConclusion.run();
                onNextConclusion = null;
            }
            discardSearch();
        }

        private void discardSearch() {
            lastCompletionResult = null;
            lastCompletionPrefix = null;
            lastSearchMatch = -1;
        }

        @Override
        public void quickSearchUpdate(String searchText) {
            this.searchText = searchText;
            if (stateRestorer == null) {
                stateRestorer = nav.cancelledSearchStateRestorer();
            }
        }

        @Override
        public void showNextSelection(boolean forward) {
            if (searchText == null) {
                searchText = "";
                if (stateRestorer == null) {
                    stateRestorer = nav.cancelledSearchStateRestorer();
                }
            }
            nav.toNextSelection(searchText, forward);
        }

        @Override
        public String findMaxPrefix(String prefix) {
            if (lastCompletionPrefix != null && lastCompletionResult != null) {
                if (prefix.equals(lastCompletionResult)) {
                    prefix = lastCompletionPrefix;
                }
            }
            searchText = prefix;
            if (stateRestorer == null) {
                stateRestorer = nav.cancelledSearchStateRestorer();
            }
            Obj<String> res = Obj.of(prefix);
            boolean result = nav.searchCompletion(lastSearchMatch + 1, searchText, (String match, int index) -> {
                res.set(match);
                lastCompletionResult = match;
                lastSearchMatch = index;
            });
            if (result) {
                lastCompletionPrefix = prefix;
            }
            return res.get();
        }

        @Override
        public void quickSearchConfirmed() {
            done(true);
        }

        @Override
        public void quickSearchCanceled() {
            done(false);
        }
    }

    public void panelActivated(Lookup context) {
        // lookup context and listen to result to get notified about context changes
        Lookup.Result<EditorCookie> editorCookieResult = context.lookupResult(EditorCookie.class);
        Lookup.Result<DataObject> dataObjectResult = context.lookupResult(DataObject.class);
        synchronized (this) {
            editorCookieContext = editorCookieResult;
            dataObjectContext = dataObjectResult;
        }
        editorCookieResult.addLookupListener(getEditorCookieListener());
        dataObjectResult.addLookupListener(createFileListener());
        fileListener.updateFromResult(dataObjectResult);
        updateFromLookupResult(editorCookieResult);
    }

    @SuppressWarnings("deprecation")
    protected void moveTo(JTextComponent pane, int startOffset, int endOffset) {
        // Move the caret
        pane.setSelectionStart(startOffset);
        pane.setSelectionEnd(endOffset);
        try {
            // Make sure the window is visible and focused,
            // and what we need is in view
            TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, pane);
            if (tc != null) {
                tc.requestActive();
                Rectangle endRect = pane.modelToView(endOffset);
                Rectangle startRect = pane.modelToView(startOffset);
                startRect.add(endRect);
                // If the element is not extramely indented, avoid
                // horizontally scrolling the editor, as that is usually
                // just annoying
                if (startRect.x < 800) {
                    startRect.x = 0;
                    startRect.width = Math.max(startRect.width, 200);
                }

                // Let the window system take care of possibly
                // activating the editor *before* we try to
                // manipulate it - we're on the event queue now,
                // but requestActive will be asynchronous
                EventQueue.invokeLater(() -> {
                    pane.scrollRectToVisible(startRect);
                    pane.requestFocus();
                });
            }
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private LookupListener getEditorCookieListener() {
        if (editorCookieListener == null) {
            editorCookieListener = new ContextListener(data -> {
                editorCookiesUpdated(data, changeCount.incrementAndGet());
            });
        }
        return editorCookieListener;
    }

    private void updateFromLookupResult(Lookup.Result<EditorCookie> result) {
        // get actual data and recompute content
        Collection<? extends EditorCookie> data = result.allInstances();
        editorCookiesUpdated(data, changeCount.incrementAndGet());
    }

    public void panelDeactivated() {
        changeCount.incrementAndGet();
        Lookup.Result<DataObject> dataObjectResult;
        Lookup.Result<EditorCookie> editorCookieResult;
        fileListener.stopListening();
        synchronized (this) {
            dataObjectResult = dataObjectContext;
            editorCookieResult = editorCookieContext;
            editorCookieContext = null;
            dataObjectContext = null;
        }
        if (editorCookieResult != null) {
            editorCookieResult.removeLookupListener(getEditorCookieListener());
        }
        if (dataObjectResult != null) {
            dataObjectResult.removeLookupListener(fileListener);
        }
        updateForFileDeletedOrPanelDeactivated();
    }

    void updateForFileChange() {
        Lookup.Result<EditorCookie> result;
        synchronized (this) {
            result = editorCookieContext;
        }
        if (result != null) {
            updateFromLookupResult(result);
        } else {
            updateForFileDeletedOrPanelDeactivated();
        }
    }

    void updateForFileDeletedOrPanelDeactivated() {
        updateFromLookupResult(Lookup.EMPTY.lookupResult(EditorCookie.class));
        setNoModel(changeCount.get());
    }

    @Override
    public Lookup getLookup() {
        return null;
    }

    protected FileListener createFileListener() {
        if (fileListener == null) {
            fileListener = new FileListener(this::updateForFileChange,
                    this::updateForFileDeletedOrPanelDeactivated);
        }
        return fileListener;
    }

    private void editorCookiesUpdated(Collection<? extends EditorCookie> cookies, int currChange) {
        if (!cookies.isEmpty()) {
            EditorCookie ck = cookies.iterator().next();
            buildModel(ck, currChange);
        }
    }

    protected abstract void setNoModel(int forChange);

    protected abstract void withNewModel(Extraction semantics, EditorCookie ck, int forChange);

    private void buildModel(EditorCookie ck, int forChange) {
        Document doc = ck.getDocument();
        if (doc == null) {
            ck.prepareDocument().addTaskListener(new TaskListener() {
                @Override
                public void taskFinished(Task task) {
                    if (forChange != changeCount.get()) {
                        setNoModel(forChange);
                    } else {
                        buildModel(ck, forChange, ck.getDocument());
                    }
                }
            });
        } else {
            buildModel(ck, forChange, doc);
        }
    }

    protected AbstractParseBuildJob newBuildJob(Document doc, int change, AtomicInteger changeCount, EditorCookie ck) {
        return new ModelBuildJob(doc, change, changeCount, ck);
    }

    private class ModelBuildJob extends AbstractParseBuildJob {

        public ModelBuildJob(Document doc, int forChange, AtomicInteger changeCount,
                EditorCookie ck) {
            super(doc, forChange, ck, changeCount);
        }

        @Override
        protected void onNoModel() {
            setNoModel(forChange);
        }

        @Override
        protected void onParseFailed() {
            onNoModel();
        }

        @Override
        protected void onReplaceExtraction(Extraction semantics) {
            withNewModel(semantics, ck, forChange);
        }
    }

    private void buildModel(EditorCookie ck, int forChange, Document doc) {
        Runnable job = newBuildJob(doc, forChange, changeCount, ck);
        RequestProcessor.Task oldFuture = fut.getAndSet(threadPool.post(job, DELAY, Thread.NORM_PRIORITY - 2));
        if (oldFuture != null && !oldFuture.isFinished()) {
            oldFuture.cancel();
        }
    }

    /**
     * Create a navigator panel that shows a tree of the extraction contents,
     * for debugging during plugin development.
     *
     * @return A navigator panel
     */
    public static NavigatorPanel createExtractionDebugPanel() {
        return new ExtractionTreeNavigatorPanel();
    }
}
