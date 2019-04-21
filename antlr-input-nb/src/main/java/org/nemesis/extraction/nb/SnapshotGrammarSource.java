package org.nemesis.extraction.nb;

import java.io.IOException;
import java.util.Optional;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.misc.Interval;
import org.nemesis.extraction.nb.api.AbstractFileObjectGrammarSourceImplementation;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.api.RelativeResolver;
import org.nemesis.source.spi.GrammarSourceImplementation;
import org.nemesis.source.spi.GrammarSourceImplementationFactory;
import org.nemesis.source.spi.RelativeResolverImplementation;
import org.nemesis.source.spi.RelativeResolverRegistry;
import org.netbeans.editor.BaseDocument;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/**
 * GrammarSourceImplementation which uses the text a snapshot returns as the
 * file content.
 *
 * @author Tim Boudreau
 */
public class SnapshotGrammarSource extends AbstractFileObjectGrammarSourceImplementation<Snapshot> {

    private final Snapshot snapshot;
    private final long lastModified;

    public SnapshotGrammarSource(Snapshot snapshot, RelativeResolver<Snapshot> resolver) {
        super(Snapshot.class);
        this.snapshot = snapshot;
        // Since we may be getting it from the file object, we want to set
        // the value as soon as we can - file object could be modified while
        // the snapshot's getText() won't be
        lastModified = _lastModified();
    }

    @Override
    public String name() {
        String result = null;
        FileObject fo = snapshot.getSource().getFileObject();
        if (fo == null) {
            Document doc = snapshot.getSource().getDocument(false);
            if (doc != null) {
                fo = NbEditorUtilities.getFileObject(doc);
            }
        }
        if (fo != null) {
            result = fo.getNameExt();
        }
        if (result == null) {
            result = snapshot.toString();
        }
        return result;
    }

    @Override
    protected <R> R lookupImpl(Class<R> type) {
        if (FileObject.class == type) {
            FileObject fo = toFileObject();
            if (fo != null) {
                return type.cast(fo);
            }
        } else if (Document.class == type || StyledDocument.class == type || BaseDocument.class == type) {
            Document d = snapshot.getSource().getDocument(false);
            if (d != null && type.isInstance(d)) {
                return type.cast(d);
            }
        } else if (Source.class == type) {
            return type.cast(snapshot.getSource());
        }
        R result = super.lookupImpl(type);
        return result == null ? snapshot.getSource().getLookup().lookup(type) : result;
    }

    @Override
    public FileObject toFileObject() {
        FileObject fo = snapshot.getSource().getFileObject();
        if (fo == null) {
            Document d = snapshot.getSource().getDocument(false);
            if (d != null) {
                fo = NbEditorUtilities.getFileObject(d);
            }
        }
        return fo;
    }

    @Override
    public CharStream stream() throws IOException {
//        return new CharSequenceCharStream(snapshot.getText(), name());
//        System.out.println("IS A STRING? " + snapshot.getText().getClass().getName());
        return CharStreams.fromString(snapshot.getText().toString(), name());
    }

    @Override
    public GrammarSourceImplementation<?> resolveImport(String name) {
        FileObject fo = snapshot.getSource().getFileObject();
        if (fo != null) {
            RelativeResolverImplementation<FileObject> resolver
                    = RelativeResolverRegistry.getDefault()
                            .forDocumentAndMimeType(fo, snapshot.getMimeType());
            if (resolver != null) {
                Optional<FileObject> sibling = resolver.resolve(fo, name);
                if (sibling.isPresent()) {
                    FileObject sib = sibling.get();
                    GrammarSource<?> gs = GrammarSource.find(sib, sib.getMIMEType());
                    if (gs != null) {
                        Optional<GrammarSourceImplementation> impl = gs.lookup(GrammarSourceImplementation.class);
                        return impl.isPresent() ? impl.get() : null;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Snapshot source() {
        return snapshot;
    }

    private final long _lastModified() {
        Document d = snapshot.getSource().getDocument(false);
        if (d != null) {
            long result = DocumentUtilities.getDocumentTimestamp(d);
            if (result > 0) {
                return result;
            }
        }
        FileObject fo = toFileObject();
        return fo == null ? 0 : fo.lastModified().getTime();
    }

    @Override
    public long lastModified() throws IOException {
        return lastModified;
    }

    @ServiceProvider(service = GrammarSourceImplementationFactory.class)
    public static final class Factory extends GrammarSourceImplementationFactory<Snapshot> {

        public Factory() {
            super(Snapshot.class);
        }

        @Override
        public GrammarSourceImplementation<Snapshot> create(Snapshot doc, RelativeResolver<Snapshot> resolver) {
            return new SnapshotGrammarSource(doc, resolver);
        }
    }

    static class CharSequenceCharStream implements CharStream {

        private final CharSequence seq;

        public CharSequenceCharStream(CharSequence seq, String name) {
            this.seq = seq;
            this.name = name;
        }

        private final String name;
        private int index = 0;

        @Override
        public void consume() {
            index++;
        }

        @Override
        public int LA(int i) {
            if (i == 0) {
                return 0; // undefined
            }
            i--;
            return i < seq.length() ? seq.charAt(i) : CharStream.EOF;
        }

        @Override
        public int mark() {
            return 0;
        }

        @Override
        public void release(int marker) {
            // do nothing
        }

        @Override
        public int index() {
            return index + 1;
        }

        @Override
        public void seek(int i) {
            this.index = i;
        }

        @Override
        public int size() {
            return seq.length() + 1;
        }

        @Override
        public String getSourceName() {
            return name == null ? UNKNOWN_SOURCE_NAME : name;
        }

        @Override
        public String getText(Interval intrvl) {
            CharSequence cs = seq.subSequence(intrvl.a, intrvl.b + 1);
            return cs.toString();
        }
    }
}