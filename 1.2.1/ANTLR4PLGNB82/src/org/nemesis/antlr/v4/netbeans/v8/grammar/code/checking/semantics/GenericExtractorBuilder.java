package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameExtractors;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameExtractors.NameInfoStore;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameReferenceSetKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NamedRegionKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.QualifierPredicate;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedRegionReferenceSets;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.SerializationContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticRegions.SemanticRegionsBuilder;
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

    public GenericExtractorBuilder(Class<T> entryPoint) {
        this.documentRootType = entryPoint;
    }

    public static <T extends ParserRuleContext> GenericExtractorBuilder<T> forDocumentRoot(Class<T> documentRoot) {
        return new GenericExtractorBuilder(documentRoot);
    }

    public GenericExtractor<T> build() {
        return new GenericExtractor<>(documentRootType, nameExtractors, regionsInfo2);
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

        GenericExtractor(Class<T> documentRootType, Set<NameExtractors<?>> nameExtractors, Set<RegionExtractionInfo<?>> regionsInfo2) {
            this.documentRootType = documentRootType;
            this.nameExtractors = nameExtractors;
            this.regionsInfo = regionsInfo2;
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

        public Extraction extract(T ruleNode) {
            Extraction extraction = new Extraction(extractorsHash());
            for (RegionExtractionInfo<?> r : regionsInfo) {
                runRegions2(r, ruleNode, extraction);
            }
            for (NameExtractors<?> n : nameExtractors) {
                runNames(ruleNode, n, extraction);
            }
            return extraction;
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

    /**
     * A Collection of data extracted during a parse of some document.
     */
    public static class Extraction implements Externalizable {

        private final Map<RegionsKey<?>, SemanticRegions<?>> regions = new HashMap<>();
        private final Map<NamedRegionExtractorBuilder.NamedRegionKey<?>, NamedSemanticRegions<?>> nameds = new HashMap<>();
        private final Map<NamedRegionExtractorBuilder.NameReferenceSetKey<?>, NamedSemanticRegions.NamedRegionReferenceSets<?>> refs = new HashMap<>();
        private final Map<NamedRegionExtractorBuilder.NameReferenceSetKey<?>, BitSetStringGraph> graphs = new HashMap<>();
        private String extractorsHash;

        private Extraction(String extractorsHash) {
            this.extractorsHash = extractorsHash;
        }

        public Extraction() { // for serialization, sigh

        }

        /**
         * This string identifies the <i>code</i> which generated an extraction.
         * In the case that these are serialized and cached on disk, this can be
         * used (for example, as a parent directory name) to ensure that a newer
         * version of a module does not load an extraction generated by a
         * previous one unless the code matches. For lambdas, hash generation
         * relies on the consistency of toString() and hashCode() for lambdas
         * across runs - which works fine on JDK 8/9.  At worst, you get a
         * false negative and parse.
         *
         * @return A hash value which should be unique to the contents of the
         * GenericExtractor which created this extraction.
         */
        public String creationHash() {
            return extractorsHash;
        }

        @Override
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
                int graphCount = in.readInt();
                for (int i = 0; i < graphCount; i++) {
                    NameReferenceSetKey<?> k = (NameReferenceSetKey<?>) in.readObject();
                    BitSetStringGraph graph = BitSetStringGraph.load(in);
                    graphs.put(k, graph);
                }
                this.regions.putAll(sregions);
                this.nameds.putAll(snameds);
                this.refs.putAll(srefs);
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
            return result == null ? null : (NamedSemanticRegions<T>) result;
        }

        public <T extends Enum<T>> NamedSemanticRegions.NamedRegionReferenceSets<T> namedRegions(NameReferenceSetKey<T> key) {
            NamedRegionReferenceSets<?> result = refs.get(key);
            return result == null ? null : (NamedRegionReferenceSets<T>) result;
        }

        @SuppressWarnings("unchecked")
        public <T> SemanticRegions<T> regions(RegionsKey<T> key) {
            SemanticRegions<?> result = regions.get(key);
            assert key.type.equals(result.keyType());
            return (SemanticRegions<T>) result;
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

        };
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
         * the key provided by the passed function.
         *
         * @return The outer builder
         */
        public GenericExtractorBuilder<EntryPointType> extractingBoundsFromRuleAndKeyWith(Function<RuleType, RegionKeyType> func) {
            return extractingKeyAndBoundsFromWith((rule, c) -> {
                RegionKeyType k = func.apply(rule);
                c.accept(k, new int[]{
                    rule.getStart().getStartIndex(),
                    rule.getStop().getStopIndex() + 1
                });
            }).finishRegionExtractor();
        }

        /**
         * Simple use-case: Record the bounds of all rules of RuleType, with a
         * null key (useful for things like block delimiters such as braces or
         * parentheses).
         *
         * @return The outer builder
         */
        public GenericExtractorBuilder<EntryPointType> extractingBoundsFromRule() {
            return extractingKeyAndBoundsFromWith((rule, c) -> {
                c.accept(null, new int[]{
                    rule.getStart().getStartIndex(),
                    rule.getStop().getStopIndex() + 1
                });
            }).finishRegionExtractor();
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

    /**
     * Key used to retrieve SemanticRegions&lt;T&gt; instances from an
     * Extraction.
     *
     * @param <T>
     */
    public static final class RegionsKey<T> implements Serializable, Hashable {

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
