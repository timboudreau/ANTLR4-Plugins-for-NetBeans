package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key;

/**
 * Marker interface for extractor methods which take a heterogenous set of keys
 * all of which must return named things.
 *
 * @param <T>
 */
public interface NamedExtractionKey<T extends Enum<T>> extends ExtractionKey<T> {
}
