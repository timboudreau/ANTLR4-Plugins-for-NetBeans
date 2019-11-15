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

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import javax.swing.Icon;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.Folders;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.spi.project.ui.PrivilegedTemplates;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.WeakSet;

/**
 * Source group for Antlr packages. Implements PrivilegedTemplates in
 * anticipation of that patch to expose the SourceGroup in the lookup of the
 * source group node, which should allow the infrastructure to pick it up and
 * use it in the New menu.
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {"antlr_sources=Antlr Sources", "antlr_imports=Antlr Imports"})
final class AntlrSourceGroup extends FileChangeAdapter implements SourceGroup, Comparable<AntlrSourceGroup>, PrivilegedTemplates {

    private final Folders fld;
    private final Project project;
    private final AntlrSources owner;
    private FileObject folder;
    private final Set<FileObject> listeningOn = new WeakSet<>();
    private int expectedRev = -1;

    public AntlrSourceGroup(Folders fld, Project project, AntlrSources owner) {
        this.fld = fld;
        this.project = project;
        this.owner = owner;
    }

    @Messages({
        "# We cannot have spaces in the localized name for this name or",
        "# FileObject.toUri() will throw an exception",
        "missing_folder=-miising-folder-"
    })
    @Override
    public synchronized FileObject getRootFolder() {
        int ownerRev = owner.rev();
        if (folder != null && expectedRev == owner.rev()) {
            return folder;
        }
        switch (fld) {
            // Try to load from the antlr configuration, so we don't trigger
            // pom parsing during startup
            case ANTLR_GRAMMAR_SOURCES:
            case ANTLR_IMPORTS:
                AntlrConfiguration config = AntlrConfiguration.forProject(project);
                Path pth = fld == Folders.ANTLR_GRAMMAR_SOURCES
                        ? config.antlrSourceDir() : config.antlrImportDir();
                if (pth != null && Files.exists(pth)) {
                    FileObject fld = FileUtil.toFileObject(pth.toFile());
                    if (fld != null) {
                        if (!listeningOn.contains(fld)) {
                            listeningOn.add(fld);
                            fld.addFileChangeListener(
                                    FileUtil.weakFileChangeListener(this, fld));

                        }
                        return this.folder = fld;
                    }
                }

        }
        FileObject newFolder = fld.findFirstFileObject(project);
        if (newFolder != null) {
            if (!listeningOn.contains(newFolder)) {
                listeningOn.add(newFolder);
                newFolder.addFileChangeListener(
                        FileUtil.weakFileChangeListener(this, newFolder));
            }
        } else {
            if (this.folder != null) {
                owner.fire("Root fileobject for " + fld
                        + " no longer obtainable - was " + folder);
            }
        }
        FileObject projectDir = project.getProjectDirectory();
        if (!listeningOn.contains(projectDir)) {
            listeningOn.add(projectDir);
            projectDir.addFileChangeListener(
                    FileUtil.weakFileChangeListener(this, projectDir));
        }
        expectedRev = ownerRev;
        if (newFolder == null) {
            try {
                return FileUtil.createMemoryFileSystem()
                        .getRoot().createFolder(Bundle.missing_folder());
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return this.folder = newFolder;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString()).append('(');
        sb.append(fld).append(' ').append(project.getProjectDirectory().getName())
                .append(' ').append(folder == null ? "null" : folder.getPath())
                .append(") owned by ").append(owner);
        return sb.toString();
    }

    @Override
    public void fileRenamed(FileRenameEvent fe) {
        if (folder == null || folder.equals(fe.getFile())) {
            owner.fire(fld + " fileRenamed " + fe.getFile());
        }
    }

    @Override
    public void fileDeleted(FileEvent fe) {
        if (folder == null || folder.equals(fe.getFile())) {
            owner.fire(fld + " fileDeleted " + fe.getFile());
        }
    }

    @Override
    public String getName() {
        return fld.name();
    }

    @Override
    public String getDisplayName() {
        return fld == Folders.ANTLR_GRAMMAR_SOURCES ? Bundle.antlr_sources() : Bundle.antlr_imports();
    }

    @Override
    public Icon getIcon(boolean opened) {
        String iconBase = fld == Folders.ANTLR_GRAMMAR_SOURCES
                ? "org/nemesis/antlr/project/extensions/antlrMainSources.png"
                : "org/nemesis/antlr/project/extensions/antlrImports.png";
        return ImageUtilities.loadImageIcon(iconBase, true);
    }

    @Override
    public boolean contains(FileObject file) {
        return FileUtil.isParentOf(getRootFolder(), file);
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        // do nothing
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        // do nothing
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 37 * hash + Objects.hashCode(this.fld);
        hash = 37 * hash + Objects.hashCode(this.project);
        hash = 37 * hash + Objects.hashCode(this.owner);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AntlrSourceGroup other = (AntlrSourceGroup) obj;
        if (this.fld != other.fld) {
            return false;
        }
        if (!Objects.equals(this.project, other.project)) {
            return false;
        }
        return Objects.equals(this.owner, other.owner);
    }

    @Override
    public int compareTo(AntlrSourceGroup o) {
        return fld.compareTo(o.fld);
    }

    // Attempt to sneak privileged templates into the Node's lookup:
    private static final String[] ANTLR_TEMPLATES = new String[]{
        "Templates/antlr/lexer-grammar.g4",
        "Templates/antlr/combined-grammar.g4"
    };

    @Override
    public String[] getPrivilegedTemplates() {
        return ANTLR_TEMPLATES;
    }
}
