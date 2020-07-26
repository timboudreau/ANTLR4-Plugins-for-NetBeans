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
package com.mastfrog.antlr.project.helpers.ant;

import static com.mastfrog.antlr.project.helpers.ant.TestAntProjectRecognition.genericAntProjectSkeleton;
import static com.mastfrog.antlr.project.helpers.ant.TestAntProjectRecognition.project;
import com.mastfrog.function.throwing.ThrowingRunnable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.project.AntTestProjects;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.project.impl.BuildFileFinderImpl;
import org.nemesis.antlr.project.impl.HeuristicFoldersHelperImplementation;
import org.nemesis.antlr.project.impl.InferredConfig;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.nemesis.antlr.sample.AntlrSampleFiles;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.ProxyURLStreamHandlerFactory;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.core.startup.NbResourceStreamHandler;
import org.netbeans.modules.projectapi.nb.NbProjectManager;
import org.openide.filesystems.FileUtil;
import org.xml.sax.SAXException;

/**
 *
 * @author Tim Boudreau
 */
public class TestHeuristicsWithMixedJavaAndAntlrSources {

    private static ThrowingRunnable teardown;
    private static Path dir;
    private static Project prj;

    @Test
    public void testHeuristics() throws Throwable {
        assertSame(prj, FileOwnerQuery.getOwner(FileUtil.toFileObject(dir.toFile())),
                    "File owner query not installed or did not find out about " + prj
                    + " - this will break all sorts of things.");

        AntlrConfiguration config = AntlrConfiguration.forProject(prj);
        System.out.println("\n\nCONFIG-NO-FILES: " + config + "\n\n");
        Folders lexer = Folders.ownerOf(dir.resolve("src/gt1/MarkdownLexer.g4"));
        System.out.println("\n\nLEXER: " + lexer + "\n\n");
        Folders parser = Folders.ownerOf(dir.resolve("src/gt1/MarkdownParser.g4"));
        System.out.println("\n\nPARSER: " + parser+ "\n\n");
        Folders java = Folders.ownerOf(dir.resolve("src/gt1/GT1.java"));
        System.out.println("\n\nJAVA: " + java+ "\n\n");

//        AntlrConfiguration config2 = AntlrConfiguration.forFile(dir.resolve("src/gt1/MarkdownLexer.g4"));
//        System.out.println("AFTER ADDING FILE: \n" + config2);
//        AntlrConfiguration config3 = AntlrConfiguration.forFile(dir.resolve("src/gt1/GT1.java"));
//        System.out.println("AFTER ADDING FILE-2: \n" + config3);
//        AntlrConfiguration config4 = AntlrConfiguration.forProject(prj);
//        System.out.println("PROJECT AFTER ADDING FILES: \n" + config4);
    }

    static Path skeletonWithGrammarsInJavaSources() throws IOException {
        Path result = genericAntProjectSkeleton("test-add-antlr", teardown);
        Path a = result.resolve("src/gt1/MarkdownLexer.g4");
        Path b = result.resolve("src/gt1/MarkdownParser.g4");
        AntlrSampleFiles.MARKDOWN_LEXER.copyTo(a);
        AntlrSampleFiles.MARKDOWN_PARSER.copyTo(b);
        return result;
    }

    @BeforeAll
    public static void setup() throws ClassNotFoundException, SAXException, IOException, URISyntaxException {
        AntTestProjects.setTestClass(TestAntProjectRecognition.class);
        teardown = new TestFixtures()
                .verboseGlobalLogging(
                        HeuristicFoldersHelperImplementation.class,
                        AntlrConfiguration.class,
                        InferredConfig.class,
                        "org.nemesis.antlr.project.AntlrConfigurationCache",
                        FolderQuery.class)
                .avoidStartingModuleSystem()
                //                .add("URLStreamHandler/nbinst", NbinstURLStreamHandler.class)
                .addToNamedLookup("URLStreamHandler/nbres", NbResourceStreamHandler.class)
                //                .add("URLStreamHandler/nbresloc", NbResourceStreamHandler.class)
                .addToDefaultLookup(
                        TestAntProjectRecognition.MockAntlrAntLibraryProvider.class,
                        //                        ProjectLibraryProvider.class,
                        //                        LibrariesStorage.class,
                        TestAntProjectRecognition.FQ.class,
                        AntTestProjects.FOQ.class,
                        //                        MF.class,
                        BuildFileFinderImpl.class,
                        AntFoldersHelperImplementationFactory.class,
                        NbProjectManager.class,
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
        ProxyURLStreamHandlerFactory.register();
        dir = skeletonWithGrammarsInJavaSources();
        prj = project(dir);
        assertNotNull(prj);

    }

    @AfterAll
    public static void teardown() throws Exception {
        if (teardown != null) {
            teardown.run();
        }
    }

}
