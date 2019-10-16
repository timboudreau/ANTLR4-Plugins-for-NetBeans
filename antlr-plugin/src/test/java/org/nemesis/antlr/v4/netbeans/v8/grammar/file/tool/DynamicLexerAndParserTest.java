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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeDataProvider;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeResolver;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocTokenId;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.DynamicLanguageSupport;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocParserResult;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.Reason.UNIT_TEST;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.LanguageReplaceabilityTest.ADP;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.RecompilationTest.TEXT_2;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyTokenType;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.nemesis.jfs.javac.JavacDiagnostic;
import org.nemesis.antlr.v4.netbeans.v8.project.ParsingTestEnvironment;
import org.netbeans.api.lexer.Language;
import org.netbeans.junit.MockServices;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.implspi.EnvironmentFactory;
import org.netbeans.modules.parsing.implspi.SchedulerControl;
import org.netbeans.modules.parsing.implspi.SourceControl;
import org.netbeans.modules.parsing.implspi.SourceEnvironment;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;
import org.netbeans.spi.editor.document.EditorMimeTypesImplementation;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public class DynamicLexerAndParserTest {

    private static Path root;
    private static TestDir nestedGrammarDir;
    private static String mime;

    public static final String TEXT_1
            = "{ skiddoo : 23, meaningful : true,\n"
            + "meaning: '42', \n"
            + "thing: 51 }";
    private static TestDir lexerDir;
    private static String lexerMime;

//    @Test
    @SuppressWarnings("unchecked")
    public void test() throws IOException, ParseException {
        assertFalse(ParserManager.canBeParsed(mime));
//        DynamicLanguageSupport.registerGrammar(FileUtil.toFileObject(nestedGrammarDir.antlrSourceFile.toFile()));
        DynamicLanguageSupport.registerGrammar(AdhocMimeTypes.mimeTypeForPath(nestedGrammarDir.antlrSourceFile), TEXT_1, UNIT_TEST);
        assertFalse(DynamicLanguageSupport.mimeTypes().isEmpty());

        assertTrue(ParserManager.canBeParsed(mime));

        ParseTreeProxy px = DynamicLanguageSupport.parseImmediately(mime, TEXT_1, UNIT_TEST);
        assertNotNull(px);
        String ext = AdhocMimeTypes.fileExtensionFor(mime);
        Path tempFile = nestedGrammarDir.tmp.resolve("Test." + ext);
        Files.write(tempFile, Arrays.asList(TEXT_2), UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileObject fo = FileUtil.toFileObject(tempFile.toFile());
        assertNotNull(fo);
        assertEquals(mime, fo.getMIMEType());
        DataObject.find(fo); // ensure registered and updated

        for (ProxyToken tok : px.tokens()) {
            int line = tok.getLine();
            int off = tok.getCharPositionInLine();
            if (tok.getType() == -1) {
                continue;
            }
            System.out.println("TOK: " + tok);
            if (off >= 0 && line >= 0) {
                ProxyToken other = px.tokenAtLinePosition(line, off);
                System.out.println("  GOT " + other);
                assertSame("line " + line + " off " + off, tok, other);
                System.out.println("MATCHED " + other);
            }
        }

        Language<AdhocTokenId> lang = (Language<AdhocTokenId>) Language.find(mime);
        assertNotNull("Did not find a language for '" + mime + "'", lang);
        for (ProxyTokenType type : px.tokenTypes()) {
            if (type.type != -1) {
                assertNotNull(type.name(), lang.validTokenId(type.name()));
                assertNotNull(type + "", lang.validTokenId(type.type));
            }
        }

        Document d = ParsingTestEnvironment.newDocumentFactory(mime).getDocument(fo);
        Parser.Result[] res = new Parser.Result[1];
        ParserManager.parse(Collections.singleton(Source.create(d)), new UserTask() {
            @Override
            public void run(ResultIterator ri) throws Exception {
                System.out.println("RESULT " + ri);
                System.out.println("PARSE RESULT: " + ri.getParserResult());
                res[0] = ri.getParserResult();
                assertNotNull(res[0]);
            }
        });
        assertNotNull(res[0]);
        assertTrue(res[0] instanceof AdhocParserResult);
        AdhocParserResult pr = (AdhocParserResult) res[0];
        ParseTreeProxy pt = pr.parseTree();
        assertNotNull(pt);
        pt.rethrow();
        System.out.println("TOKENS: " + pt.tokens());
        System.out.println("SEQHASH: " + pt.tokenSequenceHash());
    }

    @Test
    public void testLexerOnlyGrammar() throws Throwable {
        System.out.println("LexerDirRoot " + lexerDir.root);
        DynamicLanguageSupport.registerGrammar(AdhocMimeTypes.mimeTypeForPath(lexerDir.antlrSourceFile), TEXT_1, UNIT_TEST);
        ParseTreeProxy px = DynamicLanguageSupport.parseImmediately(lexerMime, TEXT_1, UNIT_TEST);
        GenerateBuildAndRunGrammarResult res = DynamicLanguageSupport.lastBuildResult(lexerMime, TEXT_1, UNIT_TEST);
        if (!res.isUsable()) {
            CompileResult cr = res.compileResult().get();
            for (JavacDiagnostic e : cr.diagnostics()) {
                System.out.println(e);
            }
        }
        assertTrue(res.isUsable());
        assertNotNull(px);
    }

    @BeforeClass
    public static void setupEnvironment() throws IOException {
        MockServices.setServices(UnitTestAntlrLibrary.class, AdhocMimeDataProvider.class,
                AdhocMimeResolver.class, EMI.class,
                EnvFact.class, ADP.class);
        root = Paths.get(System.getProperty("java.io.tmpdir"), DynamicLexerAndParserTest.class.getSimpleName() + "-" + System.currentTimeMillis());
        nestedGrammarDir = new TestDir(root, "NestedMapGrammar", "NestedMapGrammar.g4", "com.dyn");
        mime = AdhocMimeTypes.mimeTypeForPath(nestedGrammarDir.antlrSourceFile);

        lexerDir = new TestDir(root, "NestedMapLexer", "NestedMapLexer.g4", "moo.bar");
        lexerMime = AdhocMimeTypes.mimeTypeForPath(lexerDir.antlrSourceFile);
    }

    @AfterClass
    public static void cleanup() throws IOException {
        nestedGrammarDir.cleanUp();
    }

    public static final class EMI implements EditorMimeTypesImplementation {

        @Override
        public Set<String> getSupportedMimeTypes() {
            return DynamicLanguageSupport.mimeTypes();
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener pl) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener pl) {
        }
    }

    public static final class EnvFact implements EnvironmentFactory {

        @Override
        public Lookup getContextLookup() {
            return Lookup.getDefault();
        }

        @Override
        public Class<? extends Scheduler> findStandardScheduler(String string) {
            return Sched.class;
        }

        @Override
        public Parser findMimeParser(Lookup lkp, String string) {
            return DynamicLanguageSupport.parser(string);
        }

        private final Sched sched = new Sched();

        @Override
        public Collection<? extends Scheduler> getSchedulers(Lookup lkp) {
            return Collections.singleton(sched);
        }

        @Override
        public SourceEnvironment createEnvironment(Source source, SourceControl sc) {
            return new SourceEnvironment(sc) {
                @Override
                public Document readDocument(FileObject fo, boolean bln) throws IOException {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    FileUtil.copy(fo.getInputStream(), out);
                    String data = new String(out.toByteArray(), UTF_8);
                    Document doc = new DefaultStyledDocument();
                    try {
                        doc.insertString(0, data, null);
                    } catch (BadLocationException ex) {
                        throw new AssertionError(ex);
                    }
                    doc.putProperty("mimeType", "text/x-g4");
                    return doc;
                }

                @Override
                public void attachScheduler(SchedulerControl sc, boolean bln) {
                    // do nothing
                    sc.sourceChanged(source);
                }

                @Override
                public void activate() {
                    // do nothing
                }

                @Override
                public boolean isReparseBlocked() {
                    return false;
                }
            };
        }

        @Override
        public <T> T runPriorityIO(Callable<T> clbl) throws Exception {
            return clbl.call();
        }

        public static final class Sched extends Scheduler {

            @Override
            protected SchedulerEvent createSchedulerEvent(SourceModificationEvent sme) {
                return new SME(sme);
            }

            static class SME extends SchedulerEvent {

                public SME(Object source) {
                    super(source);
                }
            }
        }
    }
}
