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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import static javax.swing.Action.NAME;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.UndoRedoProvider;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui.CulpritFinder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui.PreviewPanel;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.MultiViewElementCallback;
import org.openide.awt.StatusDisplayer;
import org.openide.awt.UndoRedo;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

@MultiViewElement.Registration(
        displayName = "#LBL_G4_VISUAL",
        iconBase = "org/nemesis/antlr/v4/netbeans/v8/grammar/file/antlr-g4-file-type.png",
        mimeType = "text/x-g4",
        persistenceType = TopComponent.PERSISTENCE_NEVER,
        preferredID = "G4Visual",
        position = 2000
)
@Messages({"LBL_G4_VISUAL=Tester", "LBL_LOADING=Loading..."})
public final class G4VisualElement extends JPanel implements MultiViewElement, LookupListener {

    private final G4DataObject obj;
    private final JToolBar toolbar = new JToolBar();
    private transient MultiViewElementCallback callback;
    private final JLabel loadingLabel = new JLabel(Bundle.LBL_LOADING());
    private static final Logger LOG = Logger.getLogger(
            G4VisualElement.class.getName());
    private static final RequestProcessor INIT
            = new RequestProcessor(G4VisualElement.class.getName(), 3);
    private final MutableProxyLookup lkp;
    private UndoRedo undoRedo = UndoRedo.NONE;
    private Lookup.Result<SaveCookie> saveCookieResult;

