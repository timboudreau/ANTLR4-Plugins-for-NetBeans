package org.nemesis.extraction.key;

/**
 * Base interface for extraction key.  Each data structure that can be extracted
 * allows the extractor to associate some data with each extracted element.  The
 * type provided by this interface is what allows type-safe lookup of data, returning
 * the exact type expected.
 *
 * @author Tim Boudreau
 */
public interface ExtractionKey<T> {

    Class<T> type();

    String name();

}
