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

import com.mastfrog.function.throwing.ThrowingRunnable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.project.AntTestProjects.FOQ;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.nemesis.antlr.project.impl.HeuristicFoldersHelperImplementation;
import org.nemesis.antlr.project.impl.InferredConfig;
import org.nemesis.antlr.project.impl.HeuristicsLogging;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public class TestAntProjectWithHeuristics {

    private static ThrowingRunnable teardown;

    @Test
    public void testProjectsResovable() throws Throwable {
        for (AntTestProjects prj : AntTestProjects.values()) {
            System.out.println("\n------------------- " + prj.name() + " -------------------");
            Project p = prj.project();
            assertNotNull(p);
            for (AntTestProjects.GrammarFileEntry f : prj) {
                if (prj == AntTestProjects.GlobalActions && "GlobalCombinedActions1".equals(f.name())) {
                    HeuristicsLogging.logging(Level.ALL);
                } else {
                    HeuristicsLogging.loggingOff();
                }
                System.out.println("\n  ****************** " + f.name() + " ******************");
                FileObject fo = prj.fileObject(f.name());
                boolean isImport = fo.getParent().getName().equals("imports");

                Iterable<Path> sourceFolders = Folders.ANTLR_GRAMMAR_SOURCES.find(p, fo);

                System.out.println("SOURCE FOLDERS: " + p2s(prj.dir(), sourceFolders));

                System.out.println("    OWNER OF " + fo.getPath());
                Folders owner = Folders.ownerOf(fo);
                assertNotNull(owner);
                System.out.println("\n  -- \n");
                assertEquals(isImport ? Folders.ANTLR_IMPORTS : Folders.ANTLR_GRAMMAR_SOURCES, owner,
                        () -> "Wrong owner for " + f + ": " + owner + " in " + AntlrConfiguration.forFile(fo)
                        + " project " + prj.name());

                AntlrConfiguration config = AntlrConfiguration.forFile(fo);
                Path expectedGrammar = prj.dir().resolve("grammar");
                assertEquals(expectedGrammar, config.antlrSourceDir, "Wrong antlr source dir " + prj.dir().relativize(config.antlrSourceDir)
                        + " in project " + prj.name() + " file entry " + f.name()
                        + " should be " + prj.dir().relativize(expectedGrammar)
                        + " all antlr source folders: " + p2s(prj.dir(), sourceFolders)
                        + " with config " + config);
                if (config.antlrImportDir != null || isImport) {
                    assertEquals(prj.dir().resolve("grammar/imports"), config.antlrImportDir);
                }
                assertEquals("Heuristic", config.createdByStrategy);
                assertTrue(config.visitor);
                assertTrue(config.listener);

                System.out.println("\n\nTHE CONFIG:\n" + config + "\n\n");

                switch (prj) {
                    case Channels:
                    case CodeCompletion:
                    case CodeFolding:
                    case GlobalActions:
                    case Grammars:
                    case LexerRules:
                    case Options:
                    case ParserRules:
                    case Tokens:
                        assertEquals(prj.dir().resolve("grammar"), config.antlrSourceDir);
                        assertEquals(prj.dir().resolve("build/generated-sources/antlr4"), config.outputDir);
                        assertEquals(prj.dir().resolve("build/classes"), config.buildOutput);
                        assertEquals(prj.dir().resolve("build"), config.buildDir);
                }
                switch (prj) {
                    case Options:
                        assertEquals("anotherorg.anotherpackage", FoldersHelperTrampoline.findBestJavaPackageSuggestionForGrammarsWhenAddingAntlr(p));
                        break;
                    case CodeCompletion:
                        assertEquals("mypackage", FoldersHelperTrampoline.findBestJavaPackageSuggestionForGrammarsWhenAddingAntlr(p));
                        break;
                }
            }
            AntlrConfiguration config = AntlrConfiguration.forProject(p);
            switch (prj) {
                case Channels:
                case CodeCompletion:
                case CodeFolding:
                case GlobalActions:
                case Grammars:
                case LexerRules:
                case Options:
                case ParserRules:
                case Tokens:
                    assertEquals(prj.dir().resolve("grammar"), config.antlrSourceDir);
                    assertEquals(prj.dir().resolve("build/generated-sources/antlr4"), config.outputDir);
                    assertEquals(prj.dir().resolve("build/classes"), config.buildOutput);
                    assertEquals(prj.dir().resolve("build"), config.buildDir);
            }
        }
    }

    static String p2s(Path projectDir, Iterable<Path> paths) {
        StringBuilder sb = new StringBuilder();
        for (Path p : paths) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(projectDir.relativize(p));
        }
        return sb.toString();
    }

    @BeforeAll
    public static void setup() throws ClassNotFoundException {
//        Class<?> type = Class.forName("org.netbeans.modules.project.ant.AntBasedGenericType.class");
        teardown = new TestFixtures()
                .verboseGlobalLogging(
                        HeuristicFoldersHelperImplementation.class,
                        InferredConfig.class,
                        FoldersHelperTrampoline.class)
                .addToDefaultLookup(
                        FOQ.class,
                        FoldersLookupStrategyTest.PF.class,
                        FoldersLookupStrategyTest.TestStrategyFactory.class,
                        FoldersLookupStrategyTest.OSF.class,
                        NbMavenProjectFactory.class,
                        FoldersLookupStrategyTest.NoStrategyProjectFactory.class,
                        HeuristicFoldersHelperImplementation.HeuristicImplementationFactory.class,
                        org.netbeans.modules.project.ant.AntBasedProjectFactorySingleton.class,
                        org.netbeans.modules.java.project.ProjectClassPathProvider.class,
                        org.netbeans.modules.java.project.ProjectSourceLevelQueryImpl2.class,
                        org.netbeans.modules.java.project.ProjectSourceLevelQueryImpl.class,
                        org.netbeans.modules.java.j2seplatform.libraries.J2SELibraryClassPathProvider.class,
                        org.netbeans.modules.java.j2seplatform.platformdefinition.jrtfs.NBJRTURLMapper.class,
                        org.netbeans.modules.java.j2seplatform.platformdefinition.J2SEPlatformSourceLevelQueryImpl.class,
                        org.netbeans.modules.java.j2seplatform.libraries.J2SELibrarySourceLevelQueryImpl.class,
                        org.netbeans.modules.java.j2seplatform.queries.DefaultSourceLevelQueryImpl.class,
                        org.netbeans.modules.java.j2seplatform.platformdefinition.J2SEPlatformFactory.Provider.class
                ).build();
    }

    @AfterAll
    public static void teardown() throws Exception {
        teardown.run();
    }

    @Test
    public void testTestProjectsExist() throws URISyntaxException, IOException {
        for (AntTestProjects prj : AntTestProjects.values()) {
            for (String name : prj.names()) {
                Path path = prj.file(name);
                assertNotNull(path);
            }
        }
    }
}
