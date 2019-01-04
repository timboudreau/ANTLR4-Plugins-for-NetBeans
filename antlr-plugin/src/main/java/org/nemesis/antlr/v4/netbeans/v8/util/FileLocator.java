package org.nemesis.antlr.v4.netbeans.v8.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristicImpl.NO_PATH;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileUtil;
import org.openide.util.Parameters;

/**
 * Combines a default location a file is expected to be in, and a series of
 * heuristics to locate files via relative paths if it is not found in the
 * default location.
 *
 * @author Tim Boudreau
 */
public interface FileLocator {

    FileLocator stoppingAt(Predicate<Path> path);

    Optional<Path> locate(Path relativeTo);

    default Optional<Path> locate(Path relativeTo, String childPath) {
        Optional<Path> base = locate(relativeTo);
        if (base.isPresent()) {
            Path child = base.get().resolve(Paths.get(childPath));
            if (Files.exists(child)) {
                return Optional.of(child);
            } else {
                return NO_PATH;
            }
        }
        return base;
    }

    FileLocator withFallback(FileLocationHeuristic fallback);

    FileLocator withHeuristicStoppingAt(Predicate<Path> stoppingAt);

    FileLocator withHeuristicStoppingAt(Path ancestor);

    FileLocator withHeuristicStoppingAtAncestorNamed(String name);

    default FileLocator stoppingAtProjectRoot() {
        return stoppingAt((Path path) -> {
            Project project = FileOwnerQuery.getOwner(FileUtil.toFileObject(
                    FileUtil.normalizeFile(path.toFile())));
            if (project != null) {
                Path projectDir = FileUtil.toFile(project.getProjectDirectory()).toPath();
                if (projectDir.equals(path)) {
                    return true;
                }
            }
            return false;
        });
    }

    static FileLocator create(Function<Path, Optional<Path>> initial) {
        return new FileLocatorImpl(initial);
    }

    static <T extends Enum<T>> InitialFileLocatorMappingBuilder<T> map(T what) {
        Parameters.notNull("what", what);
        return new InitialFileLocatorMappingBuilder<>(what);
    }

    public static abstract class FileLocatorMappingBuilder<T extends Enum<T>> {

        final EnumMap<T, FileLocator> mapping;
        T current;

        FileLocatorMappingBuilder(EnumMap<T, FileLocator> mapping) {
            Parameters.notNull("mapping", mapping);
            this.mapping = new EnumMap<>(mapping);
        }

        FileLocatorMappingBuilder(EnumMap<T, FileLocator> mapping, T current) {
            Parameters.notNull("mapping", mapping);
            Parameters.notNull("current", current);
            this.mapping = new EnumMap<>(mapping);
            this.current = current;
        }

        FileLocatorMappingBuilder(T initial) {
            Parameters.notNull("initial", initial);
            this.current = initial;
            this.mapping = new EnumMap<>(initial.getDeclaringClass());
        }

    }

    public static class InitialFileLocatorMappingBuilder<T extends Enum<T>> extends FileLocatorMappingBuilder<T> {

        InitialFileLocatorMappingBuilder(EnumMap<T, FileLocator> mapping, T current) {
            super(mapping, current);
        }

        InitialFileLocatorMappingBuilder(T initial) {
            super(initial);
        }

        public BuildableFileLocatorMappingBuilder<T> to(FileLocator loc) {
            mapping.put(current, loc);
            return new BuildableFileLocatorMappingBuilder<>(mapping);
        }
    }

    public static final class BuildableFileLocatorMappingBuilder<T extends Enum<T>>
            extends FileLocatorMappingBuilder<T> {

        BuildableFileLocatorMappingBuilder(EnumMap<T, FileLocator> mapping) {
            super(new EnumMap<>(mapping));
        }

        public InitialFileLocatorMappingBuilder<T> map(T what) {
            if (mapping.containsKey(what)) {
                throw new IllegalArgumentException("A mapping is already"
                        + " set up for " + what + ": " + mapping.get(what));
            }
            return new InitialFileLocatorMappingBuilder<>(mapping, what);
        }

        public Map<T, FileLocator> build() {
            return new EnumMap<>(mapping);
        }
    }
}
