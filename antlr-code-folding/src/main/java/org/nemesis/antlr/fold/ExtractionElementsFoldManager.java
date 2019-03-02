package org.nemesis.antlr.fold;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import org.nemesis.extraction.key.RegionsKey;
import org.netbeans.api.editor.document.EditorDocumentUtils;
import org.netbeans.api.editor.fold.Fold;
import org.netbeans.api.editor.fold.FoldHierarchy;
import org.netbeans.spi.editor.fold.FoldHierarchyTransaction;
import org.netbeans.spi.editor.fold.FoldManager;
import org.netbeans.spi.editor.fold.FoldOperation;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;

final class ExtractionElementsFoldManager<T> implements FoldManager {

    private FoldOperation operation;
    private ExtractionFoldTask<T> task;
    private final RegionsKey<T> key;
    private final SemanticRegionToFoldConverter<T> converter;
    static final Logger LOG = Logger.getLogger(ExtractionElementsFoldManager.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }

    ExtractionElementsFoldManager(RegionsKey<T> key, SemanticRegionToFoldConverter<T> converter) {
        this.key = key;
        this.converter = converter;
        LOG.log(Level.FINE, "New ExtractionElementsFoldManager with {0} and {1}", new Object[] {key, converter});
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ExtractionElementsFoldManager{");
        sb.append(key);
        if (operation != null) {
            sb.append(", comp=");
            FoldHierarchy hier = operation.getHierarchy();
            if (hier != null) {
                sb.append(System.identityHashCode(hier.getComponent()));
                sb.append(", ").append(hier.getComponent().getDocument());
            } else {
                sb.append("none");
            }
        }
        return sb.append('}').toString();
    }

    private boolean first = true;
    boolean first() {
        boolean wasFirst = first;
        first = false;
        return wasFirst;
    }

    RegionsKey<T> key() {
        return key;
    }

    FoldOperation operation() {
        return operation;
    }

    @Override
    public void init(FoldOperation operation) {
        this.operation = operation;
        LOG.log(Level.FINE, "Initialize {0} with {1}", new Object[] { this, operation });
    }

    @Override
    public synchronized void initFolds(FoldHierarchyTransaction transaction) {
        Document doc = operation.getHierarchy().getComponent().getDocument();

        LOG.log(Level.FINE, "initFolds {0} on {1}", new Object[] { doc, this });
        Object od = doc.getProperty(Document.StreamDescriptionProperty);
        if (od == null) {
            FileObject fo = EditorDocumentUtils.getFileObject(doc);
            if (fo != null) {
                try {
                    od = DataObject.find(fo);
                } catch (DataObjectNotFoundException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
        if (od instanceof DataObject) {
            FileObject file = ((DataObject) od).getPrimaryFile();

            task = ExtractionFoldRefresher.getDefault().getTask(file, key, converter);
            task.setElementFoldManager(ExtractionElementsFoldManager.this, file);
        } else {
            LOG.log(Level.WARNING, "No DataObject for {0} from {1}", new Object[] { doc, this});
        }
    }

    @Override
    public void insertUpdate(DocumentEvent evt, FoldHierarchyTransaction transaction) {
        invalidate();
    }

    @Override
    public void removeUpdate(DocumentEvent evt, FoldHierarchyTransaction transaction) {
        invalidate();
    }

    @Override
    public void changedUpdate(DocumentEvent evt, FoldHierarchyTransaction transaction) {
    }

    public void removeEmptyNotify(Fold emptyFold) {
        removeDamagedNotify(emptyFold);
    }

    public void removeDamagedNotify(Fold damagedFold) {
    }

    public void expandNotify(Fold expandedFold) {
    }

    public synchronized void release() {
        if (task != null) {
            task.setElementFoldManager(this, null);
        }
        task = null;
    }

    private synchronized void invalidate() {
        if (task != null) {
            task.invalidate();
        }
    }
}
