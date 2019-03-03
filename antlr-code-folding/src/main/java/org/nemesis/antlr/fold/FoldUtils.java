package org.nemesis.antlr.fold;

import java.io.IOException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.document.EditorDocumentUtils;
import org.netbeans.api.editor.fold.FoldHierarchy;
import org.netbeans.spi.editor.fold.FoldOperation;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 *
 * @author Tim Boudreau
 */
final class FoldUtils {

    private static final Logger LOG = Logger.getLogger(FoldUtils.class.getName());

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

    static String opToString(FoldOperation op) {
        if (op == null) {
            return "<null>";
        }
        StringBuilder sb = new StringBuilder(120);
        JTextComponent comp = componentForOp(op);
        int ihash = comp == null ? 0 : System.identityHashCode(comp);
        sb.append("comp=").append(ihash).append(", ");
        Document doc = documentFor(op);
        sb.append("doc=").append(doc);
        return sb.toString();
    }

    static JTextComponent componentForOp(FoldOperation op) {
        FoldHierarchy hier = op.getHierarchy();
        if (hier != null) {
            return hier.getComponent();
        }
        return null;
    }

    static Supplier<String> opStringSupplier(FoldOperation op) {
        return new OpStringSupplier(op);
    }

    private static final class OpStringSupplier implements Supplier<String> {

        private final FoldOperation op;

        public OpStringSupplier(FoldOperation op) {
            this.op = op;
        }

        @Override
        public String get() {
            return opToString(op);
        }

        public String toString() {
            return get();
        }

    }

    private FoldUtils() {
        throw new AssertionError();
    }
}
