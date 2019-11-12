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
package org.nemesis.antlr.projectupdatenotificaton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.WeakSet;

/**
 * Registry for listening for changes in project Antlr configuration, where icon
 * badging, actions or other things should be recomputed.
 *
 * @author Tim Boudreau
 */
public final class ProjectUpdates {

    private static final Map<Path, Set<Consumer<? super Path>>> LISTENERS = new HashMap<>();
    private static final Logger LOG = Logger.getLogger(ProjectUpdates.class.getName());
    private static final Set<Consumer<? super Path>> LISTENERS_TO_ALL = new WeakSet<>();
    private static final Map<Path, Set<Path>> DEPENDENCIES = new HashMap<>();

    public static void pathDependencies(Path whenChanged, Path alsoNotify) {
        Set<Path> toUpdate;
        synchronized (DEPENDENCIES) {
            toUpdate = DEPENDENCIES.get(whenChanged);
            if (toUpdate == null) {
                toUpdate = ConcurrentHashMap.newKeySet(3);
                DEPENDENCIES.put(whenChanged, toUpdate);
            }
        }
        toUpdate.add(alsoNotify);
    }

    private static void dependencyTreeUnsafe(Path path, Set<Path> result) {
        assert Thread.holdsLock(DEPENDENCIES);
        Set<Path> deps = DEPENDENCIES.get(path);
        if (deps != null) {
            for (Path p : deps) {
                if (!result.contains(p)) { // ensure we can't loop endlessly with a circular dependency
                    dependencyTreeUnsafe(p, result);
                }
            }
        }
    }

    private static void dependencyTree(Path path, Set<Path> result) {
        synchronized (DEPENDENCIES) {
            dependencyTreeUnsafe(path, result);
        }
    }

    static Set<Path> dependencies(Path path) {
        Set<Path> res = new LinkedHashSet<>(5);
        dependencyTree(path, res);
        return res;
    }

    /**
     * Subscribe to changes in ANY project. The passed consumer will be weakly
     * referenced and must be strongly referenced from the caller's end to
     * receive notifications. Notifications will be for the project directory.
     *
     * @param r A consumer
     */
    public static void subscribeToChanges(Consumer<Path> r) {
        synchronized (LISTENERS_TO_ALL) {
            LISTENERS_TO_ALL.add(r);
        }
    }

    /**
     * Subscribe to changes in a specific project.The passed consumer will be
     * weakly referenced and must be strongly referenced from the caller's end
     * to receive notifications. Notifications will be for the project
     * directory.
     *
     * @param path The path
     * @param r The consumer which would like to receive notifications
     */
    public static void subscribeToChanges(Path path, Consumer<Path> r) {
        path = path.toAbsolutePath();
        if (Files.exists(path) && !Files.isDirectory(path)) {
            throw new IllegalArgumentException("Subscribe to changes by project "
                    + "*directory* not build file or other file such as " + path);
        }
        Set<Consumer<? super Path>> all;
        synchronized (LISTENERS) {
            all = LISTENERS.get(path);
            if (all == null) {
                all = new WeakSet<>();
                LISTENERS.put(path, all);
            }
        }
        all.add(r);
    }

    static Set<Path> currentlyNotifying = new HashSet<>();

    @SuppressWarnings("NestedSynchronizedStatement")
    public static synchronized void notifyPathChanged(Path changedPath) {
        // Ensure that in the case of reentrancy, only one notification
        // is sent
        if (currentlyNotifying.contains(changedPath)) {
            return;
        }
        Set<Path> deps = dependencies(changedPath);
        Set<Path> allPathsToNotify = new LinkedHashSet<>();
        allPathsToNotify.add(changedPath);
        allPathsToNotify.addAll(deps);
        allPathsToNotify.removeAll(currentlyNotifying);
        currentlyNotifying.addAll(allPathsToNotify);
        if (allPathsToNotify.isEmpty()) {
            return;
        }
        LOG.log(Level.FINE, "Notify project changed {0} will notify listeners for {1}",
                new Object[]{changedPath, allPathsToNotify});
        try {
            Set<Consumer<? super Path>> all;
            for (Path path : allPathsToNotify) {
                synchronized (LISTENERS) {
                    Set<Consumer<? super Path>> toNotify = LISTENERS.get(path);
                    if (toNotify == null) {
                        all = new LinkedHashSet<>(5);
                    } else {
                        all = new LinkedHashSet<>(toNotify);
                    }
                }
                synchronized (LISTENERS_TO_ALL) {
                    all.addAll(LISTENERS_TO_ALL);
                }
                LOG.log(Level.FINEST, "Will notify {0} listeners for {1}",
                        new Object[]{all.size(), path});
                for (Consumer<? super Path> r : all) {
                    try {
                        r.accept(path);
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE,
                                "Exception notifying changes in " + path, e);
                    }
                }
                for (Path p : deps) {
                    notifyPathChanged(p);
                }
            }
        } finally {
            currentlyNotifying.removeAll(allPathsToNotify);
        }
    }

    private ProjectUpdates() {
        throw new AssertionError();
    }
}
