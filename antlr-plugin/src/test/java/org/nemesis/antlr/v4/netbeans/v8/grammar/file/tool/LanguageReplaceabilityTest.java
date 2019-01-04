package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ChangeListener;
import javax.swing.text.Document;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.project.ParsingTestEnvironment;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.LanguagePath;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.Task;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.impl.RunWhenScanFinishedSupport;
import org.netbeans.modules.parsing.impl.indexing.implspi.ActiveDocumentProvider;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.ParserFactory;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;
import org.netbeans.spi.editor.document.EditorMimeTypesImplementation;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.netbeans.spi.lexer.LanguageEmbedding;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.LanguageProvider;
import static org.netbeans.spi.lexer.LanguageProvider.PROP_LANGUAGE;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.ChangeSupport;
import org.openide.util.Lookup;
import org.openide.util.TaskListener;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 * Really this tests the behavior of Language lookups in the platform, but we
 * depend on pretty specific behavior of it, so worth ensuring it has not
 * changed.
 *
 * @author Tim Boudreau
 */
public class LanguageReplaceabilityTest {

    private static Prov prov;
//    private static final String FOO = "text/x-foo";
//    private static final String FOO = "text/x-foo+stuff; path=\"\\tmp\\Foo.g4\"";
    private static final String FOO = "text/x-foo+stuff$$tmp$$Foo.g4";
    private static final String BAR = "text/x-bar";

    @Test(timeout = 15000)
    public final void testLanguageIsNotCached() throws Throwable {
        Logger.getLogger(AbstractLookup.class.getName()).setLevel(Level.ALL);
        ParserProvider pp = Lookup.getDefault().lookup(ParserProvider.class);
        assertNotNull(pp);
        FakeParserFactory fooParser = new FakeParserFactory("foo-1");
        FakeParserFactory barParser = new FakeParserFactory("bar-1");
        pp.replaceParser(FOO, fooParser);
        pp.replaceParser(BAR, barParser);

        prov.enableBarAndFire();
        prov.tokenCount(10);

        assertTrue(ParserManager.canBeParsed(FOO));
        assertTrue(ParserManager.canBeParsed(BAR));

//        Document d = documentFor(fooFile);
//        Document d = Lookup.getDefault().lookup(MimeDataProvider.class).getLookup(MimePath.get(FOO)).lookup(DocumentFactory.class).getDocument(fooFile);
        RunWhenScanFinishedSupport.performScan(()->{
            RunWhenScanFinishedSupport.performDeferredTasks();
        }, Lookup.EMPTY);
        Document d = ParsingTestEnvironment.newDocumentFactory(FOO).getDocument(fooFile);
        assertSame(fooFile, NbEditorUtilities.getFileObject(d));
        assertEquals(FOO, NbEditorUtilities.getMimeType(d));
        assertNotNull(d);

        Parser.Result[] prs = new Parser.Result[1];
        CountDownLatch ltch = new CountDownLatch(1);
        UserTask ut = new UserTask(){
            @Override
            public void run(ResultIterator ri) throws Exception {
                System.out.println("ITER: " + ri);
                Parser.Result res = ri.getParserResult();
                System.out.println("RES IS " + res);
                assertNotNull(res);
                prs[0] = res;
                ltch.countDown();
            }
        };
        Source src = Source.create(d);
//        Source src = Source.create(fooFile);
        ParsingTestEnvironment.setSourceForParse(src);

        ParserManager.parse(Arrays.asList(src), ut);
        if (prs[0] == null) {
            ltch.await(5, TimeUnit.SECONDS);
        }
        assertNotNull(prs[0]);
        assertTrue(prs[0] instanceof FakeParserResult);
        FakeParserResult r = (FakeParserResult) prs[0];
        assertEquals("foo-1", r.info);
        assertEquals(0, r.index);
    }

