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
package org.nemesis.antlr.live.language.coloring;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Escaper;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.AttributeSet;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.adhoc.mime.types.AdhocMimeResolver;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.adhoc.mime.types.InvalidMimeTypeRegistrationException;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.grammar.file.resolver.AntlrFileObjectRelativeResolver;
import org.nemesis.antlr.live.execution.AntlrRunSubscriptions;
import org.nemesis.antlr.live.language.AdhocDataLoader;
import org.nemesis.antlr.live.language.AdhocDataLoaderTest;
import org.nemesis.antlr.live.language.AdhocInitHook;
import org.nemesis.antlr.live.language.AdhocLanguageFactory;
import org.nemesis.antlr.live.language.AdhocMimeDataProvider;
import org.nemesis.antlr.live.language.AdhocReparseListeners;
import org.nemesis.antlr.live.language.DynamicLanguages;
import org.nemesis.antlr.live.language.FakeG4DataLoader;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParsers;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.live.parsing.impl.EmbeddedParser;
import org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.jfs.nb.NbJFSUtilities;
import org.nemesis.test.fixtures.support.GeneratedMavenProject;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.junit.MockServices;
import org.netbeans.modules.editor.impl.DocumentFactoryImpl;
import org.netbeans.modules.masterfs.watcher.nio2.NioNotifier;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.modules.projectapi.nb.NbProjectManager;
import org.netbeans.spi.editor.document.DocumentFactory;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public class SequenceHighlightingTest {

    @Test
    public void testHighlighting() throws Exception {
        EmbeddedAntlrParser p = EmbeddedAntlrParsers.forGrammar("test",
                gen.file("MarkdownParser.g4"));
        System.out.println("GOT PARSER " + p);
        EmbeddedAntlrParserResult pr = p.parse(readmeDraft);

        AntlrProxies.ParseTreeProxy prox = pr.proxy();
        assertNotNull(prox);
        assertFalse(prox.isUnparsed());

        hi.update(col, prox, readmeDraft.length());

        HighlightsSequence seq = hi.getHighlights(0, readmeDraft.length());
        assertTrue(seq instanceof AdhocHighlightsSequence);
        List<SeqEntry> coalesced = checkSequence((AdhocHighlightsSequence) seq, readmeDraft, prox);
        for (int i = 0; i < coalesced.size() - 1; i++) {
            System.out.println((i + 1) + "/" + coalesced.size() + ". " + coalesced.get(i));
            coalesced.get(i).checkTokenBoundaries(prox);
        }
    }

    private List<SeqEntry> checkSequence(AdhocHighlightsSequence seq, String text, ParseTreeProxy prox) {
        int ix = 1;
        SeqEntry last = null;
        List<SeqEntry> entries = new ArrayList<>();
        while (seq.moveNext()) {
            AttributeSet atts = seq.getAttributes();
            int start = seq.getStartOffset();
            int end = seq.getEndOffset();
            String sub = text.substring(start, end);
//            System.out.println(ix++ + ". " + atts + " '" + sub + "'");
            SeqEntry curr = new SeqEntry(sub, start, end, atts);
            if (last != null && last.append(curr)) {
                continue;
            } else {
                entries.add(curr);
            }
            last = curr;
        }
        return entries;
    }

    private static class SeqEntry {

        private String text;
        private final int start;
        private int end;
        private final AttributeSet atts;

        public SeqEntry(String text, int start, int end, AttributeSet atts) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.atts = atts;
        }

        private String t2s(ProxyToken tok, ParseTreeProxy prox) {
            return prox.tokenTypeForInt(tok.getType()).name()
                    + " '" + Escaper.CONTROL_CHARACTERS.escape(prox.textOf(tok)) + "' "
                    + tok.getStartIndex() + ":" + tok.getStopIndex();
        }

        void checkTokenBoundaries(ParseTreeProxy prox) {
            AntlrProxies.ProxyToken startToken = prox.tokenAtPosition(start);
            AntlrProxies.ProxyToken stopToken = prox.tokenAtPosition(end - 1);

            int tokenIndexAfterStart = prox.tokens().indexOf(startToken) + 1;

            AntlrProxies.ProxyToken subsequentToken = tokenIndexAfterStart >= prox.tokenCount()
                    ? startToken : prox.tokens().get(tokenIndexAfterStart);

            int startStart = startToken.getStartIndex();
            int substart = subsequentToken.getStartIndex();

            if (start > startStart) {
                if (startStart == substart) { // doc end, same token
                    fail("Sequence entry " + start + ":" + end + " does not start on a token boundary. Should start at "
                            + startStart + " for token " + t2s(startToken, prox)
                            + ".  Text: '" + Escaper.CONTROL_CHARACTERS.escape(text) + "'");
                } else {
                    fail("Sequence entry " + start + ":" + end + " does not start on a token boundary. Should start at "
                            + startStart + " for token " + t2s(startToken, prox)
                            + " or at " + substart + " for token " + t2s(subsequentToken, prox)
                            + ".  Text: '" + Escaper.CONTROL_CHARACTERS.escape(text) + "'");
                }
            }

            int previousIndexToStop = prox.tokens().indexOf(stopToken) - 1;
            ProxyToken priorToStopToken = prox.tokens().get(previousIndexToStop < 0 ? 0 : previousIndexToStop);
            int stopStop = stopToken.getEndIndex();
            if (stopStop != end) {
                fail("Sequence entry " + start + ":" + end + " does not end at a token boundary. Should end at "
                        + stopStop + " to include token " + t2s(stopToken, prox)
                        + " or at " + priorToStopToken.getEndIndex() + " to end with token "
                        + t2s(priorToStopToken, prox)
                        + ".  Text: '" + Escaper.CONTROL_CHARACTERS.escape(text) + "'");
            }
//
//            System.out.println("Start tok " + prox.tokenTypeForInt(startToken.getType()) + " " + startToken.getStartIndex() + ":" + startToken.getEndIndex()
//                    + " '" + Escaper.CONTROL_CHARACTERS.escape(prox.textOf(startToken)) + "'");
//            System.out.println("Stop tok " + prox.tokenTypeForInt(stopToken.getType()) + " " + stopToken.getStartIndex() + ":" + stopToken.getEndIndex()
//                    + " '" + Escaper.CONTROL_CHARACTERS.escape(prox.textOf(stopToken)) + "'");
        }

        public String toString() {
            return start + ":" + end + " " + atts + " '" + Escaper.CONTROL_CHARACTERS.escape(text) + "'";
        }

        boolean append(SeqEntry next) {
            if (end == next.start && atts.equals(next.atts)) {
                text += next.text;
                end = next.end;
                return true;
            }
            return false;
        }

        void assertText(String txt) {
            assertEquals(txt, text, "Wrong text for " + this);
        }

        void assertAttrs(AttributeSet set) {
            assertEquals(set, atts, "Wrong attributes for " + this);
        }
    }

    private static final String RADIAL_GRADIENT = "# Radial Gradient Customizer\n\nA radial gradient is a "
            + "sequence of multiple colors which is (optionally) repeated in _concentric_ circles radianting out "
            + "from a _central point_, with a defined radius and focus point.\n\nThe customizer shows point-selector "
            + "control which mirrors the aspect ratio of the picture being edited.  Press and drag with the mouse "
            + "to draw the initial point andfocus point (creating an effect as if you were viewing the colored "
            + "circles at an angle).\n\nRadial gradients can be used to produce complex, interesting fill patters "
            + "simply and in a way that is well supported by SVG rendering engines.\n\n## Adding Points\n\nDouble "
            + "click in the gradient designer to add a new color-stop, or drag existing stops to move them; each has "
            + "a color-chooser below it which can be used to change the color.\n\nInternally, a radial gradient is defined "
            + "by a collection of colors and frational values between zero and one - percentages of the spread between "
            + "the start and end  points of the gradient at which colors change.\n\nThe *Adjust Colors* button allows "
            + "you to change the palette of all colors in the gradient at once, adjusting all of their hue, saturation "
            + "or brightness at once.";
    private ThrowingRunnable shutdown;
    private GeneratedMavenProject gen;
    private Path grammarFile;
    private AdhocColorings col;
    private AdhocHighlightsContainer hi;
    private String readmeDraft;

    @BeforeEach
    public void setup() throws IOException, ClassNotFoundException, InvalidMimeTypeRegistrationException, URISyntaxException {
        Class.forName(AdhocMimeDataProvider.class.getName());
        Class.forName(AdhocLanguageFactory.class.getName());
        Class.forName(AdhocReparseListeners.class.getName());
        shutdown = initAntlrTestFixtures(true)
                .verboseGlobalLogging()
                .addToNamedLookup(AntlrRunSubscriptions.pathForType(EmbeddedParser.class), ProxiesInvocationRunner.class)
                //                .includeLogs("AdhocMimeDataProvider", "AdhocLanguageHierarchy", "AntlrLanguageFactory")
                .build();

        ProjectTestHelper helper = ProjectTestHelper.relativeTo(SequenceHighlightingTest.class);
        Path parserFile = helper.projectBaseDir().resolve("src/test/resources/org/nemesis/antlr/live/language/coloring/MarkdownParser.g4");
        Path lexerFile = helper.projectBaseDir().resolve("src/test/resources/org/nemesis/antlr/live/language/coloring/MarkdownLexer.g4");

        readmeDraft = loadResource("readmeDraft.md");

        gen = ProjectTestHelper.projectBuilder()
                .verboseLogging()
                .copyMainAntlrSource(parserFile, "com/seq")
                .copyMainAntlrSource(lexerFile, "com/seq")
                .add(Paths.get("com/seq/Sample.md"), () -> RADIAL_GRADIENT)
                .add(Paths.get("com/seq/README.md"), () -> readmeDraft)
                .build("markdown");
        grammarFile = gen.get("MarkdownParser.g4");
        gen.deletedBy(shutdown);
        shutdown.andAlways(SequenceHighlightingTest::clearCache);

        try (InputStream mdColorings = SequenceHighlightingTest.class.getResourceAsStream("md-colorings.antlrcolorings")) {
            assertNotNull(mdColorings, "md-colorings.antlrcolorings should be adjacent to " + SequenceHighlightingTest.class.getName());
            col = AdhocColorings.load(mdColorings);
        }

        col.add("preformatted", Color.CYAN, AttrTypes.ACTIVE, AttrTypes.ITALIC, AttrTypes.FOREGROUND);

        assertNotNull(gen.file("MarkdownParser.g4"));
        hi = new AdhocHighlightsContainer();
        String syntheticMimeType = AdhocMimeTypes.mimeTypeForPath(grammarFile);
        DynamicLanguages.ensureRegistered(syntheticMimeType);
        AdhocMimeTypes.registerFileNameExtension("md", syntheticMimeType);
    }

    static final String[] PREINIT = new String[]{
        "org.nemesis.antlr.live.language.AdhocMimeDataProvider",
        "org.nemesis.antlr.live.language.AdhocLanguageFactory",
        "org.nemesis.antlr.live.language.AdhocReparseListeners",
        "org.nemesis.antlr.live.RebuildSubscriptions",
        "org.nemesis.antlr.live.execution.AntlrRunSubscriptions",
        "org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner",
        "org.nemesis.antlr.live.parsing.EmbeddedAntlrParserImpl",};

    private static String loadResource(String name) throws IOException {
        try (InputStream in = SequenceHighlightingTest.class.getResourceAsStream(name)) {
            assertNotNull(in, name + " should be adjacent to " + SequenceHighlightingTest.class.getName());
            String result = Streams.readString(in, UTF_8);
            assertNotNull(result);
            assertFalse(result.trim().isEmpty(), "Loaded empty text");
            return result;
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        // If we don't do this first, test shutdown will deadlock when we
        // reinitialize MockServices
        Lookup.getDefault().lookup(NbProjectManager.class).clearNonProjectCache();
        AdhocDataLoaderTest.clearCache();
        shutdown.run();
        clearCache();
        MockServices.setServices();
    }

    static void clearCache() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Method m = AdhocMimeTypes.class.getDeclaredMethod("_reinitAndDeleteCache");
        m.setAccessible(true);
        m.invoke(null);
    }

    public static TestFixtures initAntlrTestFixtures(boolean verbose) {
        TestFixtures fixtures = new TestFixtures();
        if (verbose) {
            fixtures.verboseGlobalLogging((Object[]) PREINIT);
        }
        DocumentFactory fact = new DocumentFactoryImpl();
        return fixtures.addToMimeLookup("", fact)
                .addToMimeLookup("text/x-g4", AntlrNbParser.AntlrParserFactory.class)
                .addToMimeLookup("text/x-g4", AntlrNbParser.createErrorHighlighter(), fact)
                .addToNamedLookup(org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder.REGISTRATION_PATH,
                        new org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder())
                .addToDefaultLookup(
                        AdhocMimeResolver.class,
                        AdhocInitHook.class,
                        AdhocColoringsRegistry.class,
                        AdhocMimeDataProvider.class,
                        AdhocDataLoader.class,
                        AdhocLanguageFactory.class,
                        FakeG4DataLoader.class,
                        MavenFolderStrategyFactory.class,
                        NbMavenProjectFactory.class,
                        AntlrFileObjectRelativeResolver.class,
                        NbProjectManager.class,
                        NbJFSUtilities.class,
                        NioNotifier.class
                //                        ,
                //                        NbLoaderPool.class,
                //                        DataObjectEnvFactory.class,
                //                        EditorMimeTypesImpl.class
                );

    }
}
