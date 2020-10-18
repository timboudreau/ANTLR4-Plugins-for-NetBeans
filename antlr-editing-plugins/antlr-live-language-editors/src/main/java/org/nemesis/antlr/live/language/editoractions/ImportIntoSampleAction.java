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
package org.nemesis.antlr.live.language.editoractions;

import com.mastfrog.function.state.Bool;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.live.language.PriorityWakeup;
import org.nemesis.editor.ops.CaretInformation;
import org.nemesis.editor.ops.CaretPositionCalculator;
import org.nemesis.editor.ops.DocumentOperator;
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
import org.openide.util.NbPreferences;

/**
 *
 * @author Tim Boudreau
 */
@Messages("import=Import File...")
public final class ImportIntoSampleAction extends AbstractAction implements ContextAwareAction {

    public ImportIntoSampleAction() {
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

    static JMenuItem submenu(JTextComponent component) {
        JMenuItem top = new JMenuItem(createInstance(component));
        List<Path> recent = recentPaths();
        if (!recent.isEmpty()) {
            JMenu result = new JMenu(Bundle.importMenu());
            result.add(top);
            result.add(new JSeparator());
            for (Path p : recent) {
                result.add(new RecentPathAction(p, component, recent));
            }
            return result;
        } else {
            return top;
        }
    }

    static int RECENT_LIST_SIZE = 14;

    static List<Path> recentPaths() {
        Preferences prefs = NbPreferences.forModule(ImportIntoSampleAction.class);
        String keyBase = "recentImports-";
        List<Path> result = new ArrayList<>(RECENT_LIST_SIZE);
        for (int i = 0; i < RECENT_LIST_SIZE; i++) {
            String key = keyBase + i;
            String path = prefs.get(key, null);
            Path p = null;
            if (path != null) {
                Path pth = Paths.get(path);
                if (Files.exists(pth)) {
                    p = pth;
                }
            }
            if (p != null) {
                result.add(p);
            }
        }
        return result;
    }

    static void setRecentPaths(List<Path> paths) {
        Preferences prefs = NbPreferences.forModule(ImportIntoSampleAction.class);
        String keyBase = "recentImports-";
        for (int i = 0; i < paths.size(); i++) {
            String key = keyBase + i;
            String path = paths.get(i).toString();
            prefs.put(key, path);
        }
    }

    private static final class RecentPathAction extends AbstractAction {

        private final Path path;
        private final JTextComponent target;
        private final List<Path> allPaths;

        RecentPathAction(Path path, JTextComponent comp, List<Path> allPaths) {
            putValue(NAME, path.getFileName().toString());
            this.path = path;
            this.target = comp;
            this.allPaths = allPaths;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
            Cursor oldCursor = target.getCursor();
            target.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            // Using a high pirority thread here helps keep other tasks
            // out of our way that might otherwise try to start chewing on the
            // document
            PriorityWakeup.runImmediately(() -> {
                replaceDocumentContentsFromFile(path, fo, oldCursor, target, allPaths);
//                try {
//                    NbAntlrUtils.parseImmediately(target.getDocument());
//                } catch (Exception ex) {
//                    Exceptions.printStackTrace(ex);
//                }
            });
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
                    Cursor cursor = target.getCursor();
                    target.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    // Using a high pirority thread here helps keep other tasks
                    // out of our way that might otherwise try to start chewing on the
                    // document
                    PriorityWakeup.runImmediately(() -> {
                        replaceDocumentContentsFromFile(file.toPath(), fo, cursor, target, recentPaths());
//                        try {
//                            NbAntlrUtils.parseImmediately(target.getDocument());
//                        } catch (Exception ex) {
//                            Exceptions.printStackTrace(ex);
//                        }
                    });
                }
            }
        }
    }

    static void replaceDocumentContentsFromFile(Path path, FileObject fo, Cursor oldCursor, JTextComponent on, List<Path> allPaths) {
        try {
            String text = fo.asText();
            StyledDocument doc = (StyledDocument) on.getDocument();
            DocumentOperator.builder().acquireAWTTreeLock()
                    .blockIntermediateRepaints()
                    .disableTokenHierarchyUpdates()
                    .lockAtomicAsUser()
                    .writeLock()
                    .restoringCaretPosition(CARET_TO_ZERO)
                    .build().operateOn(doc)
                    .runOp(() -> {
                        doc.remove(0, doc.getLength());
                        doc.insertString(0, text, null);
                    });
            allPaths.remove(path);
            allPaths.add(0, path);
            setRecentPaths(allPaths);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            EventQueue.invokeLater(() -> {
                on.setCursor(oldCursor);
            });
        }
    }

    /**
     * Resets the caret to the top of the file after replacing its contents.
     */
    private static final CaretPositionCalculator CARET_TO_ZERO
            = (CaretInformation caret, JTextComponent comp, Document doc) -> bi -> {
                bi.accept(0, 0);
            };

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