    public G4VisualElement(Lookup lkp) {
        obj = lkp.lookup(G4DataObject.class);
        this.lkp = new MutableProxyLookup(lkp);
        assert obj != null;
        initComponents();
        loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loadingLabel.setVerticalAlignment(SwingConstants.CENTER);
        loadingLabel.setEnabled(false);
        add(loadingLabel, BorderLayout.CENTER);
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
        "REGISTERING_COMPLETE=Language registration complete: {0}"
    })
    private void lazyInit() {
        LOG.log(Level.FINER, "Lazy init for {0}", obj.getPrimaryFile().getPath());
        if (initTask != null) {
            LOG.log(Level.WARNING, "Init for {0} called twice", obj.getPrimaryFile().getPath());
            return;
        }
        if (obj.isValid()) {
            File file = FileUtil.toFile(obj.getPrimaryFile());
            if (file != null) {
                StatusDisplayer.getDefault().setStatusText(
                        Bundle.REGISTERING_DYNAMIC(obj.getPrimaryFile().getNameExt()));
                initTask = INIT.create(() -> {
                    _lazyInit(file.toPath());
                });
                initTask.schedule(50);
            } else {
                loadingLabel.setText(Bundle.NOT_REGULAR_FILE(obj.getPrimaryFile().getPath()));
            }
        } else {
            loadingLabel.setText(Bundle.NOT_A_FILE(obj.getPrimaryFile().getPath()));
        }
    }

    private void _lazyInit(Path path) {
        assert !EventQueue.isDispatchThread();
        // XXX theoretically the file can be deleted between lazyInit() and
        // this being run on a background thread
        String mime = AdhocMimeTypes.mimeTypeForPath(path);
        LOG.log(Level.FINER, "Background lanugage registration of {0}"
                + " as pseudo-mime-type {1}", new Object[]{obj.getPrimaryFile().getPath(), mime});
        long then = System.currentTimeMillis();
        EventQueue.invokeLater(() -> {
            StatusDisplayer.getDefault()
                    .setStatusText(
                            Bundle.REGISTERING_COMPLETE(obj.getPrimaryFile().getNameExt()));
            LOG.log(Level.FINEST,
                    "Lazy load completed in {0} ms", new Object[]{
                        System.currentTimeMillis() - then});
            PreviewPanel pnl = new PreviewPanel(mime, obj.getLookup());
            UndoRedoProvider prov = pnl.getLookup().lookup(UndoRedoProvider.class);
            if (prov != null) {
                this.undoRedo = prov.get();
                super.firePropertyChange("undoRedo", UndoRedo.NONE, undoRedo);
            }
            this.lkp.setAdditional(pnl.getLookup());
            saveCookieResult = pnl.getLookup().lookupResult(SaveCookie.class);
            saveCookieResult.addLookupListener(this);
            remove(loadingLabel);
            toolbar.add(new FindCulpritAction(pnl.getLookup()));
            add(pnl, BorderLayout.CENTER);
        });
    }

    static class FindCulpritAction extends AbstractAction implements LookupListener {

        private final Lookup lookup;
        private final Lookup.Result<ANTLRv4ParserResult> parserResultResult;
        private final Lookup.Result<ParseTreeProxy> proxyResult;

        @Messages("findCulprit=Find Culprit")
        FindCulpritAction(Lookup lookup) {
            putValue(NAME, Bundle.findCulprit());
            this.lookup = lookup;
            parserResultResult = lookup.lookupResult(ANTLRv4ParserResult.class);
            proxyResult = lookup.lookupResult(ParseTreeProxy.class);
            setEnabled(false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ANTLRv4ParserResult parserResult = first(parserResultResult);
            boolean enabled = parserResult != null;
            if (enabled) {
                ParseTreeProxy proxy = first(proxyResult);
                enabled = proxy != null && !proxy.syntaxErrors().isEmpty();
                setEnabled(enabled);
                if (enabled) {
                    perform(parserResult, proxy);
                } else {
                    System.out.println("did not find a proxy");
                }
            } else {
                System.out.println("did not find a parser result");
                setEnabled(false);
            }
        }

        private void perform(ANTLRv4ParserResult parserResult, ParseTreeProxy proxy) {
            try {
                CulpritFinder finder = new CulpritFinder(proxy, parserResult);
                MonitorPanel pnl = MonitorPanel.showDialog();
                Document doc = lookup.lookup(Document.class);
                if (doc == null) {
                    JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), "No doc", "No doc", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                Runnable launch = finder.createCombinatoricRunner(pnl.replan(), doc.getText(0, doc.getLength()));
                launch.run();
            } catch (BadLocationException | IOException ex) {
                ex.printStackTrace();
                Exceptions.printStackTrace(ex);
            }
        }

        private <T> T first(Lookup.Result<T> res) {
            Collection<? extends T> c = res.allInstances();
            if (c.isEmpty()) {
                return null;
            }
            return c.iterator().next();
        }

        @Override
        public void resultChanged(LookupEvent le) {
            Mutex.EVENT.readAccess(() -> {
                System.out.println("lookup result changed");
                ANTLRv4ParserResult parserResult = first(parserResultResult);
                System.out.println("  got parser result? " + (parserResult != null));
                ParseTreeProxy proxy = first(proxyResult);
                System.out.println("  got proxy result " + (proxy != null));
                boolean enabled = parserResult != null && proxy != null;
                setEnabled(enabled);
            });
        }

        private void addNotify() {
            parserResultResult.addLookupListener(this);
            proxyResult.addLookupListener(this);
            System.out.println("FindCulpritAction Starts Listening");
            resultChanged(null);
        }

        private void removeNotify() {
            parserResultResult.removeLookupListener(this);
            proxyResult.removeLookupListener(this);
        }

        @Override
        public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
            if (super.changeSupport == null || super.changeSupport.getPropertyChangeListeners().length == 0) {
                addNotify();
            }
            super.addPropertyChangeListener(listener);

        }

        @Override
        public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
            super.removePropertyChangeListener(listener);
            if (super.changeSupport.getPropertyChangeListeners().length == 0) {
                removeNotify();
            }
        }
    }

    @Override
    public void componentOpened() {
        lazyInit();
    }

    @Override
    public void componentClosed() {
    }

    @Override
    public void componentShowing() {
    }

    @Override
    public void componentHidden() {
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
    public CloseOperationState canCloseElement() {
        return CloseOperationState.STATE_OK;
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
