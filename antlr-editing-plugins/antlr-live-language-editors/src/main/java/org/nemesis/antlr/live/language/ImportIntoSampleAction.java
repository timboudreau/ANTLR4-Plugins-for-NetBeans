/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.live.language;

import com.mastfrog.function.state.Bool;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.live.ParsingUtils;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileChooserBuilder;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.NbDocument;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
@Messages("import=Import...")
final class ImportIntoSampleAction extends AbstractAction implements ContextAwareAction {

    ImportIntoSampleAction() {
        putValue("hideWhenDisabled", true);
        putValue(NAME, Bundle._import());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        throw new UnsupportedOperationException("Main instance.");
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return createInstance(lkp);
    }

    static Action createInstance(Lookup lkp) {
        DataObject dob = lkp.lookup(DataObject.class);
        if (dob == null) {
            FileObject fo = dob.getPrimaryFile();
            boolean isSampleFile = FileUtil.isParentOf(FileUtil.getConfigRoot(), fo);
            if (!isSampleFile) {
                return new NoAction();
            }
            return new ForSampleFile(dob);
        } else {
            return new NoAction();
        }
    }

    static Action createInstance(JTextComponent target) {
        return new ForTextComponent(target);
    }

    static final class ForTextComponent extends AbstractAction {

        private final JTextComponent target;

        ForTextComponent(JTextComponent target) {
            putValue(NAME, Bundle._import());
            putValue("hideWhenDisabled", true);
            System.out.println("create an import action");
            this.target = target;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            File file = new FileChooserBuilder(ForSampleFile.class)
                    .setFilesOnly(true).setTitle(Bundle._import())
                    .showOpenDialog();
            if (file != null && file.exists()) {
                FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
                if (fo != null) {
                    try {
                        String text = fo.asText();
                        StyledDocument doc = (StyledDocument) target.getDocument();
                        NbDocument.runAtomicAsUser(doc, () -> {
                            try {
                                doc.remove(0, doc.getLength());
                                doc.insertString(0, text, null);
                            } catch (BadLocationException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        });
                        ParsingUtils.parse(doc);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }

    }

    static final class ForSampleFile extends AbstractAction {

        private final DataObject file;

        public ForSampleFile(DataObject file) {
            this.file = file;
            putValue(NAME, Bundle._import());
            putValue("hideWhenDisabled", true);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            File file = new FileChooserBuilder(ForSampleFile.class)
                    .setFilesOnly(true).setTitle(Bundle._import())
                    .showOpenDialog();
            if (file != null && file.exists()) {
                FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
                if (fo != null) {
                    try {
                        String text = fo.asText();
                        Bool success = Bool.create();
                        EditorCookie ck = this.file.getLookup().lookup(EditorCookie.class);
                        if (ck != null) {
                            Document doc = ck.getDocument();
                            if (doc != null) {
                                NbDocument.runAtomicAsUser((StyledDocument) doc, () -> {
                                    try {
                                        doc.remove(0, doc.getLength());
                                        doc.insertString(0, text, null);
                                        success.set();
                                    } catch (BadLocationException ex) {
                                        Exceptions.printStackTrace(ex);
                                    }
                                });
                            }
                        }
                        if (!success.getAsBoolean()) {
                            try (OutputStream out = this.file.getPrimaryFile().getOutputStream()) {
                                out.write(text.getBytes(UTF_8));
                            }
                        }
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }
    }

    static final class NoAction extends AbstractAction {

        NoAction() {
            setEnabled(false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // do nothing
        }
    }
}
