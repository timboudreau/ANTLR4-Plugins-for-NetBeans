package org.nemesis.antlr.live;

import org.nemesis.test.fixtures.support.SimulatedEditorReparse;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.thread.OneThreadLatch;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.test.fixtures.support.GeneratedMavenProject;
import static org.nemesis.antlr.live.SanityCheckPlumbingTest.initAntlrTestFixtures;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.jfs.JFS;
import org.netbeans.junit.NbTestCase;
import org.nemesis.extraction.Extraction;
/**
 *
 * @author Tim Boudreau
 */
public class RebuildSubscriptionsTest {

    private ThrowingRunnable shutdownTestFixtures;

    private GeneratedMavenProject proj;

    @Test
    public void testSomeMethod() throws IOException, InterruptedException, BadLocationException {
        Sub sub = new Sub();
        Document doc = proj.document("NestedMaps.g4");

        Runnable unsubscribe = RebuildSubscriptions.instance().subscribe(proj.file("NestedMaps.g4"), sub);
        assertNotNull(unsubscribe);
        sub.assertRebuilt(1);;

        StyledDocument lexerDoc = proj.document("NMLexer.g4");
        assertNotNull(lexerDoc);

        JFS jfs = RebuildSubscriptions.instance().jfsFor(proj.project());
        assertNotNull(jfs, "No jfs");
        jfs.list(StandardLocation.SOURCE_PATH, "", EnumSet.allOf(JavaFileObject.Kind.class), true)
                .forEach(jfo -> {
                    if (jfo.getName().contains("NMLexer")) {
                        assertTrue(jfo.toString().contains("Document"), "Not using document mapping for "
                                + jfo + ", though document was loaded.  EditorCookie.Observable did not "
                                + "fire or listener code is broken");
                    }
                });

        SimulatedEditorReparse rep = new SimulatedEditorReparse(doc);
        doc.insertString(0, "// a line comment\n", null);
        sub.assertRebuilt(2);
        unsubscribe.run();
        doc.insertString(0, "// another line comment\n", null);
        sub.assertNotRebuilt();

        WeakReference<Sub> ref = new WeakReference<>(sub);
        sub = null;
        unsubscribe = null;
        NbTestCase.assertGC("Sub is still referenced", ref);
    }

    static class Sub implements Subscriber {

        private final AtomicInteger count = new AtomicInteger();
        private final OneThreadLatch latch = new OneThreadLatch();

        @Override
        public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree, String mimeType, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes) {
            count.incrementAndGet();
            latch.releaseAll();
        }

        public void assertRebuilt(int count) throws InterruptedException {
            await();
            assertEquals(count, this.count.get());
        }

        public void assertNotRebuilt() throws InterruptedException {
            int ct = count.get();
            latch.await(1500, TimeUnit.MILLISECONDS);
            assertEquals(ct, count.get(), "Was rebuilt");
        }

        public void await() throws InterruptedException {
            latch.await(5, TimeUnit.SECONDS);
        }
    }

    @BeforeEach
    public void setup() throws URISyntaxException, IOException, ClassNotFoundException {
        shutdownTestFixtures = initAntlrTestFixtures()
                .verboseGlobalLogging()
                .build();
        ProjectTestHelper helper = ProjectTestHelper.relativeTo(SanityCheckPlumbingTest.class);

        proj = ProjectTestHelper.projectBuilder()
                .writeStockTestGrammarSplit("com.foo").build("wookies");
        shutdownTestFixtures.andAlways(proj::delete);
    }

    static {
        // Preinitialize its logger
        Class<?> type = RebuildSubscriptions.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(FoldersHelperTrampoline.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
    }

    @AfterEach
    public void tearDown() throws Throwable {
        if (shutdownTestFixtures != null) {
            shutdownTestFixtures.run();
        }
    }
}
