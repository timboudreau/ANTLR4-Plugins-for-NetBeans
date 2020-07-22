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
package org.nemesis.antlr.project.extensions.actions;

import com.mastfrog.util.strings.Strings;
import java.awt.EventQueue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.netbeans.modules.editor.indent.api.Reformat;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class PostGenerate implements Runnable {

    private final List<FileObject> toOpen = new ArrayList<>();

    public PostGenerate(Iterable<? extends FileObject> toOpen) {
        // We generate grammars in reverse-dependency-order,
        // but we would like to open them main-grammar-first
        // so a wad of token fragments isn't the first thinge
        // the user sees
        for (FileObject fo : toOpen) {
            this.toOpen.add(0, fo);
        }
    }

    @Override
    public void run() {
        boolean success = false;
        for (FileObject fo : toOpen) {
            try {
                success |= openOne(fo);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        if (success && !EventQueue.isDispatchThread()) {
            EventQueue.invokeLater(this);
        }
    }

    private boolean openOne(FileObject fo) throws IOException {
        DataObject dob = DataObject.find(fo);
        EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
        if (ck != null) {
            if (!EventQueue.isDispatchThread()) {
                // Well, we wind up opening the document with the
                // reformat in its undo history, but it will do
                StyledDocument doc = ck.openDocument();
                NbDocument.runAtomic(doc, () -> {
                    Reformat format = Reformat.get(doc);
                    if (format != null) {
                        format.lock();
                        try {
                            format.reformat(0, doc.getLength());
                        } catch (BadLocationException ex) {
                            Exceptions.printStackTrace(ex);
                        } finally {
                            format.unlock();
                        }
                    }
                });
                SaveCookie sck = dob.getLookup().lookup(SaveCookie.class);
                if (sck != null) {
                    sck.save();
                }
                return true;
            } else {
                ck.open();
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "PostGenerate(" + Strings.join(',', toOpen, FileObject::getNameExt);
    }

}
