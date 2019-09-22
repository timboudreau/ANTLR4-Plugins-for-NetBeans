package org.nemesis.jfs;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Set;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import org.nemesis.jfs.spi.JFSUtilities;
import org.netbeans.api.queries.FileEncodingQuery;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.util.WeakListeners;
import org.openide.util.WeakSet;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = JFSUtilities.class)
public class NbJFSUtilities extends JFSUtilities {

    @Override
    protected Charset getEncodingFor(Path file) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file.toFile()));
        return fo == null ? null : FileEncodingQuery.getEncoding(fo);
    }

    @Override
    protected long getLastModifiedFor(Document document) {
        return DocumentUtilities.getDocumentTimestamp(document);
    }

    @Override
    protected void weakListen(Document document, DocumentListener listener) {
        document.addDocumentListener(WeakListeners.document(listener, document));
    }

    @Override
    protected <T> Set<T> createWeakSet() {
        return new WeakSet<>();
    }

    @Override
    protected <T> T convert(JFSFileObject file, Class<T> type, Object obj) {
        if (type == FileObject.class) {
            FileObject fo = null;
            if (obj instanceof Path) {
                fo = FileUtil.toFileObject(FileUtil.normalizeFile(((Path) obj).toFile()));
                return fo == null ? null : type.cast(fo);
            } else if (obj instanceof Document) {
                fo = NbEditorUtilities.getFileObject((Document) obj);
            }
            if (fo != null) {
                return type.cast(fo);
            }
        }
        return super.convert(file, type, obj);
    }

}
