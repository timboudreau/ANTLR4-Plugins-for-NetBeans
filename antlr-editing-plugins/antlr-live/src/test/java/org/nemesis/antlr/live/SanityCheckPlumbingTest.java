package org.nemesis.antlr.live;

import com.mastfrog.function.throwing.ThrowingRunnable;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.file.AntlrNbParser.AntlrParserFactory;
import org.nemesis.antlr.file.file.AntlrDataObject;
import org.nemesis.antlr.grammar.file.resolver.AntlrFileObjectRelativeResolver;
import static org.nemesis.test.fixtures.support.TestFixtures.setActiveDocument;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.antlr.spi.language.AntlrParseResult;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.project.Project;
import org.netbeans.modules.editor.impl.DocumentFactoryImpl;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.nb.EditorMimeTypesImpl;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.ParserFactory;
import org.netbeans.spi.editor.document.DocumentFactory;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
/**
 *
 * @author Tim Boudreau
 */
public class SanityCheckPlumbingTest {

    private Path grammarProject;
    private Project mavenProject;
    private Project childOfParentThatChangesDirsProject;
    private Project childOfParentThatChangesDirsAndEncodingProject;
    private ThrowingRunnable shutdownTestFixtures;

    @Test
    public void testFileObjectsWork() throws DataObjectNotFoundException, IOException, BadLocationException, InterruptedException, ParseException {

        Lookup named = Lookups.forPath("antlr/extractors/text/x-g4/org/nemesis/antlr/ANTLRv4Parser/GrammarFileContext");

        assertFalse(named.lookupAll(Object.class).isEmpty(), "Named services are not registered");

        EditorMimeTypesImpl im = Lookup.getDefault().lookup(EditorMimeTypesImpl.class);
        assertNotNull(im);

        Lookup mime = MimeLookup.getLookup("text/x-g4");

        AntlrParserFactory af = mime.lookup(AntlrParserFactory.class);
        assertNotNull(af, "Antlr parser factory not present in mime lookup for text/x-g4");
        assertTrue(af instanceof ParserFactory, "Wrong type");

        ParserFactory fac = mime.lookup(ParserFactory.class);
        assertNotNull(fac, "Parser factory not in " + MimeLookup.getLookup("text/x-g4").lookupAll(Object.class));

        assertTrue(im.getSupportedMimeTypes().contains("text/x-g4"));

        Path mainFile = grammarProject.resolve("src/main/antlr4/org/nemesis/antlr/ANTLRv4.g4");
        assertTrue(Files.exists(mainFile));
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(mainFile.toFile()));
        assertNotNull(fo);
        assertEquals("text/x-g4", fo.getMIMEType());
        DataObject dob = DataObject.find(fo);
        assertNotNull(dob);
        assertNotNull(dob.getLookup().lookup(AntlrDataObject.class));
        EditorCookie.Observable obs = dob.getLookup().lookup(EditorCookie.Observable.class);
        assertNotNull(obs);
        PCL pcl = new PCL();
        obs.addPropertyChangeListener(pcl);
        StyledDocument doc = obs.openDocument();
        setActiveDocument(doc);
        pcl.awaitFired(EditorCookie.Observable.PROP_DOCUMENT);
        assertNotNull(doc);
        doc.insertString(0, "Hello world", null);
        pcl.awaitFired(EditorCookie.Observable.PROP_MODIFIED);

        doc.remove(0, "Hello world".length());
//        pcl.awaitFired(EditorCookie.Observable.PROP_MODIFIED);

        assertTrue(ParserManager.canBeParsed("text/x-g4"));

        Source src = Source.create(doc, dob.getLookup());
        assertNotNull(src);
        assertEquals("text/x-g4", src.getMimeType());
        Parser.Result[] res = new Parser.Result[1];

        ParserManager.parse(Collections.singleton(src), new UserTask() {
            @Override
            public void run(ResultIterator resultIterator) throws Exception {
                Parser.Result r = resultIterator.getParserResult();
                res[0] = r;
            }
        });
        assertNotNull(res[0]);
        assertTrue(res[0] instanceof ExtractionParserResult);
        assertTrue(res[0] instanceof AntlrParseResult);
    }

    static class PCL implements PropertyChangeListener {

        private Set<String> changes = new HashSet<>();

        void awaitFired(String prop) throws InterruptedException {
            for (int i = 0; i < 100; i++) {
                if (changes.contains(prop)) {
                    break;
                }
                Thread.sleep(20);
            }
            assertFired(prop);
        }

        void assertFired(String prop) {
            boolean result = changes.contains(prop);
            changes.clear();
            assertTrue(result, prop + " was not fired");
        }

        @Override
        public void propertyChange(PropertyChangeEvent pce) {
//            System.out.println("PROP CHANGE " + pce.getPropertyName() + " " + pce.getOldValue() + " -> " + pce.getNewValue() + " " + Thread.currentThread());
            changes.add(pce.getPropertyName());
        }
    }

    @BeforeEach
    public void setup() throws URISyntaxException, IOException, ClassNotFoundException {
        shutdownTestFixtures = initAntlrTestFixtures().build();
        ProjectTestHelper helper = ProjectTestHelper.relativeTo(SanityCheckPlumbingTest.class);

        grammarProject = helper.findAntlrGrammarProjectDir();
        mavenProject = helper.findAntlrGrammarProject();
        childOfParentThatChangesDirsProject = helper.findChildProjectWithChangedAntlrDirsProject();
        childOfParentThatChangesDirsAndEncodingProject
                = helper.findChildProjectWithChangedAntlrDirAndEncodingProject();
    }

    @AfterEach
    public void tearDown() throws Throwable {
        if (shutdownTestFixtures != null) {
            shutdownTestFixtures.run();
        }
    }

    public static TestFixtures initAntlrTestFixtures() {
        return initAntlrTestFixtures(false);
    }

    public static TestFixtures initAntlrTestFixtures(boolean verbose) {
        TestFixtures fixtures = new TestFixtures();
        if (verbose) {
            fixtures.verboseGlobalLogging();
        }
        DocumentFactory fact = new DocumentFactoryImpl();
        return fixtures.addToMimeLookup("", fact)
                .addToMimeLookup("text/x-g4", AntlrParserFactory.class)
                .addToMimeLookup("text/x-g4", AntlrNbParser.createErrorHighlighter(), fact)
                .addToNamedLookup(org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder.REGISTRATION_PATH,
                        new org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder())
                .addToDefaultLookup(
                        FakeG4DataLoader.class,
                        MavenFolderStrategyFactory.class,
                        NbMavenProjectFactory.class,
                        AntlrFileObjectRelativeResolver.class
                );
    }

}
