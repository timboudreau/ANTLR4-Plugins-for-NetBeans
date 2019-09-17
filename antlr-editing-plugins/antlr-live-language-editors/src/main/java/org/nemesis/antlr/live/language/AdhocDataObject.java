package org.nemesis.antlr.live.language;

import java.awt.Image;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import static org.nemesis.adhoc.mime.types.AdhocMimeTypes.rawGrammarNameForMimeType;
import org.nemesis.antlr.common.AntlrConstants;
import org.nemesis.antlr.live.parsing.SourceInvalidator;
import org.openide.actions.CopyAction;
import org.openide.actions.CutAction;
import org.openide.actions.DeleteAction;
import org.openide.actions.OpenAction;
import org.openide.actions.RenameAction;
import org.openide.actions.ToolsAction;
import org.openide.awt.UndoRedo;
import org.openide.cookies.CloseCookie;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.OpenCookie;
import org.openide.cookies.PrintCookie;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileSystem.AtomicAction;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataLoader;
import org.openide.loaders.DataNode;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.loaders.DataShadow;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.CookieSet;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.text.DataEditorSupport;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakSet;
import org.openide.util.actions.SystemAction;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;

/**
 *
 * @author Tim Boudreau
 */
public final class AdhocDataObject extends DataObject implements CookieSet.Before, PropertyChangeListener, Supplier<String> {

    private final InstanceContent content = new InstanceContent();
    private final Lookup lookup;
    private final CookieSet cookies;
    private final SaveCookieImpl saver;
    private static final Set<FileObject> KNOWN = new WeakSet<>();

    @SuppressWarnings("LeakingThisInConstructor")
    public AdhocDataObject(FileObject pf, DataLoader loader) throws DataObjectExistsException {
        super(pf, loader);
        cookies = CookieSet.createGeneric(this);
        lookup = new ProxyLookup(new AbstractLookup(content), cookies.getLookup());
        content.add(this);
        @SuppressWarnings("LeakingThisInConstructor")
        String mime = mimeType(this);
        DES supp = new DES(this, mime);
        saver = new SaveCookieImpl(supp);
        if (mime != null && !"content/unknown".equals(mime)) {
            supp.setMIMEType(mime);
        }
        cookies.add(supp);
        addPropertyChangeListener(this);
        KNOWN.add(pf);
    }

    private static final Consumer<FileObject> INV = SourceInvalidator.create();
    static void invalidateSources(String mimeType) {
        System.out.println("INVALIDATE SOURCESF RO " + AdhocMimeTypes.loggableMimeType(mimeType));
        for (FileObject fo : KNOWN) {
            if (mimeType.equals(fo.getMIMEType())) {
                System.out.println("INVALIDATE SOURCE OBJECTS FOR " + fo);
                INV.accept(fo);
            }
        }
    }

