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

import static com.mastfrog.antlr.project.helpers.ant.AddAntBasedAntlrSupport.buildExtensionHash;
import static com.mastfrog.antlr.project.helpers.ant.AddAntBasedAntlrSupport.hashStream;
import static com.mastfrog.antlr.project.helpers.ant.ProjectUpgrader.configurationFragment;
import com.mastfrog.function.throwing.ThrowingRunnable;
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.strings.Strings;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.project.AntTestProjects;
import org.nemesis.antlr.project.AntTestProjects.FOQ;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.project.impl.BuildFileFinderImpl;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.nemesis.antlr.project.impl.HeuristicFoldersHelperImplementation;
import org.nemesis.antlr.project.impl.InferredConfig;
import org.nemesis.antlr.project.spi.FolderLookupStrategyImplementation;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.nemesis.antlr.project.spi.FoldersLookupStrategyImplementationFactory;
import org.nemesis.antlr.project.spi.addantlr.AddAntlrCapabilities;
import org.nemesis.antlr.project.spi.addantlr.NewAntlrConfigurationInfo;
import org.nemesis.antlr.project.spi.addantlr.SkeletonGrammarType;
import org.nemesis.antlr.wrapper.AntlrVersion;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.ProxyURLStreamHandlerFactory;
import org.netbeans.api.project.Project;
import org.netbeans.modules.java.j2seproject.J2SEProject;
import org.netbeans.modules.project.ant.AntBasedProjectFactorySingleton;
import static org.netbeans.modules.project.ant.AntBasedProjectFactorySingleton.LOG;
import static org.netbeans.modules.project.ant.AntBasedProjectFactorySingleton.PROJECT_NS;
import org.netbeans.modules.project.ant.ProjectXMLCatalogReader;
import org.netbeans.modules.project.ant.ProjectXMLKnownChecksums;
import org.netbeans.modules.projectapi.nb.NbProjectManager;
import org.netbeans.spi.project.AuxiliaryConfiguration;
import org.netbeans.spi.project.FileOwnerQueryImplementation;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectState;
import org.netbeans.spi.project.libraries.LibraryImplementation;
import org.netbeans.spi.project.libraries.LibraryProvider;
import org.netbeans.spi.project.support.ant.AntBasedProjectType;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.queries.FileEncodingQueryImplementation;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.SpecificationVersion;
import org.openide.util.BaseUtilities;
import org.openide.util.Lookup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author Tim Boudreau
 */
public class TestAntProjectRecognition {

    private static ThrowingRunnable teardown;

