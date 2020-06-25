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
package org.nemesis.antlr.refactoring;

import com.mastfrog.function.PetaFunction;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;
import org.openide.text.CloneableEditorSupport;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.Utilities;

/**
 * A ContextAwareAction implementation specific to refactoring actions, which
 * finds the token under the caret to determine enablement.
 *
 * @author Tim Boudreau
 */
public abstract class GenericRefactoringContextAction<T extends TokenId> implements Action, ContextAwareAction {

    private final List<PropertyChangeListener> listeners = new ArrayList<>();
    private final Map<String, Object> keyValuePairs = Collections.synchronizedMap(new HashMap<>());
    private final Lookup lkp;
    private boolean enabled;
    private final Supplier<Language<T>> langSupplier;
    private final Lis lis = new Lis();
    private Lookup.Result<CloneableEditorSupport> editorResult;
    private Caret caret;

    /**
     * Create an instance.
     *
     * @param langSupplier A supplier for the language which is used against the
     * active document's TokenHierarchy to find the target token for refactoring
     */
    protected GenericRefactoringContextAction(Supplier<Language<T>> langSupplier) {
        this(langSupplier, Utilities.actionsGlobalContext());
    }

    private GenericRefactoringContextAction(Supplier<Language<T>> langSupplier, Lookup lookup) {
        this.lkp = lookup;
        this.langSupplier = langSupplier;
    }

    /**
     * Called when the first listener is added. If additional listeners need to
     * be attached to objects in the lookup, do that here.
     */
    protected void addNotify() {
        // do nothing
    }

    /**
     * Called when the last listener is removed. If additional listeners were
     * added in addNotify(), remove them here.
     */
    protected void removeNotify() {
        // do nothing
    }

    /**
     * Create a fixed-context instance. For most cases the returned instance
     * will be adequate.
     *
     * @param lkp The lookup
     * @param doc The editor support
     * @param caret The caret
     * @param seq The token sequence
     * @param dot The caret dot position
     * @param tok The token at the caret
     * @return An action
     */
    protected Action createContextAction(Lookup lkp, CloneableEditorSupport doc, Caret caret, TokenSequence<T> seq,
            Integer dot, Token<T> tok) {
        return new CtxAction(doc, caret, seq, dot, tok);
    }

    /**
     * Get the lookup this instance is operating against.
     *
     * @return The lookup
     */
    protected final Lookup lookup() {
        return lkp;
    }

    @Override
    public final Object getValue(String key) {
        return keyValuePairs.get(key);
    }

    @Override
    public final void putValue(String key, Object value) {
        Object old = keyValuePairs.put(key, value);
        fire(key, old, value);
    }

    protected final void fire(String prop, Object old, Object nue) {
        List<PropertyChangeListener> pcls;
        synchronized (listeners) {
            pcls = new ArrayList<>(listeners);
        }
        if (!pcls.isEmpty() && !Objects.equals(old, nue)) {
            PropertyChangeEvent evt = new PropertyChangeEvent(this, prop, old, nue);
            for (PropertyChangeListener l : pcls) {
                l.propertyChange(evt);
            }
        }
    }

