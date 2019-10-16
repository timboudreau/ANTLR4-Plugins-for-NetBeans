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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
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

/**
 *
 * @author Tim Boudreau
 */
final class MavenFolderStrategy implements FolderLookupStrategyImplementation {

    private final Project project;
    private static final Map<Project, MavenInfo> PROJECT_INFO = new WeakHashMap<>();
    private static volatile boolean listening;
    private final FolderQuery initialQuery;
    static final Consumer<Path> LISTENER = path -> {
        try {
            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
            Project prj = ProjectManager.getDefault().findProject(fo);
            if (prj != null) {
                PROJECT_INFO.remove(prj);
            }
        } catch (IOException ioe) {
            Logger.getLogger(MavenFolderStrategy.class.getName()).log(Level.SEVERE,
                    "Updates for " + path, ioe);
        }
    };

    MavenFolderStrategy(Project project, FolderQuery initialQuery) {
        this.project = project;
        this.initialQuery = initialQuery;
    }

    MavenInfo info() {
        return infoForProject(project);
    }

    static MavenInfo infoForProject(Project project) {
        MavenInfo info = PROJECT_INFO.get(project);
        if (info == null) {
            info = new MavenInfo(project);
            PROJECT_INFO.put(project, info);
        }
        if (!listening) {
            listening = true;
            ProjectUpdates.subscribeToChanges(LISTENER);
        }
        return info;
    }

    static void drop(Project project) {
        PROJECT_INFO.remove(project);
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
            case ANTLR_GRAMMAR_SOURCES:
                return FolderLookupStrategyImplementation.super.iterable(impl.sourceDir());
            case ANTLR_IMPORTS:
                return FolderLookupStrategyImplementation.super.iterable(impl.importDir());
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
        return "Maven";
    }
}
