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

import com.mastfrog.graph.dynamic.DynamicGraph;
import org.nemesis.antlr.common.cachefile.CacheFileUtils;
import com.mastfrog.function.throwing.io.IOSupplier;
import com.mastfrog.util.collections.CollectionUtils;
import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.common.ShutdownHooks;
import org.openide.modules.Places;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakSet;

/**
 * Registry for listening for changes in project Antlr configuration, where icon
 * badging, actions or other things should be recomputed. Note, the
 * implementation does NOT listen on files - just pick up notifications when
 * something explicitly modifies a file, necessitating a reload, and when some
 * other part of the system notices a project folder has been deleted.
 * <p>
 * Maintains a graph of project dependencies discovered during project helper
 * build file parsing. New implementations for other project types should add
 * their dependencies. The graph is persistently cached and checked against file
 * dates to determine validity of cached data.
 * <p>
 * What is actually cached is very lightweight, amounting to a list of Path
 * objects and a few arrays of BitSets.
 *
 * @author Tim Boudreau
 */
public final class ProjectUpdates {

    // so it can be replaced in tests
    static IOSupplier<ProjectUpdates> instanceSupplier = () -> new ProjectUpdates(true);
    static ProjectUpdates INSTANCE;
    private static final Map<Path, Set<Consumer<? super Path>>> LISTENERS = Collections.synchronizedMap(CollectionUtils.supplierMap(WeakSet::new));
    private static final Set<Consumer<? super Path>> LISTENERS_TO_ALL = new WeakSet<>();
    private static final Logger LOG = Logger.getLogger(ProjectUpdates.class.getName());
    private DynamicGraph<Path> paths;
    private final Path path;
    private final RequestProcessor.Task saveTask;
    private static final int SAVE_DELAY = 10000;
    private final Saver saver = new Saver();
    private static final String FILE_NAME = "antlr-project-dependencies.cache";

    ProjectUpdates(boolean loadCache) throws IOException {
        this(loadCache ? Places.getCacheSubfile(FILE_NAME).toPath() : null);
    }

    ProjectUpdates() {
        paths = new DynamicGraph<>();
        path = null;
        saveTask = null;
    }

    ProjectUpdates(Path path) throws IOException {
        this.path = path;
        if (path == null || !Files.exists(path)) {
            paths = new DynamicGraph<>();
        } else {
            try (FileChannel channel = FileChannel.open(path, READ)) {
                if (channel.size() > 0) {
                    paths = DynamicGraph.load(channel, CacheFileUtils::readPath);
                }
            } catch (IOException | BufferUnderflowException ioe) {
                LOG.log(Level.INFO, "Corrupted dependency graph cache in " + path+ ". Deleting.", ioe);
                try {
                    Files.delete(path);
                } catch (IOException ex) {
                    LOG.log(Level.INFO, "Corrupted dependency graph cache in " + path+ " failed.", ex);
                }
                paths = new DynamicGraph<>();
            }
        }
        if (path != null) {
            saveTask = RequestProcessor.getDefault().create(saver);
            ShutdownHooks.addRunnable(saveTask::cancel);
            ShutdownHooks.addWeakRunnable(saveTask);
        } else {
            saveTask = null;
        }
        if (paths == null) {
            paths = new DynamicGraph();
        }
    }

    public boolean has(Path path) {
        return graph().contains(path);
    }

    private static ProjectUpdates instance() {
        if (INSTANCE == null) {
            try {
                INSTANCE = instanceSupplier.get();
            } catch (IOException ex) {
                File file = Places.getCacheSubfile(FILE_NAME);
                LOG.log(Level.INFO, "Exception reading project dependencies cache.  Deleting " + file, ex);
                if (file != null && file.exists()) {
                    file.delete();
                }
                try {
                    INSTANCE = new ProjectUpdates(true);
                } catch (IOException ex1) {
                    LOG.log(Level.INFO, "Exception NOT reading project "
                            + "dependencies cache.", ex1);
                    INSTANCE = new ProjectUpdates();
                }
            }
        }
        return INSTANCE;
    }

    private DynamicGraph<Path> _graph() {
        return paths;
    }

    static DynamicGraph<Path> graph() {
        return instance()._graph();
    }

    private void dirty() {
        if (saveTask != null) {
            saveTask.schedule(SAVE_DELAY);
        }
    }

    public static void pathDependencies(Path dependee, Path depender) {
        ProjectUpdates inst = instance();
        if (inst._graph().addEdge(depender, dependee)) {
            inst.dirty();
        }
    }

    static void fileDeleted(Path file) {
        ProjectUpdates inst = instance();
        DynamicGraph<Path> graph = inst._graph();
        if (graph.removeAllReferencesTo(file)) {
            inst.dirty();
        }
    }

    static Set<Path> directDependencies(Path path) {
        return Collections.unmodifiableSet(graph().parents(path));
    }

    static Set<Path> dependersOn(Path path) {
        return Collections.unmodifiableSet(graph().reverseClosureOf(path));
    }

    static Set<Path> dependenciesOf(Path path) {
        return Collections.unmodifiableSet(graph().closureOf(path));
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
        LISTENERS.get(path).add(r);
    }

    static final Set<Path> currentlyNotifying = ConcurrentHashMap.newKeySet(32);

    @SuppressWarnings("NestedSynchronizedStatement")
    public static synchronized void notifyPathChanged(Path changedPath) {
        // Ensure that in the case of reentrancy, only one notification
        // is sent
        if (currentlyNotifying.contains(changedPath)) {
            return;
        }
        Set<Path> deps = dependersOn(changedPath);
        Set<Path> allPathsToNotify = new LinkedHashSet<>();
        allPathsToNotify.add(changedPath);
        allPathsToNotify.addAll(deps);
        allPathsToNotify.removeAll(currentlyNotifying);
        if (allPathsToNotify.isEmpty()) {
            return;
        }
        currentlyNotifying.addAll(allPathsToNotify);
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

    private class Saver implements Runnable {

        @Override
        public void run() {
            try {
                try (FileChannel channel = FileChannel.open(path, WRITE, TRUNCATE_EXISTING, CREATE)) {
                    paths.store(channel, CacheFileUtils::writePath);
                }
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Exception saving cache", ex);
            }
        }
    }
}
