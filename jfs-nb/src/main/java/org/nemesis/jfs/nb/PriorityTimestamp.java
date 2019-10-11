package org.nemesis.jfs.nb;

import java.util.concurrent.atomic.AtomicLong;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import org.netbeans.lib.editor.util.swing.DocumentListenerPriority;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;

/**
 * NetBeans editor already does this, but it appears that it is updated too late
 * for our purposes - we really need this to run FIRST.
 *
 * @author Tim Boudreau
 */
final class PriorityTimestamp implements DocumentListener {

    private final AtomicLong timestamp;

    PriorityTimestamp(long initialValue) {
        timestamp = new AtomicLong(initialValue);
    }

    static long getTimestamp(Document doc) {
        return get(doc).get();
    }

    static PriorityTimestamp get(Document doc) {
        PriorityTimestamp result
                = (PriorityTimestamp) doc.getProperty(PriorityTimestamp.class);
        if (result == null) {
            long initial = DocumentUtilities.getDocumentTimestamp(doc);
            if (initial == 0) {
                FileObject fo = NbEditorUtilities.getFileObject(doc);
                if (fo != null) {
                    initial = fo.lastModified().getTime();
                }
            }
            result = new PriorityTimestamp(initial == 0
                    ? System.currentTimeMillis()
                    : initial);
            // Object is self-contained, not a leak
            doc.putProperty(PriorityTimestamp.class, result);
            DocumentUtilities.addPriorityDocumentListener(doc, result,
                    DocumentListenerPriority.FIRST);
        }
        return result;
    }

    long get() {
        return timestamp.get();
    }

    private void touch() {
        timestamp.getAndUpdate((old) -> {
            return System.currentTimeMillis();
        });
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        touch();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        touch();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        touch();
    }

}
