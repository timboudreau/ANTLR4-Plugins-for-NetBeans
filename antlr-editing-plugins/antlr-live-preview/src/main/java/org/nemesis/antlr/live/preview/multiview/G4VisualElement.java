/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.preview.multiview;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
import javax.swing.text.StyledDocument;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import static org.nemesis.antlr.common.AntlrConstants.ICON_PATH;
import org.nemesis.antlr.live.language.DiscardChangesCookie;
import org.nemesis.antlr.live.language.DynamicLanguages;
import org.nemesis.antlr.live.language.SampleFiles;
import org.nemesis.antlr.live.language.UndoRedoProvider;
import org.nemesis.antlr.live.preview.PreviewPanel;
import org.nemesis.antlr.project.Folders;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.MultiViewElementCallback;
import org.netbeans.core.spi.multiview.MultiViewFactory;
import org.openide.actions.SaveAction;
import org.openide.awt.StatusDisplayer;
import org.openide.awt.UndoRedo;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.actions.SystemAction;
import org.openide.util.lookup.Lookups;

import org.openide.windows.TopComponent;

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
        "WRONG_FOLDER=Preview only available for grammars under the main Antlr source folder of the project"
    })
    private void lazyInit() {
        LOG.log(Level.FINER, "Lazy init for {0}", obj.getPrimaryFile().getPath());
        if (initTask != null) {
            LOG.log(Level.WARNING, "Init for {0} called twice", obj.getPrimaryFile().getPath());
            return;
        }
        if (obj.isValid()) {
            Folders flds = Folders.ownerOf(obj.getPrimaryFile());
            switch (flds) {
                case ANTLR_TEST_GRAMMAR_SOURCES:
                case ANTLR_GRAMMAR_SOURCES:
                    File file = FileUtil.toFile(obj.getPrimaryFile());
                    if (file != null) {
                        StatusDisplayer.getDefault().setStatusText(
                                Bundle.REGISTERING_DYNAMIC(obj.getPrimaryFile().getNameExt()));
                        initTask = INIT.create(() -> {
                            _lazyInit(file.toPath());
                        });
                        initTask.schedule(5);
                    } else {
                        loadingLabel.setText(Bundle.NOT_REGULAR_FILE(obj.getPrimaryFile().getPath()));
                    }
                    break;
                default:
                    loadingLabel.setText(Bundle.WRONG_FOLDER());
            }
        } else {
            loadingLabel.setText(Bundle.NOT_A_FILE(obj.getPrimaryFile().getPath()));
        }
    }

    void veryLazyInit(Consumer<JComponent> onEqWhenDone) {
        long then = System.currentTimeMillis();
        Thread.currentThread().setName("G4VisualElement-init-" + obj.getName());
        if (obj.isValid()) {
            Folders flds = Folders.ownerOf(obj.getPrimaryFile());
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
                    if (DynamicLanguages.ensureRegistered(mime)) {
                        try {
                            // XXX we are racing here with the file change listener that
                            // detects newly installed languages - really we need
                            // something to listen on that will fire a change when the
                            // language is fully registered - otherwise we'll wind up
                            // with a text/plain component
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            LOG.log(Level.INFO, null, ex);
                        }
                    }
                    // Find or create a sample file to work with
                    DataObject sampleFileDataObject = SampleFiles.sampleFile(mime);
                    try {
                        EditorCookie ck = sampleFileDataObject.getLookup().lookup(EditorCookie.class);
                        // Open the document and set it on the editor pane
                        StyledDocument doc = ck.openDocument();
                        doc.putProperty("mimeType", mime);
                        doc.putProperty(StreamDescriptionProperty, sampleFileDataObject);

                        EventQueue.invokeLater(() -> {
                            StatusDisplayer.getDefault()
                                    .setStatusText(
                                            Bundle.REGISTERING_COMPLETE(obj.getPrimaryFile().getNameExt()));
                            LOG.log(Level.FINEST,
                                    "Lazy load completed in {0} ms", new Object[]{
                                        System.currentTimeMillis() - then});
                            PreviewPanel pnl = panel = new PreviewPanel(mime, obj.getLookup(), sampleFileDataObject, doc);
                            UndoRedoProvider prov = pnl.getLookup().lookup(UndoRedoProvider.class);
                            if (prov != null) {
                                this.undoRedo = prov.get();
                                super.firePropertyChange("undoRedo", UndoRedo.NONE, undoRedo);
                            }
                            this.lkp.setAdditional(pnl.getLookup());
                            saveCookieResult = pnl.getLookup().lookupResult(SaveCookie.class);
                            saveCookieResult.addLookupListener(this);
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

    private void _lazyInit(Path path) {
        assert !EventQueue.isDispatchThread();
        // XXX theoretically the file can be deleted between lazyInit() and
        // this being run on a background thread
        String mime = AdhocMimeTypes.mimeTypeForPath(path);
        if (DynamicLanguages.ensureRegistered(mime)) {
            try {
                // XXX we are racing here with the file change listener that
                // detects newly installed languages - really we need
                // something to listen on that will fire a change when the
                // language is fully registered - otherwise we'll wind up
                // with a text/plain component
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                LOG.log(Level.INFO, null, ex);
            }
        }
        LOG.log(Level.FINER, "Background lanugage registration of {0}"
                + " as pseudo-mime-type {1}", new Object[]{obj.getPrimaryFile().getPath(), mime});
        long then = System.currentTimeMillis();
        // Find or create a sample file to work with
        DataObject sampleFileDataObject = SampleFiles.sampleFile(mime);

        try {
            EditorCookie ck = sampleFileDataObject.getLookup().lookup(EditorCookie.class);
            // Open the document and set it on the editor pane
            StyledDocument doc = ck.openDocument();
            doc.putProperty("mimeType", mime);
            doc.putProperty(StreamDescriptionProperty, sampleFileDataObject);
            EventQueue.invokeLater(() -> {
                StatusDisplayer.getDefault()
                        .setStatusText(
                                Bundle.REGISTERING_COMPLETE(obj.getPrimaryFile().getNameExt()));
                LOG.log(Level.FINEST,
                        "Lazy load completed in {0} ms", new Object[]{
                            System.currentTimeMillis() - then});

                PreviewPanel pnl = panel = new PreviewPanel(mime, obj.getLookup(), sampleFileDataObject, doc);
                UndoRedoProvider prov = pnl.getLookup().lookup(UndoRedoProvider.class);
                if (prov != null) {
                    this.undoRedo = prov.get();
                    super.firePropertyChange("undoRedo", UndoRedo.NONE, undoRedo);
                }
                this.lkp.setAdditional(pnl.getLookup());
                saveCookieResult = pnl.getLookup().lookupResult(SaveCookie.class);
                saveCookieResult.addLookupListener(this);
                remove(loadingLabel);
//            toolbar.add(new FindCulpritAction(pnl.getLookup()));
                add(pnl, BorderLayout.CENTER);
                pnl.notifyShowing();
            });
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Exception opening " + sampleFileDataObject.getPrimaryFile().getPath(), ex);
            EventQueue.invokeLater(() -> {
                loadingLabel.setText(ex.toString());

            });
        }
    }

    @Override
    public void componentOpened() {
//        lazyInit();
        System.out.println("Component opened");
    }

    @Override
    public void componentClosed() {
        System.out.println("component closed");
    }

    @Override
    public void componentShowing() {
        System.out.println("component showing");
        superLazy.showing();
        if (panel != null) {
            panel.notifyShowing();
        }
    }

    @Override
    public void componentHidden() {
        System.out.println("component hidden");
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
        Collection<? extends SaveCookie> cks = saveCookieResult.allInstances();
        if (!cks.isEmpty()) {
            SaveAction save = SystemAction.get(SaveAction.class);
            Lookup ctx = Lookups.fixed(cks.toArray());
            MultiViewFactory.createUnsafeCloseState(Bundle.save_sample_file(),
                    save.createContextAwareInstance(ctx),
                    new DiscardAction(obj.getLookup().lookup(DiscardChangesCookie.class)));
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
                        callback.updateTitle("<b>" + obj.getName() + "-hey*");
                    }
                }
            });
        }
    }
}
