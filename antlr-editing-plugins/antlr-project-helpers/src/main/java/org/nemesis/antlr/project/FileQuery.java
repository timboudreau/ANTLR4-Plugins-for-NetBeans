package org.nemesis.antlr.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public final class FileQuery {

    private final Set<String> extensions = new LinkedHashSet<>(2);
    private final Set<String> names = new HashSet<>(2);
    private boolean searchSiblings = false;

    public FileQuery searchingParentOfSearchedForFile() {
        searchSiblings = true;
        return this;
    }

    public static FileQuery find(String name) {
        FileQuery result = new FileQuery(name);
        return result;
    }

    public static FileQuery find(String name, String... moreNames) {
        return new FileQuery(name, moreNames);
    }

    private FileQuery(String name, String... moreNames) {
        names.add(name);
        names.addAll(Arrays.asList(moreNames));
    }

    private FileQuery(String name) {
        names.add(name);
    }

    public FileQuery withExtensions(String ext) {
        this.extensions.add(ext);
        return this;
    }

    public FileQuery withExtensions(String ext, String... more) {
        this.extensions.add(ext);
        if (more.length > 0) {
            this.extensions.addAll(Arrays.asList(more));
        }
        return this;
    }

    private static Project findProject(Path path) {
        FileObject fo = FileUtil.toFileObject(path.toFile());
        if (fo != null) {
            return FileOwnerQuery.getOwner(fo);
        } else {
            return FileOwnerQuery.getOwner(path.toUri());
        }
    }

    static Optional<FileObject> convert(Optional<Path> p) {
        if (!p.isPresent()) {
            return Optional.empty();
        }
        FileObject fo = FileUtil.toFileObject(p.get().toFile());
        if (fo == null) {
            return Optional.empty();
        }
        return Optional.of(fo);
    }

    public FinishableFileQuery<Path> forPathsIn(Folders folder, Folders... flds) {
        Iterable<Folders> folders;
        if (flds.length == 0) {
            folders = FoldersHelperTrampoline.getDefault().newSingleIterable(folder);
        } else {
            folders = Folders.toSet(folder, flds);
        }
        return new PathFinishableFileQuery(extensions, names, folders,
                searchSiblings);
    }

    public FinishableFileQuery<FileObject> forFileObjectsIn(Folders folder, Folders... flds) {
        return new FileObjectFinishableFileQuery(forPathsIn(folder, flds));
    }

    public static abstract class FinishableFileQuery<T> {

        private FinishableFileQuery() {
            // do nothing
        }

        public abstract Optional<T> inProject(Project project);

        public abstract Optional<T> relativeTo(Path path);

        public Optional<T> relativeTo(FileObject path) {
            return relativeTo(FileUtil.toFile(path).toPath());
        }

        public abstract Optional<T> inProjectRelativeTo(Project project, Path file);
    }

    static final class FileObjectFinishableFileQuery extends FinishableFileQuery<FileObject> {

        private final FinishableFileQuery<Path> delegate;

        FileObjectFinishableFileQuery(FinishableFileQuery<Path> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<FileObject> inProject(Project project) {
            return convert(delegate.inProject(project));
        }

        @Override
        public Optional<FileObject> relativeTo(Path path) {
            return convert(delegate.relativeTo(path));
        }

        @Override
        public Optional<FileObject> inProjectRelativeTo(Project project, Path file) {
            return convert(delegate.inProjectRelativeTo(project, file));
        }
    }

    static final class PathFinishableFileQuery extends FinishableFileQuery<Path> {

        private final Iterable<String> extensions;
        private final Iterable<String> names;
        private final Iterable<Folders> folders;
        private final boolean searchSiblings;

        private PathFinishableFileQuery(Collection<String> extensions,
                Iterable<String> names, Iterable<Folders> folders,
                boolean searchSiblings) {
            this.extensions = extensions;
            this.names = names;
            this.folders = folders;
            this.searchSiblings = searchSiblings;
            if (extensions.isEmpty()) {
                folders.forEach((f) -> {
                    extensions.addAll(f.defaultTargetFileExtensions());
                });
            }
        }

        @Override
        public Optional<Path> inProject(Project project) {
            return inProjectRelativeTo(project, null);
        }

        @Override
        public Optional<Path> relativeTo(Path file) {
            return inProjectRelativeTo(findProject(file), file);
        }

        @Override
        public Optional<Path> inProjectRelativeTo(Project project, Path file) {
            Path parentDir = file == null ? null
                    : searchSiblings ? file.getParent() : null;
            Set<Path> searched = searchSiblings ? new HashSet<>(4) : null;
            for (String ext : extensions) {
                for (String name : names) {
                    Path fileName = Paths.get(name + '.' + ext);
                    for (Folders f : folders) {
                        Iterable<Path> dirs = f.find(project, file);
                        for (Path p : dirs) {
                            if (parentDir != null) {
                                searched.add(p);
                            }
                            Path test = p.resolve(fileName);
                            if (Files.exists(test)) {
                                return Optional.of(test);
                            }
                            if (!f.isSearchSubfolders() && fileName.getNameCount() > 1) {
                                test = test.getFileName();
                                if (Files.exists(test)) {
                                    return Optional.of(test);
                                }
                            }
                        }
                    }
                }
            }
            if (searchSiblings && parentDir != null && (searched != null && !searched.contains(parentDir))) {
                for (String ext : extensions) {
                    for (String name : names) {
                        Path fileName = Paths.get(name + '.' + ext);
                        Path test = parentDir.resolve(fileName);
                        System.out.println("search sibling in " + parentDir + " for " + test);
                        if (Files.exists(test)) {
                            return Optional.of(test);
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }
}
