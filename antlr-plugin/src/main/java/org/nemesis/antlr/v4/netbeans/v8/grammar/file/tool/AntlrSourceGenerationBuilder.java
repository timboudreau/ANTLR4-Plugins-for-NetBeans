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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ParseProxyBuilder;

/**
 *
 * @author Tim Boudreau
 */
public interface AntlrSourceGenerationBuilder {

    /**
     * Add some additional paths to the Antlr library classpath, for loading
     * foreign classes.
     *
     * @param p The path
     * @param more Additional paths, if any
     * @return this
     */
    AntlrSourceGenerationBuilder addToClasspath(Path p, Path... more);

    /**
     * Run antlr and return a result that shows the output status, generated
     * files and any captured messages.
     *
     * @return A result
     * @throws IOException If something goes wrong
     */
    AntlrSourceGenerationResult build() throws IOException;

    AntlrSourceGenerationBuilder checkCancellationOn(AtomicBoolean bool);

    ParseProxyBuilder toParseAndRunBuilder();

    /**
     * Set the import (libDirectory in Antlr parlance) folder for finding
     * grammar files that are dependencies of the one being built.
     *
     * @param importDir The new import dir.
     * @return this
     */
    default AntlrSourceGenerationBuilder withImportDir(Optional<Path> importDir) {
        if (importDir.isPresent()) {
            return withImportDir(importDir.get());
        }
        return this;
    }
    /**
     * Set the import (libDirectory in Antlr parlance) folder for finding
     * grammar files that are dependencies of the one being built.
     *
     * @param importDir The new import dir.
     * @return this
     */
    AntlrSourceGenerationBuilder withImportDir(Path importDir);

    /**
     * Explicitly set the package name of generated java sources. By default,
     * the package name is a unique generated name for this run, so as to
     * clearly differentiate classes from a given run from those output by any
     * previous run (the should not escape from classloader isolation at all,
     * but this provides marginal additional protection from CCEs).
     *
     * @param pkg The package
     * @return this
     */
    AntlrSourceGenerationBuilder withPackage(String pkg);

    /**
     * Set some Antlr generation / runtime options.
     *
     * @param option An option to set
     * @param more More options to set
     * @return this
     */
    AntlrSourceGenerationBuilder withRunOptions(AntlrRunOption option, AntlrRunOption... more);

}
