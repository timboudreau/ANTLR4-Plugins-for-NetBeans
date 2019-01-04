package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.Extraction;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

/**
 *
 * @author Tim Boudreau
 */
final class DocumentGrammarSource extends GrammarSource<Document> implements Externalizable {

    private Document doc;
    private RelativeFileObjectResolver resolver;

    public DocumentGrammarSource(Document doc, RelativeFileObjectResolver resolver) {
        this.doc = doc;
        this.resolver = resolver;
    }

    @Override
    public String name() {
        FileObject fo = toFileObject();
        return fo == null ? "<unnamed>" : fo.getName();
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
    public CharStream stream() throws IOException {
        return CharStreams.fromString(getDocumentText());
    }

    @Override
    public GrammarSource<?> resolveImport(String name, Extraction extraction) {
        FileObject fo = toFileObject();
        if (fo == null) {
            return null;
        }
        return new FileObjectGrammarSource(fo, resolver).resolveImport(name, extraction);
    }

    @Override
    public Document source() {
        return doc;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.doc);
        hash = 41 * hash + Objects.hashCode(this.resolver);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DocumentGrammarSource other = (DocumentGrammarSource) obj;
        if (!Objects.equals(this.doc, other.doc)) {
            return false;
        }
        if (!Objects.equals(this.resolver, other.resolver)) {
            return false;
        }
        return true;
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
        resolver = (RelativeFileObjectResolver) in.readObject();
    }

}
