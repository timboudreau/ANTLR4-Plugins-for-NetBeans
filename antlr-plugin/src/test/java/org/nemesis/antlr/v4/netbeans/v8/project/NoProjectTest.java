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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;
import org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristic;

/**
 *
 * @author Tim Boudreau
 */
public class NoProjectTest {

    private static TestProjectStructure structure;
    private static Path noProjectPath;

    private static final String[] NO_PROJECT_JUST_FILES = {
        "src/com/foo/bar/ANTLRv4Lexer.g4",
        "src/com/foo/bar/ANTLRv4.g4",
        "imports/LexBasic.g4",
        "src/com/foo/bar/Foo.java",
        "target/generated-sources/antlr4/com/foo/bar",
        "target/classes/com/foo/bar/ANTLRv4.tokens"
    };

//    @Test
    public void testNoProject() throws IOException {
        Path path = noProjectPath.resolve("src/com/foo/bar/ANTLRv4Lexer.g4");
        assertTrue("File not generated: " + path, Files.exists(path));

        Optional<Path> sourcePath = ProjectType.MAVEN_BASED.antlrArtifactFolder(path, AntlrFolders.IMPORT);
        assertNotNull(sourcePath);
        assertTrue("Source folder not found", sourcePath.isPresent());
        assertEquals(noProjectPath.resolve("src"), sourcePath.get());

        Optional<Path> outputPath = ProjectType.MAVEN_BASED.antlrArtifactFolder(path, AntlrFolders.OUTPUT);
        assertNotNull(outputPath);
        assertTrue("Output folder not found", outputPath.isPresent());
        assertEquals(noProjectPath.resolve("/tmp"), outputPath.get());

        Optional<Path> importFolder = ProjectType.MAVEN_BASED.antlrArtifactFolder(path, AntlrFolders.IMPORT);
        assertNotNull(importFolder);
        assertTrue("Import folder not found", importFolder.isPresent());
        assertEquals(noProjectPath.resolve("imports"), importFolder.get());
    }

    @Test
    public void testHeuristics() {
        Path path = noProjectPath.resolve("src/com/foo/bar/ANTLRv4Lexer.g4");
        Path source = noProjectPath.resolve("src");

        FileLocationHeuristic heuristic = FileLocationHeuristic.ancestorNamed("src");

        testHeuristic(path, heuristic, source);

        heuristic = FileLocationHeuristic.ancestorNamed("src").or(FileLocationHeuristic.ancestorNamed("xxxx"));

        testHeuristic(path, heuristic, source);

        heuristic = FileLocationHeuristic.ancestorNamed("xxx").or(FileLocationHeuristic.ancestorNamed("src"));

        testHeuristic(path, heuristic, source);

        heuristic = FileLocationHeuristic.ancestorWithChildNamed("imports")
                .or(FileLocationHeuristic.ancestorNamed("src")
                        .or(FileLocationHeuristic.ancestorNamed("grammar"))
                        .or(FileLocationHeuristic.ancestorNamed("antlr4"))
                        .or(FileLocationHeuristic.ancestorNamed("antlr"))
                        .or(FileLocationHeuristic.parentIsProjectRoot()));

    }

    private void testHeuristic(Path path, FileLocationHeuristic heuristic, Path expect) {
        Optional<Path> result = heuristic.locate(path);
        if (expect != null) {
            assertTrue(result.isPresent());
            assertEquals(expect, result.get());
        } else {
            assertFalse(result.isPresent());
        }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        structure = new TestProjectStructure(true, ProjectTypeTest.class,
                "no-project", ProjectTypeTest::getContent, NO_PROJECT_JUST_FILES);
        noProjectPath = structure.path();
    }

    @AfterClass
    public static void shutdown() throws Exception {
        if (structure != null) {
            structure.delete();
        }
    }

}
