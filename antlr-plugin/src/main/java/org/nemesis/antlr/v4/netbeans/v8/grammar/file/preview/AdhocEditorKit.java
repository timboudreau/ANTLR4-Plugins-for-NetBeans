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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JEditorPane;
import javax.swing.JToolBar;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.MultiKeymap;
import org.netbeans.editor.ext.ExtKit;
import org.netbeans.modules.editor.NbEditorDocument;
import org.netbeans.modules.editor.NbEditorUI;
import org.openide.util.NbBundle.Messages;
import com.mastfrog.graph.ObjectGraphVisitor;

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
        ExtKit delegate = delegate();
        if (delegate != null) {
            Action[] result = this.call("createActions");
            if (result != null) {
                return result;
            }
        }
        return super.createActions();
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



    // We have to subclass this in order to supply a toolbar, or the infrastructure
    // throws an exception, since there is no toolbar provider registered
    static final class Doc extends NbEditorDocument implements Consumer<ANTLRv4ParserResult> {

        private JToolBar bar;
        Doc(String mimeType) {
            super(mimeType);
            putProperty("mimeType", mimeType);
        }

        @Override
        public JToolBar createToolbar(JEditorPane j) {
            if (bar == null) {
                bar = createBar();
            }
            return bar;
        }

        private final DefaultComboBoxModel<String> rulesModel = new DefaultComboBoxModel<>();
        @Messages("STARTING_RULE=Parse Text &Using")
        private JToolBar createBar() {
            JToolBar bar = new JToolBar();
//            JLabel lbl = new JLabel();
//            Mnemonics.setLocalizedText(lbl, Bundle.STARTING_RULE());
//            bar.add(lbl);
//            JComboBox<String> box = new JComboBox<>(rulesModel);
//            lbl.setLabelFor(box);
//            box.setPrototypeDisplayValue("compilation_unit_body");
//            bar.add(box);
//            NBANTLRv4Parser.notifyOnReparse(this, this);
            return bar;
        }

        @Override
        public void accept(ANTLRv4ParserResult t) {
            RV rv = new RV();
            t.semanticParser().ruleTree().walk(rv);
            rv.restoreSelection();
        }

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
    }
}
