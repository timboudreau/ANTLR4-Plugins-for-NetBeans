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
package org.nemesis.antlr.live.impl;

import com.mastfrog.function.state.Bool;
import com.mastfrog.util.path.UnixPath;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;
import org.nemesis.antlr.common.AntlrConstants;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.Folders;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSCoordinates;
import org.nemesis.jfs.JFSFileObject;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 * For each project containing Antlr files, we maintain <i>one</i> JFS instance
 * which maps all of the Antlr grammar and related files into itself, and is
 * shared for parsing/regeneration/running-code-against-the-grammar for any
 * grammar file in that project.
 */
final class PerProjectJFSMappingManager {

    private static final Logger LOG = Logger.getLogger(PerProjectJFSMappingManager.class.getName());
    private static final JavaFileManager.Location GRAMMARS_LOCATION = StandardLocation.SOURCE_PATH;
    final Project project;
    final GrammarMappingsImpl mappings = new GrammarMappingsImpl(StandardLocation.SOURCE_PATH);
    private final Map<FileObject, OneFileMapping> mappingForFile = new ConcurrentHashMap<>();
    private final JFSManager jfses;
    private final ProjectDeletionListener listenForProjectDeletion;
    private String lastJFS = "-";
    private volatile boolean dead;
    private final Runnable onProjectDeleted;
    private final BiConsumer<FileObject, FileObject> onFileReplaced;

    PerProjectJFSMappingManager(Project project, JFSManager jfses, Runnable onProjectDeleted, BiConsumer<FileObject, FileObject> onFileReplaced) {
        this.project = project;
        this.jfses = jfses;
        listenForProjectDeletion = new ProjectDeletionListener(project, this::deleted);
        this.onProjectDeleted = onProjectDeleted;
        this.onFileReplaced = onFileReplaced;
    }

    void die() {
        try {
            for (OneFileMapping mapping : mappingForFile.values()) {
                mapping.setMappingMode(JFSMappingMode.UNMAPPED, null);
            }
        } finally {
            jfses.kill(project);
            dead = true;
        }
    }