    @Test
    public void testAddAntlr() throws Exception {
        Path dir = genericAntProjectSkeleton("test-add-antlr");
        Project prj = project(dir);
        assertNotNull(prj);
        AntFoldersHelperImplementationFactory factory = Lookup.getDefault().lookup(AntFoldersHelperImplementationFactory.class);
        assertNotNull(factory);
        FolderLookupStrategyImplementation impl = factory.create(prj, FoldersHelperTrampoline.getDefault().newQuery().project(prj));
        assertNull(impl, "Should not be recognized as an Antlr-ant project yet");

        AddAntlrCapabilities addOpts = factory.addAntlrCapabilities();
        assertNotNull(addOpts);

        Function<NewAntlrConfigurationInfo, CompletionStage<Boolean>> adder = factory.antlrSupportAdder(prj);
        assertNotNull(adder);
        NewAntlrConfigurationInfo info = new NewAntlrConfigurationInfo("antlr4", true, true, AntlrVersion.version(),
                SkeletonGrammarType.LEXER_AND_PARSER, "antlr4/imports", true, "gt1", "Gt");

        CompletionStage<Boolean> stage = adder.apply(info);
        assertNotNull(stage);

        CountDownLatch latch = new CountDownLatch(1);
        Boolean[] result = new Boolean[1];
        stage.thenAcceptAsync(bool -> {
            result[0] = bool;
            latch.countDown();
        });
        latch.await(30, TimeUnit.SECONDS);

        Files.walk(dir, 20).forEachOrdered(pth -> {
            System.out.println(dir.relativize(pth));
        });

//        Thread.sleep(5000);
        assertNotNull(result[0]);
        assertEquals(Boolean.TRUE, result[0]);

        assertTrue(Files.exists(dir.resolve("antlr4")), "Antlr 4 dir should exist");

        AntFoldersHelperImplementationFactory.INSTANCE.wipeCache();

        impl = factory.create(prj, FoldersHelperTrampoline.getDefault().newQuery().project(prj).relativeTo(dir.resolve("build.xml")));

        assertNotNull(impl, "Should now be recognized as an antlr-ant project");

        impl = null;
        FoldersLookupStrategyImplementationFactory.evict(dir);

        boolean updated = updateFile(dir.resolve("nbproject/project.properties"), txt -> {
            return Strings.literalReplaceAll(AntlrVersion.version(), "3.5.2", txt);
        });
        assertTrue(updated, "project.properties did not contain " + AntlrVersion.version());

        updated = updateFile(dir.resolve("nbproject/project.xml"), txt -> {
            String res = Strings.literalReplaceAll(AntlrVersion.moduleVersion(), "1.8.9", txt);
            res = Strings.literalReplaceAll(AntlrVersion.version(), "3.5.2", res);
            return res;
        });
        assertTrue(updated, "project.properties did not contain " + AntlrVersion.moduleVersion());

        updated = updateFile(dir.resolve("nbproject/antlr-build-impl.xml"), txt -> {
            return "hoogle woogle\ngwerve swerve\npachycephalasauri ate my lunch.\n";
        });
        assertTrue(updated);
        String newHash = hashStream(Files.newInputStream(dir.resolve("nbproject/antlr-build-impl.xml"), StandardOpenOption.READ));
        updated = updateFile(dir.resolve("nbproject/project.xml"), txt -> {
            String existing = buildExtensionHash();
            return Strings.literalReplaceAll(existing, newHash, txt);
        });
        assertTrue(updated, "Hash not matched");

        // Just replace the project, since we are modifying the project metadata
        // by just rewriting it for our test - need to force a rereads
        projectForPath.remove(dir);
        projects.remove(prj);
        prj = project(dir);

        Element frag = configurationFragment(prj.getLookup().lookup(AuxiliaryConfiguration.class));
        ProjectUpgrader.ModuleVersionInfo vinfo = ProjectUpgrader.versionInfo(frag);
        ProjectUpgrader.ModuleVersionInfo current = ProjectUpgrader.currentInfo();

        assertNotNull(vinfo);
        assertNotEquals(vinfo.buildScriptHash(), current.buildScriptHash());
        assertEquals(newHash, vinfo.buildScriptHash());

        assertEquals(new SpecificationVersion("3.5.2"), vinfo.antlrVersion());
        assertEquals(new SpecificationVersion("1.8.9"), vinfo.moduleVersion());

//        UpgradableProjectDetector det = new UpgradableProjectDetector();
        ProjectUpgrader up = ProjectUpgrader.needsUpgrade(prj);
        assertNotNull(up);

        boolean upgraded = up.upgrade();
        assertTrue(upgraded);

        frag = configurationFragment(prj.getLookup().lookup(AuxiliaryConfiguration.class));
        ProjectUpgrader.ModuleVersionInfo vinfo2 = ProjectUpgrader.versionInfo(frag);
        assertEquals(AntlrVersion.version(), vinfo2.antlrVersionRaw());
        assertEquals(AntlrVersion.moduleVersion(), vinfo2.moduleVersionRaw());

        String updatedHash = hashStream(Files.newInputStream(dir.resolve("nbproject/antlr-build-impl.xml"), StandardOpenOption.READ));
        assertEquals(buildExtensionHash(), updatedHash, "antlr-build-impl.xml not updated or does not match hash of current build script");
    }

