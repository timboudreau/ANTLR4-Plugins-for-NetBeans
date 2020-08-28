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
package org.nemesis.antlr.nbinput;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import static javax.swing.text.Document.StreamDescriptionProperty;
import org.antlr.v4.runtime.CharStream;
import org.nemesis.extraction.nb.api.AbstractFileObjectGrammarSourceImplementation;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.api.RelativeResolver;
import org.nemesis.source.spi.GrammarSourceImplementation;
import org.netbeans.editor.BaseDocument;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

/**
 *
 * @author Tim Boudreau
 */
final class DocumentGrammarSource extends AbstractFileObjectGrammarSourceImplementation<Document> implements Externalizable {

    private Document doc;
    private RelativeResolver<Document> resolver;

    DocumentGrammarSource(Document doc, RelativeResolver<Document> resolver) {
        super(Document.class);
        this.doc = doc;
        this.resolver = resolver;
    }

    @Override
    public long lastModified() {
        return DocumentUtilities.getDocumentTimestamp(doc);
    }

    @Override
    public String name() {
        FileObject fo = toFileObject();
        return fo == null ? "<unnamed>" : fo.getNameExt();
    }

    @Override
    public FileObject toFileObject() {
        return NbEditorUtilities.getFileObject(doc);
    }

    private String getDocumentText() throws IOException {
        if (doc instanceof BaseDocument) {
            BaseDocument bd = (BaseDocument) doc;
            bd.readLock();
            try {
                return doc.getText(0, doc.getLength());
            } catch (BadLocationException ex) {
                throw new IOException(ex);
            } finally {
                bd.readUnlock();
            }
        } else {
            String[] txt = new String[1];
            BadLocationException[] ble = new BadLocationException[1];
            doc.render(() -> {
                try {
                    txt[0] = doc.getText(0, doc.getLength());
                } catch (BadLocationException ex) {
                    ble[0] = ex;
                }
            });
            if (ble[0] != null) {
                throw new IOException(ble[0]);
            }
            return txt[0];
        }
    }

    @Override
    protected <R> R lookupImpl(Class<R> type) {
        if (Snapshot.class == type) {
            return type.cast(Source.create(doc).createSnapshot());
        }
        // The next few stanzas would also be taken care of
        // by the super call below; this is simply a faster
        // path
        Object sd = doc.getProperty(StreamDescriptionProperty);
        if (DataObject.class == type) {
            if (sd instanceof DataObject) {
                return type.cast(sd);
            }
        } else if (FileObject.class == type) {
            if (sd instanceof FileObject) {
                return type.cast(sd);
            } else if (sd instanceof DataObject) {
                DataObject dob = (DataObject) sd;
                return type.cast(dob.getPrimaryFile());
            }
        } else if (Path.class == type) {
            if (sd instanceof FileObject) {
                FileObject fo = (FileObject) sd;
                File file = FileUtil.toFile(fo);
                if (file != null) {
                    return type.cast(file.toPath());
                }
            } else if (sd instanceof DataObject) {
                DataObject dob = (DataObject) sd;
                FileObject fo = dob.getPrimaryFile();
                File file = FileUtil.toFile(fo);
                if (file != null) {
                    return type.cast(file.toPath());
                }
            }
        } else if (type.isInstance(sd)) {
            return type.cast(sd);
        }
        Object o = doc.getProperty(type);
        if (o != null && type.isInstance(o)) {
            return type.cast(o);
        }
        return super.lookupImpl(type);
    }

    @Override
    public CharStream stream() throws IOException {
        CharSequence seq = DocumentUtilities.getText(doc);
        return new CharSequenceCharStream(name(), seq);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public GrammarSourceImplementation<?> resolveImport(String name) {
        if (resolver == null) {
            return null;
        }
        Optional<Document> result = resolver.resolve(doc, name);
        if (result.isPresent()) {
            return new DocumentGrammarSource(result.get(), resolver);
        }
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        if (fo != null) {
            GrammarSource<?> gs = GrammarSource.find(fo, fo.getMIMEType());
            if (gs != null) {
                Optional<GrammarSourceImplementation> impl = gs.lookup(GrammarSourceImplementation.class);
                if (impl.isPresent()) {
                    return impl.get();
                }
            }
        }
        return null;
    }

    @Override
    public Document source() {
        return doc;
    }

    @Override
    protected Document document() {
        return doc;
    }

    public String toString() {
        FileObject fo = toFileObject();
        return fo != null ? "doc:" + fo.toURI() : "doc:" + doc.toString();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(1);
        FileObject fo = toFileObject();
        File file = fo == null ? null : FileUtil.toFile(fo);
        if (file != null) {
            out.writeInt(0);
            out.writeUTF(fo.getMIMEType());
            out.writeUTF(file.getAbsolutePath());
            out.writeObject(resolver);
        } else {
            out.writeInt(1);
            out.writeUTF(NbEditorUtilities.getMimeType(doc));
            out.writeUTF(getDocumentText());
            out.writeObject(resolver);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int v = in.readInt();
        if (v != 1) {
            throw new IOException("Unsupported version " + v);
        }
        int pathOrText = in.readInt();
        String mime;
        switch (pathOrText) {
            case 0:
                mime = in.readUTF();
                File file = new File(in.readUTF());
                if (file.exists()) {
                    FileObject fo = FileUtil.toFileObject(file);
                    DataObject dob = DataObject.find(fo);
                    EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                    if (ck != null) {
                        doc = ck.openDocument();
                    } else {
                        doc = new BaseDocument(false, mime);
                        try {
                            doc.insertString(0, fo.asText(), null);
                        } catch (BadLocationException ex) {
                            throw new IOException(ex);
                        }
                        doc.putProperty(Document.StreamDescriptionProperty, dob);
                    }
                }
                break;
            case 1:
                mime = in.readUTF();
                doc = new BaseDocument(false, mime);
                 {
                    try {
                        doc.insertString(0, in.readUTF(), null);
                    } catch (BadLocationException ex) {
                        throw new IOException(ex);
                    }
                }
                break;
            default:
                throw new IOException("Unknown value for pathOrText: " + pathOrText);
        }
        resolver = (RelativeResolver<Document>) in.readObject();
    }
}
