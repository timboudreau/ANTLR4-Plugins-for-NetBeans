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
package org.nemesis.antlr.v4.netbeans.v8.project;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public final class TestProjectStructure {

    private static final long TIMESTAMP = System.currentTimeMillis();
    private final String[] files;
    private final ProjectContentProvider dataProvider;
    private final String name;
    private final Class<?> test;
    private Path projectPath;
    private Project project;
    private final boolean dontLookUpProject;

    public TestProjectStructure(Class<?> test, String name,
            ProjectContentProvider dataProvider, String... files) throws Exception {
        this(false, test, name, dataProvider, files);
    }

    public TestProjectStructure(boolean dontLookUpProject, Class<?> test, String name,
            ProjectContentProvider dataProvider, String... files) throws Exception {
        this.dontLookUpProject = dontLookUpProject;
        this.name = name;
        this.dataProvider = dataProvider;
        this.files = files;
        this.test = test;
        init();
    }

    public String[] files() {
        return files;
    }

    public Project project() {
        return project;
    }

    public Path path() {
        return projectPath;
    }

    private void init() throws Exception {
        setUpProjects(files);
    }

    private void setUpProjects(String[] files) throws IOException, URISyntaxException {
        ProjectManager.getDefault().clearNonProjectCache();
        String testDirName = test.getSimpleName() + "-" + Long.toString(TIMESTAMP, 36);
        Path tmp = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"));
        Path root = tmp.resolve(testDirName);
        projectPath = createProject(root, name, files);

        FileObject fo = FileUtil.toFileObject(projectPath.toFile());
        assertNotNull(fo);
        if (!dontLookUpProject) {
            ProjectManager.Result res = ProjectManager.getDefault().isProject2(fo);
            assertNotNull("Not a project: " + projectPath, res);
            project = ProjectManager.getDefault().findProject(fo);
            assertNotNull(project);
        }

        List<Path> noOwner = new ArrayList<>();
        for (String file : files) {
            Path filePath = toDestPath(projectPath.resolve(file));
            assertTrue(file + " not created in " + projectPath, Files.exists(filePath));
            FileObject fileFo = FileUtil.toFileObject(FileUtil.normalizeFile(filePath.toFile()));
            assertNotNull(fileFo);
            Project owner = FileOwnerQuery.getOwner(fileFo);
            assertSame(project, owner);
            if (owner == null) {
                noOwner.add(filePath);
            }
        }
        if (!dontLookUpProject && !noOwner.isEmpty()) {
            fail("Some project files get null back from FileOwnerQuery: " + noOwner);
        } else if (dontLookUpProject && noOwner.isEmpty()) {
            fail("Project detected where it shouldn't be");
        }
    }

    public void delete() throws IOException {
        Set<Path> all = new HashSet<>();
        for (Path p : new Path[]{projectPath}) {
            if (Files.exists(p)) {
                all.add(p);
            }
            Files.walkFileTree(p, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    all.add(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    all.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    throw new RuntimeException(exc);
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        List<Path> toDelete = new ArrayList<>(all);
        Collections.sort(toDelete, (Path o1, Path o2) -> {
            int a = o1.getNameCount();
            int b = o2.getNameCount();
            int result = a < b ? 1 : a > b ? -1 : 0;
            if (result == 0) {
                result = o1.compareTo(o2);
            }
            return result;
        });
        for (Path p : toDelete) {
            Files.deleteIfExists(p);
        }
    }

    private String toDestName(String toCreate) {
        Matcher m = ENDS_WITH_DIGITS.matcher(toCreate);
        if (m.find()) {
            toCreate = m.group(1) + "." + m.group(3);
        }
        return toCreate;
    }

    private Path toDestPath(Path path) {
        assert path.getNameCount() > 1 : "Just a filename: '" + path + "'";
        String filename = path.getName(path.getNameCount() - 1).toString();
        String realSourceName = toDestName(filename);
        assert path != null : "huh? " + path;
        return path.getParent().resolve(realSourceName);
    }

    private Path createProject(Path root, String name, String... files) throws IOException, URISyntaxException {
        Path dir = root.resolve(name);
        for (String file : files) {
            if (file.endsWith("/")) {
                Path created = dir.resolve(file);
                Files.createDirectories(created);
            } else {
                Path x = dir.resolve(file);
                String toCreate = x.getName(x.getNameCount() - 1).toString();
                String sourceName = toDestName(toCreate);
                Path created = dir.resolve(file).getParent().resolve(sourceName);
                Files.createDirectories(created.getParent());
                Files.createFile(created);
                writeFile(file, name, sourceName, created);
            }
        }
        return dir;
    }

    private static final Pattern ENDS_WITH_DIGITS = Pattern.compile("^(.*?)\\-(\\d+)\\.(\\S+)");

    private void writeFile(String sourceName, String projectName, String realFileName, Path to) throws IOException, URISyntaxException {
        InputStream in = dataProvider.getContent(sourceName);
        if (in != null) {
            OutputStream out = Files.newOutputStream(to, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
            FileUtil.copy(in, out);
        }
    }

    public interface ProjectContentProvider {

        InputStream getContent(String name) throws IOException, URISyntaxException;
    }

}
