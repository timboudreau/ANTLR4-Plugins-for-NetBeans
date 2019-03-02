package org.nemesis.antlr.fold.revised;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.document.EditorDocumentUtils;
import org.netbeans.api.editor.fold.FoldHierarchy;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.spi.editor.fold.FoldOperation;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class FoldTasks {

    private static final int GC_INTERVAL = 5;
    private final Map<DocOrFileKey, FoldTask> taskForKey = new HashMap<>();
    static final Logger LOG = Logger.getLogger(FoldTasks.class.getName());
    private int gcCountdown;
    private static final FoldTasks INSTANCE = new FoldTasks();

    public static FoldTasks getDefault() {
        return INSTANCE;
    }

    public FoldTask forSnapshot(Snapshot snapshot, boolean create) {
        DocOrFileKey key = keyFor(snapshot);
        FoldTask result = null;
        if (key != null) {
            result = taskForKey.get(key);
            if (result == null && create) {
                result = newFoldTask(key);
                LOG.log(Level.FINE, "Created FoldTask {0} for key {1} for "
                        + "snap {2}", new Object[]{result, key, snapshot});
                taskForKey.put(key, result);
            }
        }
        maybeGc();
        return result;
    }

    public FoldTask forOperation(FoldOperation op, boolean create) {
        DocOrFileKey key = keyFor(op);
        FoldTask result = null;
        if (key != null) {
            result = taskForKey.get(key);
            if (result == null && create) {
                result = newFoldTask(key);
                LOG.log(Level.FINE, "Created FoldTask {0} for key {1} for "
                        + "op {2}", new Object[]{result, key, op});
                taskForKey.put(key, result);
            }
        }
        maybeGc();
        return result;
    }

    private FoldTask newFoldTask(DocOrFileKey key) {
        return new FoldTask();
    }

    private void maybeGc() {
        if (gcCountdown++ % GC_INTERVAL == 0) {
            gc();
        }
    }

    private void gc() {
        try {
            LOG.log(Level.FINER, "FoldTasks.gc with {0} tasks", taskForKey.size());
            for (Iterator<Map.Entry<DocOrFileKey, FoldTask>> it = taskForKey.entrySet().iterator(); it.hasNext();) {
                Map.Entry<DocOrFileKey, FoldTask> e = it.next();
                DocOrFileKey key = e.getKey();
                if (key.isDead() || e.getValue().isEmpty()) {
                    LOG.log(Level.FINEST, "  Dead key or fold task for {0}", key);
                    it.remove();
                }
            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    static FileObject fileObjectForDocument(Document doc) {
        return EditorDocumentUtils.getFileObject(doc);
    }

    static Document documentForFileObject(FileObject file) {
        try {
            DataObject dob = DataObject.find(file);
            EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
            return ck.openDocument();
        } catch (IOException ex) {
            LOG.log(Level.FINE, "Exception looking up data object for " + file, ex);
        }
        return null;
    }

    static Document documentFor(FoldOperation op) {
        if (op == null) {
            return null;
        }
        FoldHierarchy hierarchy = op.getHierarchy();
        if (hierarchy == null) {
            return null;
        }
        JTextComponent comp = hierarchy.getComponent();
        if (comp == null) {
            return null;
        }
        return comp.getDocument();
    }

    private static DocOrFileKey keyFor(FoldOperation op) {
        Document doc = documentFor(op);
        if (doc != null) {
            FileObject file = fileObjectForDocument(doc);
            return new DocOrFileKey(doc, file);
        }
        return null;
    }

    private static DocOrFileKey keyFor(Snapshot snapshot) {
        Source src = snapshot.getSource();
        return new DocOrFileKey(src.getDocument(true), src.getFileObject());
    }
}
