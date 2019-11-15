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

import com.mastfrog.util.file.FileUtils;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.nemesis.antlr.project.Folders.ANTLR_GRAMMAR_SOURCES;
import static org.nemesis.antlr.project.Folders.CLASS_OUTPUT;
import static org.nemesis.antlr.project.Folders.JAVA_GENERATED_SOURCES;
import static org.nemesis.antlr.project.Folders.JAVA_SOURCES;
import org.nemesis.antlr.project.FoldersLookupStrategyTest.ProjectWithOwnStrategy.Strat;
import static org.nemesis.antlr.project.ProjectTestHelper.createStructure;
import static org.nemesis.antlr.project.ProjectTestHelper.findAntlrGrammarProject;
import org.nemesis.antlr.project.impl.HeuristicFoldersHelperImplementation;
import org.nemesis.antlr.project.spi.FolderLookupStrategyImplementation;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.nemesis.antlr.project.spi.FoldersLookupStrategyImplementationFactory;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.junit.MockServices;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.spi.project.ProjectFactory2;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Tim Boudreau
 */
public class FoldersLookupStrategyTest {

    @Test
    public void sanityCheck() throws IOException {
        File prjFile = prj1.toFile();
        prjFile = FileUtil.normalizeFile(prjFile);
        FileObject pr = FileUtil.toFileObject(prjFile);
        assertTrue(ProjectManager.getDefault().isProject(pr));
        assertNotNull(ProjectManager.getDefault().isProject2(pr));
        Project prj = ProjectManager.getDefault().findProject(pr);
        assertNotNull(prj);
        assertNotNull(prj.getLookup().lookup(Foo.class));

        assertNotNull(fakeBuild);
        File file = fakeBuild.toFile();
        assertNotNull(file);
        file = FileUtil.normalizeFile(file);
        assertNotNull(file);
        FileObject fo = FileUtil.toFileObject(file);
        assertNotNull(fo);
        Project p = FileOwnerQuery.getOwner(fo);
        assertNotNull(p);
        assertSame(p, prj);
    }

    @Test
    public void testStrategyForProjectIsUsed() throws IOException {
        FoldersLookupStrategy str = FoldersLookupStrategy.get(fakeBuild);
        assertSize(1, str.find(JAVA_SOURCES));
        assertSize(1, str.find(CLASS_OUTPUT));
        assertSize(2, str.find(ANTLR_GRAMMAR_SOURCES));
        assertRelativeFile(prj1, str.findFirst(JAVA_SOURCES), "src");
        assertRelativeFile(prj1, str.findFirst(CLASS_OUTPUT), "bld/cl");
        assertRelativeFile(prj1, str.findFirst(JAVA_GENERATED_SOURCES), "bld/gen");
        Iterator<Path> it = str.find(ANTLR_GRAMMAR_SOURCES).iterator();
        assertTrue(it.hasNext());
        // ensure sorted
        List<Path> l = toList(str.find(ANTLR_GRAMMAR_SOURCES));
        assertRelativeFile(prj1, l.get(0), "antlr");
        assertRelativeFile(prj1, l.get(1), "antlr2");
    }

    @Test
    public void testInternalStrategyIsPreferred() throws IOException {
        FoldersLookupStrategy str = FoldersLookupStrategy.get(prj2);
        assertRelativeFile(prj2, str.findFirst(ANTLR_GRAMMAR_SOURCES), "rltna");
    }

    @Test
    public void testHeuristicStrategyIsFallback() throws IOException {
        FoldersLookupStrategy str = FoldersLookupStrategy.get(prj3);
        assertTrue(!str.find(ANTLR_GRAMMAR_SOURCES).iterator().hasNext());
        Path p = str.findFirst(JAVA_SOURCES);
        assertNotNull(p, "Heuristic should have recognized src dir");
        AntlrConfiguration config = str.antlrConfig();
        assertNotNull(config);
        System.out.println("CONFIG: " + config);
    }

    @Test
    public void testGrammarProjectHasGuessedConfig() throws Exception {
        AntlrConfiguration cfig = FoldersLookupStrategy.get(grammarProject).antlrConfig();
        assertNotNull(cfig);
        System.out.println("CONFIG: \n" + cfig);
    }