    private static boolean updateFile(Path path, Function<String, String> func) throws IOException {
        assertTrue(Files.exists(path));
        assertFalse(Files.isDirectory(path));
        String txt = new String(Files.readAllBytes(path), US_ASCII);
        String newText = func.apply(txt);
        boolean result = !newText.equals(txt);
        if (result) {
            Files.write(path, newText.getBytes(US_ASCII),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC,
                    StandardOpenOption.DSYNC
            );
        }
        return result;
    }

    @Test
    public void testHeuristicsNotInterferedWith() throws Throwable {
        for (AntTestProjects prj : AntTestProjects.values()) {
            Project p = prj.project();

            assertFalse(Lookup.getDefault().lookup(AntFoldersHelperImplementationFactory.class).answer(p).isViable(),
                    "AntFoldersHelperImplementationFactory should not be recognizing projects it did not "
                    + "configure, it should leave that to HeuristicFoldersHelperImplementation.");

            assertNotNull(p);
            for (AntTestProjects.GrammarFileEntry f : prj) {
                FileObject fo = prj.fileObject(f.name());
                boolean isImport = fo.getParent().getName().equals("imports");

                AntlrConfiguration config = AntlrConfiguration.forFile(fo);
                Folders owner = Folders.ownerOf(fo);
                assertNotNull(owner);
                assertEquals(isImport ? Folders.ANTLR_IMPORTS : Folders.ANTLR_GRAMMAR_SOURCES, owner,
                        "Wrong owner for " + f + ": " + owner + " in " + config);

                assertEquals(prj.dir().resolve("grammar"), config.antlrSourceDir());
                if (config.antlrImportDir() != null || isImport) {
                    assertEquals(prj.dir().resolve("grammar/imports"), config.antlrImportDir());
                }
                assertEquals("Heuristic", config.createdBy());
                assertTrue(config.visitor());
                assertTrue(config.listener());

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
                        assertEquals(prj.dir().resolve("grammar"), config.antlrSourceDir());
                        assertEquals(prj.dir().resolve("build/generated-sources/antlr4"), config.antlrSourceOutputDir());
                        assertEquals(prj.dir().resolve("build/classes"), config.buildOutput());
                        assertEquals(prj.dir().resolve("build"), config.buildDir());
                }
                switch (prj) {
                    case Options:
                        assertEquals("anotherorg.anotherpackage",
                                FoldersHelperTrampoline.findBestJavaPackageSuggestionForGrammarsWhenAddingAntlr(p));
                        break;
                    case CodeCompletion:
                        assertEquals("mypackage",
                                FoldersHelperTrampoline.findBestJavaPackageSuggestionForGrammarsWhenAddingAntlr(p));
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
                    assertEquals(prj.dir().resolve("grammar"), config.antlrSourceDir());
                    assertEquals(prj.dir().resolve("build/generated-sources/antlr4"), config.antlrSourceOutputDir());
                    assertEquals(prj.dir().resolve("build/classes"), config.buildOutput());
                    assertEquals(prj.dir().resolve("build"), config.buildDir());
            }
        }
    }

//    public static final class MF extends MultiFileSystem {
//
//        public MF() throws SAXException, IOException {
//            super(
//                    mfs()
//            );
//        }
//
//        static FileSystem mfs() throws IOException {
//            FileSystem mfs = FileUtil.createMemoryFileSystem();
//            FileObject fob = FileUtil.createData(mfs.getRoot(), "xml/entities/NetBeans/DTD_Editor_KeyBindings_settings_1_1");
//            try (InputStream in = KeyMapsStorage.class.getResourceAsStream("EditorKeyBindings-1_1.dtd")) {
//                try (OutputStream out = fob.getOutputStream()) {
//                    FileUtil.copy(in, out);
//                }
//            }
//            fob.setAttribute("hint.originalPublicID", "-//NetBeans//DTD Editor KeyBindings settings 1.1//EN");
//            fob = FileUtil.createData(mfs.getRoot(), "ProjectXMLCatalog/antlr-ant-extension/1.xsd");
//            try (InputStream in = TestAntProjectRecognition.class.getResourceAsStream("antlr-ant-extension-1.xsd")) {
//                try (OutputStream out = fob.getOutputStream()) {
//                    FileUtil.copy(in, out);
//                }
//            }
//
//            return mfs;
//        }
//    }
    @BeforeAll
    public static void setup() throws ClassNotFoundException, SAXException {
        AntTestProjects.setTestClass(TestAntProjectRecognition.class);
        teardown = new TestFixtures()
                //                .add("URLStreamHandler/nbinst", NbinstURLStreamHandler.class)
                //                .addToNamedLookup("URLStreamHandler/nbres", NbResourceStreamHandler.class)
                .verboseGlobalLogging(
                        HeuristicFoldersHelperImplementation.class,
                        AntlrConfiguration.class,
                        InferredConfig.class,
                        "org.nemesis.antlr.project.AntlrConfigurationCache",
                        FolderQuery.class)
                .avoidStartingModuleSystem()
                .addToDefaultLookup(
                        MockAntlrAntLibraryProvider.class,
                        FQ.class,
                        FOQ.class,
                        FEQI.class,
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
    }

    // Okay, there's no good way to get the libraries in place without
    // wreaking havoc loading way too much of the IDE to make things resolvable.
    // So, a mock library provider
    public static class MockAntlrAntLibraryProvider implements LibraryProvider {

        private LI[] li;

        public MockAntlrAntLibraryProvider() {
            li = new LI[]{
                new LI("antlr-ant-task"),
                new LI("antlr-" + AntlrVersion.version() + "-runtime"),
                new LI("antlr-" + AntlrVersion.version() + "-complete")
            };
        }

        @Override
        public LibraryImplementation[] getLibraries() {
            return li;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener pl) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener pl) {
        }

        static class LI implements LibraryImplementation {

            private final String name;

            public LI(String name) {
                this.name = name;
            }

            @Override
            public String getType() {
                return "j2se";
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return "desc";
            }

            @Override
            public String getLocalizingBundle() {
                return null;
            }

            @Override
            public List<URL> getContent(String string) throws IllegalArgumentException {
                return Collections.emptyList();
            }

            @Override
            public void setName(String string) {
            }

            @Override
            public void setDescription(String string) {
            }

            @Override
            public void setLocalizingBundle(String string) {
            }

            @Override
            public void addPropertyChangeListener(PropertyChangeListener pl) {
            }

            @Override
            public void removePropertyChangeListener(PropertyChangeListener pl) {
            }

            @Override
            public void setContent(String string, List<URL> list) throws IllegalArgumentException {
            }
        }
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

    static Path genericAntProjectSkeleton(String name) throws IOException {
        return genericAntProjectSkeleton(name, teardown);
    }

    static Path genericAntProjectSkeleton(String name, ThrowingRunnable teardown) throws IOException {
        Path dir = FileUtils.newTempDir(name);
        teardown.andAlways(() -> {
            FileUtils.deltree(dir);
        });
        copy(dir, "tmpl-build.xml", UnixPath.get("build.xml"));
        copy(dir, "tmpl-build-impl.xml", UnixPath.get("nbproject/build-impl.xml"));
        copy(dir, "tmpl-genfiles.properties", UnixPath.get("nbproject/genfiles.properties"));
        copy(dir, "tmpl-project.properties", UnixPath.get("nbproject/project.properties"));
        copy(dir, "tmpl-project.xml", UnixPath.get("nbproject/project.xml"));
        copy(dir, "tmpl-GT1.java.txt", UnixPath.get("src/gt1/GT1.java"));
        copy(dir, "tmpl-manifest.mf", UnixPath.get("manifest.mf"));
        return dir;
    }

    private static Path copy(Path dir, String resource, UnixPath relPath) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = TestAntProjectRecognition.class.getResourceAsStream(resource)) {
            assert in != null : "No file for " + resource;
            FileUtil.copy(in, bytes);
        }
        Path target = dir.resolve(relPath.toString());
        Path targetDir = target.getParent();
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        return Files.write(target, bytes.toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.SYNC,
                StandardOpenOption.DSYNC);
    }

    private static AntBasedProjectType projectType;
    static Set<Project> projects = new HashSet<>();

    static AntBasedProjectType projectType() {
        if (projectType != null) {
            return projectType;
        }
        Map<String, Object> map = map("type").to(J2SEProject.TYPE)
                .map("iconResource").to("org/netbeans/modules/java/j2seproject/ui/resources/j2seProject.png")
                .map("sharedName").to("data")
                .map("sharedNamespace").to(J2SEProject.PROJECT_CONFIGURATION_NAMESPACE)
                .map("privateName").to("data")
                .map("privateNamespace").to("http://www.netbeans.org/ns/j2se-project-private/1")
                .map("className").to(J2SEProject.class.getName())
                .build();
        return projectType = AntBasedProjectFactorySingleton.create(map);
    }

    private static final Map<Path, Project> projectForPath = new HashMap<>();

    public static Project project(Path dir) throws URISyntaxException, IOException, SAXException {
        try {
            Project project = projectForPath.get(dir);
            if (project != null) {
                return project;
            }
            Document doc = loadProjectXml(projectXML(dir).toAbsolutePath().toFile());
            assertNotNull(doc, "Loaded null for doc " + dir.resolve("build.xml").toAbsolutePath());
            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(dir.toFile()));
            PS ps = new PS();

            AntBasedProjectFactorySingleton sing = Lookup.getDefault().lookup(AntBasedProjectFactorySingleton.class);

            AntProjectHelper helper = AntBasedProjectFactorySingleton.HELPER_CALLBACK.createHelper(fo,
                    doc, ps, projectType());

            // need to do this so ProjectManager.saveProject will work correctly
            // once the project.xml has been modified
            Field project2HelperField = AntBasedProjectFactorySingleton.class.getDeclaredField("project2Helper");
            project2HelperField.setAccessible(true);
            Map<Project, Reference<AntProjectHelper>> project2Helper = (Map<Project, Reference<AntProjectHelper>>) project2HelperField.get(null);
            Field helper2ProjectField = AntBasedProjectFactorySingleton.class.getDeclaredField("helper2Project");
            helper2ProjectField.setAccessible(true);
            Map<AntProjectHelper, Reference<Project>> helper2Project = (Map<AntProjectHelper, Reference<Project>>) helper2ProjectField.get(null);

            Project result = project = new J2SEProject(helper);
            ps.project = result;
            projects.add(result);
            projectForPath.put(dir, result);
            helper2Project.put(helper, new WeakReference<>(result));
            project2Helper.put(result, new WeakReference<>(helper));

            NbProjectManager mgr = Lookup.getDefault().lookup(NbProjectManager.class);
            Field f = NbProjectManager.class.getDeclaredField("proj2Factory");
            f.setAccessible(true);
            Map<Project, ProjectFactory> factoryForProject = (Map<Project, ProjectFactory>) f.get(mgr);
            factoryForProject.put(result, sing);
            return result;
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    public static Path projectXML(Path dir) throws URISyntaxException, IOException {
        return dir.resolve("nbproject/project.xml");
    }

    private static Document loadProjectXml(File projectDiskFile) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = new FileInputStream(projectDiskFile);
        try {
            FileUtil.copy(is, baos);
        } finally {
            is.close();
        }
        byte[] data = baos.toByteArray();
        InputSource src = new InputSource(new ByteArrayInputStream(data));
        src.setSystemId(BaseUtilities.toURI(projectDiskFile).toString());
        try {
//            Document projectXml = XMLUtil.parse(src, false, true, Util.defaultErrorHandler(), null);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            try {
                builder = factory.newDocumentBuilder();
            } catch (ParserConfigurationException x) {
                throw new SAXException(x);
            }
            builder.setErrorHandler(org.openide.xml.XMLUtil.defaultErrorHandler());
            Document projectXml = builder.parse(src);
            LOG.finer("parsed document");
//            dumpFields(projectXml);
            Element projectEl = projectXml.getDocumentElement();
            LOG.finer("got document element");
//            dumpFields(projectXml);
//            dumpFields(projectEl);
            String namespace = projectEl.getNamespaceURI();
            LOG.log(Level.FINER, "got namespace {0}", namespace);
            if (!PROJECT_NS.equals(namespace)) {
                LOG.log(Level.FINE, "{0} had wrong root element namespace {1} when parsed from {2}",
                        new Object[]{projectDiskFile, namespace, baos});
//                dumpFields(projectXml);
//                dumpFields(projectEl);
                return null;
            }
            if (!"project".equals(projectEl.getLocalName())) { // NOI18N
                LOG.log(Level.FINE, "{0} had wrong root element name {1} when parsed from {2}",
                        new Object[]{projectDiskFile, projectEl.getLocalName(), baos});
                return null;
            }
            ProjectXMLKnownChecksums checksums = new ProjectXMLKnownChecksums();
            if (!checksums.check(data)) {
                LOG.log(Level.FINE, "Validating: {0}", projectDiskFile);
                try {
                    ProjectXMLCatalogReader.validate(projectEl);
                    checksums.save();
                } catch (SAXException x) {
                    Element corrected = ProjectXMLCatalogReader.autocorrect(projectEl, x);
                    if (corrected != null) {
                        projectXml.replaceChild(corrected, projectEl);
                        // Try to correct on disk if possible.
                        // (If not, any changes from the IDE will write out a corrected file anyway.)
                        if (projectDiskFile.canWrite()) {
                            OutputStream os = new FileOutputStream(projectDiskFile);
                            try {
                                org.openide.xml.XMLUtil.write(projectXml, os, "UTF-8");
                            } finally {
                                os.close();
                            }
                        }
                    } else {
                        throw x;
                    }
                }
            }
            return projectXml;
        } catch (SAXException e) {
            String msg = e.getMessage().
                    // org/apache/xerces/impl/msg/XMLSchemaMessages.properties validation (3.X.4)
                    replaceFirst("^cvc-[^:]+: ", ""). // NOI18N
                    replaceAll("http://www.netbeans.org/ns/", ".../"); // NOI18N
            IOException ioe = new IOException(projectDiskFile + ": " + e + " " + msg, e);
            throw ioe;
        }
    }

    static class PS implements ProjectState {

        Project project;

        PS() {

        }

        @Override
        public void markModified() {
            assertNotNull(project);
            try {
                NbProjectManager man = Lookup.getDefault().lookup(NbProjectManager.class);
                Field field = NbProjectManager.class.getDeclaredField("modifiedProjects");
                field.setAccessible(true);
                Set<Project> coll = (Set<Project>) field.get(man);
                coll.add(project);
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        }

        @Override
        public void notifyDeleted() throws IllegalStateException {
        }

    }

    public static final class FQ implements FileOwnerQueryImplementation {

        @Override
        public Project getOwner(URI uri) {
            File file = new File(uri);
            Path filePath = file.toPath().toAbsolutePath();
            for (Project p : projects) {
                Path projDir = FileUtil.toFile(p.getProjectDirectory()).toPath();
                if (filePath.startsWith(projDir)) {
                    return p;
                }
            }
            return null;
        }

        @Override
        public Project getOwner(FileObject fo) {
            Path filePath = FileUtil.toFile(fo).toPath().toAbsolutePath();
            for (Project p : projects) {
                Path projDir = FileUtil.toFile(p.getProjectDirectory()).toPath();
                if (filePath.startsWith(projDir)) {
                    return p;
                }
            }
            return null;
        }
    }

    public static final class FEQI extends FileEncodingQueryImplementation {

        @Override
        public Charset getEncoding(FileObject file) {
            return UTF_8;
        }

    }
}
