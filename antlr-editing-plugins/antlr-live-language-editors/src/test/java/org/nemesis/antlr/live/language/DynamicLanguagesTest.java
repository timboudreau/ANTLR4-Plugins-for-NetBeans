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
package org.nemesis.antlr.live.language;

import com.mastfrog.function.state.Obj;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.thread.OneThreadLatch;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.grammar.file.resolver.AntlrFileObjectRelativeResolver;
import org.nemesis.antlr.live.execution.AntlrRunSubscriptions;
import org.nemesis.antlr.live.execution.InvocationRunner;
import org.nemesis.antlr.live.language.coloring.AdhocColorings;
import org.nemesis.antlr.live.language.coloring.AdhocColoringsRegistry;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.SourceInvalidator;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.impl.EmbeddedParser;
import org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
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
import org.openide.util.Pair;

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

//    @Test
    public void testMimeResolverIsPresent() throws IOException, InvalidMimeTypeRegistrationException, DataObjectNotFoundException, InterruptedException, ParseException, BadLocationException {

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

    static class AwaitParseEnvReplacement implements BiConsumer<Extraction, GrammarRunResult<?>> {

        private final OneThreadLatch latch = new OneThreadLatch();
        private AtomicReference<Pair<Extraction, GrammarRunResult>> info = new AtomicReference<>();

        @Override
        public void accept(Extraction t, GrammarRunResult<?> u) {
            info.set(Pair.of(t, u));
            System.out.println("\n\nKABOOM\t" + t.tokensHash() + "\n\n");
            latch.releaseAll();
        }

        public Pair<Extraction, GrammarRunResult> clearAndAwait() throws InterruptedException {
            info.set(null);
            return await();
        }

        public Pair<Extraction, GrammarRunResult> await() throws InterruptedException {
            Pair<Extraction, GrammarRunResult> result = info.getAndSet(null);
            if (result != null) {
                return result;
            }
            latch.await(10, TimeUnit.SECONDS);
            result = info.getAndSet(null);
            assertNotNull(result, "No result received by timeout");
            return result;
        }
    }

    @Test
    public void testFullGenerateCompileModifyRegenerateCycle() throws InvalidMimeTypeRegistrationException, DataObjectNotFoundException, InterruptedException, IOException, ParseException, BadLocationException {
        AdhocLanguageFactory factory = AdhocLanguageFactory.get();

        // First check that our mime type gets registered
        String mime = AdhocMimeTypes.mimeTypeForPath(grammarFile);
        // We need to create this folder for EditorSettings to believe a mime
        // type is really here
        FileUtil.createFolder(FileUtil.getConfigRoot(), "Editors/" + mime);

        Path grammarPathFromMime = AdhocMimeTypes.grammarFilePathForMimeType(mime);
        String defext = AdhocMimeTypes.fileExtensionFor(mime);
        assertEquals(grammarFile, grammarPathFromMime, "Adhoc mime types registry is broken");
        assertNotNull(mime);
        DynamicLanguages.ensureRegistered(mime);
        // Register our own file extension so we can ensure that gets recognized as
        // the file type
        AdhocMimeTypes.registerFileNameExtension(EXT, mime);

        // Make sure a set of colorings got registered
        AdhocColorings col = AdhocColoringsRegistry.getDefault().get(mime);
        assertNotNull(col, "No colorings created");

        // Make sure there really is a MimeLookup for our type
        Lookup mimeLookup = MimeLookup.getLookup(mime);
        assertNotNull(mimeLookup, "No lookup");
        // And that the parser factory got registered
        assertNotNull(mimeLookup.lookup(ParserFactory.class), "No parser factory in mime lookup " + mime);

        // Create a file with our extension and make sure we can read it
        Path testit = example.getParent().resolve("testit-" + System.currentTimeMillis() + "." + defext);
        // Make sure it gets cleaned up on exit
        shutdown.andAlways(() -> {
            FileUtils.deleteIfExists(testit);
        });
        Files.copy(example, testit, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        FileObject fo = toFo(testit);
        assertNotNull(fo);
        // And make sure that a copy of the file is loaded with our newly
        // registered mime type when it has the extension we assigned
        DataObject dob = DataObject.find(toFo(testit));
        assertNotNull(dob);
        assertSame(fo, dob.getPrimaryFile());
        assertEquals(mime, fo.getMIMEType(), "Adhoc mime resolver not used or " + fo.getPath()
                + " not recognized by the resolver for our custom mime type");

        // Make sure AdhocDataLoader is working and is the one that loaded the file
        AdhocDataObject ahdob = dob.getLookup().lookup(AdhocDataObject.class);
        assertNotNull(ahdob, "Adhoc data loader not working or not used for " + dob);

        // XXx Double check?  We need to do this here for something?
        DynamicLanguages.ensureRegistered(mime);

        // Test for artifacts of bugs in masterfs
        assertEquals(TEXT_1, ahdob.getPrimaryFile().asText(), "File content altered");

        // Okay, now try really running the whole thing through the parser infrastructure.
        // The exercises a LOT of stuff - basically an end-to-end test of all of the
        // antlr generation, parsing, extraction, etc. support
        Source src = Source.create(ahdob.getPrimaryFile());
        Snapshot snap = src.createSnapshot();
        // Make sure the snapshot is sane
        assertEquals(TEXT_1, snap.getText().toString(), "Snapshot generation is broken: '" + snap.getText() + "'");

        // Unreference our first source instance
        src = null;

        // Get the language instance for our registered language - we are going
        // to test that it gets replaced when the grammar is edited, AND that
        // it really gets garbage collected rather than some piece of plumbing
        // hanging onto it
        Language<AdhocTokenId> lang = (Language<AdhocTokenId>) findHier(mime);
        assertNotNull(lang, "Language null");
        assertEquals(mime, lang.mimeType());
        int originalLanguageIdentityHashcode = System.identityHashCode(lang);
        lang = null;

        // Make sure our ParserFactory got registered in the MimeLookup for our mime type
        assertTrue(ParserManager.canBeParsed(mime), () -> "Parser manager can't find parser for " + mime
                + " in MimeLookup.  Contents: " + MimeLookup.getLookup(mime).lookupAll(Object.class));

        // Now perform a parse with our registered parser
        AdhocParserResult p = parse(testit);

        String originalTokenHash = p.grammarHash();

        // Make sure we did not get garbage or nothing in our snapshot (this really happened)
        CharSequence sq = p.getSnapshot().getText();
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
        // Double check the mime type again (necessary?)
        assertEquals(mime, toFo(example).getMIMEType());

        // Make sure the grammar path AdhocMimeTypes thinks points at our
        // grammar really does
        Path gp = AdhocMimeTypes.grammarFilePathForMimeType(mime);
        assertEquals(gp, gen.get("NestedMaps.g4"), () -> "AdhocMimeTypes reports the "
                + "grammar path for " + mime + " as " + gp + " but the generation "
                + "result says the path to NestedMaps.g4 is "
                + gen.get("NestedMaps.g4"));

        EmbeddedAntlrParser parser = AdhocLanguageHierarchy.parserFor(mime);

        AwaitParseEnvReplacement parserEnvironmentReplaced = new AwaitParseEnvReplacement();
        parser.listen(parserEnvironmentReplaced);

        // Now alter the file, generating a file change event, which should
        // trigger regeneration of all of the underpinnings of our language.
        //
        // We change the names of one lexer rule and one parser rule, and
        // that's what we should see used in the next parser result we get
        //
        // Do this this way or we create two file change events and two
        // reparses
        gen.updateText("NestedMaps.g4", txt -> {
            txt = Strings.literalReplaceAll("Number", "Puppy", txt);
            txt = Strings.literalReplaceAll("booleanValue", "dogCow", txt);
            return txt;
        });

        FileObject f1 = gen.file("NestedMaps.g4");
        String txt = f1.asText();
        assertTrue(txt.contains("dogCow"), "GeneratedAntlrProject.updateText did not update text");
        assertTrue(txt.contains("Puppy"), "GeneratedAntlrProject.updateText did not update text");

        // Make sure our original parser result became unreferenced
        // and was cleaned up by the garbage collector and didn't leak
        Reference<AdhocParserResult> ref = new WeakReference<>(p);
        p = null;
        p = assertGarbageCollected(ref, "Old parser result");

        // "Open" the document for the file in the editor plumbing.  This should
        // cause RebuildSubscriptions.Mapping for this file to switch from just
        // mapping and listening for file changes and mapping the file into the
        // JFS, to listening on the document as well and mapping the Document, not
        // the file, into the JFS
        EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
        assertNotNull(ck);
        StyledDocument doc = ck.openDocument();
        assertNotNull(doc);

        Obj<ParseTreeProxy> lastDocumentProxy = Obj.createAtomic();
        Obj<ParseTreeProxy> lastFileProxy = Obj.createAtomic();

        Set<String> fileRuleNames = Collections.synchronizedSet(new HashSet<>());
        Set<String> docRuleNames = Collections.synchronizedSet(new HashSet<>());
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        boolean[] notificationsDelivered = new boolean[4];
        Thread testThread = Thread.currentThread();
        // Create listeners to attach to AdhocReparseListeners to get notified
        // of reparses both when the file is modified, and when the document is
        BiConsumer<Document, EmbeddedAntlrParserResult> ref1 = (Document d, EmbeddedAntlrParserResult rs) -> {
            synchronized (notificationsDelivered) {
                notificationsDelivered[0] = true;
                notificationsDelivered[2] = Thread.currentThread() == testThread;
            }
            try {
                assertSame(doc, d, "called with some other document");
                docRuleNames.addAll(rs.proxy().allRuleNames());
                assertNull(lastDocumentProxy.get(), "called twice");
                lastDocumentProxy.set(rs.proxy());

                int ic2 = System.identityHashCode(Language.find(mime));
                if (ic2 == originalLanguageIdentityHashcode) {
                    try {

                        findReferenceGraphPathsTo(Language.find(mime), mimeLookup);
                    } catch (InterruptedException ex) {
                        org.openide.util.Exceptions.printStackTrace(ex);
                    }
                }
                assertNotEquals(originalLanguageIdentityHashcode, ic2, "Old language instance is still returned - tokens will be wrong");
            } catch (Throwable t) {
                t.printStackTrace();
                thrown.updateAndGet(old -> {
                    if (old != null) {
                        old.addSuppressed(t);
                        return old;
                    }
                    return t;
                });
            }
        };
        // these listeners must remain referenced or they will be gc'd
        BiConsumer<FileObject, EmbeddedAntlrParserResult> ref2 = (lfo, rs) -> {
            synchronized (notificationsDelivered) {
                notificationsDelivered[1] = true;
                notificationsDelivered[3] = Thread.currentThread() == testThread;
            }
            try {
                assertSame(dob.getPrimaryFile(), lfo, "called with some other file");
                assertNull(lastFileProxy.get(), "called twice");
                fileRuleNames.addAll(rs.proxy().allRuleNames());
                lastFileProxy.set(rs.proxy());
            } catch (Throwable t) {
                t.printStackTrace();
                thrown.updateAndGet(old -> {
                    if (old != null) {
                        old.addSuppressed(t);
                        return old;
                    }
                    return t;
                });
            }
        };

        AdhocReparseListeners.listen(mime, doc, ref1);
        AdhocReparseListeners.listen(mime, dob.getPrimaryFile(), ref2);

        // Now reparse our file, which should trigger all of the plumbing
        // to regenerate itself for the things that changed
        AdhocParserResult p1 = parse(testit);
        String newTokensHash = p1.grammarHash();

        assertNotEquals(originalTokenHash, newTokensHash);

        // The language should get replaced
        boolean languageFired = AdhocLanguageFactory.awaitFire(3000);
        Pair<Extraction, GrammarRunResult> pair = parserEnvironmentReplaced.await();
        Thread.sleep(4000);

        System.out.println("\nSLEEPING....");
        // Do a quick busyWait, since language replacement notifications are
        // async
        for (int i = 0; i < 200; i++) {
            synchronized (notificationsDelivered) {
                if (notificationsDelivered[0] && notificationsDelivered[1]) {
                    System.out.println("  notifications delivered");
                    break;
                }
            }
            Thread.sleep(20);
        }

        assertTrue(notificationsDelivered[0], "Document reparse notification should have been delivered");
        assertTrue(notificationsDelivered[1], "File reparse notification should have been delivered");

        // Remove our listeners so they don't get called again (they will throw if they are
        // and we will rethrow at the end)
        AdhocReparseListeners.unlisten(mime, doc, ref1);
        AdhocReparseListeners.unlisten(mime, dob.getPrimaryFile(), ref2);

        assertNotNull(p1);
        assertNotSame(p, p1, "Should not have gotten cached parser result");

        // Now, the money-shot:  Make sure the analysis of the file contains the token and
        // parser rule names Puppy and dogCow instead of Number and booleanValue.
        //
        // If no, then something in the path between there and here reused a
        // cached result when it should have noticed the grammar had changed.
        AntlrProxies.ParseTreeProxy ptp1 = p1.parseTree();
        JFS jfs = p1.result().runResult().jfs();

        // DELETEME - dump the JFS to disk for forensics
//        Path temp = Files.createTempDirectory("jfs-dump-");
//        System.out.println("DUMPING TO DISK: " + temp);
//        Set<Path> files = jfs.dumpToDisk(temp, StandardLocation.SOURCE_PATH, StandardLocation.SOURCE_OUTPUT, StandardLocation.CLASS_OUTPUT);
//        System.out.println("DUMP: " + files);
        // Check that the rule names we changed are there
        assertTrue(ptp1.allRuleNames().contains("dogCow"));
        assertTrue(ptp1.allRuleNames().contains("Puppy"));

        String foundText = doc.getText(0, doc.getLength());
        assertEquals(TEXT_1, foundText);

        Language<AdhocTokenId> newLanguage = (Language<AdhocTokenId>) findHier(mime);
        int newLanguageIdHash = System.identityHashCode(newLanguage);

        Set<AdhocTokenId> ids = newLanguage.tokenIds();
        newLanguage = null;
//        assertNotEquals(originalLanguageIdentityHashcode, newLanguageIdHash,
//                "Language was not replaced.");
        
//        AdhocMimeDataProvider.getDefault().gooseLanguage(mime);

        String tid = AdhocLanguageHierarchy.hierarchyInfo(mime).grammarTokensHash();
        System.out.println("TOKS HASH " + tid + " p1 hash " + p1.grammarHash() + " orig hash " + originalTokenHash);

        Set<String> names = new HashSet<>();
        for (AdhocTokenId id : ids) {
            names.add(id.name());
            System.out.println("  ID " + id);
        }
//        assertTrue(names.contains("Puppy"));

        // Now grab the editor kit, and make sure the token sequence
        // created by AdhocLanguageLexer really contains the token sequence
        // this file should have WITH the modified tokens
        EditorKit kit = mimeLookup.lookup(EditorKit.class);
        assertNotNull(kit);
        assertTrue(kit instanceof AdhocEditorKit);

        assertFalse(fileRuleNames.isEmpty());
        assertFalse(docRuleNames.isEmpty());
        assertTrue(fileRuleNames.contains("dogCow"), fileRuleNames::toString);
        assertTrue(docRuleNames.contains("dogCow"), docRuleNames::toString);

        System.out.println("SLEEP SOME MORE....");
        Thread.sleep(4000);

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
                .add("CloseBrace") // Omit this last token - with the addition of testing with and without newline,
                // we will sometimes get __dummy_token__ here, depending on the vagaries of the
                // thread scheduler and whether a document or lexer input copy of the text was
                // handed to the EmbeddedAntlrParsersImpl first
                //                .add("Whitespace")
                ;

        // Now really test the token sequence with our tester
        doc.render(() -> {
            TokenHierarchy<StyledDocument> th = TokenHierarchy.get(doc);

            assertNotNull(th);
            TokenSequence<AdhocTokenId> seq = th.tokenSequence((Language<AdhocTokenId>) Language.find(mime));
            assertNotNull(seq);
            assertFalse(seq.isEmpty());
            assertTrue(seq.isValid());
            checker.testTokenSequence(seq);
            seq.moveStart();

            // A few bits of plumbing grab the current file object from
            // a Threadlocal in AdhocEditorKit, whenever we're inside
            // render() - so make sure that actually works
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
        // Make sure the editor kit doesn't think we're STILL inside render and
        // cleared the thread local on exit
        assertNull(AdhocEditorKit.currentFileObject());

        // Now make sure our reparse listeners really got called
        assertNotNull(lastFileProxy.get(), "File reparse listener was not called");
        assertNotNull(lastDocumentProxy.get(), "Document reparse listener was not called");
        assertSame(lastFileProxy.get(), lastDocumentProxy.get(), "Listeners passed different objects");

        // And make sure they didn't get called more than once
        Throwable t = thrown.get();
        if (t != null) {
            Exceptions.chuck(t);
        }

        /*
        WaitReparse wr = new WaitReparse();
        AdhocReparseListeners.listen(mime, doc, wr);
        int last = 0;
        LongList ll = CollectionUtils.longList(2000);
        for (int i = 0; i < 200; i++) {
            long then = System.currentTimeMillis();
            int len = doc.getLength();
            String name = "pooger" + i;
            doc.insertString(len - 2, ",\n " + name + ": " + (i * 10), null);
            parse(doc);
            last = wr.assertNotified(last);
            long elapsed = System.currentTimeMillis() - then;
            if (i > 150) {
                ll.add(elapsed);
            }
//            System.out.println("loop " + i + " elapsed " + elapsed + "ms");
        }
        long cum = 0;
        for (int i = 0; i < ll.size(); i++) {
            cum += ll.getLong(i);
        }
        System.out.println("Average reparse time: " + (cum / ll.size()));
         */
    }

    static class WaitReparse implements BiConsumer<Document, EmbeddedAntlrParserResult> {

        OneThreadLatch latch = new OneThreadLatch();
        Document doc;
        EmbeddedAntlrParserResult res;
        private final AtomicInteger callCount = new AtomicInteger();

        @Override
        public void accept(Document t, EmbeddedAntlrParserResult u) {
            doc = t;
            res = u;
            callCount.incrementAndGet();
            latch.releaseAll();
        }

        public int assertNotified(int last) throws InterruptedException {
            if (callCount.get() == last + 1) {
                return last + 1;
            }
            await();
            int result = callCount.get();
            assertTrue(result > last, "Call count should be at least " + (last + 1) + " but is still " + last);
//            assertEquals(last + 1, result);
            return result;
        }

        public void await() throws InterruptedException {
            latch.await(10, TimeUnit.SECONDS);
        }
    }

    private <T> T assertGarbageCollected(Reference<T> ref, String msg) throws InterruptedException {
        T p;
        for (int i = 0; i < 50; i++) {
            System.gc();
            System.runFinalization();
            if (ref.get() == null) {
                break;
            }
            Thread.sleep(20);
        }
        p = ref.get();
        assertNull(p, msg + " was not garbage collected");
        return p;
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

    private AdhocParserResult parse(Document doc) throws ParseException {
        UT ut = new UT();
        Source src = Source.create(doc);
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
            assertNotNull(result, () -> "Got null parser result " + FakeAntlrLoggers.lastText());
            assertTrue(result instanceof AdhocParserResult);
            res = (AdhocParserResult) result;
            if (res.parseTree().isUnparsed()) {
                try {
                    res.result().runResult().rethrow();
                } catch (Throwable ex) {
                    throw new AssertionError(res.result().toString() + FakeAntlrLoggers.lastText(), ex);
                }
            }
//            assertFalse(res.parseTree().isUnparsed());
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
        shutdown = initAntlrTestFixtures(true)
                .addToNamedLookup(AntlrRunSubscriptions.pathForType(EmbeddedParser.class), ProxiesInvocationRunner.class)
                //                .includeLogs("AdhocMimeDataProvider", "AdhocLanguageHierarchy", "AntlrLanguageFactory")
                .build();
        gen = ProjectTestHelper.projectBuilder()
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
    }

    static void clearCache() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Method m = AdhocMimeTypes.class.getDeclaredMethod("_reinitAndDeleteCache");
        m.setAccessible(true);
        m.invoke(null);
    }

    public static TestFixtures initAntlrTestFixtures(boolean verbose) {
        TestFixtures fixtures = new TestFixtures()
                .avoidStartingModuleSystem()
//                .insanelyVerboseLogging();
                ;
        if (verbose) {
            fixtures.verboseGlobalLogging(
                    AdhocLanguageHierarchy.class,
                    AdhocMimeDataProvider.class,
                    AdhocLanguageFactory.class,
                    AdhocLexerNew.class,

//                    AntlrGeneratorAndCompiler.class,
//                    ParsingUtils.class,
//                    RebuildSubscriptions.class,
//                    AntlrRunSubscriptions.class,
//                    AntlrGenerator.class,
//                    JFS.class,
//                    "org.nemesis.antlr.memory.tool.ToolContext",
//                    "org.nemesis.antlr.project.AntlrConfigurationCache",
//                    "org.nemesis.antlr.project.impl.FoldersHelperTrampoline",
//                    "org.nemesis.antlr.project.impl.HeuristicFoldersHelperImplementation",
//                    "org.nemesis.antlr.project.impl.InferredConfig",
//                    AntlrGeneratorAndCompiler.class,
//                    AntlrGeneratorBuilder.class,
//                    AntlrGeneratorAndCompilerBuilder.class,
                    InvocationRunner.class,
//                    "org.nemesis.antlr.live.JFSMapping",
                    "org.nemesis.antlr.live.parsing.EmbeddedAntlrParserImpl",
                    ProxiesInvocationRunner.class,
                    DynamicLanguages.class
            );
        }
        DocumentFactory fact = new DocumentFactoryImpl();
        return fixtures.addToMimeLookup("", fact)
                .addToMimeLookup("text/x-g4", AntlrNbParser.AntlrParserFactory.class)
                .addToMimeLookup("text/x-g4", AntlrNbParser.createErrorHighlighter(), fact)
                .addToNamedLookup(
                        org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder.REGISTRATION_PATH,
                        new org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder())
                .addToDefaultLookup(
                        FakeAntlrLoggers.class,
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
                    sb.append("At token ").append(ix).append(' ').append(problem);
                }
                ix++;
            }
            if (sb.length() > 0) {
                sb.insert(0, "Wrong token sequence: ");
                fail(() -> sb.toString() + FakeAntlrLoggers.lastText());
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
