package org.nemesis.extraction.attribution;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ResolutionConsumer;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;

/**
 * Extends RegisterableResolver to simplify implementation to just finding the
 * list of imports present in an extraction and resolving (triggering a parse
 * and extraction) of them.
 *
 * @author Tim Boudreau
 * @param I the key type for imports - some file types (such as ANTLR, with its
 * "tokens" and "import" directives) allow for multiple ways to import things,
 * which need to be differentiated when deciding how to resolve them
 */
public interface SimpleRegisterableResolver<K extends Enum<K>, I> extends RegisterableResolver<K> {

    Set<I> importsThatCouldContain(Extraction extraction, UnknownNameReference<K> ref);

    NamedRegionKey<K> key();

    Extraction resolveImport(I name, Extraction in);

    @Override
    default <X> X resolve(Extraction extraction, UnknownNameReference<K> ref, ResolutionConsumer<GrammarSource<?>, NamedSemanticRegions<K>, NamedSemanticRegion<K>, K, X> c) throws IOException {
        Set<I> possibleSources = importsThatCouldContain(extraction, ref);
        String name = ref.name();
        for (I src : possibleSources) {
            Extraction ext = resolveImport(src, extraction);
            if (ext != null) {
                NamedSemanticRegions<K> names = ext.namedRegions(key());
                if (names.contains(name)) {
                    NamedSemanticRegion<K> decl = names.regionFor(name);
                    if (decl != null) {
                        return c.resolved(ref, ext.source(), names, decl);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public default <X> Map<UnknownNameReference<K>, X> resolveAll(Extraction extraction, SemanticRegions<UnknownNameReference<K>> refs, ResolutionConsumer<GrammarSource<?>, NamedSemanticRegions<K>, NamedSemanticRegion<K>, K, X> c) throws IOException {
        Map<I, Extraction> extForImport = new HashMap<>();
        Set<I> unresolvable = new HashSet<>();
        Map<UnknownNameReference<K>, X> resolved = new HashMap<>();
        Set<I> possibleSources = new HashSet<>();
        for (SemanticRegion<UnknownNameReference<K>> reg : refs) {
            String name = reg.key().name();
            Set<I> newPossibleSources = importsThatCouldContain(extraction, reg.key());
            if (!newPossibleSources.equals(possibleSources)) {
                for (I ps : newPossibleSources) {
                    if (!unresolvable.contains(ps) && !extForImport.containsKey(ps)) {
                        Extraction ext = resolveImport(ps, extraction);
                        if (ext != null) {
                            extForImport.put(ps, ext);
                        } else {
                            unresolvable.add(ps);
                        }
                    }
                    possibleSources.add(ps);
                }
            }
            for (I ps : newPossibleSources) {
                if (unresolvable.contains(ps)) {
                    continue;
                }
                Extraction ext = extForImport.get(ps);
                NamedSemanticRegions<K> names = ext.namedRegions(key());
                if (names.contains(name)) {
                    NamedSemanticRegion<K> decl = names.regionFor(name);
                    if (decl != null) {
                        X x = c.resolved(reg.key(), ext.source(), names, decl);
                        if (x != null) {
                            resolved.put(reg.key(), x);
                        }
                    }
                }
            }
        }
        return resolved;
    }
}
