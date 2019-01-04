package org.nemesis.antlr.v4.netbeans.v8.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristic.nameOf;

/**
 *
 * @author Tim Boudreau
 */
final class FileLocationHeuristicImpl implements FileLocationHeuristic {

    private final Function<Path, Optional<Path>> tester;
    static final Optional<Path> NO_PATH = Optional.empty();
    static final Predicate<Path> ALWAYS_SUCCEED = ignored -> false;

    FileLocationHeuristicImpl(Function<Path, Optional<Path>> tester) {
        this.tester = tester;
    }

    public String toString() {
        return getClass().getSimpleName() + "{" + tester + "}";
    }

    protected static boolean isNamed(Path path, String name) {
        if (path.getNameCount() == 0) {
            return File.separator.equals(name);
        }
        return name.equals(nameOf(path));
    }

    public Optional<Path> locate(Path relativeTo) {
        return scanParentFolders(relativeTo, tester, ALWAYS_SUCCEED);
    }

    static Predicate<Path> parentNamed(String name) {
        return path -> {
            return isNamed(path, name);
        };
    }

    public Optional<Path> locate(Path relativeTo, String stoppingAtParentNamed) {
        return locate(relativeTo, parentNamed(stoppingAtParentNamed));
    }

    public Optional<Path> locate(Path relativeTo, Path stoppingAt) {
        return locate(relativeTo, stoppingAt::equals);
    }

    public Optional<Path> locate(Path relativeTo, Predicate<Path> stoppingAt) {
        return scanParentFolders(relativeTo, tester, stoppingAt);
    }

    protected static Optional<Path> scanParentFolders(Path start, Function<Path, Optional<Path>> test, Predicate<Path> stoppingAt) {
        if (start == null) {
            return NO_PATH;
        }
        Path parent = start;
        if (!Files.isDirectory(start)) {
            parent = start.getParent();
        }
        do {
            Optional<Path> maybeFound = test.apply(parent);
//            System.out.println("  test " + parent + " gets " + maybeFound);
            if (maybeFound.isPresent()) {
                return maybeFound;
            }
            parent = parent.getParent();
//            System.out.println("next is " + parent + " with nc " + parent.getNameCount() + " stoppingAt " + stoppingAt);
            if (stoppingAt != null && stoppingAt.test(parent)) {
//                System.out.println("STOP AT " + parent);
                break;
            }
        } while (parent.getNameCount() > 0);
        return NO_PATH;
    }
}
