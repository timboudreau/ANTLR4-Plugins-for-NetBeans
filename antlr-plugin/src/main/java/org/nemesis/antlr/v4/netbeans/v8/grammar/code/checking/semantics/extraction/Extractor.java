package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.Hashable;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.SemanticRegions;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.SingletonKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.GrammarSource;

/**
 * An object which extracts information about source regions using strategies
 * provided to its builder, producing an {@link Extraction}, which is essentially
 * a lightweight, serializable, high performance database of information about
 * the file's contents.
 * <p>
 * Usage:
 * <ol>
 * <li>Create ExtractionKey objects such as NamedRegionKey, to identify the things
 * you want to extract and retrieve; typically these are stored as public static
 * fields.</li>
 * <li>Call <code>Extractor.builder(SomeParseTreeType.class)</code> to create a builder,
 * which you will provide strategies to for what to extract and how</li>
 * <li>Run your extraction (you will need an implementation of GrammarSource which can
 * resolve imports if files reference contents from other files).</li>
 * <li>Use your keys to retrieve data structures that describe elements in the sources,
 * such as variable definitions or anything else that could be derived from a ParserRuleContext,
 * and use them for syntax highlighting, semantic error detection, code folding, etc.</li>
 * </ol>
 *
 * @author Tim Boudreau
 */
public final class Extractor<T extends ParserRuleContext> {

    private final Class<T> documentRootType;
    final Set<NamesAndReferencesExtractionStrategy<?>> nameExtractors;
    final Set<RegionExtractionStrategies<?>> regionsInfo;
    private String extractorsHash;
    final Map<SingletonKey<?>, SingletonExtractionStrategies<?>> singles;

    Extractor(Class<T> documentRootType, Set<NamesAndReferencesExtractionStrategy<?>> nameExtractors, Set<RegionExtractionStrategies<?>> regionsInfo2, Map<SingletonKey<?>, SingletonExtractionStrategies<?>> singles) {
        this.documentRootType = documentRootType;
        this.nameExtractors = nameExtractors;
        this.regionsInfo = regionsInfo2;
        this.singles = singles;
    }

    public static <T extends ParserRuleContext> ExtractorBuilder<T> builder(Class<T> documentRootType) {
        return new ExtractorBuilder<>(documentRootType);
    }

    public Class<T> documentRootType() {
        return documentRootType;
    }

    /**
     * Returns an opaque string representing a unique hash code for all
     * of the strategies employed in creating this extraction.  This is
     * useful when you are caching Extractions on disk to avoid re-parses, but
     * do not want to accidentally work against extractions produced by an older
     * version of your module which will not contain what you need or otherwise
     * be incompatible.
     *
     * @return A hash string
     */
    public String extractorsHash() {
        if (extractorsHash == null) {
            Hashable.Hasher hasher = Hashable.newHasher();
            for (NamesAndReferencesExtractionStrategy<?> n : nameExtractors) {
                hasher.hashObject(n);
            }
            for (RegionExtractionStrategies<?> e : regionsInfo) {
                hasher.hashObject(e);
            }
            for (Map.Entry<SingletonKey<?>, SingletonExtractionStrategies<?>> e : singles.entrySet()) {
                hasher.hashObject(e.getValue());
            }
            extractorsHash = hasher.hash();
        }
        return extractorsHash;
    }

    /**
     * Run extraction, passing a rule node and grammar source (which is
     * used to resolve imports).
     *
     * @param ruleNode The root node of the parse tree to walk
     * @param source The source (document, file, whatever) where the content
     * is found, which optionally can resolve references to other sources.
     *
     * @return An extraction - a database of information about the content
     * of this file, whose contents can be retrieved by keys provided to
     * this extractor's builder.
     */
    public Extraction extract(T ruleNode, GrammarSource<?> source) {
        Extraction extraction = new Extraction(extractorsHash(), source);
        for (RegionExtractionStrategies<?> r : regionsInfo) {
            runRegions2(r, ruleNode, extraction);
        }
        for (NamesAndReferencesExtractionStrategy<?> n : nameExtractors) {
            runNames(ruleNode, n, extraction);
        }
        for (Map.Entry<SingletonKey<?>, SingletonExtractionStrategies<?>> e : singles.entrySet()) {
            runSingles(e.getValue(), ruleNode, extraction);
        }
        return extraction;
    }

    private <K> void runSingles(SingletonExtractionStrategies<K> single, T ruleNode, Extraction extraction) {
        SingletonEncounters<K> encounters = single.extract(ruleNode);
        extraction.addSingle(single.key, encounters);
    }

    private <L extends Enum<L>> void runNames(T ruleNode, NamesAndReferencesExtractionStrategy<L> x, Extraction into) {
        x.invoke(ruleNode, into.store);
    }

    private <K> void runRegions2(RegionExtractionStrategies<K> info, T ruleNode, Extraction extraction) {
        SemanticRegions.SemanticRegionsBuilder<K> bldr = SemanticRegions.builder(info.key.type());
        ParseTreeVisitor<?> v = info.createVisitor((k, bounds) -> {
            bldr.add(k, bounds[0], bounds[1]);
        });
        ruleNode.accept(v);
        extraction.add(info.key, bldr.build());
    }
}