    final Set<FileObject> siblingsOf(FileObject fo) {
        if (mappingForFile.size() == 1 && fo.equals(mappingForFile.entrySet().iterator().next().getKey())) {
            return Collections.emptySet();
        }
        Set<FileObject> result = new HashSet<>(mappingForFile.size() - 1);
        for (Map.Entry<FileObject, OneFileMapping> e : mappingForFile.entrySet()) {
            if (!fo.equals(e.getKey()) && AntlrConstants.ANTLR_MIME_TYPE.equals(fo.getMIMEType())) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    long newestGrammarLastModified() {
        long result = -1;
        for (Map.Entry<FileObject, OneFileMapping> e : mappingForFile.entrySet()) {
            switch (e.getValue().mappingMode()) {
                case DOCUMENT:
                    JFSFileObject fo = e.getValue().file();
                    if (fo != null) {
                        result = Math.max(result, fo.getLastModified());
                        break;
                    }
                case FILE:
                    result = Math.max(result, e.getKey().lastModified().getTime());
            }
        }
        return result;
    }

    Document documentFor(FileObject fo) {
        OneFileMapping mapping = mappingForFile.get(fo);
        if (mapping != null) {
            return mapping.document();
        }
        return null;
    }

    void deleted() {
        die();
        onProjectDeleted.run();
    }

    void initMappings() {
        mappingForFile.clear();
        mappings.clear();
        scanAndMap(Folders.ANTLR_GRAMMAR_SOURCES);
        scanAndMap(Folders.ANTLR_IMPORTS);
    }

    private void scanAndMap(Folders fld) {
        Set<FileObject> tokensFiles = new HashSet<>();
        Bool configChecked = Bool.create();
        fld.allFileObjects(project).forEach(fo -> {
            if (!configChecked.getAsBoolean()) {
                // If using Heuristic config, force it to fully initialize
                configChecked.runAndSet(() -> {
                    AntlrConfiguration.forFile(fo);
                });
            }
            // Antlr imports may be underneath antlr sources,
            // so don't map twice
            if (!mappingForFile.containsKey(fo)) {
                Folders owner = Folders.ownerOf(fo);
                if (owner != null && !owner.isAntlrSourceFolder()) {
                    owner = fld;
                }
                if (owner == null) {
                    if (fo.getParent().getName().equals("imports")) {
                        owner = Folders.ANTLR_IMPORTS;
                    } else {
                        owner = Folders.ANTLR_GRAMMAR_SOURCES;
                    }
                }
                addMapping(owner, fo);
            }
        });
        // There are sometimes .tokens files in source, which should be used - the tool
        // will prefer newly generated ones but use these if not present
        for (Path p : fld.find(project)) {
            try {
                if (Files.exists(p) && Files.isDirectory(p)) {
                    Files.walk(p, 11).filter(pth -> pth.getFileName().toString().endsWith(".tokens")).forEach(tokensFile -> {
                        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(tokensFile.toFile()));
                        if (fo != null) {
                            addMapping(fld, fo);
                        }
                    });
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

    }

    void onPrimaryFileChange(FileObject old, FileObject nue, OneFileMapping oldMapping) {
        LOG.log(Level.FINEST, "Primary file change {0} -> {1} for {2} oldpath {3}", new Object[]{old, nue, oldMapping.path});
        if (old != null) {
            removeMapping(old, oldMapping);
        }
        if (nue != null && !mappingForFile.containsKey(nue)) {
            Folders f = Folders.ownerOf(nue);
            if (f == null) {
                f = oldMapping.owner;
            }
            addMapping(f, nue);
        }
        onFileReplaced.accept(old, nue);
    }

    void onFileRenamed(FileObject file, String oldName, String newName, OneFileMapping mapping) {
        LOG.log(Level.FINEST, "File renamed {0} -> {1} for {2} oldpath {3}", new Object[]{oldName, newName, mapping.path});
        removeMapping(file, mapping);
        addMapping(mapping.owner, file);
    }

    void removeMapping(FileObject fo, OneFileMapping mapping) {
        LOG.log(Level.FINEST, "Remove mapping {0}", mapping);
        mapping.discarded();
        mappings.remove(fo);
    }

    private void onMappingAdded(Folders fld, FileObject fo, OneFileMapping mapping) {
        mapping.addTo(mappings);
        mappingForFile.put(fo, mapping);
    }

    JFSCoordinates addSurpriseMapping(Folders fld, FileObject fo) {
        // Force the heuristic config to do some work and resolve more
        AntlrConfiguration updated = AntlrConfiguration.forFile(fo);
        if (updated.antlrSourceDir() != null || updated.antlrImportDir() != null) {
            initMappings();
        }
        LOG.log(Level.INFO, "Found grammar file with no owning antlr folder; mapping it relative to the project root: ", fo.getPath());
        addMapping(fld, fo);
        for (FileObject sib : fo.getParent().getChildren()) {
            if (sib != fo && AntlrConstants.ANTLR_MIME_TYPE.equals(sib.getMIMEType())) {
                LOG.log(Level.INFO, "Also preemptively map {0}", sib.getPath());
                addMapping(fld, sib);
            } else if ("tokens".equals(sib.getExt())) {
                LOG.log(Level.INFO, "Also preemptively map tokens file {0}", sib.getPath());
                addMapping(fld, sib);
            }
        }
        return mappings.forFileObject(fo);
    }

    private boolean addMapping(Folders fld, FileObject fo) {
        OneFileMapping mapping = null;
        try {
            mapping = map(fld, fo);
            if (mapping != null) {
                LOG.log(Level.FINEST, "Add jfs mapping for {0} in {1} -> {2}", new Object[]{fo.getNameExt(), fld, mapping.path});
                onMappingAdded(fld, fo, mapping);
            } else {
                LOG.log(Level.FINE, "No path to {0} for {1}", new Object[]{fld, fo.getNameExt()});
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return mapping != null;
    }

    private OneFileMapping map(Folders fld, FileObject fo) throws IOException {
        Path path = toPath(fo);
        if (path == null) {
            return null;
        }
        if (fld == Folders.ANTLR_IMPORTS) {
            JFSCoordinates coords = JFSCoordinates.create(GRAMMARS_LOCATION, AntlrGenerationSubscriptionsImpl.IMPORTS.resolve(fo.getNameExt()));
            OneFileMapping result = new OneFileMapping(fo, coords, this::onPrimaryFileChange, this::onFileRenamed, this::jfs, fld);
            return result;
        }
        Iterable<Path> allFoldersOfType = fld.find(project);
        boolean noRoot = true;
        for (Path p : allFoldersOfType) {
            if (path.startsWith(p)) {
                Path relativePath = p.relativize(path);
                if (relativePath != null) {
                    JFSCoordinates coords = JFSCoordinates.create(GRAMMARS_LOCATION, UnixPath.get(relativePath));
                    OneFileMapping result = new OneFileMapping(fo, coords, this::onPrimaryFileChange, this::onFileRenamed, this::jfs, fld);
                    noRoot = false;
                    return result;
                }
            }
        }
        if (noRoot) {
            Path projectDir = toPath(project.getProjectDirectory());
            if (projectDir != null) {
                Path rel = projectDir.relativize(path);
                if (rel != null) {
                    JFSCoordinates coords = JFSCoordinates.create(GRAMMARS_LOCATION, UnixPath.get(rel));
                    OneFileMapping result = new OneFileMapping(fo, coords, this::onPrimaryFileChange, this::onFileRenamed, this::jfs, fld);
                    return result;
                }
            }
        }
        return null;
    }

    private static Path toPath(FileObject fo) {
        File file = FileUtil.toFile(fo);
        return file != null ? file.toPath() : null;
    }

    JFS jfs() {
        if (dead) {
            try {
                return JFS.builder().build();
            } catch (IOException ex) {
                return com.mastfrog.util.preconditions.Exceptions.chuck(ex);
            }
        }
        JFS result = jfses.forProject(project);
        String id = result.id();
        boolean needInit = false;
        synchronized (this) {
            if (!Objects.equals(id, lastJFS)) {
                lastJFS = id;
                needInit = true;
            }
        }
        if (needInit) {
            initMappings();
        }
        return result;
    }

}
