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

    private static Path first(Iterable<Path> p) {
        if (FoldersHelperTrampoline.getDefault().isEmptyIterable(p)) {
            return null;
        }
        Iterator<Path> it = p.iterator();
        return it.hasNext() ? it.next() : null;
    }

    private static Path firstParent(Iterable<Path> p) {
        Path result = first(p);
        if (result != null) {
            result = result.getParent();
        }
        return result;
    }

    private static Path bestMatch(Iterable<Path> p, String matching) {
        if (FoldersHelperTrampoline.getDefault().isEmptyIterable(p)) {
            return null;
        }
        for (Path path : p) {
            if (path.getFileName().toString().contains(matching)) {
                return path;
            }
        }
        return first(p);
    }

    @Override
    public Path importDir() {
        return first(impl.find(Folders.ANTLR_IMPORTS, impl.initialQuery()));
    }

    @Override
    public Path antlrOutputDir() {
        // XXX, this may return annotation-processors subdir
        return bestMatch(impl.find(Folders.JAVA_GENERATED_SOURCES,
                impl.initialQuery()), "antlr");
    }

    @Override
    public Path sourceDir() {
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
