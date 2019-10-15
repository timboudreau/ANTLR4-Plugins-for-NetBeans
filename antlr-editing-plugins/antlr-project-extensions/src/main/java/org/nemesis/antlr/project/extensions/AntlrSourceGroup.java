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

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import javax.swing.Icon;
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

    @Messages("missing_folder=Missing Folder")
    @Override
    public synchronized FileObject getRootFolder() {
        int ownerRev = owner.rev();
        if (folder != null && expectedRev == owner.rev()) {
            return folder;
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
