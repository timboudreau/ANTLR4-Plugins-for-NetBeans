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
package org.nemesis.antlr.project;

import static com.mastfrog.util.collections.CollectionUtils.immutableSetOf;
import com.mastfrog.util.path.UnixPath;
import java.io.File;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
import org.nemesis.antlr.project.spi.OwnershipQuery;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Folders that are of interest to Antlr projects. Use the find methods to
 * locate folders of interest. Note that path-returning methods may return paths
 * that do not currently exist on disk, but are where the project would put
 * things like build files; equivalent methods that return file objects omit
 * these, since a file object must exist on disk.
 *
 * @author Tim Boudreau
 */
public enum Folders {
    ANTLR_IMPORTS,
    ANTLR_GRAMMAR_SOURCES,
    JAVA_GENERATED_SOURCES,
    JAVA_SOURCES,
    RESOURCES,
    TEST_RESOURCES,
    JAVA_TEST_GENERATED_SOURCES,
    JAVA_TEST_SOURCES,
    CLASS_OUTPUT,
    ANTLR_TEST_IMPORTS,
    ANTLR_TEST_GRAMMAR_SOURCES,
    TEST_CLASS_OUTPUT;

    public boolean isAntlrSourceFolder() {
        switch (this) {
            case ANTLR_GRAMMAR_SOURCES:
            case ANTLR_TEST_GRAMMAR_SOURCES:
            case ANTLR_IMPORTS:
            case ANTLR_TEST_IMPORTS:
                return true;
            default:
                return false;
        }
    }

    public boolean isSearchSubfolders() {
        switch (this) {
            case ANTLR_IMPORTS:
            case ANTLR_TEST_IMPORTS:
                return false;
        }
        return true;
    }

    public boolean isJavaSourceFolder() {
        switch (this) {
            case JAVA_SOURCES:
            case JAVA_TEST_SOURCES:
            case JAVA_GENERATED_SOURCES:
            case JAVA_TEST_GENERATED_SOURCES:
                return true;
            default:
                return false;
        }
    }

    public boolean isSourceFolder() {
        return isAntlrSourceFolder() || isJavaSourceFolder();
    }

    private static final Set<String> ANTLR_EXTS
            = immutableSetOf("g4", "g");
    private static final Set<String> ANTLR_MIME
            = immutableSetOf("text/x-g4");
    private static final Set<String> JAVA_EXTS
            = immutableSetOf("java");
    private static final Set<String> JAVA_MIME
            = immutableSetOf("text/x-java");
    private static final Set<String> CLASS_EXTS
            = immutableSetOf("class");
    private static final Set<String> CLASS_MIME
            = immutableSetOf("application/x-class-file");

    public static Folders primaryFolderFor(FileObject fo) {
        Folders result = null;
        String mime = fo.getMIMEType();
        if (JAVA_MIME.contains(mime)) {
            result = JAVA_SOURCES;
        } else if (ANTLR_MIME.contains(mime)) {
            result = ANTLR_GRAMMAR_SOURCES;
        } else if (CLASS_MIME.contains(mime)) {
            result = CLASS_OUTPUT;
        } else {
            result = RESOURCES;
        }
        return result;
    }

    public static Folders primaryFolderFor(Path path) {
        String ext = UnixPath.get(path).extension();
        if (ANTLR_EXTS.contains(ext)) {
            return ANTLR_GRAMMAR_SOURCES;
        } else if (JAVA_EXTS.contains(ext)) {
            return JAVA_SOURCES;
        } else {
            return RESOURCES;
        }
    }

    @SuppressWarnings("ManualArrayToCollectionCopy")
    static Set<Folders> toSet(Folders... flds) {
        Set<Folders> result = EnumSet.noneOf(Folders.class);
        for (int i = 0; i < flds.length; i++) {
            result.add(flds[i]);
        }
        return result;
    }

    @SuppressWarnings("ManualArrayToCollectionCopy")
    static Set<Folders> toSet(Folders first, Folders... flds) {
        Set<Folders> result = EnumSet.noneOf(Folders.class);
        result.add(first);
        for (int i = 0; i < flds.length; i++) {
            result.add(flds[i]);
        }
        return result;
    }

    public Set<String> defaultMimeTypes() {
        switch (this) {
            case ANTLR_GRAMMAR_SOURCES:
            case ANTLR_IMPORTS:
            case ANTLR_TEST_GRAMMAR_SOURCES:
            case ANTLR_TEST_IMPORTS:
            case RESOURCES:
            case TEST_RESOURCES:
                return ANTLR_MIME;
            case JAVA_GENERATED_SOURCES:
            case JAVA_TEST_SOURCES:
            case JAVA_SOURCES:
            case JAVA_TEST_GENERATED_SOURCES:
                return JAVA_MIME;
            case CLASS_OUTPUT:
            case TEST_CLASS_OUTPUT:
                return CLASS_MIME;
            default:
                throw new AssertionError(this);

        }
    }

    public Set<String> defaultTargetFileExtensions() {
        switch (this) {
            case ANTLR_GRAMMAR_SOURCES:
            case ANTLR_IMPORTS:
            case ANTLR_TEST_GRAMMAR_SOURCES:
            case ANTLR_TEST_IMPORTS:
            case RESOURCES:
            case TEST_RESOURCES:
                return ANTLR_EXTS;
            case JAVA_GENERATED_SOURCES:
            case JAVA_TEST_SOURCES:
            case JAVA_SOURCES:
            case JAVA_TEST_GENERATED_SOURCES:
                return JAVA_EXTS;
            case CLASS_OUTPUT:
            case TEST_CLASS_OUTPUT:
                return CLASS_EXTS;
            default:
                throw new AssertionError(this);
        }
    }

