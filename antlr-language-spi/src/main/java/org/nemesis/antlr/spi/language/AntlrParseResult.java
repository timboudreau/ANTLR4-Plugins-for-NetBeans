/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.spi.language;

import java.io.IOException;
import org.nemesis.antlr.spi.language.fix.Fixes;
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
    private final List<ErrorDescription> addedErrorDescriptions = new ArrayList<>(25);
    private static volatile long IDS;
    private final long id = IDS++;
    private final NbLexerAdapter<?,?> adapter;
    private NbParserHelper helper;
    private volatile boolean wasInvalidated;

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
                .append(", errors=").append(syntaxErrors)
                .append(", wasInvalidated=").append(wasInvalidated);
        return sb.append('}').toString();
    }

    @Override
    public Extraction extraction() {
        return extraction;
    }

    /**
     * Determine if this parse result is being used within the closure
     * of the call to ParserManager that created it.  This is needed,
     * for example, for the Antlr live editor to determine if it is going
     * to be passed a new parser result via ParserResultHook, or if it needs to
     * call itself with the result, since it depends on triggering a
     * reparse to update subscribers.
     *
     * @return True if invalidate() was called by the parsing infrastructure
     */
    public boolean wasInvalidated() {
        return wasInvalidated;
    }

    @Override
    protected void invalidate() {
        wasInvalidated = true;
//        syntaxErrors.clear();
//        pairs.clear();
//        extraction.dispose();
//        helper = null;
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

        @Override
        Fixes fixes() throws IOException {
            return Fixes.create(extraction, this);
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
