/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.language;

import static com.google.common.base.Charsets.UTF_8;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.SourceInvalidator;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.impl.EmbeddedParser;
import org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.jfs.nb.NbJFSUtilities;
import org.nemesis.test.fixtures.support.GeneratedMavenProject;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.modules.editor.impl.DocumentFactoryImpl;
import org.netbeans.modules.editor.settings.storage.api.EditorSettings;
import org.netbeans.modules.masterfs.watcher.nio2.NioNotifier;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.ParserFactory;
import org.netbeans.modules.projectapi.nb.NbProjectManager;
import org.netbeans.spi.editor.document.DocumentFactory;
import org.netbeans.spi.lexer.LanguageProvider;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public class DynamicLanguagesTest {

    private static final String EXT = "woogle";
    private ThrowingRunnable shutdown;
    private GeneratedMavenProject gen;
    private Path grammarFile;
    private Path example;

    public static final String TEXT_1
            = "{ skiddoo : 23, meaningful : true,\n"
            + "meaning: '42', \n"
            + "thing: 51 }";

    @Test
    public void testMimeResolverIsPresent() throws IOException, InvalidMimeTypeRegistrationException {

        Collection<? extends MIMEResolver> all = Lookup.getDefault().lookupAll(MIMEResolver.class);
        AdhocMimeResolver found = null;
        int ix = 0;
        for (MIMEResolver m : all) {
            if (m instanceof AdhocMimeResolver) {
                found = (AdhocMimeResolver) m;
                break;
            }
            ix++;
        }
        assertNotNull(found);
        String mime = AdhocMimeTypes.mimeTypeForPath(grammarFile);
        String defext = AdhocMimeTypes.fileExtensionFor(mime);
        Path testit = example.getParent().resolve("testit-" + System.currentTimeMillis() + "." + defext);
        shutdown.andAlways(() -> {
            FileUtils.deleteIfExists(testit);
        });
        Files.copy(example, testit, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        FileObject fo = toFo(testit);
        String mt = found.findMIMEType(fo);
        assertNotNull(mt);
        assertEquals(mime, mt);

        assertEquals(mime, FileUtil.getMIMEType(fo), "MIMEResolver present but not used for " + fo.getPath());
        assertEquals(mime, fo.getMIMEType(), "MIMEResolver present but not used for " + fo.getPath());

        assertTrue(AdhocMimeTypes.allExtensionsForMimeType(mime).contains(defext));
        try {
            boolean registered = AdhocMimeTypes.registerFileNameExtension(EXT, mime);
            assertTrue(registered);
        } catch (InvalidMimeTypeRegistrationException e) {
            // ok, earlier test registered it
        }
        assertTrue(AdhocMimeTypes.allExtensionsForMimeType(mime).contains(EXT));
        assertTrue(AdhocMimeTypes.allExtensionsForMimeType(mime).contains(defext));

        FileObject ex = toFo(example);
        assertTrue(AdhocMimeTypes.isAdhocMimeType(ex.getMIMEType()));
        Path p = AdhocMimeTypes.grammarFilePathForMimeType(ex.getMIMEType());
        // We may be seeing a cached mime type from a previous test method -
        // this is being cached somewhere in the bowels of the filesystem api - so
        // test that the file names match and that will be sufficient
        assertEquals(p.getFileName(), grammarFile.getFileName());
        assertEquals(ex.getMIMEType(), FileUtil.getMIMEType(ex));
    }

    @Test
    public void testCorrectDataLoaderIsUsed() throws InvalidMimeTypeRegistrationException, DataObjectNotFoundException, InterruptedException, IOException, ParseException, BadLocationException {
        AdhocLanguageFactory factory = AdhocLanguageFactory.get();

        String mime = AdhocMimeTypes.mimeTypeForPath(grammarFile);
        // We need to create this folder for EditorSettings to believe a mime
        // type is really here
        FileUtil.createFolder(FileUtil.getConfigRoot(), "Editors/" + mime);

        Path grammarPathFromMime = AdhocMimeTypes.grammarFilePathForMimeType(mime);
        String defext = AdhocMimeTypes.fileExtensionFor(mime);
        assertEquals(grammarFile, grammarPathFromMime, "Adhoc mime types registry is broken");
        assertNotNull(mime);
        DynamicLanguages.ensureRegistered(mime);
        AdhocMimeTypes.registerFileNameExtension(EXT, mime);

        AdhocColorings col = AdhocColoringsRegistry.getDefault().get(mime);
        assertNotNull(col, "No colorings created");

        Lookup mimeLookup = MimeLookup.getLookup(mime);
        assertNotNull(mimeLookup, "No lookup");
        assertNotNull(mimeLookup.lookup(ParserFactory.class), "No parser factory in mime lookup " + mime);

        Path testit = example.getParent().resolve("testit-" + System.currentTimeMillis() + "." + defext);
        shutdown.andAlways(() -> {
            FileUtils.deleteIfExists(testit);
        });
        Files.copy(example, testit, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        FileObject fo = toFo(testit);

        assertNotNull(fo);
        DataObject dob = DataObject.find(toFo(testit));
        assertNotNull(dob);
        assertSame(fo, dob.getPrimaryFile());
        assertEquals(mime, fo.getMIMEType(), "Adhoc mime resolver not used");

        AdhocDataObject ahdob = dob.getLookup().lookup(AdhocDataObject.class);
        assertNotNull(ahdob);

        DynamicLanguages.ensureRegistered(mime);

        // Test for artifacts of bugs in masterfs
        assertEquals(TEXT_1, ahdob.getPrimaryFile().asText(), "File content altered");
        Source src = Source.create(ahdob.getPrimaryFile());
        Snapshot snap = src.createSnapshot();
        assertEquals(TEXT_1, snap.getText().toString(), "Snapshot generation is broken: '" + snap.getText() + "'");

        src = null;

        Language<AdhocTokenId> lang = (Language<AdhocTokenId>) findHier(mime);
        assertNotNull(lang, "Language null");
        assertEquals(mime, lang.mimeType());
        int ic = System.identityHashCode(lang);
        lang = null;

        assertTrue(ParserManager.canBeParsed(mime), "Parser manager can't find parser for " + mime);

        AdhocParserResult p = parse(testit);
        CharSequence sq = p.getSnapshot().getText();
        System.out.println("TYPE OF sq is " + sq.getClass().getName());
        assertEquals(TEXT_1, sq.toString(), "Text was somehow altered in snapshot creation: " + p.getSnapshot());
        assertNotNull(p);
        AntlrProxies.ParseTreeProxy ptp = p.parseTree();
        assertFalse(ptp.isUnparsed());
        assertFalse(ptp.hasErrors(), () -> {
            try {
                return Strings.join('\n', ptp.syntaxErrors() + " in " + sq);
            } catch (Exception ex) {
                return Exceptions.chuck(ex);
            }
        });

        assertEquals(mime, toFo(example).getMIMEType());

        Path gp = AdhocMimeTypes.grammarFilePathForMimeType(mime);
        assertEquals(gp, gen.get("NestedMaps.g4"));

        System.out.println("\n\n\nDO THE THING.\n");

        gen.replaceString("NestedMaps.g4", "Number", "Puppy").replaceString("NestedMaps.g4", "booleanValue", "dogCow");

        FileObject f1 = gen.file("NestedMaps.g4");
        String txt = f1.asText();
        assertTrue(txt.contains("dogCow"));
        assertTrue(txt.contains("Puppy"));

        Reference<AdhocParserResult> ref = new WeakReference<>(p);
        p = null;
        for (int i = 0; i < 50; i++) {
            System.gc();
            System.runFinalization();
            if (ref.get() == null) {
                break;
            }
            Thread.sleep(20);
        }
        p = ref.get();
//        assertNull(p, "Old parser result was not garbage collected");

        int ic2 = System.identityHashCode(Language.find(mime));
        if (ic2 == ic) {
            findReferenceGraphPathsTo(Language.find(mime), mimeLookup);
        }
//        assertNotEquals(ic, ic2, "Old language instance is still returned - tokens will be wrong");
        EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
        assertNotNull(ck);
        StyledDocument doc = ck.openDocument();
        assertNotNull(doc);

        // these listeners must remain referenced or they will be gc'd
        BiConsumer<Document, EmbeddedAntlrParserResult> ref1;
        BiConsumer<FileObject, EmbeddedAntlrParserResult> ref2;
        ParseTreeProxy[] lastProxy = new ParseTreeProxy[2];

        AdhocReparseListeners.listen(mime, doc, ref1 = (Document d, EmbeddedAntlrParserResult rs) -> {
            assertSame(doc, d, "called with some other document");
            assertNull(lastProxy[0], "called twice");
            lastProxy[0] = rs.proxy();
        });
        AdhocReparseListeners.listen(mime, dob.getPrimaryFile(), ref2 = (lfo, rs) -> {
            assertSame(dob.getPrimaryFile(), lfo, "called with some other file");
            assertNull(lastProxy[1], "called twice");
            lastProxy[1] = rs.proxy();
        });
        Thread.sleep(2000);

        AdhocParserResult p1 = parse(testit);
        assertNotNull(p1);
        assertNotSame(p, p1, "Should not have gotten cached parser result");

        System.out.println("\nDO parse that should be changed");
        AntlrProxies.ParseTreeProxy ptp1 = p1.parseTree();
        assertTrue(ptp1.toString().contains("dogCow"));
        assertTrue(ptp1.toString().contains("Puppy"));

        String foundText = doc.getText(0, doc.getLength());
        assertEquals(TEXT_1, foundText);

        EditorKit kit = mimeLookup.lookup(EditorKit.class);
        assertNotNull(kit);
        assertTrue(kit instanceof AdhocEditorKit);

        TokenSequenceChecker checker = new TokenSequenceChecker()
                .add("OpenBrace").add("Whitespace")
                .add("Identifier", "skiddoo")
                .add("Whitespace")
                .add("Colon")
                .add("Whitespace")
                .add("Puppy", "23")
                .add("Comma")
                .add("Whitespace")
                .add("Identifier", "meaningful")
                .add("Whitespace")
                .add("Colon")
                .add("Whitespace")
                .add("True", "true")
                .add("Comma")
                .add("Whitespace")
                .add("Identifier", "meaning")
                .add("Colon")
                .add("Whitespace")
                .add("String", "'42'")
                .add("Comma")
                .add("Whitespace")
                .add("Identifier", "thing")
                .add("Colon")
                .add("Whitespace")
                .add("Puppy", "51")
                .add("Whitespace")
                .add("CloseBrace")
                .add("Whitespace");

        doc.render(() -> {
            TokenHierarchy<StyledDocument> th = TokenHierarchy.get(doc);

            assertNotNull(th);
            TokenSequence<AdhocTokenId> seq = th.tokenSequence((Language<AdhocTokenId>) Language.find(mime));
            assertNotNull(seq);
            assertFalse(seq.isEmpty());
            assertTrue(seq.isValid());
            checker.testTokenSequence(seq);
            seq.moveStart();

            assertSame(dob.getPrimaryFile(), AdhocEditorKit.currentFileObject());

            assertSame(AdhocEditorKit.currentDocument(), th.inputSource());

            StringBuilder tokenizedText = new StringBuilder();
            int length = 0;
            while (seq.moveNext()) {
                Token<AdhocTokenId> tok = seq.token();
                assertNotNull(tok);
                tokenizedText.append(tok.text());
                length += tok.length();
            }
            // WTF? Lexer infrastructure appends a nonexistent newline?
            assertEquals(TEXT_1 + "\n", tokenizedText.toString());
            assertEquals(TEXT_1.length() + 1, length);
        });
        assertNull(AdhocEditorKit.currentFileObject());
        Thread.sleep(2000);
        assertNotNull(lastProxy[1], "File reparse listener was not called");
        assertNotNull(lastProxy[0], "Document reparse listener was not called");
        assertSame(lastProxy[0], lastProxy[1], "Listeners passed different objects");
    }

    private static void findReferenceGraphPathsTo(Object shouldBeGcd, Lookup lkp) throws InterruptedException {
        boolean fired = AdhocLanguageFactory.awaitFire(1500);
        assertTrue(fired, "AdhocLanguageFactory did not fire a change");
        if (true) {
            return;
        }
        Map<String, Object> roots = new HashMap<>();
        roots.put("ParserManager", ParserManager.class);
        roots.put("MimeLookup", lkp);
        roots.put("EditorSettings", EditorSettings.getDefault());
        roots.put("DefaultLookup", Lookup.getDefault());
        List<String> targets = ReferencesFinder.detect(shouldBeGcd, roots);
        if (!targets.isEmpty()) {
            fail("Referenced by:\n" + Strings.join('\n', targets));
        }
    }

    private AdhocParserResult parse(Path path) throws ParseException {
        UT ut = new UT();
        FileObject fo = toFo(path);
        Source src = Source.create(fo);
        SourceInvalidator.create().accept(fo);
        src = null;
        src = Source.create(fo);
        ParserManager.parse(Collections.singleton(src), ut);
        assertNotNull(ut.res, "No parser result");
        assertTrue(ut.res instanceof AdhocParserResult);

        AdhocParserResult ah = (AdhocParserResult) ut.res;
        assertFalse(ah.parseTree().isUnparsed());
        return ah;
    }

    static final class UT extends UserTask {

        private AdhocParserResult res;

        @Override
        public void run(ResultIterator resultIterator) throws Exception {
            Parser.Result result = resultIterator.getParserResult();
            assertNotNull(result);
            assertTrue(result instanceof AdhocParserResult);
            res = (AdhocParserResult) result;
            System.out.println("P_RES " + res.grammarHash() + " - " + res.parseTree());
            assertFalse(res.parseTree().isUnparsed());
        }
    }

    private Language<?> findHier(String mime) {
        for (LanguageProvider p : Lookup.getDefault().lookupAll(LanguageProvider.class)) {
            Language<?> lang = p.findLanguage(mime);
            if (lang != null) {
                return lang;
            }
        }
        return null;
    }

    private static FileObject toFo(Path path) {
        return FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
    }

    @BeforeEach
    public void setup() throws IOException, ClassNotFoundException {
        Class.forName(AdhocMimeDataProvider.class.getName());
        Class.forName(AdhocLanguageFactory.class.getName());
        Class.forName(AdhocReparseListeners.class.getName());
        shutdown = initAntlrTestFixtures(false)
                .addToNamedLookup(AntlrRunSubscriptions.pathForType(EmbeddedParser.class), ProxiesInvocationRunner.class)
                //                .includeLogs("AdhocMimeDataProvider", "AdhocLanguageHierarchy", "AntlrLanguageFactory")
                .build();
        gen = ProjectTestHelper.projectBuilder()
                .verboseLogging()
                .writeStockTestGrammar("com.foo")
                .build("foo");
        grammarFile = gen.get("NestedMaps.g4");
        gen.deletedBy(shutdown);
        example = Paths.get(System.getProperty("java.io.tmpdir")).resolve("test-" + Long.toString(System.currentTimeMillis(), 36) + "." + EXT);

        Files.write(example, TEXT_1.getBytes(UTF_8), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        shutdown.andAlways(() -> {
            FileUtils.deleteIfExists(example);
        });
        shutdown.andAlways(DynamicLanguagesTest::clearCache);
    }

    @AfterEach
    public void tearDown() throws Exception {
        // If we don't do this first, test shutdown will deadlock when we
        // reinitialize MockServices
//        Lookup.getDefault().lookup(NbProjectManager.class).clearNonProjectCache();
        AdhocMimeDataProvider.getDefault().clear();
        shutdown.run();
        clearCache();
//        MockServices.setServices();
    }

    static void clearCache() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Method m = AdhocMimeTypes.class.getDeclaredMethod("_reinitAndDeleteCache");
        m.setAccessible(true);
        m.invoke(null);
    }

    public static TestFixtures initAntlrTestFixtures(boolean verbose) {
        TestFixtures fixtures = new TestFixtures();
        if (verbose) {
            fixtures.verboseGlobalLogging();
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

    static class TokenSequenceChecker {

        private final List<Check> all = new ArrayList<>();

        public TokenSequenceChecker add(String tokenType) {
            all.add(new Check(tokenType, null));
            return this;
        }

        public TokenSequenceChecker add(String tokenType, String text) {
            all.add(new Check(tokenType, text));
            return this;
        }

        public void testTokenSequence(TokenSequence<AdhocTokenId> seq) {
            StringBuilder sb = new StringBuilder();
            int ix = 0;
            Iterator<Check> chex = all.iterator();
            while (chex.hasNext() && seq.moveNext()) {
                Token<AdhocTokenId> tok = seq.token();
                Check check = chex.next();
                String problem = check.test(tok);
                if (problem != null) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append("At token ").append(ix).append(problem);
                }
                ix++;
            }
            if (sb.length() > 0) {
                fail(sb.toString());
            }
        }

        static class Check {

            private final String typeName;
            private final String text;

            public Check(String typeName, String text) {
                this.typeName = typeName;
                this.text = text;
            }

            public String test(Token<AdhocTokenId> tok) {
                AdhocTokenId id = tok.id();
                if (!typeName.equals(id.name())) {
                    return "Expected token type '" + typeName + "' but was '" + id.name() + "'";
                }
                if (text != null) {
                    CharSequence sq = tok.text();
                    if (sq == null) {
                        return "Expected non-null token text '" + text + "' but was null";
                    }
                    if (!text.equals(sq.toString())) {
                        return "Expected token text '" + text + "' but was '" + sq + "'";
                    }
                }
                return null;
            }

        }
    }
}
