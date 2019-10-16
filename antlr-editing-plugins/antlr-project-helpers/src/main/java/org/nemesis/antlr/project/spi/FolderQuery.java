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
package org.nemesis.antlr.project.spi;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.netbeans.api.project.Project;

/**
 * A query which can be used for a lookup strategy implementation to store hints
 * about where to look for files. One is passed to the factory that creates a
 * FoldersLookupStrategyImplementation to store basic information and things
 * that were expensive to compute. Another one is passed for each file query.
 *
 * @author Tim Boudreau
 */
public final class FolderQuery {

    private Project project;
    private Path relativeTo;
    private Set<String> hints;

    FolderQuery() {

    }

    @Override
    public String toString() {
        return "FolderQuery{" + "project=" + project + ", relativeTo=" + relativeTo + ", hints=" + hints + '}';
    }

    public boolean isEmpty() {
        return project == null && relativeTo == null
                && (hints == null || hints.isEmpty());
    }

    public FolderQuery updateFrom(FolderQuery other) {
        if (project == null) {
            project = other.project;
        }
        if (relativeTo == null) {
            relativeTo = other.relativeTo;
        }
        if (other.hints != null) {
            if (hints != null) {
                hints.addAll(other.hints);
            } else {
                this.hints = new HashSet<>(other.hints);
            }
        }
        return this;
    }

    public FolderQuery duplicate() {
        FolderQuery result = new FolderQuery();
        result.project = project;
        result.relativeTo = relativeTo;
        if (hints != null) {
            result.hints = new HashSet<>(hints);
        }
        return result;
    }

    public boolean get(String hint) {
        return hints == null ? false : hints.contains(hint);
    }

    public Project project() {
        return project;
    }

    public Path relativeTo() {
        return relativeTo;
    }

    public <T> T ifHasProject(Function<Project, T> c) {
        if (project != null) {
            return c.apply(project);
        }
        return null;
    }

    public <T> T ifHasRelativeFile(Function<Path, T> c) {
        if (relativeTo != null) {
            return c.apply(relativeTo);
        }
        return null;
    }

    public <T> T ifHasProjectAndRelativeFile(BiFunction<Path, Project, T> c) {
        if (project != null && relativeTo != null) {
            return c.apply(relativeTo, project);
        }
        return null;
    }

    public FolderQuery set(String hint) {
        if (hints == null) {
            hints = new HashSet<>(3);
        }
        hints.add(hint);
        return this;
    }

    public FolderQuery project(Project project) {
        this.project = project;
        return this;
    }

    public FolderQuery relativeTo(Path file) {
        this.relativeTo = file;
        return this;
    }

    static {
        FoldersHelperTrampoline.QUERY_SUPPLIER = FolderQuery::new;
        FoldersHelperTrampoline.SINGLE_ITERABLE_TEST = new IterableTest(true);
        FoldersHelperTrampoline.EMPTY_ITERABLE_TEST = new IterableTest(false);
        FoldersHelperTrampoline.EMPTY_ITERABLE = SingleIterable.EMPTY;
        FoldersHelperTrampoline.SINGLE_ITERABLE_FACTORY = SingleIterable::new;
    }

    private static final class IterableTest implements Predicate<Iterable<?>> {

        private final boolean single;

        public IterableTest(boolean single) {
            this.single = single;
        }

        @Override
        public boolean test(Iterable<?> t) {
            if (single) {
                if (t instanceof SingleIterable) {
                    return true;
                } else if (t == SingleIterable.EMPTY) {
                    return false;
                }
                Iterator<?> iter = t.iterator();
                if (iter.hasNext()) {
                    iter.next();
                    return (!iter.hasNext());
                } else {
                    return false;
                }
            } else {
                if (t == SingleIterable.EMPTY) {
                    return true;
                } else if (t instanceof SingleIterable) {
                    return false;
                } else {
                    return !t.iterator().hasNext();
                }
            }
        }

    }
}
