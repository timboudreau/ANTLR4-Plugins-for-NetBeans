package org.nemesis.extraction.attribution;

import com.mastfrog.util.collections.ArrayUtils;
import com.mastfrog.util.collections.CollectionUtils;
import java.util.Collections;
import java.util.Set;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;

/**
 * Interface for things which can be registered in a named lookup based on the
 * mime type, e.g. <code>antlr/resolvers/text/x-foo</code>, which will resolve
 * whatever the language's concept of an "import" is. Can also be a factory for
 * a RegisterableResolver for attributing unknown variables.
 *
 * @author Tim Boudreau
 */
public interface ImportFinder {

    public Set<GrammarSource<?>> allImports(Extraction importer,
            Set<? super NamedSemanticRegion<? extends Enum<?>>> notFound);

    /**
     * Get the set of grammar sources that could be the origin of a particular
     * unresolvable name reference. The default implementation simply returns
     * all imports by calling allImports() - if an implementation can narrow
     * this to examine fewer imported source files, it should.
     *
     * @param <K> The key type
     * @param ref The name reference, unresolvable within the original source
     * file
     * @param in The current extraction of the original source file
     * @return A set of grammar sources
     */
    default <K extends Enum<K>> Set<GrammarSource<?>> possibleImportersOf(
            UnknownNameReference<K> ref, Extraction in) {
        return allImports(in, CollectionUtils.blackHoleSet());
    }

    /**
     * Create an ImportFinder which uses the passed array of named region keys,
     * resolving them against an extraction and using the name() value for each
     * extracted element for that key as a file name sans-extension to pass to
     * GrammarSource.resolveRelative().
     *
     * @param first A key
     * @param more More keys
     * @return an import finder
     */
    static ImportFinder forKeys(NamedRegionKey<?> first,
            NamedRegionKey<?>... more) {
        return new KeyBasedImportFinder(ArrayUtils.prepend(first, more));
    }

    /**
     * Create a reference resolver for resolving name references which do not
     * have a referent in the current source file, using the passed key.
     *
     * @param <K> The key type
     * @param key The key
     * @return A registerable resolver
     */
    default <K extends Enum<K>> RegisterableResolver<K>
            createReferenceResolver(NamedRegionKey<K> key) {
        return new ImportFinderResolver<>(key, this);
    }

    /**
     * No-op implementation of ImportFinder used as a fallback when none is
     * registered.
     */
    static ImportFinder EMPTY = new ImportFinder() {
        @Override
        public Set<GrammarSource<?>> allImports(Extraction importer,
                Set<? super NamedSemanticRegion<? extends Enum<?>>> notFound) {
            return Collections.emptySet();
        }

        @Override
        public String toString() {
            return "empty-import-finder";
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o.getClass() == getClass();
        }

        @Override
        public int hashCode() {
            return 0;
        }
    };

    static ImportFinder forMimeType(String mimeType) {
        return ResolverRegistry.importFinder(mimeType);
    }
}
