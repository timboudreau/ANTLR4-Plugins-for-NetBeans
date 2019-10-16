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
package org.nemesis.antlr.v4.netbeans.v8.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristicImpl.ALWAYS_SUCCEED;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristicImpl.NO_PATH;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristicImpl.isNamed;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristicImpl.parentNamed;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileUtil;

/**
 * Strategies for locating files by relative position when, for one
 * reason or another, nothing works.
 *
 * @author Tim Boudreau
 */
public interface FileLocationHeuristic {

    static FileLocationHeuristic NOT_FOUND = (Path relativeTo, Predicate<Path> stoppingAt) -> {
        return NO_PATH;
    };

    default Optional<Path> locate(Path relativeTo) {
        return locate(relativeTo, ALWAYS_SUCCEED);
    }

    default Optional<Path> locate(Path relativeTo, Path stoppingAtParent) {
        return locate(relativeTo, Predicate.isEqual(stoppingAtParent));
    }

    default Optional<Path> locate(Path relativeTo, String stoppingAtParentNamed) {
        return locate(relativeTo, parentNamed(stoppingAtParentNamed));
    }

    Optional<Path> locate(Path relativeTo, Predicate<Path> stoppingAt);

    static FileLocationHeuristic parentOfTestedFile() {
        return (Path relativeTo, Predicate<Path> stoppingAt) -> {
            return Optional.of(relativeTo.getParent());
        };
    }

    default FileLocationHeuristic or(FileLocationHeuristic heur) {
        return (Path relativeTo, Predicate<Path> stoppingAt) -> {
            Optional<Path> result = FileLocationHeuristic.this.locate(relativeTo, stoppingAt);
            if (!result.isPresent()) {
                result = heur.locate(relativeTo);
            }
            return result;
        };
    }

    default FileLocationHeuristic and(FileLocationHeuristic heur) {
        return (Path relativeTo, Predicate<Path> stoppingAt) -> {
            Optional<Path> first = FileLocationHeuristic.this.locate(relativeTo, stoppingAt);
            if (first.isPresent()) {
                Optional<Path> second = heur.locate(relativeTo, stoppingAt);
                if (second.isPresent() && first.get().equals(second)) {
                    return second;
                }
            }
            return NO_PATH;
        };
    }

    default FileLocationHeuristic unless(Predicate<Path> test) {
        return (Path relativeTo, Predicate<Path> stoppingAt) -> {
            Optional<Path> result = FileLocationHeuristic.this.locate(relativeTo, stoppingAt);
            if (result.isPresent() && !test.test(result.get())) {
                return result;
            }
            return NO_PATH;
        };
    }

    static FileLocationHeuristic tempDir() {
        return (Path relativeTo, Predicate<Path> stoppingAt) -> {
            File tmp = new File(System.getProperty("java.io.tmpdir"));
            File antlrTmp = new File(tmp, "nb-antlr-output");
            File dir = new File(antlrTmp, relativeTo.getParent().toString().replaceAll("\\" + File.separator, "_"));
            dir.mkdirs();
            return Optional.of(dir.toPath());
        };
    }

    static FileLocationHeuristic parentIsProjectRoot() {
        return (Path relativeTo, Predicate<Path> stoppingAt) -> {
            Project project = FileOwnerQuery.getOwner(FileUtil.toFileObject(FileUtil.normalizeFile(relativeTo.toFile())));
            if (project != null) {
                if (relativeTo.equals(FileUtil.toFile(project.getProjectDirectory()).toPath())) {
                    return Optional.of(relativeTo);
                }
            }
            return NO_PATH;
        };
    }

    static Predicate<Path> fileName(String name) {
        return path -> {
            return isNamed(path, name);
        };
    }

    public static ForwardScanHeuristic withChildPath(String path) {
        return new ForwardScanHeuristicImpl(path);
    }

    public static String nameOf(Path path) {
        return path.getNameCount() > 0 ? path.getName(path.getNameCount() - 1).toString() : "";
    }

    public static FileLocationHeuristic ancestorNameContains(String partialName) {
        return new FileLocationHeuristicImpl(path -> {
            String name = nameOf(path);
            boolean test = name.contains(partialName) || name.equals(partialName);
            if (test) {
                return Optional.of(path);
            }
            return NO_PATH;
        });
    }

    public static FileLocationHeuristic ancestorNamed(String name) {
        return new FileLocationHeuristicImpl(path -> {
            boolean test = isNamed(path, name);
            if (test) {
                return Optional.of(path);
            }
            return NO_PATH;
        });
    }

    public static FileLocationHeuristic ancestorWithChildNamed(String name) {
        return new FileLocationHeuristicImpl(path -> {
            Path test = path.resolve(name);
            if (Files.exists(test)) {
                return Optional.of(path);
            }
            return NO_PATH;
        });
    }

    public static FileLocationHeuristic childOfAncestorNamed(String name) {
        return new FileLocationHeuristicImpl(path -> {
            Path test = path.resolve(name);
            if (Files.exists(test)) {
                return Optional.of(test);
            }
            return NO_PATH;
        });
    }
}
