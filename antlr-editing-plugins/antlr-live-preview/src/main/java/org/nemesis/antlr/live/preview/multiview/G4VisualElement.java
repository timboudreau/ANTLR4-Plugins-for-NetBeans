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
package org.nemesis.antlr.live.preview.multiview;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import static javax.swing.text.Document.StreamDescriptionProperty;
import javax.swing.text.EditorKit;
import javax.swing.text.StyledDocument;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import static org.nemesis.antlr.common.AntlrConstants.ICON_PATH;
import org.nemesis.antlr.live.language.AdhocLanguageHierarchy;
import org.nemesis.antlr.live.language.coloring.AdhocColorings;
import org.nemesis.antlr.live.language.coloring.AdhocColoringsRegistry;
import org.nemesis.antlr.live.language.DiscardChangesCookie;
import org.nemesis.antlr.live.language.DynamicLanguages;
import org.nemesis.antlr.live.language.SampleFiles;
import org.nemesis.antlr.live.language.UndoRedoProvider;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.preview.PreviewPanel;
import org.nemesis.antlr.project.Folders;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.lexer.Language;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.MultiViewElementCallback;
import org.netbeans.core.spi.multiview.MultiViewFactory;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.openide.actions.SaveAction;
import org.openide.awt.StatusDisplayer;
import org.openide.awt.UndoRedo;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.actions.SystemAction;
import org.openide.util.lookup.Lookups;

import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

@MultiViewElement.Registration(
        displayName = "#LBL_G4_VISUAL",
        iconBase = ICON_PATH,
        mimeType = ANTLR_MIME_TYPE,
        persistenceType = TopComponent.PERSISTENCE_NEVER,
        preferredID = "G4Visual",
        position = 2000
)
@Messages({"LBL_G4_VISUAL=Tester", "LBL_LOADING=Loading..."})
public final class G4VisualElement extends JPanel implements MultiViewElement, LookupListener {

    private final DataObject obj;
    private final JToolBar toolbar = new JToolBar();
    private transient MultiViewElementCallback callback;
    private final JLabel loadingLabel = new JLabel(Bundle.LBL_LOADING());
    private static final Logger LOG = Logger.getLogger(
            G4VisualElement.class.getName());
    private static final RequestProcessor INIT
            = new RequestProcessor(G4VisualElement.class.getName(), 5);
    private final MutableProxyLookup lkp;
    private UndoRedo undoRedo = UndoRedo.NONE;
    private Lookup.Result<SaveCookie> saveCookieResult;
    VeryLazyInitPanel superLazy = new VeryLazyInitPanel(this::veryLazyInit, INIT);

    public G4VisualElement(Lookup lkp) {
        obj = lkp.lookup(DataObject.class);
        this.lkp = new MutableProxyLookup(lkp);
        assert obj != null;
        initComponents();
        loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loadingLabel.setVerticalAlignment(SwingConstants.CENTER);
        loadingLabel.setEnabled(false);
        add(superLazy, BorderLayout.CENTER);
    }