    @Override
    public final void setEnabled(boolean b) {
        if (enabled != b) {
            this.enabled = b;
            fire("enabled", !b, b);
        }
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    @Override
    public final void addPropertyChangeListener(PropertyChangeListener listener) {
        boolean notify;
        synchronized (listeners) {
            listeners.add(listener);
            notify = listeners.size() == 1;
        }
        if (notify) {
            _addNotify();
        }
    }

    @Override
    public final void removePropertyChangeListener(PropertyChangeListener listener) {
        boolean notify;
        synchronized (listeners) {
            boolean removed = listeners.remove(listener);
            notify = removed && listeners.size() == 0;
        }
        if (notify) {
            _removeNotify();
        }
    }

    private void _addNotify() {
        editorResult = lkp.lookupResult(CloneableEditorSupport.class);
        editorResult.addLookupListener(lis);
        setCaret(caret());
        checkEnabled();
        editorResult.allInstances();
        addNotify();
    }

    private void _removeNotify() {
        if (editorResult != null) {
            editorResult.removeLookupListener(lis);
            editorResult = null;
        }
        if (caret != null) {
            caret.removeChangeListener(lis);
            caret = null;
        }
        removeNotify();
    }

    private void checkEnabled() {
        setEnabled(checkEnabled(lkp));
    }

    private synchronized void setCaret(Caret caret) {
        if (caret != this.caret) {
            if (this.caret != null) {
                this.caret.removeChangeListener(lis);
            }
            this.caret = caret;
            if (caret != null) {
                caret.addChangeListener(lis);
            }
        }
    }

    Caret caret() {
        CloneableEditorSupport supp = lkp.lookup(CloneableEditorSupport.class);
        if (supp == null) {
            return null;
        }
        Document doc = supp.getDocument();
        if (doc == null) {
            return null;
        }
        JTextComponent comp = component(doc);
        if (comp == null) {
            return null;
        }
        return comp.getCaret();
    }

    boolean checkEnabled(Lookup lkp) {
        Boolean result = withRelevantObjects(lkp, new PetaFunction<CloneableEditorSupport, Caret, TokenSequence<T>, Integer, Token<T>, Boolean>() {
            @Override
            public Boolean apply(CloneableEditorSupport doc, Caret caret, TokenSequence<T> seq, Integer dot, Token<T> tok) {
                boolean ena = isEnabled(doc, caret, seq, dot, tok);
                return ena;
            }
        });
        return result == null ? false : result.booleanValue();
    }

    private <R> R withRelevantObjects(Lookup lkp, PetaFunction<CloneableEditorSupport, Caret, TokenSequence<T>, Integer, Token<T>, R> pc) {
        CloneableEditorSupport supp = lkp.lookup(CloneableEditorSupport.class);
        if (supp == null) {
            return null;
        }
        StyledDocument doc = supp.getDocument();
        if (doc == null) {
            return null;
        }
        JTextComponent comp = component(doc);
        if (comp == null) {
            return null;
        }
        Caret caret = comp.getCaret();
        BaseDocument bd = (BaseDocument) doc;
        bd.readLock();
        try {
            TokenHierarchy<StyledDocument> hier = TokenHierarchy.get(doc);
            TokenSequence<T> seq = hier.tokenSequence(langSupplier.get());
            if (seq == null) {
                return null;
            }
            int dot = caret.getDot();
            seq.move(dot);
            seq.moveNext();
            Token<T> tok = seq.offsetToken();
            if (tok == null) {
                return null;
            }
            return pc.apply(supp, caret, seq, dot, tok);
        } finally {
            bd.readUnlock();
        }
    }

    protected abstract boolean isEnabled(CloneableEditorSupport doc, Caret caret, TokenSequence<T> seq,
            int caretPosition, Token<T> caretToken);

    protected abstract void perform(CloneableEditorSupport doc, Caret caret, TokenSequence<T> seq,
            int caretPosition, Token<T> caretToken);

    private JTextComponent component(Document document) {
        return EditorRegistry.findComponent(document);
    }

    @Override
    public final void actionPerformed(ActionEvent e) {
        Action action = createContextAwareInstance(lkp);
        if (action.isEnabled()) {
            action.actionPerformed(e);
        }
    }

    @Override
    public final Action createContextAwareInstance(Lookup lkp) {
        Action result = withRelevantObjects(lkp, (CloneableEditorSupport doc, Caret caret1,
                TokenSequence<T> seq, Integer dot, Token<T> tok) -> {
            boolean enabled = isEnabled();
            if (!isEnabled(doc, caret, seq, dot, tok)) {
                return new GenericDisabledAction((String) getValue(NAME));
            }
            return createContextAction(lkp, doc, caret1, seq, dot, tok);
        });
        if (result == null) {
            return new GenericDisabledAction((String) getValue(NAME));
        }
        return result;
    }

    private class Lis implements LookupListener, ChangeListener {

        @Override
        public void resultChanged(LookupEvent le) {
            setCaret(caret());
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            checkEnabled();
        }
    }

    private class CtxAction implements Action {

        private final CloneableEditorSupport doc;
        private final Caret caret;
        private final TokenSequence<T> seq;
        private final int dot;
        private final Token<T> tok;

        private CtxAction(CloneableEditorSupport doc, Caret caret, TokenSequence<T> seq, Integer dot, Token<T> tok) {
            this.doc = doc;
            this.caret = caret;
            this.seq = seq;
            this.dot = dot;
            this.tok = tok;
        }

        @Override
        public Object getValue(String key) {
            return GenericRefactoringContextAction.this.getValue(key);
        }

        @Override
        public void putValue(String key, Object value) {
            GenericRefactoringContextAction.this.putValue(key, value);
        }

        @Override
        public void setEnabled(boolean b) {
            throw new UnsupportedOperationException("Not settable imperatively.");
        }

        @Override
        public boolean isEnabled() {
            if (caret.getDot() != dot) {
                return false;
            }
            return GenericRefactoringContextAction.this.isEnabled(doc, caret, seq, dot, tok);
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            // do nothing
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            // do nothing
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            perform(doc, caret, seq, dot, tok);
        }
    }

    static final class GenericDisabledAction implements Action {

        private final String name;

        public GenericDisabledAction(String name) {
            this.name = name == null ? "None" : name;
        }

        @Override
        public Object getValue(String key) {
            switch (key) {
                case NAME:
                    return name;
                default:
                    return null;
            }
        }

        @Override
        public void putValue(String key, Object value) {
            // do nothing
        }

        @Override
        public void setEnabled(boolean b) {
            // do nothing
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            // do nothing
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            // do nothing
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // do nothing
        }
    }
}