    private static void assertSize(int size, Iterable<?> iter) {
        int ct = 0;
        for (Object ignored : iter) {
            ct++;
        }
        assertEquals(size, ct, iter.toString());
    }

    private static void assertRelativeFile(Path to, Path file, String name) {
        String rel = to.relativize(file).toString();
        assertEquals(rel, name);
    }

    static <T extends Comparable<T>> List<T> toList(Iterable<T> iter) {
        List<T> result = new ArrayList<>();
        iter.forEach(result::add);
        Collections.sort(result);
        return result;
    }

    private static Path dir;
    private static Path fakeBuild;
    private static Path prj1;
    private static Map<String, FileObject> lkp1;
    private static Project project1;

    private static Path prj2;
    private static Project project2;
    private static Map<String, FileObject> lkp2;

    private static Path prj3;
    private static Project project3;
    private static Map<String, FileObject> lkp3;

    private static Path realMavenProject;
    private static Path pomFile;
    private static Project grammarProject;

    @BeforeAll
    public static void setup() throws IOException, URISyntaxException {
        lkp1 = new HashMap<>();
        MockServices.setServices(PF.class, TestStrategyFactory.class, OSF.class,
                NbMavenProjectFactory.class, NoStrategyProjectFactory.class,
                HeuristicFoldersHelperImplementation.HeuristicImplementationFactory.class);
        dir = FileUtils.newTempDir("flst-");
        prj1 = createStructure(dir, CUST_PRJ, lkp1, p -> {
            project1 = p;
        });
        assertNotNull(project1);
        assertNotNull(prj1);
        fakeBuild = prj1.resolve("fakeBuild.thing");
        assertTrue(Files.exists(fakeBuild));

        lkp2 = new HashMap<>();
        prj2 = createStructure(dir, CUST2, lkp2, p -> {
            project2 = p;
        });
        assertNotNull(project2);
        assertTrue(project2.getLookup().lookup(Strat.class) != null,
                "Second project " + prj2 + " resolved as wrong type "
                + project2.getClass().getName());

        lkp3 = new HashMap<>();
        prj3 = createStructure(dir, CUST3, lkp2, p -> {
            project3 = p;
        });
        assertNotNull(project3);
        assertTrue(project3.getLookup().lookup(Bar.class) != null,
                "Second project " + prj3 + " resolved as wrong type "
                + project3.getClass().getName());

        realMavenProject = findAntlrGrammarProject();
        pomFile = realMavenProject.resolve("pom.xml");
        assertTrue(Files.exists(pomFile));
        grammarProject = ProjectManager.getDefault().findProject(FileUtil.toFileObject(FileUtil.normalizeFile(realMavenProject.toFile())));
        assertNotNull(grammarProject, "Project manager could not resolve maven project");
    }

    @Test
    public void testHeuristicsOverAntlrProject() throws URISyntaxException, IOException {
        realMavenProject = findAntlrGrammarProject();
        pomFile = realMavenProject.resolve("pom.xml");
        assertTrue(Files.exists(pomFile));
        grammarProject = ProjectManager.getDefault().findProject(FileUtil.toFileObject(FileUtil.normalizeFile(realMavenProject.toFile())));
        assertNotNull(grammarProject, "Project manager could not resolve maven project");

        System.out.println("\nGUESSED CONFIG:");
        AntlrConfiguration config = AntlrConfiguration.forProject(grammarProject);
        System.out.println(config);
        Path expectedSourceDir = realMavenProject.resolve("src/main/antlr4");
        assertEquals(expectedSourceDir, config.antlrSourceDir());
        Path expectedImportDir = realMavenProject.resolve("src/main/antlr4/imports");
        assertEquals(expectedImportDir, config.antlrImportDir());
        assertTrue(config.visitor());
        assertTrue(config.listener());
        Path expectedAntlrOutputDir = realMavenProject.resolve("target/generated-sources/antlr4");
        assertEquals(expectedAntlrOutputDir, config.antlrSourceOutputDir());
        Path expectedClassesDir = realMavenProject.resolve("target/classes");
        assertEquals(expectedClassesDir, config.buildOutput());

        System.out.println("\n\n");
    }

