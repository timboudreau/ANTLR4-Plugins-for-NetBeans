package org.nemesis.antlr.v4.netbeans.v8.util;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristicImpl.NO_PATH;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class FileLocatorImpl implements FileLocator {

    private final Function<Path, Optional<Path>> initial;
    private final Predicate<Path> stopAt;
    private final FileLocationHeuristic heuristic;

    FileLocatorImpl(Function<Path, Optional<Path>> initial) {
        this(initial, null, null);
    }

    FileLocatorImpl(Function<Path, Optional<Path>> initial, Predicate<Path> stopAt, FileLocationHeuristic heuristic) {
        this.initial = initial;
        this.stopAt = stopAt;
        this.heuristic = heuristic;
    }

    public FileLocationHeuristic heuristic() {
        if (heuristic == null) {
            return FileLocationHeuristic.NOT_FOUND;
        }
        return heuristic;
    }

    public FileLocator stoppingAt(Predicate<Path> pred) {
        return new FileLocatorImpl(initial, stopAt == null ? pred : stopAt.or(pred), heuristic);
    }

    public Optional<Path> locate(Path relativeTo) {
        try {
            Optional<Path> result = initial.apply(relativeTo);
            if (!result.isPresent()) {
                if (stopAt != null) {
                    result = heuristic.locate(relativeTo, stopAt);
                } else {
                    result = heuristic.locate(relativeTo);
                }
            }
            return result;
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            return NO_PATH;
        }
    }

    public FileLocator withFallback(FileLocationHeuristic fallback) {
        if (heuristic == null) {
            return new FileLocatorImpl(initial, stopAt, fallback);
        } else {
            return new FileLocatorImpl(initial, stopAt, heuristic.or(heuristic));
        }
    }

    public FileLocator withHeuristicStoppingAt(Predicate<Path> stoppingAt) {
        return new FileLocatorImpl(initial, stopAt == null ? stoppingAt : stopAt.or(stoppingAt), heuristic);
    }

    public FileLocator withHeuristicStoppingAt(Path ancestor) {
        return withHeuristicStoppingAt(ancestor);
    }

    public FileLocator withHeuristicStoppingAtAncestorNamed(String name) {
        return withHeuristicStoppingAt(FileLocationHeuristic.fileName(name));
    }

}
