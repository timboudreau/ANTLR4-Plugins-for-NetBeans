package org.nemesis.source.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;

/**
 * For complex parses using multiple visitors or listeners, provides a means of
 * collecting the results of different passes over a grammar source in a single
 * place. If a Document can be looked up from the document, the results will be
 * stored with the document; otherwise, the results will be stored
 * independently. Parsing bags are weakly cached so two calls to
 * forGrammarSource() over the same grammar source instance will return the same
 * object.
 *
 * @author Tim Boudreau
 */
public abstract class ParsingBag {

    private GrammarSource<?> source;
    private static final Map<GrammarSource<?>, ParsingBag> INSTANCES = new WeakHashMap<>();

    ParsingBag(GrammarSource<?> source) {
        this.source = source;
    }

    public synchronized final GrammarSource<?> source() {
        return source;
    }

    @SuppressWarnings("unchecked")
    final synchronized void setSource(GrammarSource<?> source) {
        assert source != null;
        this.source = source;
    }

    public abstract <R> R get(Class<R> type);

    public abstract <R> void put(Class<R> type, R value);

    public abstract <R> void clear(Class<R> type);

    /**
     * Get the parsing bag associated with a given grammar source. The bag is
     * cached weakly and should live as long as the grammar source does. If this
     * is called with a new instance of GrammarSource which equals() one which
     * is still cached, that one will be returned, but the value returned from
     * source() will henceforth be this one.
     * <p>
     * If the grammar source has an associate Document (one is open in the
     * editor, or otherwise exists in memory), then the parsing bag will use
     * Document.putProperty() to store its data, so that expensive-to-create
     * information is cached and retrievable from the document. If not, an
     * instance backed by a map is created.
     * </p>
     *
     * @param <T> The type of source
     * @param source The source
     * @return A parsing bag, which may have been cached from previous uses
     */
    @SuppressWarnings("unchecked")
    public static synchronized <T> ParsingBag forGrammarSource(GrammarSource<T> source) {
        if (source == GrammarSource.NONE) {
            return new BlackHole();
        }
        ParsingBag result = INSTANCES.get(source);
        if (result == null) {
            Optional<Document> doc = source.lookup(Document.class);
            if (doc.isPresent()) {
                Document document = doc.get();
                result = new DocumentBag(source, document);
            } else {
                result = new NoDocumentBag(source);
            }
            INSTANCES.put(source, result);
        }
        if (result.source() != source) {
            result.setSource(source);
        }
        return result;
    }

    public static <T> ParsingBag get(T fileOrDocument, String mimeType) {
        return forGrammarSource(GrammarSource.find(fileOrDocument, mimeType));
    }

    private static final class DocumentBag extends ParsingBag {

        private final Document doc;

        DocumentBag(GrammarSource<?> source, Document doc) {
            super(source);
            this.doc = doc;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> R get(Class<R> type) {
            Object result = doc.getProperty(type);
            if (type.isInstance(result)) {
                return type.cast(result);
            } else if (result != null) {
                Logger.getLogger(NoDocumentBag.class.getName()).log(Level.WARNING,
                        "An object exists for the key {0} but it is not of that "
                        + "type: {1}: {2}",
                        new Object[]{type.getName(), result.getClass().getName(), result});
            }
            return null;
        }

        @Override
        public <R> void put(Class<R> type, R value) {
            doc.putProperty(type, value);
        }

        @Override
        public <R> void clear(Class<R> type) {
            doc.putProperty(type, null);
        }
    }

    private static final class NoDocumentBag extends ParsingBag {

        private final Map<Class<?>, Object> cache = new HashMap<>();

        NoDocumentBag(GrammarSource<?> source) {
            super(source);
        }

        @Override
        public synchronized <R> R get(Class<R> type) {
            Object result = cache.get(type);
            if (result == null) {
                return null;
            }
            if (type.isInstance(result)) {
                return type.cast(result);
            }
            Logger.getLogger(NoDocumentBag.class.getName()).log(Level.WARNING,
                    "An object exists for the key {0} but it is not of that "
                    + "type: {1}: {2}",
                    new Object[]{type.getName(), result.getClass().getName(), result});
            return null;
        }

        @Override
        public synchronized <R> void put(Class<R> type, R value) {
            assert value != null : "null value";
            assert type.isInstance(value);
            cache.put(type, value);
        }

        @Override
        public synchronized <R> void clear(Class<R> type) {
            cache.remove(type);
        }
    }

    static final class BlackHole extends ParsingBag {

        public BlackHole() {
            super(GrammarSource.NONE);
        }

        @Override
        public <R> R get(Class<R> type) {
            return null;
        }

        @Override
        public <R> void put(Class<R> type, R value) {
            // do nothing
        }

        @Override
        public <R> void clear(Class<R> type) {
            // do nothing
        }

    }
}
