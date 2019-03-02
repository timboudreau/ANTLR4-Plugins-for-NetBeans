package org.nemesis.extraction.key;

/**
 * Marker interface for extractor methods which take a heterogenous set of keys
 * all of which must return named things.
 *
 * @param <T>
 */
public interface NamedExtractionKey<T extends Enum<T>> extends ExtractionKey<T> {
}
