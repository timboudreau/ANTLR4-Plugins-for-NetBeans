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
package org.nemesis.antlr.project.impl;

import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.util.Iterator;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.project.spi.AntlrConfigurationImplementation;

/**
 *
 * @author Tim Boudreau
 */
class HeuristicAntlrConfigurationImplementation implements AntlrConfigurationImplementation {

    private final HeuristicFoldersHelperImplementation impl;

    public HeuristicAntlrConfigurationImplementation(HeuristicFoldersHelperImplementation impl) {
        this.impl = impl;
    }

    @Override
    public boolean atn() {
        return false;
    }

    @Override
    public Charset encoding() {
        // XXX if a project, could guess using FileEncodingQuery
        return UTF_8;
    }

    @Override
    public String excludePattern() {
        return "";
    }

    @Override
    public boolean forceATN() {
        return false;
    }

    @Override
    public String includePattern() {
        return "**/*.g4";
    }

    @Override
    public boolean listener() {
        return true;
    }

    @Override
    public boolean visitor() {
        return true;
    }

    private Path first(Iterable<Path> p) {
        if (FoldersHelperTrampoline.getDefault().isEmptyIterable(p)) {
            return null;
        }
        Iterator<Path> it = p.iterator();
        if (it.hasNext()) {
            Path result = p.iterator().next();
            if (!result.isAbsolute()) {
                throw new IllegalStateException(impl + " returning a non-absolute path: " + p);
            }
            return result;
        }
        return null;
    }

    private Path firstParent(Iterable<Path> p) {
        Path result = first(p);
        if (result != null) {
            result = result.getParent();
        }
        return result;
    }

    private Path bestMatch(Iterable<Path> p, String matching) {
        if (FoldersHelperTrampoline.getDefault().isEmptyIterable(p)) {
            return null;
        }
        for (Path path : InferredConfig.depthFirst(p)) {
            if (path.getFileName().toString().contains(matching)) {
                return path;
            }
        }
        return first(p);
    }

    @Override
    public Path antlrImportDir() {
        return first(impl.find(Folders.ANTLR_IMPORTS, impl.initialQuery()));
    }

    @Override
    public Path antlrOutputDir() {
        // XXX, this may return annotation-processors subdir
        return bestMatch(impl.find(Folders.JAVA_GENERATED_SOURCES,
                impl.initialQuery()), "antlr");
    }

    @Override
    public Path antlrSourceDir() {
        return first(impl.find(Folders.ANTLR_GRAMMAR_SOURCES, impl.initialQuery()));
    }

    @Override
    public Path buildDir() {
        return firstParent(impl.find(Folders.CLASS_OUTPUT, impl.initialQuery()));
    }

    @Override
    public boolean isGuessedConfig() {
        return true;
    }

    @Override
    public Path buildOutput() {
        return first(impl.find(Folders.CLASS_OUTPUT, impl.initialQuery()));
    }

    @Override
    public Path testOutput() {
        return null;
    }

    @Override
    public Path javaSources() {
        return first(impl.find(Folders.JAVA_SOURCES, impl.initialQuery()));
    }

    @Override
    public Path testSources() {
        return null;
    }
}
