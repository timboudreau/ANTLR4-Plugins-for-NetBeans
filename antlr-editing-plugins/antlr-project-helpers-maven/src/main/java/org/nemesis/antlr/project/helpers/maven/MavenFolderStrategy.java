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
package org.nemesis.antlr.project.helpers.maven;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.project.spi.AntlrConfigurationImplementation;
import org.nemesis.antlr.project.spi.FolderLookupStrategyImplementation;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.nemesis.antlr.projectupdatenotificaton.ProjectUpdates;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.Places;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
final class MavenFolderStrategy implements FolderLookupStrategyImplementation {

    private static final Logger LOG = Logger.getLogger(MavenFolderStrategy.class.getName());
    static final String MAVEN = "Maven";
    private final Project project;
    private static final Map<Path, MavenInfo> PROJECT_INFO
            = new ConcurrentHashMap<>();
    private static volatile boolean listening;
    private final FolderQuery initialQuery;
    static final Consumer<Path> LISTENER = path -> {
        try {
            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
            Project prj = ProjectManager.getDefault().findProject(fo);
            if (prj != null) {
                LOG.log(Level.INFO, "Drop cached info for {0}", path);
                PROJECT_INFO.remove(path);
                new Exception("Drop info for " + path).printStackTrace();
            }
        } catch (IOException ioe) {
            Logger.getLogger(MavenFolderStrategy.class.getName()).log(Level.SEVERE,
                    "Updates for " + path, ioe);
        }
        loadCache();
    };

    private static Path pathForProject(Project prj) {
        FileObject fo = prj.getProjectDirectory();
        File file = FileUtil.toFile(fo);
        if (file != null) {
            return file.toPath();
        }
        return null;
    }

    MavenFolderStrategy(Project project, FolderQuery initialQuery) {
        this.project = project;
        this.initialQuery = initialQuery;
    }

    MavenInfo info() {
        return infoForProject(project);
    }

    static boolean cacheLoaded;