    @AfterAll
    public static void tearDown() throws IOException {
        FileUtils.deltree(dir);
        lkp1.clear();
        lkp2.clear();
    }

    private static final String[] CUST_PRJ = new String[]{
        "prj1",
        "~prj1/fakeBuild.thing:<buildThing></buildThing>\n",
        "prj1/src",
        "~prj1/antlr/org/whatevs/SomeGrammar.g4:lexer grammar SomeGrammar;\n\nFoo : 'x';\n",
        "prj1/antlr2/imports",
        "prj1/bld/gen",
        "prj1/bld/cl",};

    private static final String[] CUST2 = new String[]{
        "prj2",
        "prj2/rltna",
        "prj2/ecruos",
        "prj2/dliub",
        "~prj2/lmx.dliub:wookie",};

    private static final String[] CUST3 = new String[]{
        "prj3",
        "~prj3/bubu.build:buildMe!",
        "prj3/src"
    };

    public static final class TestStrategyFactory implements FoldersLookupStrategyImplementationFactory {

        @Override
        public FolderLookupStrategyImplementation create(Project project, FolderQuery initialQuery) {
//            FileObject fo = project.getProjectDirectory().getFileObject("fakeBuild.thing");
//            if (fo != null) {
//                return new TestStrategy(project, initialQuery);
//            }
//            return null;
            if (project != null && project.getLookup().lookup(Foo.class) != null) {
                return new TestStrategy(project, initialQuery);
            }
            return null;
        }

        @Override
        public void collectImplementationNames(Set<? super String> into) {
            into.add("Things");
        }

        static class TestStrategy implements FolderLookupStrategyImplementation {

            private final Project project;

            public TestStrategy(Project project, FolderQuery initial) {
                this.project = project;
            }

            @Override
            public String toString() {
                return "TestStrategy(" + project + ")";
            }

            @Override
            public Iterable<Path> find(Folders folder, FolderQuery query) throws IOException {
                FileObject fo = null;
                switch (folder) {
                    case ANTLR_GRAMMAR_SOURCES:
//                        return findFolders("antlr", project.getProjectDirectory());
                        return scanFor(project.getProjectDirectory(), pth -> {
                            return pth.getFileName().toString().startsWith("antlr");
                        });
                    case CLASS_OUTPUT:
                        fo = project.getProjectDirectory().getFileObject("bld/cl");
                        break;
                    case ANTLR_IMPORTS:
                        List<Path> result = new ArrayList<>(3);
                        for (Path p : find(Folders.ANTLR_GRAMMAR_SOURCES, query)) {
                            result.add(p);
                        }
                        return result;
                    case JAVA_SOURCES:
                        fo = project.getProjectDirectory().getFileObject("src");
                        break;
                    case JAVA_GENERATED_SOURCES:
                        fo = project.getProjectDirectory().getFileObject("bld/gen");
                        break;
                }
                if (fo == null) {
                    return Collections.emptyList();
                }
                return Collections.<Path>singleton(FileUtil.toFile(fo).toPath());
            }

            @Override
            public String name() {
                return "Things";
            }

            @Override
            public Iterable<Path> allFiles(Folders type) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        }
    }

    public static final class PF implements ProjectFactory2 {

        @Override
        public ProjectManager.Result isProject2(FileObject projectDirectory) {
            if (isProject(projectDirectory)) {
                return new ProjectManager.Result(projectDirectory.getName(), projectDirectory.getName(), null);
            }
            return null;
        }

        @Override
        public boolean isProject(FileObject projectDirectory) {
            boolean result = projectDirectory.getFileObject("fakeBuild.thing") != null;
            return result;
        }

        @Override
        public Project loadProject(FileObject projectDirectory, ProjectState state) throws IOException {
            if (isProject(projectDirectory)) {
                return new Prj(projectDirectory, state);
            }
            return null;
        }

        @Override
        public void saveProject(Project project) throws IOException, ClassCastException {
            // do nothing
        }
    }

    static class Prj implements Project {

        private final FileObject dir;
        private final ProjectState state;

        public Prj(FileObject dir, ProjectState state) {
            this.dir = dir;
            this.state = state;
        }