    @Override
    public String getName() {
        return "G4VisualElement";
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    @Override
    public JComponent getVisualRepresentation() {
        return this;
    }

    @Override
    public JComponent getToolbarRepresentation() {
        return toolbar;
    }

    @Override
    public Action[] getActions() {
        return callback == null ? new Action[0] : callback.createDefaultActions();
    }

    @Override
    public Lookup getLookup() {
        return lkp;
    }

    private RequestProcessor.Task initTask;

    @Messages({
        "# {0} - grammar file path",
        "NOT_A_FILE=Not a valid file: {0}",
        "# {0} - grammar file path",
        "NOT_REGULAR_FILE=Virtual files cannot be parsed by Antlr: {0}",
        "# {0} - grammar file path",
        "REGISTERING_DYNAMIC=Compiling {0} and registering syntax highlighting",
        "# {0} - grammar file path",
        "REGISTERING_COMPLETE=Language registration complete: {0}",
        "WRONG_FOLDER=Preview only available for grammars under the main Antlr source folder of the project",
        "DETECTING_FOLDERS=Checking Antlr Configuration",
        "REGISTER_LANGUAGE=Registering dynamic language config",
        "OPENING_EDITOR=Registering dynamic language config",
        "COULD_NOT_UNDERSTAND_PROJECT_LAYOUT=Cannot understand project grammar layout"
    })
    void veryLazyInit(Consumer<JComponent> onEqWhenDone) {
        long then = System.currentTimeMillis();
        Thread.currentThread().setName("G4VisualElement-init-" + obj.getName());
        if (obj.isValid()) {
            Folders flds = Folders.ownerOf(obj.getPrimaryFile());
            if (flds == null) {
                flds = Folders.ANTLR_GRAMMAR_SOURCES;
                EventQueue.invokeLater(() -> {
                    loadingLabel.setText(Bundle.COULD_NOT_UNDERSTAND_PROJECT_LAYOUT());
                    onEqWhenDone.accept(loadingLabel);
                });
                return;
            }
            superLazy.status(Bundle.DETECTING_FOLDERS());
            // Editor cookie may be lazy initialized (because the entire DataObject is) - wait
            // for it
            for (int i = 0; i < 10 && obj.getLookup().lookup(EditorCookie.class) == null; i++) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
            switch (flds) {
                case ANTLR_TEST_GRAMMAR_SOURCES:
                case ANTLR_GRAMMAR_SOURCES:
                    File file = FileUtil.toFile(obj.getPrimaryFile());
                    if (file != null) {
                        StatusDisplayer.getDefault().setStatusText(
                                Bundle.REGISTERING_DYNAMIC(obj.getPrimaryFile().getNameExt()));
                    } else {
                        loadingLabel.setText(Bundle.NOT_REGULAR_FILE(obj.getPrimaryFile().getPath()));
                    }
                    String mime = AdhocMimeTypes.mimeTypeForPath(file.toPath());

                    superLazy.status(Bundle.REGISTER_LANGUAGE());
                    if (DynamicLanguages.ensureRegistered(mime)) {

                        try {
                            // XXX we are racing here with the file change listener that
                            // detects newly installed languages - really we need
                            // something to listen on that will fire a change when the
                            // language is fully registered - otherwise we'll wind up
                            // with a text/plain component
                            Thread.sleep(120);
                        } catch (InterruptedException ex) {
                            LOG.log(Level.INFO, null, ex);
                        }
                        // Warm some things up that will be needed soon
                        for (int i = 0; i < 15; i++) {
                            if (Language.find(mime) == null) {
                                try {
                                    Thread.sleep(30);
                                } catch (InterruptedException ex) {
                                    LOG.log(Level.INFO, null, ex);
                                }
                            }
                        }
                    }
                    Lookup lkp = MimeLookup.getLookup(MimePath.parse(mime));
                    // Force init of lookup contents ahead of time
                    EditorKit kit = lkp.lookup(EditorKit.class);
//                    Object o;
//                    for (Object ob : lkp.lookupAll(Object.class)) {
//                        o = ob; // ensure the compiler doesn't optimize this away
//                    }
                    // Find or create a sample file to work with
                    DataObject sampleFileDataObject = SampleFiles.sampleFile(mime);
                    // Get the colorings for this grammar's pseudo-mime-type, creating
                    // them if necessary
                    AdhocColorings colorings = AdhocColoringsRegistry.getDefault().get(mime);

                    try {
                        EditorCookie ck = sampleFileDataObject.getLookup().lookup(EditorCookie.class);
                        Thread.yield();
                        // Open the document and set it on the editor pane
                        StyledDocument doc = ck.openDocument();
                        doc.putProperty("mimeType", mime);
                        doc.putProperty(StreamDescriptionProperty, sampleFileDataObject);
                        superLazy.status(Bundle.OPENING_EDITOR());
                        EmbeddedAntlrParser parser = AdhocLanguageHierarchy.parserFor(mime);
                        CharSequence text = DocumentUtilities.getText(doc);
                        Thread.yield();
                        EmbeddedAntlrParserResult result = parser.parse(text);
                        Thread.yield();
                        EventQueue.invokeLater(() -> {
                            StatusDisplayer.getDefault()
                                    .setStatusText(
                                            Bundle.REGISTERING_COMPLETE(obj.getPrimaryFile().getNameExt()));
                            LOG.log(Level.FINEST,
                                    "Lazy load completed in {0} ms", new Object[]{
                                        System.currentTimeMillis() - then});
                            PreviewPanel pnl = panel = new PreviewPanel(mime, obj.getLookup(),
                                    sampleFileDataObject, doc, colorings, kit, lkp);
                            UndoRedoProvider prov = pnl.getLookup().lookup(UndoRedoProvider.class);
                            if (prov != null) {
                                this.undoRedo = prov.get();
                                super.firePropertyChange("undoRedo", UndoRedo.NONE, undoRedo);
                            }
                            pnl.addPropertyChangeListener("undoRedo", (evt) -> {
                                UndoRedo old = this.undoRedo;
                                if (evt.getNewValue() != null && evt.getNewValue() != old) {
                                    this.undoRedo = (UndoRedo) evt.getNewValue();
                                    super.firePropertyChange("undoRedo", UndoRedo.NONE, this.undoRedo);
                                }
                            });
                            this.lkp.setAdditional(pnl.getLookup());
                            saveCookieResult = pnl.getLookup().lookupResult(SaveCookie.class);
                            saveCookieResult.addLookupListener(this);
                            pnl.accept(doc, result);
                            onEqWhenDone.accept(pnl);
                        });
                    } catch (Exception ex) {
                        LOG.log(Level.SEVERE, "Exception opening " + sampleFileDataObject.getPrimaryFile().getPath(), ex);
                        EventQueue.invokeLater(() -> {
                            loadingLabel.setText(ex.toString());
                            onEqWhenDone.accept(loadingLabel);
                        });
                    }
                    break;

                default:
                    EventQueue.invokeLater(() -> {
                        loadingLabel.setText(Bundle.WRONG_FOLDER());
                        onEqWhenDone.accept(loadingLabel);
                    });
            }
        } else {
            loadingLabel.setText(Bundle.NOT_A_FILE(obj.getPrimaryFile().getPath()));
            EventQueue.invokeLater(() -> {
                onEqWhenDone.accept(loadingLabel);
            });
        }
    }

    private PreviewPanel panel;

    @Override
    public void componentOpened() {
//        lazyInit();
    }

    @Override
    public void componentClosed() {
    }

    @Override
    public void componentShowing() {
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            superLazy.showing();
            if (panel != null) {
                panel.notifyShowing();
            }
        });
    }

    @Override
    public void componentHidden() {
        superLazy.hidden();
        if (panel != null) {
            panel.notifyHidden();
        }
    }

    @Override
    public void componentActivated() {
    }

    @Override
    public void componentDeactivated() {
    }

    @Override
    public UndoRedo getUndoRedo() {
        return undoRedo;
    }

    @Override
    public void setMultiViewCallback(MultiViewElementCallback callback) {
        this.callback = callback;
    }

    @Override
    @Messages("save_sample_file=Save sample file?")
    public CloseOperationState canCloseElement() {
        if (saveCookieResult != null) { // can be null if an exception was thrown opening
            Collection<? extends SaveCookie> cks = saveCookieResult.allInstances();
            if (!cks.isEmpty()) {
                SaveAction save = SystemAction.get(SaveAction.class);
                Lookup ctx = Lookups.fixed(cks.toArray());
                MultiViewFactory.createUnsafeCloseState(Bundle.save_sample_file(),
                        save.createContextAwareInstance(ctx),
                        new DiscardAction(obj.getLookup().lookup(DiscardChangesCookie.class)));
            }
        }
        return CloseOperationState.STATE_OK;
    }

    @Messages("discard=Discard Changes")
    static class DiscardAction extends AbstractAction {

        private final DiscardChangesCookie ck;

        public DiscardAction(DiscardChangesCookie ck) {
            super(Bundle.discard());
            this.ck = ck;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (ck != null) {
                ck.discardChanges();
            }
        }
    }

    @Override
    public void resultChanged(LookupEvent le) {
        if (callback != null) {
            Mutex.EVENT.readAccess((Runnable) () -> {
                Collection<? extends SaveCookie> all = saveCookieResult.allInstances();
                if (callback != null) {
                    if (all.isEmpty()) {
                        callback.updateTitle(obj.getName());
                    } else {
                        callback.updateTitle("<b>" + obj.getName());
                    }
                }
            });
        }
    }
}