    public String get() {
        try {
            return getPrimaryFile().asText();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (PROP_MODIFIED.equals(evt.getPropertyName())) {
            if (Boolean.TRUE.equals(evt.getNewValue())) {
                cookies.add(saver);
            } else {
                cookies.remove(saver);
            }
        }
    }

    static final class SaveCookieImpl implements SaveCookie {

        private final DES des;

        SaveCookieImpl(DES des) {
            this.des = des;
        }

        @Override
        public void save() throws IOException {
            des.saveDocument();
        }

        @Override
        public String toString() {
            // ToString on the SaveCookie is what is printed
            // to the status line on save
            return des.getDataObject().getName();
        }
    }

    static final class DES extends DataEditorSupport implements OpenCookie,
            EditorCookie, EditorCookie.Observable,
            CloseCookie, PrintCookie,
            UndoRedoProvider {

        private static final long serialVersionUID = 1;
        final String mimeType;

        DES(DataObject d, String mimeType) {
            super(d, d.getLookup(), new DEnv(d));
            super.setMIMEType(mimeType);
            this.mimeType = mimeType;
        }

        @Override
        public void open() {
            if (AdhocMimeTypes.isMimeTypeWithExistingGrammar(mimeType)) {
//                try {
//                    DynamicLanguageSupport.registerGrammar(mimeType,
//                            getDataObject().getPrimaryFile().asText(), OPEN_DATA_OBJECT);
//                } catch (IOException ex) {
//                    Exceptions.printStackTrace(ex);
//                }
            }
            super.open();
        }

        @Override
        public UndoRedo get() {
            return super.getUndoRedo();
        }
    }

    private static String mimeType(DataObject dob) {
        FileObject theFile = dob.getPrimaryFile();
        String mime = theFile.getMIMEType();
        if ("content/unknown".equals(mime)) {
            mime = AdhocMimeTypes.mimeTypeForFileExtension(theFile.getExt());
        }
        return mime;
    }

    static class DEnv extends DataEditorSupport.Env {

        private static final long serialVersionUID = 1;

        DEnv(DataObject obj) {
            super(obj);
        }

        @Override
        protected FileObject getFile() {
            return super.getDataObject().getPrimaryFile();
        }

        @Override
        public String getMimeType() {
            return mimeType(getDataObject());
        }

        @Override
        protected FileLock takeLock() throws IOException {
            return getFile().lock();
        }
    }

    @Override
    public <T extends Node.Cookie> T getCookie(Class<T> type) {
        T cookie = cookies.getCookie(type);
        if (cookie != null) {
            return cookie;
        }
        return super.getCookie(type);
    }

    final void updateFilesInCookieSet() {
        cookies.assign(FileObject.class, files().toArray(new FileObject[0]));
    }

    @Override
    @Messages({
        "# {0} - The grammar name",
        "sampleFileName=Sample Text ({0})"
    })
    public String getName() {
        if (SampleFiles.isSampleFile(this)) {
            Path grammarFile = AdhocMimeTypes.grammarFilePathForMimeType(getPrimaryFile().getMIMEType());
            if (grammarFile != null) {
                String grammarName = AdhocMimeTypes.rawFileName(grammarFile);
                return Bundle.sampleFileName(grammarName);
            }
        }
        String result = getPrimaryFile().getName();
        return result;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    @Override
    public void beforeLookup(Class<?> type) {
        updateFilesInCookieSet();
    }

    @Override
    public boolean isDeleteAllowed() {
        return getPrimaryFile().canWrite() && !SampleFiles.isSampleFile(this);
    }

    @Override
    public boolean isCopyAllowed() {
        return getPrimaryFile().canRead();
    }

    @Override
    public boolean isMoveAllowed() {
        return isDeleteAllowed();
    }

    @Override
    public boolean isRenameAllowed() {
        return isDeleteAllowed();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected DataObject handleCopy(DataFolder df) throws IOException {
        FileObject myFile = getPrimaryFile();
        String name = getPrimaryFile().getName();
        String ext = getPrimaryFile().getExt();
        FileObject fld = df.getPrimaryFile();
        FileObject[] created = new FileObject[1];
        fld.getFileSystem().runAtomicAction(() -> {
            String finalName = findUnusedName(fld, name, ext);
            FileObject nue = fld.createData(finalName);
            created[0] = nue;
            try (InputStream in = myFile.getInputStream()) {
                try (OutputStream out = nue.getOutputStream()) {
                    FileUtil.copy(in, out);
                }
            }
        });
        return DataObject.find(created[0]);
    }

    @Override
    protected void handleDelete() throws IOException {
        if (isDeleteAllowed()) {
            getPrimaryFile().delete();
            try {
                setValid(false);
            } catch (PropertyVetoException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    @Override
    protected FileObject handleRename(String string) throws IOException {
        if (isRenameAllowed()) {
            FileObject myFile = getPrimaryFile();
            FileLock lock = myFile.lock();
            try {
                String ext = myFile.getExt();
                myFile.rename(lock, string, ext);
            } finally {
                lock.releaseLock();
            }
            updateFilesInCookieSet();
        }
        return getPrimaryFile();
    }

    @Override
    protected FileObject handleMove(DataFolder df) throws IOException {
        if (isMoveAllowed()) {
            FileObject myFile = getPrimaryFile();
            FileObject fld = df.getPrimaryFile();
            String name = myFile.getName();
            String ext = myFile.getExt();
            fld.getFileSystem().runAtomicAction(() -> {
                String finalName = findUnusedName(fld, name, ext);
                FileLock lock = myFile.lock();
                try {
                    myFile.move(lock, fld, finalName, ext);
                } finally {
                    lock.releaseLock();

                }
            });
            updateFilesInCookieSet();
        }
        return getPrimaryFile();
    }

    @Override
    protected DataObject handleCreateFromTemplate(DataFolder df, String name) throws IOException {
        FileObject myFile = getPrimaryFile();
        String ext = getPrimaryFile().getExt();
        FileObject fld = df.getPrimaryFile();
        FileObject[] created = new FileObject[1];
        fld.getFileSystem().runAtomicAction(new AtomicAction() {
            @Override
            public void run() throws IOException {
                String finalName = findUnusedName(fld, name, ext);
                FileObject nue = fld.createData(finalName);
                created[0] = nue;
                try (InputStream in = myFile.getInputStream()) {
                    try (OutputStream out = nue.getOutputStream()) {
                        FileUtil.copy(in, out);
                    }
                }
            }
        });
        return DataObject.find(created[0]);
    }

    @SuppressWarnings("empty-statement")
    private String findUnusedName(FileObject fld, String name, String ext) {
        String targetName = name;
        for (int i = 0; fld.getFileObject(targetName + "." + ext) != null; i++, targetName = name + "_" + i);
        return targetName;
    }

    @Override
    protected <T extends Node.Cookie> T getCookie(DataShadow shadow, Class<T> clazz) {
        return lookup.lookup(clazz);
    }

    @Override
    public boolean isShadowAllowed() {
        return false;
    }

    @Override
    protected Node createNodeDelegate() {
        return new AdhocDataNode(this);
    }

    private static final class AdhocDataNode extends DataNode implements FileChangeListener {

        private boolean hasGrammarFile;

        AdhocDataNode(AdhocDataObject obj) {
            this(obj, new ShowGrammarChildren(obj));
        }

        AdhocDataNode(AdhocDataObject obj, Children ch) {
            super(obj, ch);
            FileObject fo = findGrammarFile();
            hasGrammarFile = fo != null;
            if (fo != null) {
                fo.addFileChangeListener(FileUtil.weakFileChangeListener(this, fo));
            }
        }

        @Override
        public Action[] getActions(boolean context) {
            List<Action> result = new ArrayList<>();
            result.add(SystemAction.get(OpenAction.class).createContextAwareInstance(getLookup()));
            result.add(null);
            result.add(new OpenGrammarAction());
            result.add(null);
            result.add(SystemAction.get(CutAction.class).createContextAwareInstance(getLookup()));
            result.add(SystemAction.get(CopyAction.class).createContextAwareInstance(getLookup()));
            if (!SampleFiles.isSampleFile(getDataObject())) {
                result.add(SystemAction.get(RenameAction.class).createContextAwareInstance(getLookup()));
                result.add(SystemAction.get(DeleteAction.class).createContextAwareInstance(getLookup()));
            }
            result.add(null);
            result.add(SystemAction.get(ToolsAction.class).createContextAwareInstance(getLookup()));
            return result.toArray(new Action[result.size()]);
        }

        @Messages("openAssociatedGrammar=Open Associated &Grammar")
        final class OpenGrammarAction extends AbstractAction {

            OpenGrammarAction() {
                super(Bundle.openAssociatedGrammar());
            }

            @Override
            public void actionPerformed(ActionEvent ae) {
                Path grammarFile = AdhocMimeTypes.grammarFilePathForMimeType(getLookup().lookup(DataObject.class).getPrimaryFile().getMIMEType());
                FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(grammarFile.toFile()));
                if (fo != null) {
                    RequestProcessor.getDefault().submit(() -> {
                        DataObject dob = DataObject.find(fo);
                        OpenCookie ck = dob.getLookup().lookup(OpenCookie.class);
                        if (ck != null) {
                            ck.open();
                        }
                        return null;
                    });
                }
            }
        }

        public String getName() {
            return getDataObject().getPrimaryFile().getName();
        }

        @Override
        public Image getOpenedIcon(int type) {
            return getIcon(type);
        }

        @Override
        public String getDisplayName() {
            if (SampleFiles.isSampleFile(getDataObject())) {
                return getDataObject().getName();
            }
            FileObject fo = getDataObject().getPrimaryFile();
            if (fo.getExt().startsWith(AdhocMimeTypes.FILE_EXTENSION_PREFIX)) {
                return fo.getName();
            }
            return super.getDisplayName();
        }

        private String mimeType() {
            FileObject theFile = getDataObject().getPrimaryFile();
            String mime = theFile.getMIMEType();
            if ("content/unknown".equals(mime)) {
                mime = AdhocMimeTypes.mimeTypeForFileExtension(theFile.getExt());
            }
            return mime;
        }

        private FileObject findGrammarFile() {
            String mime = mimeType();
            Path grammar = AdhocMimeTypes.grammarFilePathForMimeType(mime);
            if (grammar == null) {
                return null;
            }
            FileObject fo = FileUtil.toFileObject(grammar.toFile());
            if (fo.isValid()) {
                return fo;
            }
            return null;
        }

        int ix = 0;

        @Override
        public String getHtmlDisplayName() {
            if (SampleFiles.isSampleFile(getDataObject())) {
                return getDataObject().getName();
            }
            // Allow some means of eventual refresh - getHtmlDisplayName is
            // frequently called.  Changing VCS branches and such can cause
            // temporary deletion and reappearance
            if (!hasGrammarFile && ix++ % 10 == 0) {
                hasGrammarFile = findGrammarFile() != null;
            }
            String name = super.getDisplayName();
            String grammarName = rawGrammarNameForMimeType(getDataObject().getPrimaryFile().getMIMEType());
            if (!hasGrammarFile) {
                name = "<font color=\"!nb.errorForeground\">" + name;
            }
            return grammarName == null
                    ? name
                    : name + " <font color=\"!controlShadow\">(" + grammarName + ")";
        }

        @Override
        public Image getIcon(int type) {
            return ImageUtilities.icon2Image(AntlrConstants.parserIcon());
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            hasGrammarFile = false;
            firePropertyChange(PROP_DISPLAY_NAME, null, null);
        }

        @Override
        public void fileFolderCreated(FileEvent fe) {
            // do nothing
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
            // do nothing
        }

        @Override
        public void fileChanged(FileEvent fe) {
            // do nothing
        }

        @Override
        public void fileRenamed(FileRenameEvent fre) {
            // do nothing
        }

        @Override
        public void fileAttributeChanged(FileAttributeEvent fae) {
            // do nothing
        }
    }

    private static final class ShowGrammarChildren extends Children.Keys<String> {

        private final AdhocDataObject obj;
        private static final String GRAMMAR_KEY = "grammar";

        ShowGrammarChildren(AdhocDataObject obj) {
            super(true);
            this.obj = obj;
        }

        @Override
        protected void addNotify() {
            setKeys(new String[]{GRAMMAR_KEY});
            super.addNotify();
        }

        @Override
        protected void removeNotify() {
            super.removeNotify();
            setKeys(new String[0]);
        }

        @Override
        @Messages({
            "# {0} - The name of the grammar file",
            "# {1} - The path to the grammar file's folder",
            "missingGrammarFile=Missing grammar file {0} in {1}",
            "# {0} - wrong mime type",
            "badMimeType=Not an Antlr MIME type: {0}"
        })
        protected Node[] createNodes(String t) {
            String mimeType = obj.getPrimaryFile().getMIMEType();
            Path path = AdhocMimeTypes.grammarFilePathForMimeType(mimeType);
            if (path == null) {
                return new Node[]{
                    new ErrorNode(Bundle.badMimeType(mimeType))
                };
            }
            FileObject grammarFile = FileUtil.toFileObject(path.toFile());
            if (grammarFile == null) {
                return new Node[]{new ErrorNode(Bundle.missingGrammarFile(
                    path.getFileName(), path.getParent()))};
            }
            try {
                DataObject dob = DataObject.find(grammarFile);
                return new Node[]{new FilterNode(dob.getNodeDelegate(), Children.LEAF)};
            } catch (DataObjectNotFoundException ex) {
                Logger.getLogger(ShowGrammarChildren.class.getName()).log(Level.WARNING,
                        "Dead data object " + path, ex);
                return new Node[]{new ErrorNode(ex.getLocalizedMessage())};
            }
        }
    }

    private static final class ErrorNode extends AbstractNode {

        ErrorNode(String msg) {
            super(Children.LEAF);
            setDisplayName(msg);
            setName(msg);
        }

        @Override
        public String getHtmlDisplayName() {
            return "<font color=\"!nb.errorForeground\">" + getDisplayName();
        }

        @Override
        public Image getIcon(int type) {
            return ImageUtilities.loadImage(AntlrConstants.ICON_PATH);
        }
    }
}
