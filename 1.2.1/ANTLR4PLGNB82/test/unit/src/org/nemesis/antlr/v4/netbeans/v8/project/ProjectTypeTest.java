package org.nemesis.antlr.v4.netbeans.v8.project;

import static com.google.common.base.Charsets.UTF_8;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir.projectBaseDir;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.MavenProjectHelper;
import org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristic;
import org.nemesis.antlr.v4.netbeans.v8.util.FileLocator;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public class ProjectTypeTest {

    private static Set<TestProjectStructure> createdProjects = new HashSet<>();

    private static Path simpleMavenProjectPath;
    private static Project simpleMavenProject;
    private static Path nonStandardMavenProjectPath;
    private static Project nonStandardMavenProject;

    private static final String[] SIMPLE_MAVEN_PROJECT_FILES = {
        "pom-1.xml",
        "src/main/antlr4/com/foo/bar/ANTLRv4Lexer.g4",
        "src/main/antlr4/com/foo/bar/ANTLRv4.g4",
        "src/main/antlr4/imports/LexBasic.g4",
        "src/main/java/com/foo/bar/Foo.java",
        "target/generated-sources/antlr4/com/foo/bar",
        "target/classes/com/foo/bar/ANTLRv4.tokens"
    };

    static final String[] NON_STANDARD_LAYOUT_MAVEN_PROJECT_FILES = {
        "pom-2.xml",
        "other/com/foo/bar/ANTLRv4Lexer.g4",
        "other/com/foo/bar/ANTLRv4.g4",
        "other/imports/LexBasic.g4",
        "src/main/java/com/foo/bar/Foo.java",
        "target/foo/com/foo/bar",
        "target/classes/com/foo/bar/ANTLRv4.tokens"
    };

    @BeforeClass
    public static void setUpTestProjects() throws Exception {
        TestProjectStructure structure = new TestProjectStructure(ProjectTypeTest.class,
                "simple-maven", ProjectTypeTest::getContent, SIMPLE_MAVEN_PROJECT_FILES);
        createdProjects.add(structure);
        simpleMavenProjectPath = structure.path();
        simpleMavenProject = structure.project();

        structure = new TestProjectStructure(ProjectTypeTest.class,
                "non-standard", ProjectTypeTest::getContent, NON_STANDARD_LAYOUT_MAVEN_PROJECT_FILES);
        createdProjects.add(structure);
        nonStandardMavenProjectPath = structure.path();
        nonStandardMavenProject = structure.project();
    }

    @AfterClass
    public static void deleteProjects() throws Exception {
        Exception ex = null;
        try {
            for (TestProjectStructure structure : createdProjects) {
                try {
                    structure.delete();
                } catch (Exception e1) {
                    if (ex == null) {
                        ex = e1;
                    } else {
                        ex.addSuppressed(e1);
                    }
                }
            }
        } finally {
            if (ex != null) {
                throw ex;
            }
        }
    }

    @Test
    public void testNonStandardLayout() throws IOException {
        Path path = nonStandardMavenProjectPath.resolve("other/com/foo/bar/ANTLRv4Lexer.g4");
        assertTrue("File not generated: " + path, Files.exists(path));
        Optional<Path> importFolder = ProjectType.MAVEN_BASED.antlrArtifactFolder(path, AntlrFolders.IMPORT);
        assertNotNull(importFolder);
        assertTrue("Import folder not found", importFolder.isPresent());
        assertEquals(nonStandardMavenProjectPath.resolve("other/imports"), importFolder.get());
    }

    @Test
    public void testProjectIsRecognized() throws IOException {
        assertNotNull(simpleMavenProjectPath);
        FileObject fo = FileUtil.toFileObject(simpleMavenProjectPath.toFile());
        assertNotNull(fo);
        Project p = ProjectManager.getDefault().findProject(fo);
        assertNotNull(p);
        NbMavenProject mavenProject = p.getLookup().lookup(NbMavenProject.class);
        assertNotNull("maven project type did not recognize " + p, mavenProject);
    }

    @Test
    public void testMavenProjectHelper() throws Exception {
        MavenProjectHelper<Project> helper = MavenProjectHelper.forProjects();
        helper.antlrSourceFolderIfExists(simpleMavenProject, AntlrFolders.SOURCE, (err, res) -> {
            if (err.isPresent()) {
                fail("Did not find source folder: " + err.get());
            }
            assertEquals(simpleMavenProjectPath.resolve("src/main/antlr4"), res);
            return res;
        });
        helper.antlrSourceFolderIfExists(simpleMavenProject, AntlrFolders.IMPORT, (err, res) -> {
            if (err.isPresent()) {
                fail("Did not find import folder: " + err.get());
            }
            assertEquals(simpleMavenProjectPath.resolve("src/main/antlr4/imports"), res);
            return res;
        });
        helper.antlrSourceFolderIfExists(simpleMavenProject, AntlrFolders.OUTPUT, (err, res) -> {
            if (err.isPresent()) {
                fail("Did not find import folder: " + err.get());
            }
            assertEquals(simpleMavenProjectPath.resolve("target/generated-sources/antlr4"), res);
            return res;
        });

        helper.antlrSourceFolderIfExists(nonStandardMavenProject, AntlrFolders.IMPORT, (err, res) -> {
            if (err.isPresent()) {
                fail("Did not find import folder: " + err.get());
            }
            assertEquals(nonStandardMavenProjectPath.resolve("other/imports"), res);
            return res;
        });

        MavenProjectHelper<Path> pathHelper = MavenProjectHelper.forOwningProjectOfFile();
        Path path = simpleMavenProjectPath.resolve(SIMPLE_MAVEN_PROJECT_FILES[1]);

        pathHelper.antlrSourceFolderIfExists(path, AntlrFolders.SOURCE, (err, res) -> {
            if (err.isPresent()) {
                fail("Did not find source folder from source file: " + err.get());
            }
            assertEquals(simpleMavenProjectPath.resolve("src/main/antlr4"), res);
            return res;
        });
        pathHelper.antlrSourceFolderIfExists(path, AntlrFolders.IMPORT, (err, res) -> {
            if (err.isPresent()) {
                fail("Did not find import folder from source file: " + err.get());
            }
            assertEquals(simpleMavenProjectPath.resolve("src/main/antlr4/imports"), res);
            return res;
        });
        pathHelper.antlrSourceFolderIfExists(path, AntlrFolders.OUTPUT, (err, res) -> {
            if (err.isPresent()) {
                fail("Did not find import folder from source file: " + err.get());
            }
            assertEquals(simpleMavenProjectPath.resolve("target/generated-sources/antlr4"), res);
            return res;
        });

        path = simpleMavenProjectPath.resolve(SIMPLE_MAVEN_PROJECT_FILES[1]);
        assertTrue(Files.exists(path));
        assertNotNull("Project manager no longer sees an owner for " + path,
                FileOwnerQuery.getOwner(
                        FileUtil.toFileObject(
                                FileUtil.normalizeFile(path.toFile()))));

        pathHelper.antlrSourceFolderIfExists(path, AntlrFolders.SOURCE, (err, res) -> {
            if (err.isPresent()) {
                fail("Did not find source folder from import file: " + err.get());
            }
            assertEquals(simpleMavenProjectPath.resolve("src/main/antlr4"), res);
            return res;
        });
        pathHelper.antlrSourceFolderIfExists(path, AntlrFolders.IMPORT, (err, res) -> {
            if (err.isPresent()) {
                fail("Did not find import folder from import file: " + err.get());
            }
            assertEquals(simpleMavenProjectPath.resolve("src/main/antlr4/imports"), res);
            return res;
        });
        pathHelper.antlrSourceFolderIfExists(path, AntlrFolders.OUTPUT, (err, res) -> {
            if (err.isPresent()) {
                fail("Did not find import folder from import file: " + err.get());
            }
            assertEquals(simpleMavenProjectPath.resolve("target/generated-sources/antlr4"), res);
            return res;
        });
    }

    @Test
    public void testLookupImportFolderFromSourceFile() {
        Path path = simpleMavenProjectPath.resolve("src/main/antlr4/com/foo/bar/ANTLRv4Lexer.g4");
        assertTrue("File not generated: " + path, Files.exists(path));
        Optional<Path> importFolder = ProjectType.MAVEN_BASED.antlrArtifactFolder(path, AntlrFolders.IMPORT);
        assertNotNull(importFolder);
        assertTrue("Import folder not found", importFolder.isPresent());
        assertEquals(simpleMavenProjectPath.resolve("src/main/antlr4/imports"), importFolder.get());
    }

    @Test
    public void testLookupImportFolderFromImportFile() {
        Path path = simpleMavenProjectPath.resolve("src/main/antlr4/imports/LexBasic.g4");
        assertTrue("File not generated: " + path, Files.exists(path));
        Optional<Path> importFolder = ProjectType.MAVEN_BASED.antlrArtifactFolder(path, AntlrFolders.IMPORT);
        assertNotNull(importFolder);
        assertTrue("Import folder not found", importFolder.isPresent());
        assertEquals(simpleMavenProjectPath.resolve("src/main/antlr4/imports"), importFolder.get());
    }

    @Test
    public void testLookupSourceFolder() {
        Path path = simpleMavenProjectPath.resolve("src/main/antlr4/com/foo/bar/ANTLRv4Lexer.g4");
        assertTrue("File not generated: " + path, Files.exists(path));
        Optional<Path> importFolder = ProjectType.MAVEN_BASED.antlrArtifactFolder(path, AntlrFolders.SOURCE);
        assertNotNull(importFolder);
        assertTrue("Source folder not found", importFolder.isPresent());
        assertEquals(simpleMavenProjectPath.resolve("src/main/antlr4"), importFolder.get());
    }

    @Test
    public void testLookupSourceFolderFromImportFile() {
        Path path = simpleMavenProjectPath.resolve("src/main/antlr4/imports/LexBasic.g4");
        assertTrue("File not generated: " + path, Files.exists(path));
        Optional<Path> sourceFolder = ProjectType.MAVEN_BASED.antlrArtifactFolder(path, AntlrFolders.SOURCE);
        assertNotNull("Source folder not found", sourceFolder);
        assertTrue(sourceFolder.isPresent());
        assertEquals(simpleMavenProjectPath.resolve("src/main/antlr4"), sourceFolder.get());
    }

    @Test
    public void testHeuristics() {
        Path path = simpleMavenProjectPath.resolve("src/main/antlr4/com/foo/bar/ANTLRv4Lexer.g4");
        FileLocator loc = FileLocator.create((Path t) -> Optional.empty())
                .stoppingAtProjectRoot()
                .withFallback(FileLocationHeuristic.childOfAncestorNamed("imports")
                        .or(FileLocationHeuristic.ancestorNamed("imports")));

        Optional<Path> got = loc.locate(path);
        assertNotNull(got);
        assertTrue(got.isPresent());
        assertEquals(simpleMavenProjectPath.resolve("src/main/antlr4/imports"),
                got.get());

        path = simpleMavenProjectPath.resolve("src/main/antlr4/imports/LexBasic.g4");
        got = loc.locate(path);
        assertNotNull(got);
        assertTrue(got.isPresent());
        assertEquals(simpleMavenProjectPath.resolve("src/main/antlr4/imports"),
                got.get());

        path = simpleMavenProjectPath.resolve("src");
        got = loc.locate(path);
        assertNotNull(got);
        assertFalse(got.isPresent());
    }

    static InputStream getContent(String name) throws IOException, URISyntaxException {
        InputStream result = null;
        Path baseDir = projectBaseDir();
        switch (name) {
            case "ANTLRv4.tokens":
                result = NBANTLRv4Parser.class.getResourceAsStream("impl/" + name);
                assertNotNull("Could not read " + name + " file from classpath", result);
                break;
            case "Foo.java":
                return new ByteArrayInputStream("package com.foo.bar;\n\npublic class Foo {\n}\n".getBytes(UTF_8));
            case "pom-1.xml":
                return ProjectTypeTest.class.getResourceAsStream("pom-1.xml");
            case "pom-2.xml":
                return ProjectTypeTest.class.getResourceAsStream("pom-2.xml");
            case "ANTLRv4Lexer.g4":
            case "ANTLRv4.g4":
                Path grammarPath = baseDir.resolve("grammar/grammar_syntax_checking/").resolve(name);
                assertTrue("Missing test file: " + grammarPath, Files.exists(grammarPath));
                return Files.newInputStream(grammarPath);
            case "LexBasic.g4":
                Path lexerPath = baseDir.resolve("grammar/imports/").resolve(name);
                assertTrue("Missing test file: " + lexerPath, Files.exists(lexerPath));
                return Files.newInputStream(lexerPath);
            // file:/home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/1.2.1/ANTLR4PLGNB82/build/test/unit/classes/
        }
        return null;
    }

}
