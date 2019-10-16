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
package org.nemesis.antlr.grammar.file.resolver;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.junit.MockServices;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrFileObjectRelativeResolverTest {

    private Path grammarProject;
    private FileObject projectFO;
    private Project mavenProject;

    private Path childOfParentThatChangesDirs;
    private Project childOfParentThatChangesDirsProject;

    private Path childOfParentThatChangesDirsAndEncoding;
    private Project childOfParentThatChangesDirsAndEncodingProject;

    @Test
    public void testRelatedFilesAreResolved() {
        Path lexerGrammar = grammarProject.resolve("src/main/antlr4/org/nemesis/antlr/ANTLRv4Lexer.g4");
        Path lexBasic = grammarProject.resolve("src/main/antlr4/imports/LexBasic.g4");
        assertTrue(Files.exists(lexerGrammar));
        GrammarSource<Path> gs = GrammarSource.find(lexerGrammar, "text/x-g4");
        assertNotNull(gs);
        Optional<Path> opath = gs.lookup(Path.class);
        assertNotNull(opath);
        assertTrue(opath.isPresent());
        assertEquals(lexerGrammar, opath.get());
        GrammarSource<?> resolved = gs.resolveImport("LexBasic");
        assertNotNull(resolved);
        Optional<Path> resolvedFile = resolved.lookup(Path.class);
        assertNotNull(resolvedFile);
        assertTrue(resolvedFile.isPresent());
        assertEquals(lexBasic, resolvedFile.get());
    }

    @BeforeEach
    public void setup() throws URISyntaxException, IOException {
        MockServices.setServices(MavenFolderStrategyFactory.class, NbMavenProjectFactory.class);
        grammarProject = ProjectTestHelper.findAntlrGrammarProject();
        projectFO = FileUtil.toFileObject(FileUtil.normalizeFile(grammarProject.toFile()));
        mavenProject = ProjectManager.getDefault().findProject(projectFO);
        assertNotNull(mavenProject);

        childOfParentThatChangesDirs = ProjectTestHelper.projectBaseDir()
                .resolve("../antlr-project-helpers-maven/antlr-maven-test-projects/child-with-changed-antlr-dir").normalize();
        assertTrue(Files.exists(childOfParentThatChangesDirs), childOfParentThatChangesDirs
                + " does not exist");
        childOfParentThatChangesDirsProject = ProjectManager.getDefault().findProject(
                FileUtil.toFileObject(FileUtil.normalizeFile(childOfParentThatChangesDirs.toFile())));
        assertNotNull(childOfParentThatChangesDirsProject);

        childOfParentThatChangesDirsAndEncoding = ProjectTestHelper.projectBaseDir()
                .resolve("../antlr-project-helpers-maven/antlr-maven-test-projects/other-parent-that-changes-encoding/child-with-changed-antlr-dir-and-encoding").normalize();
        assertTrue(Files.exists(childOfParentThatChangesDirsAndEncoding),
                childOfParentThatChangesDirsAndEncoding + " does not exist");
        childOfParentThatChangesDirsAndEncodingProject = ProjectManager.getDefault().findProject(
                FileUtil.toFileObject(FileUtil.normalizeFile(childOfParentThatChangesDirsAndEncoding.toFile())));
        assertNotNull(childOfParentThatChangesDirsAndEncodingProject);
    }
}
