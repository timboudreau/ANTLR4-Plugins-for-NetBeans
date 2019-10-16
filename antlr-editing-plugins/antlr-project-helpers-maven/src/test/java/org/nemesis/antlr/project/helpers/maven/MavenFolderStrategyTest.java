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
package org.nemesis.antlr.project.helpers.maven;

import java.io.IOException;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.project.FoldersLookupStrategy;
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
public class MavenFolderStrategyTest {

    private Path grammarProject;
    private FileObject projectFO;
    private Project mavenProject;

    private Path childOfParentThatChangesDirs;
    private Project childOfParentThatChangesDirsProject;

    private Path childOfParentThatChangesDirsAndEncoding;
    private Project childOfParentThatChangesDirsAndEncodingProject;

    @Test
    public void testAntlrGrammarProject() {
        FoldersLookupStrategy strat = FoldersLookupStrategy.get(mavenProject);
        assertNotNull(strat);
        AntlrConfiguration config = AntlrConfiguration.forProject(mavenProject);
        assertNotNull(config);
        assertEquals("Maven", config.createdBy());
        assertTrue(strat.toString().contains("Maven"));
        strat.allFileObjects(Folders.ANTLR_GRAMMAR_SOURCES).forEach(fo -> {
            System.out.println(fo.getPath());
        });
    }

    @Test
    public void testDirsAlteredByParentProject() throws Exception {
        FoldersLookupStrategy strat = FoldersLookupStrategy.get(childOfParentThatChangesDirsProject);
        System.out.println("\n\nALTERED PROPS CONFIG: ");
        AntlrConfiguration config = AntlrConfiguration.forProject(childOfParentThatChangesDirsProject);
        System.out.println(config);

        Path expectedAntlrImportDir = childOfParentThatChangesDirs.resolve("src/main/antlr/lib");
        assertEquals(expectedAntlrImportDir, config.antlrImportDir());
        assertTrue(config.forceATN());
        assertEquals(Arrays.asList(expectedAntlrImportDir), toList(strat.find(Folders.ANTLR_IMPORTS)));
        assertFalse(config.listener());

        Path expectedAntlrSourceDir = childOfParentThatChangesDirs.resolve("src/main/antlr/source");
        assertEquals(expectedAntlrSourceDir, config.antlrSourceDir());
        assertEquals(Arrays.asList(expectedAntlrSourceDir), toList(strat.find(Folders.ANTLR_GRAMMAR_SOURCES)));
    }

    @Test
    public void testDirsAndEncodingAlteredByParentProjects() throws Exception {
        FoldersLookupStrategy strat = FoldersLookupStrategy.get(childOfParentThatChangesDirsAndEncodingProject);
        System.out.println("\n\nDIRS AND ENCODING CONFIG: ");
        AntlrConfiguration config = AntlrConfiguration.forProject(childOfParentThatChangesDirsAndEncodingProject);
        System.out.println(config);

        Path expectedAntlrImportDir = childOfParentThatChangesDirsAndEncoding.resolve("src/main/antlr/lib");
        assertEquals(expectedAntlrImportDir, config.antlrImportDir());
        assertFalse(config.forceATN());
        assertFalse(config.visitor());
        assertTrue(config.atn());
        assertEquals(Arrays.asList(expectedAntlrImportDir), toList(strat.find(Folders.ANTLR_IMPORTS)));
        assertTrue(config.listener());

        Path expectedAntlrSourceDir = childOfParentThatChangesDirsAndEncoding.resolve("src/main/antlr/source");
        assertEquals(expectedAntlrSourceDir, config.antlrSourceDir());
        assertEquals(Arrays.asList(expectedAntlrSourceDir), toList(strat.find(Folders.ANTLR_GRAMMAR_SOURCES)));
        assertEquals(US_ASCII, config.encoding());
    }

    @Test
    public void testProjectsHaveNoUnresolvedProperties() {
        Pattern pat = Pattern.compile(".*?\\{(.*?)\\}.*");
        for (Project project : new Project[]{mavenProject, childOfParentThatChangesDirsAndEncodingProject, childOfParentThatChangesDirsProject}) {
            AntlrConfiguration config = AntlrConfiguration.forProject(project);
            for (Folders f : Folders.values()) {
                for (Path p : f.find(project)) {
                    String s = p.toString();
                    Matcher m = pat.matcher(s);
                    if (m.find()) {
                        String name = project.getProjectDirectory().getName();
                        fail(name + " contains unresolved '" + m.group(1) + "' in " + f + ": " + p);
                    }
                }
            }
            for (Path path : new Path[]{config.antlrImportDir(), config.antlrSourceDir(),
                config.antlrSourceOutputDir(), config.buildDir(), config.buildOutput(),
                config.javaSources(), config.testOutput(), config.testSources()}) {
                String s = path.toString();
                Matcher m = pat.matcher(s);
                if (m.find()) {
                    String name = project.getProjectDirectory().getName();
                    fail(name + " contains unresolved '" + m.group(1) + " in " + path);
                }
            }
        }
    }

    static <T> List<T> toList(Iterable<T> it) {
        List<T> result = new ArrayList<>();
        for (T t : it) {
            result.add(t);
        }
        return result;
    }

    @BeforeEach
    public void setup() throws URISyntaxException, IOException {
        MockServices.setServices(MavenFolderStrategyFactory.class, NbMavenProjectFactory.class);
        grammarProject = ProjectTestHelper.findAntlrGrammarProject();
        projectFO = FileUtil.toFileObject(FileUtil.normalizeFile(grammarProject.toFile()));
        mavenProject = ProjectManager.getDefault().findProject(projectFO);
        assertNotNull(mavenProject);

        childOfParentThatChangesDirs = ProjectTestHelper.projectBaseDir()
                .resolve("antlr-maven-test-projects/child-with-changed-antlr-dir").normalize();
        assertTrue(Files.exists(childOfParentThatChangesDirs));
        childOfParentThatChangesDirsProject = ProjectManager.getDefault().findProject(
                FileUtil.toFileObject(FileUtil.normalizeFile(childOfParentThatChangesDirs.toFile())));
        assertNotNull(childOfParentThatChangesDirsProject);

        childOfParentThatChangesDirsAndEncoding = ProjectTestHelper.projectBaseDir()
                .resolve("antlr-maven-test-projects/other-parent-that-changes-encoding/child-with-changed-antlr-dir-and-encoding").normalize();
        assertTrue(Files.exists(childOfParentThatChangesDirsAndEncoding));
        childOfParentThatChangesDirsAndEncodingProject = ProjectManager.getDefault().findProject(
                FileUtil.toFileObject(FileUtil.normalizeFile(childOfParentThatChangesDirsAndEncoding.toFile())));
        assertNotNull(childOfParentThatChangesDirsAndEncodingProject);
    }
}
