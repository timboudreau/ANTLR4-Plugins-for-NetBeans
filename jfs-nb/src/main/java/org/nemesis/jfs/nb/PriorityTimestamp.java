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
