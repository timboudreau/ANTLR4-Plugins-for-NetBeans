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

import org.nemesis.antlr.live.language.editoractions.AdhocEditorPopupAction;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.util.Dictionary;
import javax.swing.Action;
import javax.swing.JToolBar;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.editor.doc.EnhEditorDocument;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.MultiKeymap;
import org.netbeans.editor.ext.ExtKit;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Language;
import org.netbeans.editor.BaseTextUI;
import org.netbeans.editor.EditorUI;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocEditorKit extends NbEditorKit {

    private final String mimeType;
    private static final ThreadLocal<Doc> RENDERING_DOCUMENT = new ThreadLocal<>();
    static final RequestProcessor ADHOC_POPULATE_MENU_POOL = new RequestProcessor("populate-adhoc-popup", 1, true);
    // We get registered actions and such from the text/plain mime type,
    // so create a fake editor kit that will load and expose those
    private static final FakePlainKit plainTextEditorKit = new FakePlainKit();

    public AdhocEditorKit(String mimeType) {
        this.mimeType = mimeType;
    }

    public static RequestProcessor popupPool() {
        return ADHOC_POPULATE_MENU_POOL;
    }

    @Override
    public String getContentType() {
        return mimeType;
    }

    @Override
    public Action getActionByName(String name) {
        switch (name) {
            case ExtKit.buildPopupMenuAction:
                return new AdhocEditorPopupAction(this);
        }
        return plainTextEditorKit.getActionByName(name);
    }

    @Override
    protected Action[] getDeclaredActions() {
        return plainTextEditorKit.getDeclaredActions();
    }

    @Override
    protected Action[] getCustomActions() {
        return plainTextEditorKit.getCustomActions();
    }

    @Override
    protected BaseTextUI createTextUI() {
        return super.createTextUI();
    }

    @Override
    protected EditorUI createEditorUI() {
        EditorUI result = super.createEditorUI();
        result.setLineNumberEnabled(true);
        
        return result;
    }


    @Override
    protected Action[] createActions() {
        Action[] result = plainTextEditorKit.createActions();
        if (result != null) {
            // XXX why are we doing this?
            for (int i = 0; i < result.length; i++) {
                if (result[i] instanceof PasteAction) {
                    result[i] = new AsyncPasteAction(false);
                }
            }
        } else {
            result = new Action[0];
        }
        return result;
    }

    @Override
    protected void initDocument(BaseDocument doc) {
        super.initDocument(doc);
        doc.putProperty("mimeType", mimeType);
    }

    @Override
    public MultiKeymap getKeymap() {
        return plainTextEditorKit.getKeymap();
    }

    @Override
    public Document createDefaultDocument() {
        return new Doc(mimeType);
    }


    public static Document currentDocument() {
        return RENDERING_DOCUMENT.get();
    }

    public static FileObject currentFileObject() {
        Document doc = currentDocument();
        return doc != null ? NbEditorUtilities.getFileObject(doc) : null;
    }

    public static AdhocDataObject currentDataObject() {
        Document doc = currentDocument();
        DataObject dob = doc == null ? NbEditorUtilities.getDataObject(doc) : null;
        return dob == null ? null : dob.getLookup().lookup(AdhocDataObject.class);
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

    static void renderWhenPossible(Document doc, Runnable run) {
        if (doc instanceof Doc) {
            ((Doc) doc).renderWhenPossible(run);
        } else {
            doc.render(run);
        }
    }

    // We have to subclass this in order to supply a toolbar, or the infrastructure
    // throws an exception, since there is no toolbar provider registered
    static final class Doc extends EnhEditorDocument {

        private JToolBar bar;
        private final String mimeType;

        @SuppressWarnings("LeakingThisInConstructor")
        Doc(String mimeType) {
            super(mimeType);
            this.mimeType = mimeType;
            putProperty("mimeType", mimeType);
            putProperty(InputAttributes.class, new LangPropertyEvaluator(this));
        }

        @Override
        public String toString() {
            return "AdhocEditorDoc(" + System.identityHashCode(this)
                    + " for " + getProperty(StreamDescriptionProperty) + ")";
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
                int created = AdhocLanguageHierarchy.hierarchiesCreated();
                Language<?> lang = Language.find(doc.mimeType);
                attrs.setValue(lang, Document.class, doc, false);
                if (created > createdAtLastCall) {
                    if (lang != null) {
                        attrs.setValue(lang, "doc", doc, false);
                    }
                    FileObject fo = NbEditorUtilities.getFileObject(doc);
                    if (fo != null) {
                        attrs.setValue(lang, FileObject.class, fo, false);
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
                    FileObject fo = NbEditorUtilities.getFileObject(doc);
                    Language<?> lang = Language.find(doc.mimeType);
                    if (lang != null) {
                        attrs.setValue(lang, "doc", doc, false);
                    }
                    if (fo != null) {
                        attrs.setValue(lang, FileObject.class, fo, false);
                    }
                    return attrs;
                }
                Object result = super.get(key);
                return result;
            }
        }

        @Override
        public void render(Runnable r) {
            // This is just evil, but may help diagnose things
            Runnable removeThisThreadFromDeadlockBreaker = DocumentDeadlockBreaker.enqueue();
            try {
                super.render(wrap(r));
            } finally {
                removeThisThreadFromDeadlockBreaker.run();
            }
        }

        private Runnable wrap(Runnable r) {
            return new Wrap(this, r);
        }

        static class Wrap implements Runnable {

            private final Doc doc;
            private final Runnable r;

            public Wrap(Doc doc, Runnable r) {
                this.doc = doc;
                this.r = r;
            }

            @Override
            public void run() {
                Doc old = RENDERING_DOCUMENT.get();
                try {
                    RENDERING_DOCUMENT.set(doc);
                    r.run();
                } finally {
                    RENDERING_DOCUMENT.set(old);
                }
            }

        }
    }

    /**
     * We just want to load all of the keyboard and popup actions any plain text
     * file would have, for any adhoc file type - cut, paste, etc.
     */
    static final class FakePlainKit extends NbEditorKit {

        public static final String PLAIN_MIME_TYPE = "text/plain"; // NOI18N

        public String getContentType() {
            return PLAIN_MIME_TYPE;
        }

        @Override
        public Action[] getDeclaredActions() {
            return super.getDeclaredActions();
        }

        @Override
        public Action[] getCustomActions() {
            return super.getCustomActions();
        }

        @Override
        public Action[] createActions() {
            return super.createActions();
        }
    }
}