    static synchronized void loadCache() {
        if (!cacheLoaded) {
            cacheLoaded = true;
            try {
                SerCache cache = SerCache.load();
                if (cache != null && !cache.isEmpty()) {
                    LOG.log(Level.INFO, "Loaded maven cache {0}", cache);
                    MavenInfo.putKnownNonAntlr(cache.knownNonAntlr);
                    for (Map.Entry<String, MavenInfo> e : cache.projectInfo.entrySet()) {
                        Path path = Paths.get(e.getKey());
                        if (Files.exists(path)) {
                            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(Paths.get(e.getKey()).toFile()));
                            if (fo != null) {
                                Project prj = ProjectManager.getDefault().findProject(fo);
                                if (prj != null) {
                                    PROJECT_INFO.put(path, e.getValue());
                                }
                            }
                        }
                    }
                    LOG.log(Level.INFO, "Cache now {0}", PROJECT_INFO);
                } else {
                    LOG.log(Level.INFO, "No or empty maven cache {0}", cache);
                }
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(MavenFolderStrategy.class.getName())
                        .log(Level.INFO, "Could not load cache", ex);
            }
        }
    }

    static void saveCache() {
        SerCache sc = new SerCache();
        if (!sc.isEmpty()) {
            try {
                sc.save();
                LOG.log(Level.INFO, "Saved maven cache {0}", sc);
            } catch (IOException ex) {
                Logger.getLogger(MavenFolderStrategy.class.getName())
                        .log(Level.INFO, "Could not load cache", ex);
            }
        }
    }
    private static final RequestProcessor.Task SAVE_TASK
            = RequestProcessor.getDefault().create(MavenFolderStrategy::saveCache);
    private static final int SAVE_TASK_DELAY_MS = 20000;

    static void scheduleCacheSave() {
        SAVE_TASK.schedule(SAVE_TASK_DELAY_MS);
    }

    static class SerCache implements Serializable {

        final Map<String, MavenInfo> projectInfo;
        final Map<String, Long> knownNonAntlr = MavenInfo.knownNonAntlrProjects();
        static final long serialVersionUID = -4558997829579415276L;
        static final String CACHE_FILE = "maven-antlr-project-cache.ser";

        SerCache() {
            projectInfo = new HashMap<>(PROJECT_INFO.size());
            for (Map.Entry<Path, MavenInfo> e : PROJECT_INFO.entrySet()) {
                if (Files.exists(e.getKey())) {
                    projectInfo.put(e.getKey().toString(), e.getValue());
                }
            }
        }

        @Override
        public String toString() {
            return projectInfo.size() + " cached project infos and "
                    + knownNonAntlr.size() + " known-non-antlr-projects";
        }

        boolean isEmpty() {
            return projectInfo.isEmpty() && knownNonAntlr.isEmpty();
        }

        void save() throws IOException {
            Path path = new File(Places.getCacheDirectory(), CACHE_FILE).toPath();
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
                out.writeObject(this);
            }
            LOG.log(Level.INFO, "Saved cache to {0}", path);
        }

        static SerCache load() throws IOException, ClassNotFoundException {
            Path path = new File(Places.getCacheDirectory(), CACHE_FILE).toPath();
            if (Files.exists(path)) {
                try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(path, StandardOpenOption.READ))) {
                    return (SerCache) in.readObject();
                }
            } else {
                LOG.log(Level.INFO, "No cache file {0}", path);
            }
            return null;
        }
    }

    static MavenInfo infoForProject(Project project) {
        Path path = pathForProject(project);
        MavenInfo info = PROJECT_INFO.get(path);
        if (info == null) {
            loadCache();
            info = PROJECT_INFO.get(path);
            if (info == null) {
                info = new MavenInfo(project);
                PROJECT_INFO.put(path, info);
                scheduleCacheSave();
            }
        }
        if (!listening) {
            listening = true;
            ProjectUpdates.subscribeToChanges(LISTENER);
        }
        return info;
    }

    static void drop(Project project) {
        PROJECT_INFO.remove(pathForProject(project));
        scheduleCacheSave();
    }

    @Override
    public <T> T get(Class<T> type) {
        if (AntlrConfigurationImplementation.class == type) {
            MavenInfo info = info();
            if (info != null) {
                return type.cast(info.pluginInfo());
            }
        }
        return FolderLookupStrategyImplementation.super.get(type);
    }

    @Override
    public Iterable<Path> find(Folders folder, FolderQuery query) throws IOException {
        AntlrConfigurationImplementation impl = info().pluginInfo();
        switch (folder) {
            case ANTLR_IMPORTS:
                return FolderLookupStrategyImplementation.super.iterable(impl.antlrImportDir());
            case ANTLR_GRAMMAR_SOURCES:
                return FolderLookupStrategyImplementation.super.iterable(impl.antlrSourceDir());
            case CLASS_OUTPUT:
                return FolderLookupStrategyImplementation.super.iterable(impl.buildDir().resolve("classes"));
            case JAVA_GENERATED_SOURCES:
                return FolderLookupStrategyImplementation.super.iterable(impl.antlrOutputDir());
            case JAVA_SOURCES:
                return FolderLookupStrategyImplementation.super.iterable(impl.javaSources());
            case JAVA_TEST_SOURCES:
                return FolderLookupStrategyImplementation.super.iterable(impl.testSources());
            case TEST_CLASS_OUTPUT:
                return FolderLookupStrategyImplementation.super.iterable(impl.testOutput());
            case RESOURCES:
                // XXX need to capture these in the antlr configuration
                Path javaSrc = impl.javaSources();
                if (javaSrc != null) {
                    Path resources = javaSrc.getParent().resolve("resources");
                    if (Files.exists(resources)) {
                        return FolderLookupStrategyImplementation.super.iterable(resources);
                    }
                }
            case TEST_RESOURCES:
                Path javaTestSrc = impl.testSources();
                if (javaTestSrc != null) {
                    Path resources = javaTestSrc.getParent().resolve("resources");
                    if (Files.exists(resources)) {
                        return FolderLookupStrategyImplementation.super.iterable(resources);
                    }
                }
            case JAVA_TEST_GENERATED_SOURCES:
            case ANTLR_TEST_GRAMMAR_SOURCES:
            case ANTLR_TEST_IMPORTS:
                return empty(); // XXX
            default:
                throw new AssertionError("FIXME - " + folder);
        }
    }

    @Override
    public Iterable<Path> allFiles(Folders type) {
        Iterable<Path> result = FolderLookupStrategyImplementation.super.allFiles(type);
        Predicate<Path> filter = null;
        AntlrConfigurationImplementation config = get(AntlrConfigurationImplementation.class);
        if (config != null && type == Folders.ANTLR_GRAMMAR_SOURCES || type == Folders.ANTLR_IMPORTS) {
            if (!"**/*.g4".equals(config.includePattern())) {
                try {
                    Iterable<Path> flds = find(type, initialQuery);
                    for (Path p : flds) {
                        if (filter == null) {
                            filter = globFilter(config.includePattern(), p);
                        } else {
                            filter = filter.or(globFilter(config.includePattern(), p));
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(MavenFolderStrategy.class.getName()).log(Level.INFO,
                            "exception filtering", ex);
                }
            }
            Predicate<Path> excludeFilter = null;
            if (config.excludePattern() != null && !"".equals(config.excludePattern())) {
                try {
                    Iterable<Path> flds = find(type, initialQuery);
                    for (Path p : flds) {
                        if (excludeFilter == null) {
                            excludeFilter = globFilter(config.excludePattern(), p);
                        } else {
                            excludeFilter = filter.or(globFilter(config.excludePattern(), p));
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(MavenFolderStrategy.class.getName()).log(Level.INFO,
                            "exception filtering", ex);
                }
            }
            if (excludeFilter != null) {
                if (filter == null) {
                    filter = excludeFilter.negate();
                } else {
                    filter = filter.and(excludeFilter.negate());
                }
            }
        }
        if (filter != null) {
            result = filter(result, filter);
        }
        return result;
    }

    @Override
    public String name() {
        return MAVEN;
    }
}
