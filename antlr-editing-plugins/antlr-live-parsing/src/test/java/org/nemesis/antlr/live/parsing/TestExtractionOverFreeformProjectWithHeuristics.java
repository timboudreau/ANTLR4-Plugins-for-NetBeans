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
package org.nemesis.antlr.live.parsing;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.thread.OneThreadLatch;
import java.awt.Component;
import java.awt.Graphics;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import javax.swing.Icon;
import javax.swing.event.ChangeListener;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import static org.nemesis.antlr.live.parsing.TestExtractorGenerationWithTokenVocab.initAntlrTestFixtures;
import org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner;
import org.nemesis.antlr.live.parsing.impl.ReparseListeners;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.project.impl.BuildFileFinderImpl;
import org.nemesis.antlr.project.impl.HeuristicFoldersHelperImplementation;
import org.nemesis.antlr.project.impl.HeuristicFoldersHelperImplementation.HeuristicImplementationFactory;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.extraction.Extraction;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.ProxyURLStreamHandlerFactory;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.modules.projectapi.nb.NbProjectManager;
import org.netbeans.spi.project.FileOwnerQueryImplementation;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectFactory2;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.Pair;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Tim Boudreau
 */
public class TestExtractionOverFreeformProjectWithHeuristics {

    private static final String PRJ_PATH = "ANTLRTestProjects/freeform/GrammarsAdhoc/";
    public static final String PARSER_BASE
            = PRJ_PATH + "grammar/com/foo/MarkdownParser.g4";
    public static final String LEXER_BASE
            = PRJ_PATH + "grammar/com/foo/MarkdownLexer.g4";
    public static final String FRAGMENTS_BASE
            = PRJ_PATH + "grammar/imports/basics.g4";
    public static final String COMBINED_BASE
            = PRJ_PATH + "grammar/com/foo/wookie/SimpleLanguage.g4";

    private static ThrowingRunnable teardown;
    private static Path markdownParserFile;
    private static Path markdownLexerFile;
    private static Path markdownFragmentsImportFile;
    private static Path combinedGrammarFile;
    private static Path freeformProjectPath;
    private static String markdownParserMime;
    private static String markdownLexerMime;
    private static String simpleLanguageMime;
    private static String basicMarkdownMime;

    @Test
    public void testit() throws Exception {
        FileObject projectRoot = fo(freeformProjectPath);

        Project prj = ProjectManager.getDefault().findProject(projectRoot);
        assertNotNull(prj, "Project plumbing is probably not set up correctly.");
        assertFalse(prj == FileOwnerQuery.UNOWNED, "Got the unowned project");
        assertTrue(prj instanceof FakeProject, "Huh? Wrong project: " + prj);

        assertSame(Folders.ANTLR_IMPORTS, Folders.ownerOf(markdownFragmentsImportFile));
        assertSame(Folders.ANTLR_GRAMMAR_SOURCES, Folders.ownerOf(markdownLexerFile));
        assertSame(Folders.ANTLR_GRAMMAR_SOURCES, Folders.ownerOf(markdownParserFile));
        assertSame(Folders.ANTLR_GRAMMAR_SOURCES, Folders.ownerOf(combinedGrammarFile));

        AntlrConfiguration config = AntlrConfiguration.forFile(markdownParserFile);
        assertNotNull(config.antlrImportDir());
        assertNotNull(config.antlrSourceDir());
        assertEquals(freeformProjectPath.resolve("grammar"), config.antlrSourceDir());
        assertEquals(freeformProjectPath.resolve("grammar/imports"), config.antlrImportDir());

        System.out.println("\n\n ----------------- do the thing ------------------\n");

        EmbeddedAntlrParser par = EmbeddedAntlrParsers.forGrammar("test", fo(markdownParserFile));
        Listener l = new Listener();
        par.listen(l);

        EmbeddedAntlrParserResult res = par.parse("# Hello world\n\n> This is some stuff here.\n\n");
//        l.assertUpdated();

        assertFalse(res.proxy().isUnparsed());

        EmbeddedAntlrParser lpar = EmbeddedAntlrParsers.forGrammar("test", fo(markdownLexerFile));
        EmbeddedAntlrParserResult lres = lpar.parse("# Hello world\n\n> This is some stuff here.\n\n");
        assertTrue(lres.isUsable(), () -> {
            return lres.toString() + "\n\nOUTPUT:\n:" + FakeAntlrLoggers.output();
        });
        assertFalse(res.proxy().isUnparsed(), () -> res.runResult().toString() + " - " + FakeAntlrLoggers.output());
    }

