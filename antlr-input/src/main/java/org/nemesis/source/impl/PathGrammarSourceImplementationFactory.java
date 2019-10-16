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
package org.nemesis.source.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.nemesis.source.api.RelativeResolver;
import org.nemesis.source.spi.GrammarSourceImplementation;
import org.nemesis.source.spi.GrammarSourceImplementationFactory;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
// use Integer.MAX_VALUE so ones which check the project's encoding take precedence
@ServiceProvider(service=GrammarSourceImplementationFactory.class, position = Integer.MAX_VALUE)
public class PathGrammarSourceImplementationFactory extends GrammarSourceImplementationFactory<Path> {

    public PathGrammarSourceImplementationFactory() {
        super(Path.class);
    }

    @Override
    public GrammarSourceImplementation<Path> create(Path doc, RelativeResolver<Path> resolver) {
        return new PathSourceImpl(doc, resolver);
    }

    static final class PathSourceImpl extends GrammarSourceImplementation<Path> {

        private final Path source;
        private final RelativeResolver<Path> resolver;

        PathSourceImpl(Path path, RelativeResolver<Path> resolver) {
            super(Path.class);
            this.source = path;
            this.resolver = resolver;
        }

        @Override
        public String name() {
            return source().getFileName().toString();
        }

        @Override
        public CharStream stream() throws IOException {
            return CharStreams.fromPath(source);
        }

        @Override
        public GrammarSourceImplementation<?> resolveImport(String name) {
            Optional<Path> opt = resolver.resolve(source, name);
            if (opt.isPresent()) {
                return new PathSourceImpl(opt.get(), resolver);
            }
            return null;
        }

        @Override
        public Path source() {
            return source;
        }
    }
}