    public static Path ownerRelativePath(FileObject fo) {
        File file = FileUtil.toFile(fo);
        if (file == null) {
            return null;
        }
        Path path = file.toPath();
        Folders owner = ownerOf(fo);
        if (owner != null) {
            Iterable<Path> dirs = owner.find(fo);
            for (Path p : dirs) {
                if (path.startsWith(p)) {
                    return p.relativize(path);
                }
            }
        }
        return null;
    }

    public static Folders ownerOf(FileObject fo) {
        FoldersLookupStrategy strat = FoldersLookupStrategy.get(fo);
        OwnershipQuery oq = strat.get(OwnershipQuery.class);
        if (oq != null) {
            File file = FileUtil.toFile(fo);
            if (file != null) {
                Folders result = oq.findOwner(file.toPath());
                if (result != null) {
                    return result;
                }
            }
        }
        File file = FileUtil.toFile(fo);
        if (file == null) {
            return null;
        }
        Path path = file.toPath();
        for (Folders f : Folders.values()) {
            Iterable<Path> paths = strat.find(f, path);
            for (Path p : paths) {
                if (path.startsWith(p)) {
                    return f;
                }
            }
        }
        return null;
    }

    public static Folders ownerOf(Path path) {
        FoldersLookupStrategy strat = FoldersLookupStrategy.get(path);
        if (path == null) {
            return null;
        }
        for (Folders f : Folders.values()) {
            Iterable<Path> paths = strat.find(f, path);
            for (Path p : paths) {
                if (path.startsWith(p)) {
                    return f;
                }
            }
        }
        return null;
    }

    public Iterable<Path> allFiles(Project project) {
        return FoldersLookupStrategy.get(project).allFiles(this);
    }

    public Iterable<FileObject> allFiles(FileObject fo) {
        return FoldersLookupStrategy.get(fo).allFileObjects(this);
    }

    public Iterable<Path> allFiles(Path path) {
        return FoldersLookupStrategy.get(path).allFiles(this);
    }

    public Iterable<FileObject> allFileObjects(Project project) {
        return FoldersLookupStrategy.get(project).allFileObjects(this);
    }

    /**
     * Look up all file objects for this Folders in a project, relative to a
     * specific file. We need this in order to handle the case of Antlr sources
     * mixed in with Java sources, to manage to return all of sources in the
     * same folder, in the case that grammars are mixed in with Antlr sources as
     * some projects (c.f. Quorum) do.
     *
     * @param project A project
     * @param relativeTo A file - if text/x-g4, should return all grammar files
     * in the same directory if this particular folder is actually the java
     * souruces
     * @return An iterable
     */
    public Iterable<FileObject> allFileObjects(Project project, FileObject relativeTo) {
        return FoldersLookupStrategy.get(project).allFileObjects(this, relativeTo);
    }

    public Iterable<Path> find(Project project) {
        return FoldersLookupStrategy.get(project).find(this);
    }

    public Iterable<Path> find(Project project, Path path) {
        return FoldersLookupStrategy.get(project, path).find(this);
    }

    public Iterable<Path> find(Project project, FileObject fo) {
        return FoldersLookupStrategy.get(project, fo).find(this);
    }

    public Iterable<Path> find(FileObject fo) {
        return FoldersLookupStrategy.get(fo).find(this);
    }

    public Path findFirst(Project project) {
        return FoldersLookupStrategy.get(project).findFirst(this);
    }

    public Path findFirst(Project project, Path path) {
        return FoldersLookupStrategy.get(project, path).findFirst(this);
    }

    public Path findFirst(Project project, FileObject fo) {
        return FoldersLookupStrategy.get(project, fo).findFirst(this);
    }

    public Path findFirst(FileObject fo) {
        return FoldersLookupStrategy.get(fo).findFirst(this);
    }

    public Iterable<FileObject> findFileObject(Project project) {
        return FileObjectIterable.create(FoldersLookupStrategy.get(project).find(this));
    }

    public Iterable<FileObject> findFileObject(Project project, Path path) {
        return FileObjectIterable.create(FoldersLookupStrategy.get(project, path).find(this));
    }

    public Iterable<FileObject> findFileObject(Project project, FileObject fo) {
        return FileObjectIterable.create(FoldersLookupStrategy.get(project, fo).find(this));
    }

    public Iterable<FileObject> findFileObject(FileObject fo) {
        return FileObjectIterable.create(FoldersLookupStrategy.get(fo).find(this));
    }

    public FileObject findFirstFileObject(Project project) {
        return convert(FoldersLookupStrategy.get(project).findFirst(this));
    }

    public FileObject findFirstFileObject(Project project, Path path) {
        return convert(FoldersLookupStrategy.get(project, path).findFirst(this));
    }

    public FileObject findFirstFileObject(Project project, FileObject fo) {
        return convert(FoldersLookupStrategy.get(project, fo).findFirst(this));
    }

    public FileObject findFirstFileObject(FileObject fo) {
        return convert(FoldersLookupStrategy.get(fo).findFirst(this));
    }

    private static FileObject convert(Path path) {
        return path == null ? null : FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
    }

    public static Folders forLocation(StandardLocation loc) {
        switch (loc) {
            case CLASS_OUTPUT:
                return CLASS_OUTPUT;
            case SOURCE_OUTPUT:
                return JAVA_GENERATED_SOURCES;
            case SOURCE_PATH:
                return JAVA_SOURCES;
            default:
                return null;
        }
    }

    public Location toLocation() {
        switch (this) {
            case CLASS_OUTPUT:
                return StandardLocation.CLASS_OUTPUT;
            case JAVA_SOURCES:
                return StandardLocation.SOURCE_PATH;
            case JAVA_GENERATED_SOURCES:
                return StandardLocation.SOURCE_OUTPUT;
            default:
                return null;
        }
    }
}
