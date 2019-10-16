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
package org.nemesis.antlr.nbinput;

import java.io.IOException;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.nemesis.source.api.RelativeResolver;
import org.nemesis.source.spi.GrammarSourceImplementation;
import org.nemesis.source.spi.GrammarSourceImplementationFactory;
import org.openide.util.lookup.ServiceProvider;

/**
 * Allows GrammarSource instances to be created over non-file strings and char
 * streams for in-memory parsing of trivial things.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = GrammarSourceImplementationFactory.class)
public class CharStreamGrammarSourceFactory extends GrammarSourceImplementationFactory<CharStream> {

    public CharStreamGrammarSourceFactory() {
        super(CharStream.class);
    }

    @Override
    public GrammarSourceImplementation<CharStream> create(CharStream doc, RelativeResolver<CharStream> resolver) {
        return new StreamSource(doc);
    }

    static final class StreamSource extends GrammarSourceImplementation<CharStream> {

        private final CharStream stream;
        private final long lm = System.currentTimeMillis();

        public StreamSource(CharStream stream) {
            super(CharStream.class);
            this.stream = stream;
        }

        @Override
        public long lastModified() throws IOException {
            return lm;
        }

        @Override
        public String name() {
            return stream.getSourceName() == null
                    ? "<unnamed>" : stream.getSourceName();
        }

        @Override
        public CharStream stream() throws IOException {
            return source();
        }

        @Override
        public GrammarSourceImplementation<?> resolveImport(String name) {
            return null;
        }

        @Override
        public CharStream source() {
            return stream;
        }
    }

    @ServiceProvider(service = GrammarSourceImplementationFactory.class)
    public static class StringGrammarSourceFactory extends GrammarSourceImplementationFactory<String> {

        public StringGrammarSourceFactory() {
            super(String.class);
        }

        @Override
        public GrammarSourceImplementation<String> create(String doc, RelativeResolver<String> resolver) {
            return new StringGrammarSource(doc);
        }
    }

    private static class StringGrammarSource extends GrammarSourceImplementation<String> {

        private final String data;
        private final long lm = System.currentTimeMillis();

        StringGrammarSource(String data) {
            super(String.class);
            this.data = data;
        }

        @Override
        public String name() {
            return "<unnamed>";
        }

        @Override
        public long lastModified() throws IOException {
            return lm;
        }

        @Override
        public CharStream stream() throws IOException {
            return CharStreams.fromString(data);
        }

        @Override
        public GrammarSourceImplementation<?> resolveImport(String name) {
            return null;
        }

        @Override
        public String source() {
            return data;
        }

        public String toString() {
            String sub = data.length() < 15 ? data : data.substring(0, 15);
            return "string(\"" + sub + "\")";
        }
    }
}
