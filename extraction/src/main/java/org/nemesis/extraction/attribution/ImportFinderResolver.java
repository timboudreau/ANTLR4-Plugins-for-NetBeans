package org.nemesis.extraction.attribution;

import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.Extractors;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;

/**
 *
 * @author Tim Boudreau
 */
final class ImportFinderResolver<K extends Enum<K>> implements SimpleRegisterableResolver<K, GrammarSource<?>> {

    private final NamedRegionKey<K> key;
    private final ImportFinder finder;

    public ImportFinderResolver(NamedRegionKey<K> key, ImportFinder finder) {
        this.key = key;
        this.finder = finder;
    }

    @Override
    public Set<GrammarSource<?>> importsThatCouldContain(Extraction extraction, UnknownNameReference<K> ref) {
        return finder.possibleImportersOf(ref, extraction);
    }

    @Override
    public NamedRegionKey<K> key() {
        return key;
    }

    @Override
    public Extraction resolveImport(GrammarSource<?> g, Extraction in) {
        if (g != null) {
            Class<? extends ParserRuleContext> ruleType = in.documentRootType();
            return Extractors.getDefault().extract(in.mimeType(), g, ruleType);
        }
        return null;
    }

    @Override
    public Class<K> type() {
        return key.type();
    }
}
