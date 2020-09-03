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
package org.nemesis.antlr.live;

import org.nemesis.antlr.live.impl.AntlrGenerationSubscriptionsImpl;
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

        Runnable unsubscribe = RebuildSubscriptions.subscribe(proj.file("NestedMaps.g4"), sub);
        assertNotNull(unsubscribe, "No unsubscriber runnable");
        sub.assertRebuilt(1, "Subscribing should have caused an immediate "
                + " ANTLR generation.  It didn't.  Is the startup delay code not "
                + "detecting that it's running in a test?");

        StyledDocument lexerDoc = proj.document("NMLexer.g4");
        assertNotNull(lexerDoc);

        JFS jfs = AntlrGenerationSubscriptionsImpl.instance().jfsFor(proj.project());
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

        // Note: Whether this should be 1 or 2 seems to vary by whether
        // we are running synchronously or not
        sub.assertRebuilt(RebuildSubscriptions.SUBSCRIBE_BUILDS_ASYNC ? 1 : 2);
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
            if (this.count.get() < count) {
                await();
                assertEquals(count, this.count.get());
                return;
            }
            assertEquals(count, this.count.get());
        }
        public void assertRebuilt(int count, String msg) throws InterruptedException {
            if (this.count.get() < count) {
                await();
                assertEquals(count, this.count.get(), msg);
                return;
            }
            assertEquals(count, this.count.get(), msg);
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
        shutdownTestFixtures = initAntlrTestFixtures(true)
                .verboseGlobalLogging()
                .build();
        proj = ProjectTestHelper.projectBuilder()
                .writeStockTestGrammarSplit("com.foo").build("wookies");
        shutdownTestFixtures.andAlways(proj::delete);
    }

    @AfterEach
    public void tearDown() throws Throwable {
        if (shutdownTestFixtures != null) {
            shutdownTestFixtures.run();
        }
    }
}
