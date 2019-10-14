package org.nemesis.extraction.attribution;

import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.Extractors;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;

/**
 *
 * @author Tim Boudreau
 */
public interface ImportBasedResolver<K extends Enum<K>> extends SimpleRegisterableResolver<K, String> {

    default Extraction resolveImport(String name, Extraction in) {
        // XXX use ParserManager?
        GrammarSource<?> g = in.resolveRelative(name);
        if (g != null) {
            Class<? extends ParserRuleContext> ruleType = in.documentRootType();
            return Extractors.getDefault().extract(in.mimeType(), g, ruleType);
        }
        return null;
    }

    static <K extends Enum<K>> ImportBasedResolver<K> create(NamedRegionKey<K> key, NamedRegionKey<?>... importKeys) {
        return new DefaultImportBasedResolver(key, importKeys);
    }
}
