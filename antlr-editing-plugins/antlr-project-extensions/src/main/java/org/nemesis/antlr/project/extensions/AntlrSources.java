/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
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

public final class AntlrSources extends FileChangeAdapter implements Sources, Consumer<Path> {

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
            if (LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, this + " addNotify() via ", new Exception());
            }
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

    void fire(Object reason) {
        int newRev = rev.incrementAndGet();
        LOG.log(Level.FINER, "{0} fire change due to {1} has listeners {2}",
                new Object[]{this, reason, supp.hasListeners()});
        AntlrIconAnnotator.fireIconBadgingChanges();
        supp.fireChange();
    }

    private boolean isLegitAntlrProject(Project project, AntlrConfiguration config) {
        if (config == null) {
            return false;
        }
        return valid(Folders.ANTLR_GRAMMAR_SOURCES.find(project)) || valid(Folders.ANTLR_IMPORTS.find(project));
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
            Folders[] flds = config.isImportDirChildOfSourceDir() ? new Folders[]{Folders.ANTLR_GRAMMAR_SOURCES} : new Folders[]{Folders.ANTLR_GRAMMAR_SOURCES, Folders.ANTLR_IMPORTS};
            for (Folders f : flds) {
                if (valid(f.find(project))) {
                    groups.add(new AntlrSourceGroup(f, project, this));
                }
            }
        }
    }
}
