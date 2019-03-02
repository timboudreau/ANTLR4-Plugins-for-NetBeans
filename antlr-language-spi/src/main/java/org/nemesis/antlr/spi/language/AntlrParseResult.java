package org.nemesis.antlr.spi.language;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.hints.ErrorDescription;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrParseResult extends Parser.Result implements ExtractionParserResult, Iterable<SyntaxError> {

    private final Map<Key<?>, Object> pairs = new HashMap<>();
    private final Extraction extraction;
    private final List<SyntaxError> syntaxErrors = new ArrayList<>();
    private BiFunction<Snapshot, SyntaxError, ErrorDescription> errorConverter;
    private final List<ErrorDescription> addedErrorDescriptions = new ArrayList<>(5);
    private static volatile long IDS;
    private final long id = IDS++;
    private final NbLexerAdapter<?,?> adapter;
    private NbParserHelper helper;

    AntlrParseResult(NbLexerAdapter<?,?> adapter, Snapshot _snapshot, Extraction extraction, Consumer<ParseResultContents> inputConsumer) {
        super(_snapshot);
        this.adapter = adapter;
        this.extraction = extraction;
        inputConsumer.accept(input());
    }

    @Override
    public Iterator<SyntaxError> iterator() {
        return syntaxErrors.iterator();
    }

    /**
     * Get the syntax errors encountered during lexing.
     *
     * @see getErrorDescriptions()
     * @return A list of syntax errors
     */
    public List<? extends SyntaxError> syntaxErrors() {
        return Collections.unmodifiableList(syntaxErrors);
    }

    /**
     * Get the syntax errors in a form suitable for use in the editor.
     *
     * @return A list of error descriptions
     */
    public List<? extends ErrorDescription> getErrorDescriptions() {
        List<ErrorDescription> result = new ArrayList<>(getErrorDescriptions(errorConverter));
        if (!addedErrorDescriptions.isEmpty()) {
            result.addAll(addedErrorDescriptions);
        }
        return result;
    }

    /**
     * Get the syntax errors in a form suitable for use in the editor,
     * optionally converting them using the passed function.
     *
     * @param converter A conversion function
     * @return A list of error descriptions
     */
    private List<? extends ErrorDescription> getErrorDescriptions(BiFunction<Snapshot, SyntaxError, ErrorDescription> converter) {
        List<ErrorDescription> result = new ArrayList<>(syntaxErrors.size());
        Snapshot snapshot = super.getSnapshot();
        for (SyntaxError s : syntaxErrors) {
            ErrorDescription ed = s.toErrorDescription(snapshot, adapter, helper);
            if (ed != null) {
                result.add(ed);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AntlrParseResult{");
        sb.append(id).append(" ");
        sb.append("pairs=").append(pairs.size()).append(", snapshot=")
                .append(getSnapshot()).append(", ")
                .append("extraction=").append(extraction.logString())
                .append(", errors=").append(syntaxErrors);
        return sb.append('}').toString();
    }

    @Override
    public Extraction extraction() {
        return extraction;
    }

    @Override
    protected void invalidate() {
//        syntaxErrors.clear();
//        pairs.clear();
//        extraction = null;
    }

    public static final <T> Key<T> key(String name, Class<T> type) {
        return new Key<>(name == null ? type.getName() : name, type);
    }

    public <T> Optional<T> get(Key<T> key) {
        Object result = pairs.get(key);
        if (result == null) {
            return Optional.empty();
        }
        return Optional.of(key.type().cast(result));
    }

    ParseResultContents input() {
        return new ParseResultInput();
    }

    public static abstract class ParseResultContents {

        private ParseResultContents() {
        }

        public final <T> Optional<T> get(Key<T> key) {
            return _get(key);
        }

        public abstract ParseResultContents addErrorDescription(ErrorDescription err);

        abstract void setSyntaxErrors(List<? extends SyntaxError> errors, NbParserHelper helper);

        abstract void addSyntaxError(SyntaxError err) ;

        abstract <T> Optional<T> _get(Key<T> key);

        public final <T> ParseResultContents put(Key<T> key, T obj) {
            if (obj != null) {
                if (!key.type().isInstance(obj)) {
                    throw new ClassCastException("Value is not an instance of "
                            + "key's type: " + key.type().getName()
                            + " vs " + obj.getClass().getName()
                            + " (" + obj + ")");
                }
                _put(key, obj);
            }
            return this;
        }

        abstract <T> void _put(Key<T> key, T obj);
    }

    /**
     * A (mostly) write-only input to the parser result, so it has no
     * mutator methods - this is passed into the parser helper for it to
     * write additional data to be consumed by things that want to
     * provide input to ui components such as error highlighting or
     * navigator panels.
     */
    final class ParseResultInput extends ParseResultContents {

        @Override
        <T> Optional<T> _get(Key<T> key) {
            return AntlrParseResult.this.get(key);
        }

        @Override
        <T> void _put(Key<T> key, T obj) {
            pairs.put(key, obj);
        }

        @Override
        void setSyntaxErrors(List<? extends SyntaxError> errors, NbParserHelper helper) {
            syntaxErrors.addAll(errors);
            AntlrParseResult.this.helper = helper;
        }

        @Override
        public final String toString() {
            return AntlrParseResult.this.toString();
        }

        @Override
        public final ParseResultContents addErrorDescription(ErrorDescription err) {
            addedErrorDescriptions.add(err);
            return this;
        }

        @Override
        void addSyntaxError(SyntaxError err) {
            syntaxErrors.add(err);
        }
    }

    public static final class Key<T> {

        private final String name;
        private final Class<T> type;

        private Key(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }

        Class<T> type() {
            return type;
        }

        String name() {
            return name;
        }

        public String toString() {
            if (name.equals(type.getName())) {
                return "<" + type.getSimpleName() + ">";
            }
            return name + "<" + type.getSimpleName() + ">";
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 53 * hash + Objects.hashCode(this.name);
            hash = 53 * hash + Objects.hashCode(this.type);
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
            final Key<?> other = (Key<?>) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return Objects.equals(this.type, other.type);
        }
    }
}
