/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
@ServiceProvider(service=GrammarSourceImplementationFactory.class)
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

        public PathSourceImpl(Path path, RelativeResolver<Path> resolver) {
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
