package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import static javax.swing.text.Document.StreamDescriptionProperty;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.ExtractionKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.GrammarSource;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameExtractors;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameExtractors.NameInfoStore;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameReferenceSetKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NamedExtractionKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NamedRegionKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.QualifierPredicate;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.ResolvedForeignNameReference;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.UnknownNameReference;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.UnknownNameReference.UnknownNameReferenceResolver;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedRegionReferenceSets;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.SerializationContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticRegions.SemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticRegions.SemanticRegionsBuilder;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class GenericExtractorBuilder<T extends ParserRuleContext> {

    private final Class<T> documentRootType;

    private static final AtomicInteger keyIds = new AtomicInteger();

    private final Set<RegionExtractionInfo<?>> regionsInfo2 = new HashSet<>();
    private final Set<NameExtractors<?>> nameExtractors = new HashSet<>();
    private final Map<SingleObjectKey<?>, SingleObjectExtractionInfos<?>> singles = new HashMap<>();

    public GenericExtractorBuilder(Class<T> entryPoint) {
        this.documentRootType = entryPoint;
    }

    public static <T extends ParserRuleContext> GenericExtractorBuilder<T> forDocumentRoot(Class<T> documentRoot) {
        return new GenericExtractorBuilder(documentRoot);
    }

    public GenericExtractor<T> build() {
        return new GenericExtractor<>(documentRootType, nameExtractors, regionsInfo2, singles);
    }

    public <K extends Enum<K>> NamedRegionExtractorBuilder<K, GenericExtractorBuilder<T>> extractNamedRegions(Class<K> type) {
        return new NamedRegionExtractorBuilder<>(type, ne -> {
            nameExtractors.add(ne);
            return this;
        });
    }

    void addRegionEx(RegionExtractionInfo<?> info) {
        regionsInfo2.add(info);
    }

    public static final class GenericExtractor<T extends ParserRuleContext> {

        private final Class<T> documentRootType;
        private final Set<NameExtractors<?>> nameExtractors;
        private final Set<RegionExtractionInfo<?>> regionsInfo;
        private String extractorsHash;
        private final Map<SingleObjectKey<?>, SingleObjectExtractionInfos<?>> singles;

        GenericExtractor(Class<T> documentRootType, Set<NameExtractors<?>> nameExtractors, Set<RegionExtractionInfo<?>> regionsInfo2, Map<SingleObjectKey<?>, SingleObjectExtractionInfos<?>> singles) {
            this.documentRootType = documentRootType;
            this.nameExtractors = nameExtractors;
            this.regionsInfo = regionsInfo2;
            this.singles = singles;
        }

        public Class<T> documentRootType() {
            return documentRootType;
        }

        public String extractorsHash() {
            if (extractorsHash == null) {
                Hashable.Hasher hasher = new Hashable.Hasher();
                for (NameExtractors<?> n : nameExtractors) {
                    hasher.hashObject(n);
                }
                for (RegionExtractionInfo<?> e : regionsInfo) {
                    hasher.hashObject(e);
                }
                extractorsHash = hasher.hash();
            }
            return extractorsHash;
        }

        public Extraction extract(T ruleNode, GrammarSource<?> source) {
            Extraction extraction = new Extraction(extractorsHash(), source);
            for (RegionExtractionInfo<?> r : regionsInfo) {
                runRegions2(r, ruleNode, extraction);
            }
            for (NameExtractors<?> n : nameExtractors) {
                runNames(ruleNode, n, extraction);
            }
            for (Map.Entry<SingleObjectKey<?>, SingleObjectExtractionInfos<?>> e : singles.entrySet()) {
                runSingles(e.getValue(), ruleNode, extraction);
            }
            return extraction;
        }

        private <K> void runSingles(SingleObjectExtractionInfos<K> single, T ruleNode, Extraction extraction) {
            Encounters<K> encounters = single.extract(ruleNode);
            extraction.addSingle(single.key, encounters);
        }

        private <L extends Enum<L>> void runNames(T ruleNode, NameExtractors<L> x, Extraction into) {
            x.invoke(ruleNode, into.store);
        }

        private <K> void runRegions2(RegionExtractionInfo<K> info, T ruleNode, Extraction extraction) {
            SemanticRegionsBuilder<K> bldr = SemanticRegions.builder(info.key.type());
            ParseTreeVisitor v = info.createVisitor((k, bounds) -> {
                bldr.add(k, bounds[0], bounds[1]);
            });
            ruleNode.accept(v);
            extraction.add(info.key, bldr.build());
        }
    }

    public interface ExtractionKey<T> {

        Class<T> type();

        String name();
    }

    public interface GrammarSource<T> extends Serializable {

        String name();

        CharStream stream() throws IOException;

        GrammarSource<?> resolveImport(String name, Extraction extraction);

        T source();

        static GrammarSource<FileObject> forFileObject(FileObject fo, RelativeFileObjectResolver resolver) {
            return new FileObjectGrammarSource(fo, resolver);
        }

        static GrammarSource<CharStream> forSingleCharStream(String name, CharStream stream) {
            return new StringGrammarSource(name, stream);
        }

        static GrammarSource<CharStream> forText(String name, String grammarBody) {
            return forSingleCharStream(name, CharStreams.fromString(grammarBody));
        }

        static GrammarSource<?> forDocument(Document doc, RelativeFileObjectResolver resolver) {
            return new DocumentGrammarSource(doc, resolver);
        }

        default FileObject toFileObject() {
            return null;
        }
    }

    @FunctionalInterface
    public interface RelativeFileObjectResolver extends Serializable {

        Optional<FileObject> resolve(FileObject relativeTo, String name, Extraction in);
    }

    private static final class DocumentGrammarSource implements GrammarSource<Document>, Externalizable {

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
                            doc.putProperty(StreamDescriptionProperty, dob);
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

    private static final class FileObjectGrammarSource implements GrammarSource<FileObject> {

        private final FileObject file;
        private final RelativeFileObjectResolver resolver;

        public FileObjectGrammarSource(FileObject fob, RelativeFileObjectResolver resolver) {
            this.file = fob;
            this.resolver = resolver;
        }

        @Override
        public String name() {
            return file.getName();
        }

        public String toString() {
            return file.toURI().toString();
        }

        @Override
        public FileObject toFileObject() {
            return file;
        }

        @Override
        public CharStream stream() throws IOException {
            DataObject dob = DataObject.find(file);
            EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
            if (ck != null) {
                Document doc = ck.getDocument();
                if (doc != null) {
                    return new DocumentGrammarSource(doc, resolver).stream();
                }
            }
            return CharStreams.fromString(file.asText());
        }

        @Override
        public GrammarSource<?> resolveImport(String name, Extraction extraction) {
            Optional<FileObject> result = resolver.resolve(file, name, extraction);
            if (result.isPresent()) {
                return new FileObjectGrammarSource(result.get(), resolver);
            }
            return null;
        }

        @Override
        public FileObject source() {
            return file;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 79 * hash + Objects.hashCode(this.file);
            hash = 79 * hash + Objects.hashCode(this.resolver);
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
            final FileObjectGrammarSource other = (FileObjectGrammarSource) obj;
            if (!Objects.equals(this.file, other.file)) {
                return false;
            }
            if (!Objects.equals(this.resolver, other.resolver)) {
                return false;
            }
            return true;
        }
    }

    private static final class StringGrammarSource implements GrammarSource<CharStream> {

        private final String name;
        // This class is for tests, don't fail the serializatoin tests because of this:
        private transient final CharStream stream;

        public StringGrammarSource(String name, CharStream stream) {
            this.name = name;
            this.stream = stream;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CharStream stream() {
            return stream;
        }

        @Override
        public GrammarSource<?> resolveImport(String name, Extraction ex) {
            return null;
        }

        @Override
        public CharStream source() {
            return stream();
        }
    }

    public interface ExtractionParserResult {

        public Extraction extraction();
    }

    /**
     * A Collection of data extracted during a parse of some document.
     */
    public static class Extraction implements Externalizable {

        private final Map<RegionsKey<?>, SemanticRegions<?>> regions = new HashMap<>();
        private final Map<NamedRegionKey<?>, NamedSemanticRegions<?>> nameds = new HashMap<>();
        private final Map<NamedRegionExtractorBuilder.NamedRegionKey<?>, Map<String, Set<NamedSemanticRegion<?>>>> duplicates = new HashMap<>();

        private final Map<NamedRegionExtractorBuilder.NameReferenceSetKey<?>, NamedSemanticRegions.NamedRegionReferenceSets<?>> refs = new HashMap<>();
        private final Map<NamedRegionExtractorBuilder.NameReferenceSetKey<?>, BitSetStringGraph> graphs = new HashMap<>();
        private final Map<NamedRegionExtractorBuilder.NameReferenceSetKey<?>, SemanticRegions<NamedRegionExtractorBuilder.UnknownNameReference<?>>> unknowns = new HashMap<>();
        private transient volatile Map<NameReferenceSetKey<?>, Map<UnknownNameReferenceResolver<?, ?, ?, ?>, ResolutionInfo<?, ?, ?, ?>>> resolutionCache;
        private transient volatile Map<Set<ExtractionKey<?>>, Set<String>> keysCache;
        private String extractorsHash;
        private GrammarSource<?> source;

        private Extraction(String extractorsHash, GrammarSource<?> source) {
            this.extractorsHash = extractorsHash;
            this.source = source;
        }

        public Extraction() { // for serialization, sigh
        }

        public GrammarSource<?> source() {
            return source;
        }

        public GrammarSource<?> resolveRelative(String name) {
            return source.resolveImport(name, this);
        }

        public <T extends Enum<T>> Map<String, Set<NamedSemanticRegion<T>>> duplicates(NamedRegionKey<T> key) {
            Map result = duplicates.get(key);
            if (result == null) {
                result = Collections.emptyMap();
            }
            return result;
        }

        public <T extends Enum<T>> NamedSemanticRegion<T> regionOrReferenceAt(int pos, NamedExtractionKey<T> a) {
            return regionOrReferenceAt(pos, Collections.singleton(a));
        }

        public <T extends Enum<T>> NamedSemanticRegion<T> regionOrReferenceAt(int pos, NamedExtractionKey<T> a, NamedExtractionKey<T> b) {
            return regionOrReferenceAt(pos, Arrays.asList(a, b));
        }

        public <T extends Enum<T>> NamedSemanticRegion<T> regionOrReferenceAt(int pos, NamedExtractionKey<T> a, NamedExtractionKey<T> b, NamedExtractionKey<T> c) {
            return regionOrReferenceAt(pos, Arrays.asList(a, b, c));
        }

        public <T extends Enum<T>> NamedSemanticRegion<T> regionOrReferenceAt(int pos, Iterable<? extends NamedExtractionKey<T>> keys) {
            NamedSemanticRegion<T> result = null;
            for (NamedExtractionKey<T> key : keys) {
                NamedSemanticRegion<T> candidate = null;
                if (key instanceof NamedRegionKey<?>) {
                    NamedRegionKey<T> k = (NamedRegionKey<T>) key;
                    NamedSemanticRegions<T> ns = (NamedSemanticRegions<T>) nameds.get(k);
                    if (ns != null) {
                        candidate = ns.at(pos);
                    }
                } else if (key instanceof NameReferenceSetKey<?>) {
                    NameReferenceSetKey<T> k = (NameReferenceSetKey<T>) key;
                    NamedRegionReferenceSets<T> r = (NamedRegionReferenceSets<T>) refs.get(k);
                    if (r != null) {
                        candidate = r.at(pos);
                    }
                }
                if (candidate != null) {
                    if (result == null || candidate.length() < result.length()) {
                        result = candidate;
                    }
                }
            }
            return result;
        }

        private transient Map<GenericExtractor<?>, Map<GrammarSource<?>, Extraction>> cachedExtractions;

        public synchronized Extraction resolveExtraction(GenericExtractor<?> extractor, String importName, Function<GrammarSource<?>, Extraction> runExtraction) {
            GrammarSource<?> src = resolveRelative(importName);
            if (src == null) {
                return new Extraction();
            }
            return resolveExtraction(extractor, src, runExtraction);
        }

        public synchronized Extraction resolveExtraction(GenericExtractor<?> extractor, GrammarSource<?> src, Function<GrammarSource<?>, Extraction> runExtraction) {
            if (src.equals(source)) {
                return this;
            }
            if (cachedExtractions != null) {
                Map<GrammarSource<?>, Extraction> forSource = cachedExtractions.get(extractor);
                if (forSource != null) {
                    Extraction cached = forSource.get(src);
                    if (cached != null) {
                        return cached;
                    }
                }
            }
            Extraction nue = runExtraction.apply(src);
            if (cachedExtractions == null) {
                cachedExtractions = new HashMap<>();
            }
            Map<GrammarSource<?>, Extraction> forSource = cachedExtractions.get(extractor);
            if (forSource == null) {
                forSource = new HashMap<>();
                cachedExtractions.put(extractor, forSource);
            }
            forSource.put(src, nue);
            nue.cachedExtractions = cachedExtractions;
            return nue;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("extractor:").append(extractorsHash);
            if (!regions.isEmpty()) {
                sb.append("\n******************* UNNAMED REGIONS **********************");
                for (Map.Entry<RegionsKey<?>, SemanticRegions<?>> e : regions.entrySet()) {
                    sb.append("\n ------------- REGIONS ").append(e.getKey()).append(" -----------------");
                    for (SemanticRegion<?> reg : e.getValue()) {
                        sb.append("\n  ").append(reg);
                    }
                }
            }
            if (!nameds.isEmpty()) {
                sb.append("\n******************* NAMED REGIONS **********************");
                for (Map.Entry<NamedRegionKey<?>, NamedSemanticRegions<?>> e : nameds.entrySet()) {
                    sb.append("\n ------------- NAMED SET ").append(e.getKey()).append(" -----------------");
                    for (NamedSemanticRegions.NamedSemanticRegion<?> nr : e.getValue().index()) {
                        sb.append("\n  ").append(nr);
                    }
                }
            }
            if (!refs.isEmpty()) {
                sb.append("\n******************* REFERENCES TO NAMED REGIONS **********************");
                for (Map.Entry<NameReferenceSetKey<?>, NamedRegionReferenceSets<?>> e : refs.entrySet()) {
                    sb.append("\n ------------- REFERENCE SETS ").append(e.getKey()).append(" -----------------");
                    for (NamedSemanticRegions.NamedSemanticRegionReference<?> nrrs : e.getValue().asIterable()) {
                        sb.append("\n  ").append(nrrs);
                    }
                }
            }
            if (!graphs.isEmpty()) {
                sb.append("\n******************* REFERENCE GRAPHS **********************");
                for (Map.Entry<NameReferenceSetKey<?>, BitSetStringGraph> e : graphs.entrySet()) {
                    sb.append("\n ------------- GRAPH ").append(e.getKey()).append(" -----------------");
                    sb.append("\n  ").append(e.getValue());
                }
            }
            if (!unknowns.isEmpty()) {
                sb.append("\n******************* UNKNOWN REFERENCES **********************");
                for (Map.Entry<NameReferenceSetKey<?>, SemanticRegions<UnknownNameReference<?>>> e : unknowns.entrySet()) {
                    sb.append("\n ------------- UNKNOWNS ").append(e.getKey()).append(" -----------------");
                    for (SemanticRegion<UnknownNameReference<?>> v : e.getValue()) {
                        sb.append("\n  ").append(v);
                    }
                }
            }
            return sb.toString();
        }

        public Set<String> allKeys(ExtractionKey<?> first, ExtractionKey<?>... more) {
            Set<ExtractionKey<?>> keys = new HashSet<>();
            keys.add(first);
            for (ExtractionKey<?> k : more) {
                keys.add(k);
            }
            Set<String> result = keysCache == null ? null : keysCache.get(keys);
            if (result == null) {
                result = new HashSet<>();
                for (ExtractionKey<?> k : keys) {
                    if (k instanceof NamedRegionKey<?>) {
                        NamedRegionKey<?> kk = (NamedRegionKey<?>) k;
                        NamedSemanticRegions<?> r = nameds.get(kk);
                        if (r != null) {
                            result.addAll(Arrays.asList(r.nameArray()));
                        }
                    } else if (k instanceof NamedRegionExtractorBuilder.NameReferenceSetKey<?>) {
                        NamedRegionExtractorBuilder.NameReferenceSetKey<?> nrk = (NamedRegionExtractorBuilder.NameReferenceSetKey<?>) k;
                        NamedRegionReferenceSets<?> r = refs.get(nrk);
                        if (r != null) {
                            for (NamedRegionReferenceSets.NamedRegionReferenceSet<?> i : r) {
                                result.add(i.name());
                            }
                        }
                    }
                }
                if (keysCache == null) {
                    keysCache = new HashMap<>();
                }
                keysCache.put(keys, result);
            }
            return result;
        }

        /**
         * This string identifies the <i>code</i> which generated an extraction.
         * In the case that these are serialized and cached on disk, this can be
         * used (for example, as a parent directory name) to ensure that a newer
         * version of a module does not load an extraction generated by a
         * previous one unless the code matches. For lambdas, hash generation
         * relies on the consistency of toString() and hashCode() for lambdas
         * across runs - which works fine on JDK 8/9. At worst, you get a false
         * negative and parse.
         *
         * @return A hash value which should be unique to the contents of the
         * GenericExtractor which created this extraction.
         */
        public String creationHash() {
            return extractorsHash;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void writeExternal(ObjectOutput out) throws IOException {
            Set<NamedSemanticRegions<?>> allNamed = new HashSet<>(nameds.values());
            NamedSemanticRegions.SerializationContext ctx = NamedSemanticRegions.createSerializationContext(allNamed);
            try {
                NamedSemanticRegions.withSerializationContext(ctx, () -> {
                    out.writeInt(1);
                    out.writeObject(ctx);
                    out.writeUTF(extractorsHash);
                    out.writeObject(regions);
                    out.writeObject(nameds);
                    out.writeObject(refs);
                    out.writeObject(unknowns);
                    out.writeInt(graphs.size());
                    for (Map.Entry<NameReferenceSetKey<?>, BitSetStringGraph> e : graphs.entrySet()) {
                        out.writeObject(e.getKey());
                        e.getValue().save(out);
                    }
                });
            } catch (ClassNotFoundException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            int v = in.readInt();
            if (v != 1) {
                throw new IOException("Incompatible version " + v);
            }
            SerializationContext ctx = (SerializationContext) in.readObject();
            NamedSemanticRegions.withSerializationContext(ctx, () -> {
                extractorsHash = in.readUTF();
                Map<RegionsKey<?>, SemanticRegions<?>> sregions = (Map<RegionsKey<?>, SemanticRegions<?>>) in.readObject();
                Map<NamedRegionExtractorBuilder.NamedRegionKey<?>, NamedSemanticRegions<?>> snameds = (Map<NamedRegionExtractorBuilder.NamedRegionKey<?>, NamedSemanticRegions<?>>) in.readObject();
                Map<NamedRegionExtractorBuilder.NameReferenceSetKey<?>, NamedSemanticRegions.NamedRegionReferenceSets<?>> srefs = (Map<NamedRegionExtractorBuilder.NameReferenceSetKey<?>, NamedSemanticRegions.NamedRegionReferenceSets<?>>) in.readObject();
                Map<NamedRegionExtractorBuilder.NameReferenceSetKey<?>, SemanticRegions<NamedRegionExtractorBuilder.UnknownNameReference<?>>> sunknowns = (Map<NamedRegionExtractorBuilder.NameReferenceSetKey<?>, SemanticRegions<NamedRegionExtractorBuilder.UnknownNameReference<?>>>) in.readObject();
                int graphCount = in.readInt();
                for (int i = 0; i < graphCount; i++) {
                    NameReferenceSetKey<?> k = (NameReferenceSetKey<?>) in.readObject();
                    BitSetStringGraph graph = BitSetStringGraph.load(in);
                    graphs.put(k, graph);
                }
                this.regions.putAll(sregions);
                this.nameds.putAll(snameds);
                this.refs.putAll(srefs);
                this.unknowns.putAll(sunknowns);
            });
        }

        <T> void add(RegionsKey<T> key, SemanticRegions<T> oneRegion) {
            regions.put(key, oneRegion);
        }

        public <T extends Enum<T>> NamedRegionReferenceSets<T> references(NameReferenceSetKey<T> key) {
            NamedRegionReferenceSets<?> result = refs.get(key);
            return (NamedRegionReferenceSets<T>) result;
        }

        public BitSetStringGraph referenceGraph(NameReferenceSetKey<?> key) {
            return graphs.get(key);
        }

        public <T extends Enum<T>> NamedSemanticRegions<T> namedRegions(NamedRegionKey<T> key) {
            NamedSemanticRegions<?> result = nameds.get(key);
            return result == null ? NamedSemanticRegions.empty() : (NamedSemanticRegions<T>) result;
        }

        public <T extends Enum<T>> NamedSemanticRegions.NamedRegionReferenceSets<T> nameReferences(NameReferenceSetKey<T> key) {
            NamedRegionReferenceSets<?> result = refs.get(key);
            if (result == null) {
                System.out.println("NO REFERENCES IN " + refs.keySet() + " for " + key);
            }
            return result == null ? null : (NamedRegionReferenceSets<T>) result;
        }

        @SuppressWarnings("unchecked")
        public <T> SemanticRegions<T> regions(RegionsKey<T> key) {
            SemanticRegions<?> result = regions.get(key);
            assert result == null || key.type.equals(result.keyType());
            return result == null ? SemanticRegions.empty() : (SemanticRegions<T>) result;
        }

        public <T extends Enum<T>> SemanticRegions<NamedRegionExtractorBuilder.UnknownNameReference<?>> unknowns(NameReferenceSetKey<T> key) {
            SemanticRegions<NamedRegionExtractorBuilder.UnknownNameReference<?>> result = unknowns.get(key);
            if (result == null) {
                return SemanticRegions.empty();
            }
            return result;
        }

        public void clearResolutionCache() {
            resolutionCache = null;
        }

        public <R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegions.NamedSemanticRegion<T>, T extends Enum<T>> ResolutionInfo<R, I, N, T> resolveUnknowns(NameReferenceSetKey<?> key, UnknownNameReferenceResolver<R, I, N, T> res) throws IOException {
            Map<NameReferenceSetKey<?>, Map<UnknownNameReferenceResolver<?, ?, ?, ?>, ResolutionInfo<?, ?, ?, ?>>> rc = this.resolutionCache;
            Map<UnknownNameReferenceResolver<?, ?, ?, ?>, ResolutionInfo<?, ?, ?, ?>> perResolverCache = null;
            if (rc != null) {
                perResolverCache = rc.get(key);
                if (perResolverCache != null) {
                    ResolutionInfo<R, I, N, T> result = (ResolutionInfo<R, I, N, T>) perResolverCache.get(res);
                    if (result != null) {
                        return result;
                    }
                }
            } else {
                rc = new HashMap<>();
                perResolverCache = new WeakHashMap<>();
                rc.put(key, perResolverCache);
            }
            SemanticRegions<NamedRegionExtractorBuilder.UnknownNameReference<?>> u = unknowns(key);
            if (u == null) {
                return null;
            }
            Class c = ResolvedForeignNameReference.class;
            SemanticRegionsBuilder<ResolvedForeignNameReference<R, I, N, T>> bldr = SemanticRegions.builder(c);
            SemanticRegionsBuilder<UnknownNameReference> remaining = SemanticRegions.builder(UnknownNameReference.class);
            for (SemanticRegions.SemanticRegion<UnknownNameReference<?>> reg : u) {
                ResolvedForeignNameReference<R, I, N, T> r = reg.key().resolve(this, res);
                if (r != null) {
                    bldr.add(r, reg.start(), reg.end());
                } else {
                    remaining.add(reg.key(), reg.start(), reg.end());
                }
            }
            ResolutionInfo<R, I, N, T> result = new ResolutionInfo<>(bldr.build(), remaining.build());
            perResolverCache.put(res, result);
            return result;
        }

        public <K> Encounters<K> encounters(SingleObjectKey<K> key) {
            Encounters<K> result = (Encounters<K>) singles.get(key);
            return result == null ? new Encounters<>() : result;
        }

        private final Map<SingleObjectKey<?>, Encounters<?>> singles = new HashMap<>(4);

        private <K> void addSingle(SingleObjectKey<K> key, Encounters<K> encounters) {
            singles.put(key, encounters);
        }

        public static final class ResolutionInfo<R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegions.NamedSemanticRegion<T>, T extends Enum<T>> {

            private final SemanticRegions<ResolvedForeignNameReference<R, I, N, T>> resolved;
            private final SemanticRegions<UnknownNameReference> unresolved;

            ResolutionInfo(SemanticRegions<ResolvedForeignNameReference<R, I, N, T>> resolved, SemanticRegions<UnknownNameReference> unresolved) {
                this.resolved = resolved;
                this.unresolved = unresolved;
            }

            public SemanticRegions<ResolvedForeignNameReference<R, I, N, T>> resolved() {
                return resolved;
            }

            public SemanticRegions<UnknownNameReference> unresolved() {
                return unresolved;
            }
        }

        NameInfoStore store = new NameInfoStore() {

            @Override
            public <T extends Enum<T>> void addNamedRegions(NamedRegionExtractorBuilder.NamedRegionKey<T> key, NamedSemanticRegions<T> regions) {
                nameds.put(key, regions);
            }

            @Override
            public <T extends Enum<T>> void addReferences(NamedRegionExtractorBuilder.NameReferenceSetKey<T> key, NamedSemanticRegions.NamedRegionReferenceSets<T> regions) {
                refs.put(key, regions);
            }

            @Override
            public <T extends Enum<T>> void addReferenceGraph(NameReferenceSetKey<T> refSetKey, BitSetStringGraph stringGraph) {
                graphs.put(refSetKey, stringGraph);
            }

            @Override
            public <T extends Enum<T>> void addUnknownReferences(NameReferenceSetKey<T> refSetKey, SemanticRegions<NamedRegionExtractorBuilder.UnknownNameReference<T>> unknownReferences) {
                SemanticRegions s = unknownReferences;
                unknowns.put(refSetKey, s);
            }

            @Override
            public <T extends Enum<T>> void addDuplicateNamedRegions(NamedRegionKey<T> key, String name, Iterable<? extends NamedSemanticRegion<T>> duplicates) {
                Set<NamedSemanticRegion<?>> all = new HashSet<>();
                for (NamedSemanticRegion<T> x : duplicates) {
                    all.add(x);
                }
                Map<String, Set<NamedSemanticRegion<?>>> m = Extraction.this.duplicates.get(key);
                if (m == null) {
                    m = new HashMap<>();
                    Extraction.this.duplicates.put(key, m);
                }
                m.put(name, all);
            }
        };
    }

    public <KeyType> SingleObjectBuilder<T, KeyType> extractingSingletonUnder(SingleObjectKey<KeyType> key) {
        return new SingleObjectBuilder<>(this, key);
    }

    public static class SingleObjectBuilder<T extends ParserRuleContext, KeyType> {

        final GenericExtractorBuilder<T> bldr;
        final SingleObjectKey<KeyType> key;
        Predicate<RuleNode> ancestorQualifier;
        final Set<SingleObjectExtractionInfo<KeyType, ?>> set;

        SingleObjectBuilder(GenericExtractorBuilder<T> bldr, SingleObjectKey<KeyType> key, Set<SingleObjectExtractionInfo<KeyType, ?>> set) {
            this.set = set;
            this.bldr = bldr;
            this.key = key;
        }

        SingleObjectBuilder(GenericExtractorBuilder<T> bldr, SingleObjectKey<KeyType> key) {
            this.bldr = bldr;
            this.key = key;
            this.set = new HashSet<>(2);
        }

        public SingleObjectBuilder<T, KeyType> whenAncestor(Class<? extends RuleNode> ancestorType) {
            if (ancestorQualifier == null) {
                ancestorQualifier = new NamedRegionExtractorBuilder.QualifierPredicate(ancestorType);
            } else {
                ancestorQualifier = ancestorQualifier.or(new NamedRegionExtractorBuilder.QualifierPredicate(ancestorType));
            }
            return this;
        }

        public SingleObjectBuilder<T, KeyType> whenAncestorMatches(Predicate<RuleNode> qualifier) {
            if (ancestorQualifier == null) {
                ancestorQualifier = qualifier;
            } else {
                ancestorQualifier = ancestorQualifier.or(qualifier);
            }
            return this;
        }

        public <R extends ParserRuleContext> SingleObjectBuilderWithRule<T, KeyType, R> whereRuleIs(Class<R> ruleType) {
            return new SingleObjectBuilderWithRule<>(bldr, key, ancestorQualifier, ruleType, set);
        }
    }

    public static final class FinishableSingleObjectBuilder<T extends ParserRuleContext, KeyType> {

        final GenericExtractorBuilder<T> bldr;
        final SingleObjectKey<KeyType> key;
        final Set<SingleObjectExtractionInfo<KeyType, ?>> set;

        FinishableSingleObjectBuilder(GenericExtractorBuilder<T> bldr, SingleObjectKey<KeyType> key, Set<SingleObjectExtractionInfo<KeyType, ?>> set) {
            this.bldr = bldr;
            this.key = key;
            this.set = set;
        }

        public GenericExtractorBuilder<T> finishObjectExtraction() {
            return bldr.addSingles(key, set);
        }

        public SingleObjectBuilder<T, KeyType> whenAncestor(Class<? extends RuleNode> ancestorType) {
            SingleObjectBuilder<T, KeyType> result = new SingleObjectBuilder<>(bldr, key, set);
            result.ancestorQualifier = new NamedRegionExtractorBuilder.QualifierPredicate(ancestorType);
            return result;
        }

        public SingleObjectBuilder<T, KeyType> whenAncestorMatches(Predicate<RuleNode> qualifier) {
            SingleObjectBuilder<T, KeyType> result = new SingleObjectBuilder<>(bldr, key, set);
            result.ancestorQualifier = qualifier;
            return result;
        }

        public <R extends ParserRuleContext> SingleObjectBuilderWithRule<T, KeyType, R> whereRuleIs(Class<R> ruleType) {
            return new SingleObjectBuilderWithRule<>(bldr, key, null, ruleType, set);
        }
    }

    public static final class SingleObjectBuilderWithRule<T extends ParserRuleContext, KeyType, R extends ParserRuleContext> {

        final GenericExtractorBuilder<T> bldr;
        final SingleObjectKey<KeyType> key;
        final Predicate<RuleNode> ancestorQualifier;
        final Class<R> ruleType;
        final Set<SingleObjectExtractionInfo<KeyType, ?>> set;

        SingleObjectBuilderWithRule(GenericExtractorBuilder<T> bldr,
                SingleObjectKey<KeyType> key,
                Predicate<RuleNode> ancestorQualifier, Class<R> ruleType, Set<SingleObjectExtractionInfo<KeyType, ?>> set) {
            this.bldr = bldr;
            this.key = key;
            this.ruleType = ruleType;
            this.ancestorQualifier = ancestorQualifier;
            this.set = set;
        }

        public FinishableSingleObjectBuilder<T, KeyType> extractingObjectWith(Function<R, KeyType> func) {
            SingleObjectExtractionInfo<KeyType, R> info = new SingleObjectExtractionInfo<>(key, ancestorQualifier, ruleType, func);
            set.add(info);
            return new FinishableSingleObjectBuilder<>(bldr, key, set);
        }
    }

    private <KeyType> GenericExtractorBuilder<T> addSingles(SingleObjectKey<KeyType> key, Set<SingleObjectExtractionInfo<KeyType, ?>> single) {
        if (singles.containsKey(key)) {
            SingleObjectExtractionInfos<KeyType> infos = (SingleObjectExtractionInfos<KeyType>) singles.get(key);
            infos.addAll(single);
            return this;
        }
        SingleObjectExtractionInfos<KeyType> infos = new SingleObjectExtractionInfos<>(key, single);
        singles.put(key, infos);
        return this;
    }

    private static final class SingleObjectExtractionInfos<KeyType> implements Hashable {

        private final SingleObjectKey<KeyType> key;
        private final Set<SingleObjectExtractionInfo<KeyType, ?>> infos;

        public SingleObjectExtractionInfos(SingleObjectKey<KeyType> key, Set<SingleObjectExtractionInfo<KeyType, ?>> infos) {
            this.key = key;
            this.infos = new HashSet<>(infos);
        }

        void addAll(Set<SingleObjectExtractionInfo<KeyType, ?>> all) {
            for (SingleObjectExtractionInfo<KeyType, ?> so : all) {
                infos.add(so);
            }
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.hashObject(key);
            for (SingleObjectExtractionInfo i : infos) {
                hasher.hashObject(i);
            }
        }

        public Encounters<KeyType> extract(ParserRuleContext node) {
            SingleVisitor<KeyType> v = new SingleVisitor<>(infos);
            node.accept(v);
            return v.encounters;
        }

        static final class SingleVisitor<KeyType> extends AbstractParseTreeVisitor<Void> {

            private final SingleObjectExtractionInfo<KeyType, ?>[] infos;
            private int[] activations;
            private final Encounters<KeyType> encounters = new Encounters<>();

            SingleVisitor(Set<SingleObjectExtractionInfo<KeyType, ?>> infos) {
                this.infos = infos.toArray((SingleObjectExtractionInfo<KeyType, ?>[]) Array.newInstance(SingleObjectExtractionInfo.class, infos.size()));
                activations = new int[this.infos.length];
                for (int i = 0; i < this.infos.length; i++) {
                    SingleObjectExtractionInfo<?, ?> info = this.infos[i];
                    if (info.ancestorQualifier == null) {
                        activations[i] = 1;
                    }
                }
            }

            @Override
            public Void visitChildren(RuleNode node) {
                if (node instanceof ParserRuleContext) {
                    visitRule((ParserRuleContext) node);
                } else {
                    super.visitChildren(node);
                }
                return null;
            }

            private <R extends ParserRuleContext> void runOne(SingleObjectExtractionInfo<KeyType, R> extractor, ParserRuleContext ctx) {
                if (extractor.ruleType.isInstance(ctx)) {
                    doRunOne(extractor, extractor.ruleType.cast(ctx));
                }
            }

            private <R extends ParserRuleContext> void doRunOne(SingleObjectExtractionInfo<KeyType, R> extractor, R ctx) {
                KeyType found = extractor.extractor.apply(ctx);
                if (found != null) {
                    encounters.add(found, ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1, extractor.ruleType);
                }
            }

            private void visitRule(ParserRuleContext rule) {
                boolean[] scratch = new boolean[infos.length];
                boolean anyActivated = false;
                for (int i = 0; i < infos.length; i++) {
                    if (infos[i].ancestorQualifier != null) {
                        if (infos[i].ancestorQualifier.test(rule)) {
                            activations[i]++;
                            scratch[i] = true;
                            anyActivated = true;
                        }
                    }
                    if (activations[i] > 0) {
                        runOne(infos[i], rule);
                    }
                }
                super.visitChildren(rule);
                if (anyActivated) {
                    for (int i = 0; i < infos.length; i++) {
                        if (scratch[i]) {
                            activations[i]--;
                        }
                    }
                }
            }
        }
    }

    private static final class SingleObjectExtractionInfo<KeyType, R extends ParserRuleContext> implements Hashable {

        final SingleObjectKey<KeyType> key;
        final Predicate<RuleNode> ancestorQualifier;
        final Class<R> ruleType;
        final Function<R, KeyType> extractor;

        public SingleObjectExtractionInfo(SingleObjectKey<KeyType> key, Predicate<RuleNode> ancestorQualifier, Class<R> ruleType, Function<R, KeyType> extractor) {
            this.key = key;
            this.ancestorQualifier = ancestorQualifier;
            this.ruleType = ruleType;
            this.extractor = extractor;
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.hashObject(key);
            hasher.hashObject(ancestorQualifier);
            hasher.writeString(ruleType.getName());
            hasher.hashObject(extractor);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 79 * hash + Objects.hashCode(this.key);
            hash = 79 * hash + Objects.hashCode(this.ancestorQualifier);
            hash = 79 * hash + Objects.hashCode(this.ruleType);
            hash = 79 * hash + Objects.hashCode(this.extractor);
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
            final SingleObjectExtractionInfo<?, ?> other = (SingleObjectExtractionInfo<?, ?>) obj;
            if (!Objects.equals(this.key, other.key)) {
                return false;
            }
            if (!Objects.equals(this.ancestorQualifier, other.ancestorQualifier)) {
                return false;
            }
            if (!Objects.equals(this.ruleType, other.ruleType)) {
                return false;
            }
            if (!Objects.equals(this.extractor, other.extractor)) {
                return false;
            }
            return true;
        }
    }

    /**
     * The set of encounters with somethibg which is expected to be a singleton,
     * but in a malformed source, may not be.
     *
     * @param <KeyType>
     */
    public static class Encounters<KeyType> {

        private final List<Encounter> encounters = new ArrayList<>(3);

        public Encounter first() {
            return hasEncounter() ? encounters.get(0) : null;
        }

        void add(KeyType key, int start, int end, Class<? extends ParserRuleContext> in) {
            encounters.add(new Encounter<>(start, end, key, in));
        }

        public boolean is(KeyType keyType) {
            return encounters.size() == 1 && Objects.equals(keyType, first().value());
        }

        public int visitOthers(Consumer<Encounter<KeyType>> c) {
            if (encounters.size() < 2) {
                return 0;
            }
            for (int i = 1; i < encounters.size(); i++) {
                c.accept(encounters.get(i));;
            }
            return encounters.size() - 2;
        }

        public boolean hasEncounter() {
            return !encounters.isEmpty();
        }

        public boolean hasMultiple() {
            return encounters.size() > 1;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("");
            for (int i = 0; i < encounters.size(); i++) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(i).append(": ").append(encounters.get(i));
            }
            return sb.toString();
        }

        public static final class Encounter<KeyType> {

            private final int start;
            private final int end;
            private final KeyType key;
            private final Class<? extends ParserRuleContext> in;

            public Encounter(int start, int end, KeyType key, Class<? extends ParserRuleContext> in) {
                this.start = start;
                this.end = end;
                this.key = key;
                this.in = in;
            }

            public int start() {
                return start;
            }

            public int end() {
                return end;
            }

            public KeyType value() {
                return key;
            }

            public Class<? extends ParserRuleContext> in() {
                return in;
            }

            public String toString() {
                return key + "@" + start + ":" + end + "`" + in.getSimpleName();
            }
        }
    }

    public <KeyType> RegionExtractionBuilderWithKey<T, KeyType> extractingRegionsTo(RegionsKey<KeyType> key) {
        return new RegionExtractionBuilderWithKey<>(this, key);
    }

    public static final class RegionExtractionBuilder<EntryPointType extends ParserRuleContext> {

        private final GenericExtractorBuilder<EntryPointType> bldr;

        public RegionExtractionBuilder(GenericExtractorBuilder<EntryPointType> bldr) {
            this.bldr = bldr;
        }

        public <T> RegionExtractionBuilderWithKey<EntryPointType, T> recordingRegionsUnder(RegionsKey<T> key) {
            return new RegionExtractionBuilderWithKey<>(bldr, key);
        }
    }

    public static final class RegionExtractionBuilderWithKey<EntryPointType extends ParserRuleContext, RegionKeyType> {

        private final GenericExtractorBuilder<EntryPointType> bldr;
        private final RegionsKey<RegionKeyType> key;

        RegionExtractionBuilderWithKey(GenericExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key) {
            this.bldr = bldr;
            this.key = key;
        }

        public <T extends ParserRuleContext> RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, T> whenRuleType(Class<T> type) {
            return new RegionExtractionBuilderForOneRuleType<>(bldr, key, type);
        }
    }

    public static final class RegionExtractionBuilderForOneRuleType<EntryPointType extends ParserRuleContext, RegionKeyType, RuleType extends ParserRuleContext> {

        private final GenericExtractorBuilder<EntryPointType> bldr;
        private final RegionsKey<RegionKeyType> key;
        private final Class<RuleType> ruleType;
        private Predicate<RuleNode> ancestorQualifier;
        private final Set<OneRegionExtractor<RegionKeyType, ?, ?>> extractors = new HashSet<>();

        RegionExtractionBuilderForOneRuleType(GenericExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, Class<RuleType> ruleType, Set<OneRegionExtractor<RegionKeyType, ?, ?>> set) {
            this(bldr, key, ruleType);
            this.extractors.addAll(set);
        }

        RegionExtractionBuilderForOneRuleType(GenericExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, Class<RuleType> ruleType) {
            this.bldr = bldr;
            this.key = key;
            this.ruleType = ruleType;
        }

        public RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, RuleType> whenAncestorMatches(Predicate<RuleNode> pred) {
            if (ancestorQualifier == null) {
                ancestorQualifier = pred;
            } else {
                ancestorQualifier = ancestorQualifier.or(pred);
            }
            return this;
        }

        public RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, RuleType> whenAncestorRuleOf(Class<? extends RuleNode> type) {
            return whenAncestorMatches(new QualifierPredicate(type));
        }

        private FinishableRegionExtractor<EntryPointType, RegionKeyType> finish() {
            return new FinishableRegionExtractor<>(bldr, key, extractors);
        }

        /**
         * Simple-ish use-case: Record the bounds of all rules of RuleType, with
         * the value provided by the passed function.
         *
         * @return The outer builder
         */
        public FinishableRegionExtractor<EntryPointType, RegionKeyType> extractingBoundsFromRuleAndKeyWith(Function<RuleType, RegionKeyType> func) {
            return extractingKeyAndBoundsFromWith((rule, c) -> {
                RegionKeyType k = func.apply(rule);
                c.accept(k, new int[]{
                    rule.getStart().getStartIndex(),
                    rule.getStop().getStopIndex() + 1
                });
            });
        }

        /**
         * Simple use-case: Record the bounds of all rules of RuleType, with a
         * null value (useful for things like block delimiters such as braces or
         * parentheses).
         *
         * @return The outer builder
         */
        public FinishableRegionExtractor<EntryPointType, RegionKeyType> extractingBoundsFromRule() {
            return extractingKeyAndBoundsFromWith((rule, c) -> {
                c.accept(null, new int[]{
                    rule.getStart().getStartIndex(),
                    rule.getStop().getStopIndex() + 1
                });
            });
        }

        public FinishableRegionExtractor<EntryPointType, RegionKeyType> extractingBoundsFromRuleUsingKey(RegionKeyType key) {
            return extractingKeyAndBoundsFromWith((rule, c) -> {
                c.accept(key, new int[]{
                    rule.getStart().getStartIndex(),
                    rule.getStop().getStopIndex() + 1
                });
            });
        }

        public FinishableRegionExtractor<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromTokenWith(BiConsumer<RuleType, BiConsumer<RegionKeyType, Token>> consumer) {
            extractors.add(new OneRegionExtractor<>(ruleType, ancestorQualifier, consumer, RegionExtractType.TOKEN));
            return finish();
        }

        public FinishableRegionExtractor<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromWith(BiConsumer<RuleType, BiConsumer<RegionKeyType, int[]>> consumer) {
            extractors.add(new OneRegionExtractor<>(ruleType, ancestorQualifier, consumer, RegionExtractType.INT_ARRAY));
            return finish();
        }

        public FinishableRegionExtractor<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromRuleWith(BiConsumer<RuleType, BiConsumer<RegionKeyType, ParserRuleContext>> consumer) {
            extractors.add(new OneRegionExtractor<>(ruleType, ancestorQualifier, consumer, RegionExtractType.PARSER_RULE_CONTEXT));
            return finish();
        }

        public FinishableRegionExtractor<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromTerminalNodeWith(BiConsumer<RuleType, BiConsumer<RegionKeyType, TerminalNode>> consumer) {
            extractors.add(new OneRegionExtractor<>(ruleType, ancestorQualifier, consumer, RegionExtractType.TERMINAL_NODE));
            return finish();
        }

        public FinishableRegionExtractor<EntryPointType, RegionKeyType> extractingKeyAndBoundsFromTerminalNodeList(BiConsumer<RuleType, BiConsumer<RegionKeyType, List<TerminalNode>>> consumer) {
            extractors.add(new OneRegionExtractor<>(ruleType, ancestorQualifier, consumer, RegionExtractType.TERMINAL_NODE_LIST));
            return finish();
        }
    }

    enum RegionExtractType implements Function<Object, int[]> {
        TOKEN,
        TERMINAL_NODE,
        INT_ARRAY,
        PARSER_RULE_CONTEXT,
        TERMINAL_NODE_LIST;

        public <K, TType> BiConsumer<K, TType> wrap(BiConsumer<K, int[]> c) {
            return (K t, TType u) -> {
                if (RegionExtractType.this == TERMINAL_NODE_LIST && u instanceof List<?>) {
                    List<TerminalNode> tns = (List<TerminalNode>) u;
                    for (TerminalNode n : tns) {
                        c.accept(t, TERMINAL_NODE.apply(u));
                    }
                } else {
                    c.accept(t, apply(u));
                }
            };
        }

        @Override
        public int[] apply(Object t) {
            if (t == null) {
                return null;
            }
            switch (this) {
                case TOKEN:
                    Token tok = (Token) t;
                    return new int[]{tok.getStartIndex(), tok.getStopIndex() + 1};
                case TERMINAL_NODE:
                    return TOKEN.apply(((TerminalNode) t).getSymbol());
                case PARSER_RULE_CONTEXT:
                    ParserRuleContext rule = (ParserRuleContext) t;
                    return new int[]{rule.getStart().getStartIndex(), rule.getStop().getStopIndex() + 1};
                case INT_ARRAY:
                    int[] val = (int[]) t;
                    if (val.length != 2) {
                        throw new IllegalArgumentException("Array must have two elements: " + Arrays.toString(val));
                    }
                    return val;
                default:
                    throw new AssertionError(this);
            }
        }
    }

    static final class OneRegionExtractor<KeyType, RuleType extends RuleNode, TType> implements Hashable {

        final Class<RuleType> ruleType;
        final Predicate<RuleNode> ancestorQualifier;
        final BiConsumer<RuleType, BiConsumer<KeyType, TType>> extractor;
        private final RegionExtractType ttype;

        public OneRegionExtractor(Class<RuleType> ruleType, Predicate<RuleNode> ancestorQualifier, BiConsumer<RuleType, BiConsumer<KeyType, TType>> tok, RegionExtractType ttype) {
            this.ruleType = ruleType;
            this.ancestorQualifier = ancestorQualifier;
            this.extractor = tok;
            this.ttype = ttype;
        }

        public void extract(RuleType rule, BiConsumer<KeyType, int[]> c) {
            extractor.accept(rule, ttype.wrap(c));
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeString(ruleType.getName());
            hasher.hashObject(ancestorQualifier);
            hasher.hashObject(extractor);
            hasher.writeInt(ttype.ordinal());
        }
    }

    public static final class FinishableRegionExtractor<EntryPointType extends ParserRuleContext, RegionKeyType> {

        private final GenericExtractorBuilder<EntryPointType> bldr;
        private final RegionsKey<RegionKeyType> key;
        private final Set<OneRegionExtractor<RegionKeyType, ?, ?>> extractors = new HashSet<>();

        FinishableRegionExtractor(GenericExtractorBuilder<EntryPointType> bldr, RegionsKey<RegionKeyType> key, Set<OneRegionExtractor<RegionKeyType, ?, ?>> set) {
            this.bldr = bldr;
            this.key = key;
            this.extractors.addAll(set);
        }

        public <T extends ParserRuleContext> RegionExtractionBuilderForOneRuleType<EntryPointType, RegionKeyType, T> whenRuleType(Class<T> type) {
            return new RegionExtractionBuilderForOneRuleType<>(bldr, key, type);
        }

        GenericExtractorBuilder<EntryPointType> finishRegionExtractor() {
            assert !extractors.isEmpty();
            bldr.addRegionEx(new RegionExtractionInfo<>(key, extractors));
            return bldr;
        }
    }

    static final class RegionExtractionInfo<RegionKeyType> implements Hashable {

        final RegionsKey<RegionKeyType> key;
        final Set<OneRegionExtractor<RegionKeyType, ?, ?>> extractors;

        public RegionExtractionInfo(RegionsKey<RegionKeyType> key, Set<OneRegionExtractor<RegionKeyType, ?, ?>> extractors) {
            this.key = key;
            this.extractors = extractors;
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.hashObject(key);
            for (OneRegionExtractor<?, ?, ?> e : extractors) {
                hasher.hashObject(e);
            }
        }

        ParseTreeVisitor<Void> createVisitor(BiConsumer<RegionKeyType, int[]> c) {
            return new V(key.type, c, extractors);
        }

        static class V<RegionKeyType> extends AbstractParseTreeVisitor<Void> {

            private final BiConsumer<RegionKeyType, int[]> c;
            private final OneRegionExtractor<?, ?, ?>[] extractors;
            private final int[] activatedCount;

            public V(Class<RegionKeyType> keyType, BiConsumer<RegionKeyType, int[]> c, Set<OneRegionExtractor<?, ?, ?>> extractors) {
                this.c = c;
                this.extractors = extractors.toArray(new OneRegionExtractor<?, ?, ?>[extractors.size()]);
                this.activatedCount = new int[this.extractors.length];
                for (int i = 0; i < this.extractors.length; i++) {
                    if (this.extractors[i].ancestorQualifier == null) {
                        activatedCount[i] = 1;
                    }
                }
            }

            @Override
            public Void visitChildren(RuleNode node) {
                boolean[] scratch = new boolean[extractors.length];
                for (int i = 0; i < scratch.length; i++) {
                    OneRegionExtractor<RegionKeyType, ?, ?> e = (OneRegionExtractor<RegionKeyType, ?, ?>) extractors[i];
                    if (e.ancestorQualifier != null) {
                        if (e.ancestorQualifier.test(node)) {
                            activatedCount[i]++;
                            scratch[i] = true;
                        }
                    }
                    if (activatedCount[i] > 0) {
                        runOne(node, e);
                    }
                }
                super.visitChildren(node);
                for (int i = 0; i < scratch.length; i++) {
                    if (scratch[i]) {
                        activatedCount[i]--;
                    }
                }
                return null;
            }

            private <RuleType extends RuleNode, TType> void runOne(RuleNode node, OneRegionExtractor<RegionKeyType, RuleType, TType> e) {
                if (e.ruleType.isInstance(node)) {
                    doRunOne(e.ruleType.cast(node), e);
                }
            }

            private <RuleType extends RuleNode, TType> void doRunOne(RuleType node, OneRegionExtractor<RegionKeyType, RuleType, TType> e) {
                e.extract(node, c);
            }
        }
    }

    public static final class SingleObjectKey<T> implements Serializable, Hashable, ExtractionKey<T> {

        private final Class<? super T> type;
        private final String name;

        private SingleObjectKey(Class<? super T> type, String name) {
            this.name = name;
            this.type = type;
        }

        private SingleObjectKey(Class<? super T> type) {
            this.type = type;
            this.name = null;
        }

        public static <T> SingleObjectKey<T> create(Class<? super T> type, String name) {
            return new SingleObjectKey<>(type, name == null ? type.getSimpleName() : name);
        }

        public static <T> SingleObjectKey<T> create(Class<T> type) {
            return create(type, null);
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeString(type.getName());
            if (name != type.getSimpleName()) {
                hasher.writeString(name);
            }
        }

        public Class<T> type() {
            return (Class<T>) type;
        }

        public String name() {
            return name;
        }

        public String toString() {
            String nm = type.getSimpleName();
            return nm.equals(name) ? nm : name + ":" + nm;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 43 * hash + Objects.hashCode(this.type);
            hash = 43 * hash + Objects.hashCode(this.name);
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
            final SingleObjectKey<?> other = (SingleObjectKey<?>) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            return true;
        }
    }

    /**
     * Key used to retrieve SemanticRegions&lt;T&gt; instances from an
     * Extraction.
     *
     * @param <T>
     */
    public static final class RegionsKey<T> implements Serializable, Hashable, ExtractionKey<T> {

        private final Class<? super T> type;
        private final String name;

        private RegionsKey(Class<? super T> type, String name) {
            this.name = name;
            this.type = type;
        }

        private RegionsKey(Class<? super T> type) {
            this.type = type;
            this.name = null;
        }

        public static <T> RegionsKey<T> create(Class<? super T> type, String name) {
            return new RegionsKey<>(type, name == null ? type.getSimpleName() : name);
        }

        public static <T> RegionsKey<T> create(Class<T> type) {
            return create(type, null);
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeString(type.getName());
            if (name != type.getSimpleName()) {
                hasher.writeString(name);
            }
        }

        public Class<T> type() {
            return (Class<T>) type;
        }

        public String name() {
            return name;
        }

        public String toString() {
            String nm = type.getSimpleName();
            return nm.equals(name) ? nm : name + ":" + nm;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.type);
            hash = 37 * hash + Objects.hashCode(this.name);
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
            final RegionsKey<?> other = (RegionsKey<?>) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            return true;
        }
    }
}
