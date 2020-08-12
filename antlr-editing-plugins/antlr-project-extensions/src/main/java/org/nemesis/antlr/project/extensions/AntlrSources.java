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
package org.nemesis.antlr.project.extensions;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ChangeListener;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.projectupdatenotificaton.ProjectUpdates;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileUtil;
import org.openide.util.ChangeSupport;
import org.openide.util.Lookup;

public final class AntlrSources extends FileChangeAdapter implements Sources, Consumer<Path>, Kickable {

    private static final Logger LOG = Logger.getLogger(
            AntlrSources.class.getName());
    private final Lookup projectLookup;
    private final ChangeSupport supp = new ChangeSupport(this);
    private final List<AntlrSourceGroup> groups = new ArrayList<>();
    private final AtomicInteger rev = new AtomicInteger(Integer.MIN_VALUE);
    private int revWhenGroupsLastComputed = -1;

    @SuppressWarnings("LeakingThisInConstructor")
    public AntlrSources(Lookup projectLookup) {
        this.projectLookup = projectLookup;
        LOG.log(Level.FINE, "Create {0}", this);
    }

    @Override
    public void accept(Path t) {
        fire("Update notification " + t);
    }

    @Override
    public String toString() {
        Project p = projectLookup.lookup(Project.class);
        String dirname = p == null ? "no-project" : p.getProjectDirectory().getName();
        return "AntlrSources(" + dirname + " rev "
                + rev() + " @" + Integer.toHexString(System.identityHashCode(this)) + ")";
    }

    private void addNotify() {
        computeGroups();
        LOG.log(Level.FINER, "{0}.addNotify()", this);
        for (File possibleBuildFile : possibleBuildFiles()) {
            FileUtil.addFileChangeListener(this, possibleBuildFile);
            LOG.log(Level.FINER, "{0} listen on {1}", new Object[]{this, possibleBuildFile});
        }
        Path path = FileUtil.toFile(projectLookup.lookup(Project.class).getProjectDirectory()).toPath();
        ProjectUpdates.subscribeToChanges(path, this);
    }

    private File[] possibleBuildFiles() {
        Project project = projectLookup.lookup(Project.class);
        if (project != null) {
            Set<Path> possibleBuildFiles = AntlrConfiguration.potentialBuildFilePaths(project);
            List<File> files = new ArrayList<>(possibleBuildFiles.size());
            possibleBuildFiles.forEach((p) -> {
                files.add(p.toFile());
            });
            return files.toArray(new File[files.size()]);
        }
        return new File[0];
    }

    @Override
    public SourceGroup[] getSourceGroups(String type) {
//        LOG.log(Level.FINEST, "AntlrSources.getSourceGroups {0}", type);
        if (!"java".equals(type)) {
            return new SourceGroup[0];
        }
        if (rev.compareAndSet(Integer.MIN_VALUE, 0)) {
            addNotify();
        }
        computeGroups();
        // XXX we need to listen on pom file or something to fire changes
        // if antlr support is added
        return groups.toArray(new SourceGroup[groups.size()]);
    }

    private synchronized List<AntlrSourceGroup> computeGroups() {
        int currentRev = rev();
        if (currentRev != revWhenGroupsLastComputed) {
            Set<AntlrSourceGroup> oldGroups = new HashSet<>(groups);
            LOG.log(Level.FINER, this + " computeGroups for rev {0}", currentRev);
            Set<AntlrSourceGroup> newGroups = new LinkedHashSet<>();
            Project project = projectLookup.lookup(Project.class);
            AntlrConfiguration config = AntlrConfiguration.forProject(projectLookup.lookup(Project.class));
            if (config != null) {
                createSourceGroups(project, config, newGroups);
            }
            revWhenGroupsLastComputed = currentRev;
            if (newGroups.equals(oldGroups)) {
                LOG.log(Level.FINER, "{0} groups unchanged", currentRev);
                return groups;
            }
            Set<AntlrSourceGroup> removed = oldGroups;
            removed.removeAll(newGroups);
            Set<AntlrSourceGroup> added = new HashSet<>(newGroups);
            added.removeAll(groups);
            groups.removeAll(removed);
            groups.addAll(added);
            Collections.sort(groups);
            LOG.log(Level.FINEST, "Updated groups for {0} adding {1} removing {2}",
                    new Object[]{this, added, removed});
        }
        return groups;
    }

    int rev() {
        return rev.get();
    }

    @Override
    public void addChangeListener(ChangeListener listener) {
        supp.addChangeListener(listener);
    }

    @Override
    public void removeChangeListener(ChangeListener listener) {
        supp.removeChangeListener(listener);
    }

    @Override
    public void fileChanged(FileEvent fe) {
        Project project = projectLookup.lookup(Project.class);
        File file = FileUtil.toFile(project.getProjectDirectory());
        if (file != null) {
            ProjectUpdates.notifyPathChanged(file.toPath());
        }
    }

    @Override
    public void kick() {
        supp.fireChange();
    }

    void fire(Object reason) {
        int newRev = rev.incrementAndGet();
        LOG.log(Level.FINER, "{0} fire change due to {1} has listeners {2} rev {3}",
                new Object[]{this, reason, supp.hasListeners(), newRev});
        AntlrIconAnnotator.fireIconBadgingChanges();
        supp.fireChange();
    }

    private boolean isLegitAntlrProject(Project project, AntlrConfiguration config) {
        if (config == null) {
            return false;
        }
//        return valid(Folders.ANTLR_GRAMMAR_SOURCES.find(project)) || valid(Folders.ANTLR_IMPORTS.find(project));
        return !config.isGuessedConfig();
    }

    private boolean valid(Iterable<? extends Path> it) {
        for (Path p : it) {
            if (valid(p)) {
                return true;
            }
        }
        return false;
    }

    private boolean valid(Path path) {
        return path != null && Files.exists(path);
    }

    private void createSourceGroups(Project project, AntlrConfiguration config, Set<? super AntlrSourceGroup> groups) {
        if (isLegitAntlrProject(project, config)) {
//            Path antlrSrc = config.antlrSourceDir();
//            Path antlrImport = config.antlrImportDir();
//            if (valid(antlrImport) && !config.isImportDirChildOfSourceDir()) {
//                groups.add(new AntlrSourceGroup(Folders.ANTLR_GRAMMAR_SOURCES, project, this));
//                groups.add(new AntlrSourceGroup(Folders.ANTLR_IMPORTS, project, this));
//            } else if (valid(antlrSrc)) {
//                groups.add(new AntlrSourceGroup(Folders.ANTLR_GRAMMAR_SOURCES, project, this));
//            }
            Folders[] flds = config.isImportDirChildOfSourceDir() ? new Folders[]{Folders.ANTLR_GRAMMAR_SOURCES} : new Folders[]{Folders.ANTLR_GRAMMAR_SOURCES, Folders.ANTLR_IMPORTS};
            for (Folders f : flds) {
                switch (f) {
                    // for the defaults, avoid parsing POMs during startup:
                    case ANTLR_GRAMMAR_SOURCES:
                        if (valid(config.antlrSourceDir())) {
                            groups.add(new AntlrSourceGroup(f, project, this));
                        }
                        continue;
                    case ANTLR_IMPORTS:
                        if (valid(config.antlrImportDir())) {
                            groups.add(new AntlrSourceGroup(f, project, this));
                        }
                        continue;
                }
                if (valid(f.find(project))) {
                    groups.add(new AntlrSourceGroup(f, project, this));
                }
            }
        }
    }
}
