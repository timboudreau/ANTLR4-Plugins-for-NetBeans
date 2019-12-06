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
package org.nemesis.antlr.live.language;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JToolBar;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.MultiKeymap;
import org.netbeans.editor.ext.ExtKit;
import org.netbeans.modules.editor.NbEditorDocument;
import org.netbeans.modules.editor.NbEditorUI;
import org.openide.util.NbBundle.Messages;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Language;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.awt.Mnemonics;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocEditorKit extends ExtKit {

    private final String mimeType;
    private final EditorKit plainTextEditorKit;

    public AdhocEditorKit(String mimeType) {
        this.mimeType = mimeType;
        plainTextEditorKit = MimeLookup.getLookup(MimePath.parse("text/plain")).lookup(EditorKit.class);
    }

    private ExtKit delegate() {
        if (plainTextEditorKit != null && plainTextEditorKit instanceof ExtKit) {
            return (ExtKit) plainTextEditorKit;
        }
        return null;
    }

    @Override
    public String getContentType() {
        return mimeType;
    }

    private final Map<String, Method> methods = new HashMap<>();

    private Method lookupMethod(String name, Class<?>... argTypes) {
        ExtKit del = delegate();
        Class<?> type = del.getClass();
        Method result = null;
        while (type != Object.class) {
            try {
                result = type.getDeclaredMethod(name, argTypes);
                break;
            } catch (NoSuchMethodException | SecurityException ex) {
                if (type.getSuperclass() == Object.class) {
                    ex.printStackTrace();
                }
                type = type.getSuperclass();
            }
        }
        if (result != null) {
            methods.put(name, result);
        }
        return result;
    }

    private <T> T call(String method) {
        return call(method, new Class<?>[0], new Object[0]);
    }

    @SuppressWarnings("unchecked")
    private <T> T call(String name, Class<?>[] argTypes, Object... args) {
        T result = null;
        Method m = lookupMethod(name, argTypes);
        try {
            result = (T) m.invoke(delegate(), args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(AdhocEditorKit.class.getName()).log(Level.WARNING,
                    "Could not invoke method {0} on {1}",
                    new Object[]{name, delegate().getClass().getName()});
        }
        return result;
    }

    @Override
    public Action getActionByName(String name) {
        ExtKit delegate = delegate();
        if (delegate != null) {
            Action result = delegate.getActionByName(name);
            return result;
        }
        return super.getActionByName(name); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected Action[] getDeclaredActions() {
        ExtKit delegate = delegate();
        if (delegate != null) {
            Action[] result = this.call("getDeclaredActions");
            if (result != null) {
                return result;
            }
        }
        return super.getDeclaredActions(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected Action[] getCustomActions() {
        ExtKit delegate = delegate();
        if (delegate != null) {
            Action[] result = this.call("getCustomActions");
            if (result != null) {
                return result;
            }
        }
        return super.getCustomActions(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected Action[] createActions() {
        Action[] result = null;
        ExtKit delegate = delegate();
        if (delegate != null) {
            result = this.call("createActions");
            if (result == null) {
                result = super.createActions();
            }
        }
        for (int i = 0; i < result.length; i++) {
            if (result[i] instanceof PasteAction) {
                result[i] = new AsyncPasteAction(false);
            }
        }
        return result;
    }

    @Override
    protected void initDocument(BaseDocument doc) {
        super.initDocument(doc); //To change body of generated methods, choose Tools | Templates.
        doc.putProperty("mimeType", mimeType);
    }

    @Override
    public MultiKeymap getKeymap() {
        ExtKit delegate = delegate();
        if (delegate != null) {
            return delegate.getKeymap();
        }
        return super.getKeymap();
    }

    @Override
    public Document createDefaultDocument() {
        return new Doc(mimeType);
    }

    @Override
    protected EditorUI createEditorUI() {
//        EditorUI result = super.createEditorUI();
        EditorUI result = new NbEditorUI();

        return result;
    }

    // Give the lexer a way to find out what document it's lexing
    private static final ThreadLocal<Document> RENDERING_DOCUMENT
            = new ThreadLocal<>();

    public static Document currentDocument() {
        return RENDERING_DOCUMENT.get();
    }

    public static FileObject currentFileObject() {
        Document doc = currentDocument();
        if (doc != null) {
            return NbEditorUtilities.getFileObject(doc);
        }
        return null;
    }

    public static AdhocDataObject currentDataObject() {
        FileObject fo = currentFileObject();
        if (fo != null) {
            try {
                DataObject dob = DataObject.find(fo);
                return dob.getLookup().lookup(AdhocDataObject.class);
            } catch (DataObjectNotFoundException ex) {
                Logger.getLogger(AdhocEditorKit.class.getName())
                        .log(Level.SEVERE, "No dob for " + fo, ex);
            }
        }
        return null;
    }

    static final class AsyncPasteAction extends PasteAction {

        public AsyncPasteAction(boolean formatted) {
            super(formatted);
        }

        @Override
        public void actionPerformed(ActionEvent evt, JTextComponent target) {
            EventQueue.invokeLater(() -> {
                super.actionPerformed(evt, target);
            });
        }

    }

    // We have to subclass this in order to supply a toolbar, or the infrastructure
    // throws an exception, since there is no toolbar provider registered
    static final class Doc extends NbEditorDocument {

        private JToolBar bar;
        private final String mimeType;

        @SuppressWarnings("LeakingThisInConstructor")
        Doc(String mimeType) {
            super(mimeType);
            this.mimeType = mimeType;
            putProperty("mimeType", mimeType);
            putProperty(InputAttributes.class, new LangPropertyEvaluator(this));
        }

        static final class LangPropertyEvaluator implements PropertyEvaluator {
            // Looking up the language during Document creation can deadlock
            // by reentering LanguageHierarchy.language(), so initialize that
            // on demand

            private final InputAttributes attrs = new InputAttributes();
            private final Doc doc;
            private volatile int createdAtLastCall = -1;

            public LangPropertyEvaluator(Doc doc) {
                this.doc = doc;
            }

            @Override
            public Object getValue() {
                // XXX could keep the hiearchy atomicInt count
                // as of the last call to get value, and only update
                // on change
                int created = AdhocLanguageHierarchy.hierarchiesCreated();
                if (created > createdAtLastCall) {
                    Language<?> lang = Language.find(doc.mimeType);
                    if (lang != null) {
                        attrs.setValue(lang, "doc", doc, false);
                    }
                    createdAtLastCall = created;
                }
                return attrs;
            }

        }

        static final class LazyLanguageMap extends LazyPropertyMap {

            private final Doc doc;

            public LazyLanguageMap(Dictionary dict, Doc doc) {
                super(dict);
                this.doc = doc;
            }

            @Override
            public Object get(Object key) {
                if (InputAttributes.class.equals(key)) {
                    InputAttributes attrs = new InputAttributes();
                    Language<?> lang = Language.find(doc.mimeType);
                    if (lang != null) {
                        attrs.setValue(lang, "doc", doc, false);
                    }
                    return attrs;
                }
                Object result = super.get(key);
                return result;
            }
        }

        @Override
        public JToolBar createToolbar(JEditorPane j) {
            if (bar == null) {
                bar = createBar();
            }
            return bar;
        }

        @Override
        public void runAtomicAsUser(Runnable r) {
            super.runAtomicAsUser(wrap(r));
        }

        @Override
        public void runAtomic(Runnable r) {
            super.runAtomic(wrap(r));
        }

        @Override
        public void render(Runnable r) {
            super.render(wrap(r));
        }

        private Runnable wrap(Runnable r) {
            return () -> {
                Document old = RENDERING_DOCUMENT.get();
                try {
                    RENDERING_DOCUMENT.set(this);
                    r.run();
                } finally {
                    RENDERING_DOCUMENT.set(old);
                }
            };
        }

//        private final DefaultComboBoxModel<String> rulesModel = new DefaultComboBoxModel<>();
        @Messages("STARTING_RULE=Parse Text &Using")
        private JToolBar createBar() {
            JToolBar bar = new JToolBar();
            JLabel lbl = new JLabel();
            Mnemonics.setLocalizedText(lbl, Bundle.STARTING_RULE());
            bar.add(lbl);
//            JComboBox<String> box = new JComboBox<>(rulesModel);
//            lbl.setLabelFor(box);
//            box.setPrototypeDisplayValue("compilation_unit_body");
//            bar.add(box);
//            NBANTLRv4Parser.notifyOnReparse(this, this);
            return bar;
        }

        /*
        class RV implements ObjectGraphVisitor<String> {

            String oldSelection;
            boolean oldSelectionFound;

            RV() {
                oldSelection = (String) rulesModel.getSelectedItem();
                rulesModel.removeAllElements();
            }

            @Override
            public void enterNode(String rule, int depth) {
                if (rule.equals(oldSelection)) {
                    oldSelectionFound = true;
                }
                rulesModel.addElement(rule);
            }

            @Override
            public void exitNode(String rule, int depth) {
            }

            void restoreSelection() {
                if (oldSelectionFound) {
                    rulesModel.setSelectedItem(oldSelection);
                }
            }
        }
         */
    }
}