    @Test
    public void sanityCheckMime() throws DataObjectNotFoundException, InterruptedException {
        assertEquals(FOO, fooFile.getMIMEType());
        assertEquals(BAR, barFile.getMIMEType());

        DataObject dob = DataObject.find(fooFile);
        System.out.println("dob type " + dob.getClass().getName());

        EditorCookie ec = dob.getLookup().lookup(EditorCookie.class);
        assertNotNull(ec);

        Document d = ec.getDocument();

        if (d == null) {
            org.openide.util.Task tsk = ec.prepareDocument();
            CountDownLatch ltch = new CountDownLatch(1);
            tsk.addTaskListener(new TaskListener() {
                @Override
                public void taskFinished(org.openide.util.Task task) {
                    ltch.countDown();
                }
            });
            ltch.await(10, TimeUnit.SECONDS);
            d = ec.getDocument();
        }
        assertNotNull(d);
    }

    private Document documentFor(FileObject fo) throws InterruptedException, DataObjectNotFoundException {
        DataObject dob = DataObject.find(fooFile);
        System.out.println("dob type " + dob.getClass().getName());

        assertEquals(FOO, fooFile.getMIMEType());

        EditorCookie ec = dob.getLookup().lookup(EditorCookie.class);
        assertNotNull(ec);

        Document d = ec.getDocument();

        if (d == null) {
            org.openide.util.Task tsk = ec.prepareDocument();
            CountDownLatch ltch = new CountDownLatch(1);
            tsk.addTaskListener(new TaskListener() {
                @Override
                public void taskFinished(org.openide.util.Task task) {
                    System.out.println("prepare doc finished");
                    ltch.countDown();
                }
            });
            ltch.await(10, TimeUnit.SECONDS);
            d = ec.getDocument();
        }
        assertNotNull(d);
        return d;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLanguageIsFoundAndReplacedWhenNeeded() throws Throwable {
        Language<FakeTokenId> fooLang1 = (Language<FakeTokenId>) Language.find(FOO);
        System.out.println("LANG: " + fooLang1);
        assertNotNull(fooLang1);
        assertEquals(5, fooLang1.tokenIds().size());

        assertSame(fooLang1, Language.find(FOO));
        System.out.println("WOO HOO: " + fooLang1.tokenId(2));
        assertEquals(1, prov.fooInvocations);
        assertEquals(0, prov.barInvocations);

        assertNull("Test is broken - system prop not honored", Language.find(BAR));

        prov.tokenCount(10);
        Language<FakeTokenId> fooLang2 = (Language<FakeTokenId>) Language.find(FOO);
        assertNotSame("After LanguageProvider fires a change, a different instance"
                + " of Language should be constructed", fooLang1, fooLang2);
        assertEquals(2, prov.fooInvocations);

        assertEquals("Received an obsolete version of the language, before the"
                + " token count change", 10, fooLang2.tokenIds().size());

        prov.enableBar();
        Language<FakeTokenId> barLang1 = (Language<FakeTokenId>) Language.find(BAR);
        assertNotNull("Bar lang was not found", barLang1);
        assertEquals("Test is broken - wrong number of tokens", 10, barLang1.tokenIds().size());
        prov.disableBar();

        assertNotNull((Language<FakeTokenId>) Language.find(BAR));
        prov.enableBar();
        Language<FakeTokenId> barLang2 = (Language<FakeTokenId>) Language.find(BAR);
        assertNotNull(barLang2);
        assertSame(barLang1, barLang2);

        prov.disableBarAndFire();
        assertNull((Language<FakeTokenId>) Language.find(BAR));

        prov.enableBar();
        assertNull("Disabled language is found even though LanguageProvider "
                + "did not fire a change", Language.find(BAR));
        prov.enableBarAndFire();

        Language<FakeTokenId> barLang3 = (Language<FakeTokenId>) Language.find(BAR);
        assertNotNull(barLang3);
        assertNotSame(barLang1, barLang3);

        prov.tokenCount(15);
        Language<FakeTokenId> barLang4 = (Language<FakeTokenId>) Language.find(BAR);
        assertNotNull(barLang4);
        assertNotSame(barLang3, barLang4);
        assertEquals(15, barLang4.tokenIds().size());
    }

    @Before
    public void resetState() throws IOException {
        assertNotNull(prov);
        prov.reset();
        mfs = FileUtil.createMemoryFileSystem();
        fooFile = mfs.getRoot().createData("hey.foo");
        try (OutputStream out = fooFile.getOutputStream()) {
            try (PrintStream ps = new PrintStream(out, true)) {
                ps.println("Hey hey foo");
            }
        }
        barFile = mfs.getRoot().createData("hey.bar");
        try (OutputStream out = fooFile.getOutputStream()) {
            try (PrintStream ps = new PrintStream(out, true)) {
                ps.println("Hey hey bar");
            }
        }
    }

    private FileObject fooFile;
    private FileObject barFile;
    private FileSystem mfs;

    @BeforeClass
    public static void enable() {
        ParsingTestEnvironment.init(Prov.class, ParserProvider.class, MimeRec.class, 
                EMI.class, ADP.class);
        System.setProperty("LanguageReplaceabilityTest", "true");
        prov = Lookup.getDefault().lookup(Prov.class);
        System.out.println("looked up " + prov);
        assertNotNull(prov);
    }

    public static final class MimeRec extends MIMEResolver {

        // The no argument superclass constructor is deprecated, to encourage
        // declarative registration - which is of course impossible here
        @SuppressWarnings("deprecation")
        public MimeRec() {
            System.out.println("CREATED A MimeRec");
        }

        @Override
        public String findMIMEType(FileObject fo) {
            System.out.println("find mime type " + fo.getPath());
            String nm = fo.getNameExt();
            if (nm.endsWith(".foo")) {
                System.out.println("returning foo");
                return FOO;
            }
            if (nm.endsWith(".bar")) {
                System.out.println("returning bar");
                return BAR;
            }
            System.out.println("no result for " + nm);
            return null;
        }
    }

    public static final class ADP implements ActiveDocumentProvider {

        private Document doc;
        private List<ActiveDocumentListener> listeners = new ArrayList<>();
        void setDocument(Document doc) {
            Document old = this.doc;
            this.doc = doc;
            for (ActiveDocumentListener l : listeners) {
                l.activeDocumentChanged(new ActiveDocumentEvent(this, old, doc, getActiveDocuments()));
            }
        }

        @Override
        public Document getActiveDocument() {
            return doc;
        }

        @Override
        public Set<? extends Document> getActiveDocuments() {
            return doc == null ? Collections.emptySet() : Collections.singleton(doc);
        }

        @Override
        public void addActiveDocumentListener(ActiveDocumentListener al) {
            listeners.add(al);
        }

        @Override
        public void removeActiveDocumentListener(ActiveDocumentListener al) {
            listeners.remove(al);
        }

    }

    static final class DynamicLanguageLookup implements InstanceContent.Convertor <String, Language<?>> {

        @Override
        public Language<?> convert(String t) {
            Prov prov = Lookup.getDefault().lookup(Prov.class);
            return prov.findLanguage(t);
//            return Language.find(t);
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public Class<? extends Language<?>> type(String t) {
            Class c = Language.class;
            return c;
        }

        @Override
        public String id(String t) {
            return t;
        }

        @Override
        public String displayName(String t) {
            return t;
        }

    }

    static class LLKP extends Lookup {

        private final Lookup lkp;

        public LLKP(Lookup lkp) {
            this.lkp = lkp;
        }

        @Override
        public <T> T lookup(Class<T> type) {
            System.out.println("\nLOOKUP " + type.getName() + " \n");
            return lkp.lookup(type);
        }

        @Override
        public <T> Result<T> lookup(Template<T> tmplt) {
            System.out.println("\nLOOKUP TPL " + tmplt.getType() + " \n");
            return lkp.lookup(tmplt);
        }

    }

    public static final class ParserProvider implements MimeDataProvider {

        private final InstanceContent foo = new InstanceContent();
        private final InstanceContent bar = new InstanceContent();
        private FakeParserFactory fooParser;
        private FakeParserFactory barParser;
        private static Throwable construction;

        public static ParserProvider INSTANCE;

        public ParserProvider() {
            if (INSTANCE != null) {
                throw new IllegalStateException("Constructed twice!", construction);
            }
            construction = new Exception();
            INSTANCE = this;

            foo.add(ParsingTestEnvironment.newDocumentFactory(FOO));
            bar.add(ParsingTestEnvironment.newDocumentFactory(BAR));
            DynamicLanguageLookup cvt = new DynamicLanguageLookup();
            foo.add(FOO, cvt);
            bar.add(BAR, cvt);
        }

        void replaceParser(String type, FakeParserFactory parser) {
            System.out.println("replace parser " + type + " with " + parser);
            switch (type) {
                case FOO:
                    if (fooParser != null) {
                        foo.remove(fooParser);
                    }
                    System.out.println("add foo parser " + parser);
                    fooParser = parser;
                    foo.add(parser);
                    break;
                case BAR:
                    if (barParser != null) {
                        bar.remove(fooParser);
                    }
                    bar.add(parser);
                    barParser = parser;
                    System.out.println("add bar parser " + parser);
                    break;
            }
        }

        @Override
        public Lookup getLookup(MimePath mp) {
            System.out.println("ParserProvider.lookup " + mp.getPath());
            Lookup res = lkps.get(mp.getPath());
            if (res == null) {
                String mime = mp.getPath();
                System.out.println("create lookup for '" + mime + "' in " + mp);
//                new Exception("MIME: '" + mp + "' ").printStackTrace(System.out);
                switch (mime) {
                    case FOO:
                        res = new LLKP(new AbstractLookup(foo));
                        lkps.put(mime, res);
                        break;
                    case BAR:
                        res = new LLKP(new AbstractLookup(bar));
                        lkps.put(mime, res);
                        break;
                    default:
                        res = Lookup.EMPTY;
                }
            }
            System.out.println("lkp returning " + res);
            return res;
        }

        private final Map<String, Lookup> lkps = Collections.synchronizedMap(new HashMap<>());
    }

    public static final class FakeParserFactory extends ParserFactory {

        private final String info;

        public FakeParserFactory(String info) {
            this.info = info;
        }

        public String toString() {
            return "FakeParserFactory{" + info + "}";
        }

        @Override
        public Parser createParser(Collection<Snapshot> clctn) {
            return new FakeParser(info);
        }
    }

    public static final class FakeParser extends Parser {

        private int index;
        private String info;
        private final Map<Task, FakeParserResult> results = new HashMap<>();
        private final ChangeSupport supp = new ChangeSupport(this);

        public FakeParser(String info) {
            this.info = info;
        }

        void setInfo(String info) {
            this.info = info;
            supp.fireChange();
        }

        public String toString() {
            return "FakeParser{" + info + "}";
        }

        @Override
        public void parse(Snapshot snpsht, Task task, SourceModificationEvent sme) throws ParseException {
            System.out.println("PARSE: " + snpsht.getMimeType() + " " + snpsht.getSource().getDocument(false));
            results.put(task, new FakeParserResult(info, snpsht, index++));
        }

        @Override
        public FakeParserResult getResult(Task task) throws ParseException {
            FakeParserResult res = results.remove(task);
            System.out.println("get result " + res);
            return res;
        }

        @Override
        public void addChangeListener(ChangeListener cl) {
            supp.addChangeListener(cl);
        }

        @Override
        public void removeChangeListener(ChangeListener cl) {
            supp.removeChangeListener(cl);
        }
    }

    static final class FakeParserResult extends Parser.Result {

        private final String info;
        private final int index;

        FakeParserResult(String info, Snapshot sn, int index) {
            super(sn);
            this.info = info;
            this.index = index;
        }

        public int index() {
            return index;
        }

        public String info() {
            return info;
        }

        @Override
        protected void invalidate() {
            System.out.println("FakeParserResult.invalidate " + this);
        }

        public String toString() {
            return info + "-" + index;
        }
    }

    public static final class EMI implements EditorMimeTypesImplementation {

        @Override
        public Set<String> getSupportedMimeTypes() {
            return new HashSet<>(Arrays.asList(FOO, BAR));
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener pl) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener pl) {
        }
    }

    public static final class Prov extends LanguageProvider {

        static boolean barEnabled;
        int tokenCount = 5;

        int fooInvocations;
        int barInvocations;

        void reset() {
            fooInvocations = 0;
            tokenCount = 5;
            barEnabled = false;
        }

        void enableBar() {
            barEnabled = true;
        }

        void disableBar() {
            barEnabled = false;
        }

        void enableBarAndFire() {
            barEnabled = true;
            super.firePropertyChange(PROP_LANGUAGE);
        }

        void disableBarAndFire() {
            barEnabled = false;
            super.firePropertyChange(PROP_LANGUAGE);
        }

        void tokenCount(int count) {
            this.tokenCount = count;
            super.firePropertyChange(PROP_LANGUAGE);
        }

        public Language<?> findLanguage(String mimeType) {
            if (!Boolean.getBoolean("LanguageReplaceabilityTest")) {
                System.out.println("not in LanguageReplaceabilityTest, rn");
                return null;
            }
            if (FOO.equals(mimeType)) {
                fooInvocations++;
                return new Hier(mimeType, "foo", tokenCount).language();
            }
            if (BAR.equals(mimeType)) {
                barInvocations++;
                if (barEnabled) {
                    return new Hier(mimeType, "bar", tokenCount).language();
                }
            }
            return null;
        }

        public LanguageEmbedding<?> findLanguageEmbedding(Token<?> token, LanguagePath lp, InputAttributes ia) {
            return null;
        }
    }

    static final class Hier extends LanguageHierarchy<FakeTokenId> {

        private final String mime;

        private final String nm;
        private final int maxToken;

        Hier(String mime, String nm, int maxToken) {
            this.mime = mime;
            this.nm = nm;
            this.maxToken = maxToken;
        }

        @Override
        protected List<FakeTokenId> createTokenIds() {
            List<FakeTokenId> result = new ArrayList<>(maxToken);
            for (int i = 0; i < maxToken; i++) {
                result.add(new FakeTokenId(nm, i));
            }
            return result;
        }

        @Override
        protected Lexer<FakeTokenId> createLexer(LexerRestartInfo<FakeTokenId> lri) {
            return new FakeLexer(lri, createTokenIds());
        }

        @Override
        protected String mimeType() {
            return mime;
        }
    }

    static final class FakeLexer implements Lexer<FakeTokenId> {

        private final LexerRestartInfo<FakeTokenId> info;
        private final List<FakeTokenId> tokenIds;

        FakeLexer(LexerRestartInfo<FakeTokenId> info, List<FakeTokenId> tokenIds) {
            this.info = info;
            this.tokenIds = tokenIds;
        }

        @Override
        public Token<FakeTokenId> nextToken() {
            LexerInput in = this.info.input();
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = in.read()) >= 0) {
                sb.append((char) ch);
            }
            return info.tokenFactory().createToken(tokenIds.get(tokenIds.size() - 1), sb.length());
        }

        @Override
        public Object state() {
            return null;
        }

        @Override
        public void release() {
        }
    }

    static final class FakeTokenId implements TokenId {

        private final String nm;

        private final int num;

        FakeTokenId(String nm, int num) {
            this.nm = nm;
            this.num = num;
        }

        @Override
        public String name() {
            return nm + "-" + Integer.toString(num);
        }

        @Override
        public int ordinal() {
            return num;
        }

        @Override
        public String primaryCategory() {
            return "foo";
        }

        public String toString() {
            return name();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof FakeTokenId
                    && ((FakeTokenId) o).num == num
                    && ((FakeTokenId) o).nm.equals(nm);
        }

        @Override
        public int hashCode() {
            return (num + 2) * nm.hashCode();
        }
    }
}
