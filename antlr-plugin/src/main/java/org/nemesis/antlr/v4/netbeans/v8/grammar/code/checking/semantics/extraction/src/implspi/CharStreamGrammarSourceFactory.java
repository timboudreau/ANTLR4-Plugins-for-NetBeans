/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.implspi;

import java.io.IOException;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.RelativeResolver;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.GrammarSourceImplementation;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.GrammarSourceImplementationFactory;
import org.openide.util.lookup.ServiceProvider;

/**
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
            return "<unnamed>";
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
            return new StringGrammarSourceImpl(doc);
        }
    }

    private static class StringGrammarSourceImpl extends GrammarSourceImplementation<String> {

        private final String data;
        private final long lm = System.currentTimeMillis();

        StringGrammarSourceImpl(String data) {
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