    static class Listener implements BiConsumer<Extraction, GrammarRunResult<?>> {

        OneThreadLatch latch = new OneThreadLatch();
        private final AtomicReference<Pair<Extraction, GrammarRunResult<?>>> last = new AtomicReference<>();

        @Override
        public void accept(Extraction t, GrammarRunResult<?> u) {
            last.set(Pair.of(t, u));
            latch.releaseAll();
        }

        public Pair<Extraction, GrammarRunResult<?>> assertUpdated() throws InterruptedException {
            Pair<Extraction, GrammarRunResult<?>> p = last.get();
            if (p == null) {
                for (int i = 0; i < 10; i++) {
                    latch.await(10, TimeUnit.MILLISECONDS);
                    p = last.get();
                    if (p != null) {
                        break;
                    }
                }
            }
            assertNotNull(p);
            return p;
        }

    }

    static class S implements Subscriber {

        AtomicReference<Extraction> lastExtraction = new AtomicReference<>();

        Runnable un;

        S(FileObject fo) {
            un = RebuildSubscriptions.subscribe(fo, this);
        }

        @Override
        public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree, String mimeType, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes) {
            lastExtraction.set(extraction);
        }

    }

    private static FileObject fo(Path p) {
        return FileUtil.toFileObject(FileUtil.normalizeFile(p.toFile()));
    }

    @BeforeAll
    public static void setup() throws URISyntaxException {
        ProjectTestHelper helper = ProjectTestHelper.relativeTo(TestExtractionOverFreeformProjectWithHeuristics.class);
        TestFixtures fixtures = initAntlrTestFixtures(true)
                .addToDefaultLookup(
                        OQ.class,
                        NbProjectManager.class,
                        HeuristicFoldersHelperImplementation.HeuristicImplementationFactory.class,
                        BuildFileFinderImpl.class,
                        FakeProjectFactory.class
                )
                .addToNamedLookup("antlr/invokers/org/nemesis/antlr/live/parsing/impl/EmbeddedParser", ProxiesInvocationRunner.class)
                .verboseGlobalLogging(HeuristicImplementationFactory.class,
                        HeuristicFoldersHelperImplementation.class,
                        EmbeddedAntlrParserImpl.class,
                        ProxiesInvocationRunner.class,
                        ReparseListeners.class,
                        EmbeddedAntlrParsers.class,
                        EmbeddedAntlrParser.class,
                        "org.nemesis.antlr.project.impl.InferredConfig"
                ) //                .insanelyVerboseLogging()
                ;
        Path top = helper.projectBaseDir().getParent().getParent();;
        markdownParserFile = top.resolve(PARSER_BASE);
        markdownLexerFile = top.resolve(LEXER_BASE);
        markdownFragmentsImportFile = top.resolve(FRAGMENTS_BASE);
        combinedGrammarFile = top.resolve(COMBINED_BASE);
        freeformProjectPath = top.resolve(PRJ_PATH);

        for (Path p : new Path[]{markdownParserFile, markdownLexerFile, markdownFragmentsImportFile, combinedGrammarFile, freeformProjectPath}) {
            assertTrue(Files.exists(p), "Could not find grammar file " + p);
        }
        ProxyURLStreamHandlerFactory.register();

        markdownParserMime = AdhocMimeTypes.mimeTypeForPath(markdownParserFile);
        markdownLexerMime = AdhocMimeTypes.mimeTypeForPath(markdownLexerFile);
        simpleLanguageMime = AdhocMimeTypes.mimeTypeForPath(combinedGrammarFile);
        basicMarkdownMime = AdhocMimeTypes.mimeTypeForPath(markdownFragmentsImportFile);

        fixtures.addToMimeLookup(markdownParserMime, FakeParserFactory.class)
                .addToMimeLookup(markdownLexerMime, FakeParserFactory.class)
                .addToMimeLookup(basicMarkdownMime, FakeParserFactory.class)
                .addToMimeLookup(simpleLanguageMime, FakeParserFactory.class);

        teardown = fixtures.build();
    }

    @AfterAll
    public static void teardown() throws Exception {
        if (teardown != null) {
            teardown.run();
        }
    }

    static FakeProject instance;

    public static final class FakeProjectFactory implements ProjectFactory, ProjectFactory2 {

        @Override
        public boolean isProject(FileObject fo) {
            Path pth = FileUtil.toFile(fo).toPath();
            return pth.startsWith(freeformProjectPath);
        }

        @Override
        public Project loadProject(FileObject fo, ProjectState ps) throws IOException {
            if (isProject(fo)) {
                return new FakeProject(ps, fo);
            }
            return null;
        }

        @Override
        public void saveProject(Project prjct) throws IOException, ClassCastException {
            // do nothing
        }

        @Override
        public ProjectManager.Result isProject2(FileObject fo) {
            if (isProject(fo)) {
                return new ProjectManager.Result("freeform", "fake", null);
            }
            return null;
        }
    }

    static final class FakeProject implements Project {

        private final ProjectState state;
        private final FileObject fld;
        private final Lookup lkp;

        public FakeProject(ProjectState state, FileObject fld) {
            instance = this;
            this.state = state;
            this.fld = fld;
            lkp = Lookups.fixed(this, fld, state, new Src());
        }

        @Override
        public FileObject getProjectDirectory() {
            return fld;
        }

        @Override
        public Lookup getLookup() {
            return lkp;
        }

        class Src implements Sources {

            private final Group[] groups;

            Src() {
                groups = new Group[]{
                    new Group(fld.getFileObject("grammar"), "antlr", "Antlr"),
                    new Group(fld.getFileObject("src"), "java", "Java")
                };
            }

            @Override
            public SourceGroup[] getSourceGroups(String string) {
                return groups;
            }

            @Override
            public void addChangeListener(ChangeListener cl) {
                // do nothing
            }

            @Override
            public void removeChangeListener(ChangeListener cl) {
                // do nothing
            }
        }

        static class Group implements SourceGroup {

            private final FileObject dir;
            private final String name;
            private final String displayName;

            public Group(FileObject dir, String name, String displayName) {
                this.dir = dir;
                this.name = name;
                this.displayName = displayName;
            }

            @Override
            public FileObject getRootFolder() {
                return dir;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDisplayName() {
                return displayName;
            }

            @Override
            public Icon getIcon(boolean bln) {
                return new Icon() {
                    @Override
                    public void paintIcon(Component c, Graphics g, int x, int y) {
                        // do nothing
                    }

                    @Override
                    public int getIconWidth() {
                        return 0;
                    }

                    @Override
                    public int getIconHeight() {
                        return 0;
                    }
                };
            }

            @Override
            public boolean contains(FileObject fo) {
                return FileUtil.isParentOf(dir, fo);
            }

            @Override
            public void addPropertyChangeListener(PropertyChangeListener pl) {
                // do nothing
            }

            @Override
            public void removePropertyChangeListener(PropertyChangeListener pl) {
                // do nothing
            }
        }
    }

    public static final class OQ implements FileOwnerQueryImplementation {

        @Override
        public Project getOwner(URI uri) {
            Path pth = Paths.get(uri);
            if (instance != null && pth.startsWith(freeformProjectPath)) {
                return instance;
            }
            return null;
        }

        @Override
        public Project getOwner(FileObject fo) {
            Path pth = FileUtil.toFile(fo).toPath();
            if (instance != null) {
                if (pth.startsWith(freeformProjectPath)) {
                    return instance;
                }
            }
            return null;
        }
    }
}