        @Override
        public FileObject getProjectDirectory() {
            return dir;
        }

        @Override
        public Lookup getLookup() {
            return Lookups.fixed(dir, new Foo(), this);
        }

        @Override
        public String toString() {
            return "PRJ(" + dir.getPath() + ")";
        }
    }

    public static class NoStrategyProjectFactory implements ProjectFactory2 {

        @Override
        public ProjectManager.Result isProject2(FileObject projectDirectory) {
            if (isProject(projectDirectory)) {
                return new ProjectManager.Result(projectDirectory.getName(), projectDirectory.getName(), null);
            }
            return null;
        }

        @Override
        public boolean isProject(FileObject projectDirectory) {
            boolean result = projectDirectory.getFileObject("bubu.build") != null;
            return result;
        }

        @Override
        public Project loadProject(FileObject projectDirectory, ProjectState state) throws IOException {
            if (isProject(projectDirectory)) {
                return new NoStrategyProject(projectDirectory, state);
            }
            return null;
        }

        @Override
        public void saveProject(Project project) throws IOException, ClassCastException {
            // do nothing
        }
    }

    public static class NoStrategyProject implements Project {

        private final Lookup lkp = Lookups.fixed(this, new Bar());
        private final FileObject dir;
        private final ProjectState state;

        public NoStrategyProject(FileObject dir, ProjectState state) {
            this.dir = dir;
            this.state = state;
        }

        @Override
        public FileObject getProjectDirectory() {
            return dir;
        }

        @Override
        public Lookup getLookup() {
            return lkp;
        }

        @Override
        public String toString() {
            return "NoStrategyProject(" + dir.getPath() + ")";
        }
    }

    public static class OSF implements ProjectFactory2 {

        @Override
        public ProjectManager.Result isProject2(FileObject projectDirectory) {
            if (isProject(projectDirectory)) {
                return new ProjectManager.Result(projectDirectory.getName(), projectDirectory.getName(), null);
            }
            return null;
        }

        @Override
        public boolean isProject(FileObject projectDirectory) {
            return projectDirectory.getFileObject("lmx.dliub") != null;
        }

        @Override
        public Project loadProject(FileObject projectDirectory, ProjectState state) throws IOException {
            if (isProject(projectDirectory)) {
                return new ProjectWithOwnStrategy(projectDirectory, state);
            }
            return null;
        }

        @Override
        public void saveProject(Project project) throws IOException, ClassCastException {
            // do nothing
        }
    }

    static class ProjectWithOwnStrategy implements Project {

        private final FileObject dir;
        private final ProjectState state;
        private final Lookup lkp = Lookups.fixed(this, new Strat());

        public ProjectWithOwnStrategy(FileObject dir, ProjectState state) {
            this.dir = dir;
            this.state = state;
        }

        @Override
        public FileObject getProjectDirectory() {
            return dir;
        }

        @Override
        public Lookup getLookup() {
            return lkp;
        }

        public String toString() {
            return getClass().getSimpleName() + '(' + dir.getPath() + ')';
        }

        final class Strat implements FolderLookupStrategyImplementation {

            @Override
            public Iterable<Path> find(Folders folder, FolderQuery query) throws IOException {
                FileObject fo = null;
                switch (folder) {
                    case ANTLR_GRAMMAR_SOURCES:
                        fo = dir.getFileObject("rltna");
                        break;
                    case JAVA_SOURCES:
                        fo = dir.getFileObject("ecruos");
                        break;
                    case ANTLR_IMPORTS:
                        fo = dir.getFileObject("rltna/stropmi");
                        break;
                    case CLASS_OUTPUT:
                        fo = dir.getFileObject("dliub/sessalc");
                        break;
                }
                return fo == null ? Collections.emptySet()
                        : Collections.singleton(FileUtil.toFile(fo).toPath());
            }

            @Override
            public String name() {
                return "Custom(" + ProjectWithOwnStrategy.this + ")";
            }

            @Override
            public String toString() {
                return name();
            }

            @Override
            public Iterable<Path> allFiles(Folders type) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }
    }

    static class Foo {

    }

    static class Bar {

    }
}
