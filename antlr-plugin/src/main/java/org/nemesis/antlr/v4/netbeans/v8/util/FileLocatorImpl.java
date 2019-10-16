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
