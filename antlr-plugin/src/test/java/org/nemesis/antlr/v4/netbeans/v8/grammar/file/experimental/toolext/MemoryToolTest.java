package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.toolext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import static javax.tools.JavaFileObject.Kind.SOURCE;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.SOURCE_PATH;
import javax.tools.ToolProvider;
import org.antlr.v4.tool.Grammar;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSClassLoader;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.AntlrRunOption;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.LanguageReplaceabilityTest;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyDetailedSyntaxError;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxySyntaxError;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ExtractionCodeGenerator;
import org.nemesis.jfs.javac.JavacOptions;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public class MemoryToolTest {

    private final Path grammarPath = Paths.get("com/foo/NestedMapGrammar.g4");
    private final Path importDir = Paths.get("imports");
    private final String pkg = "com.foo";
    private final AntlrRunOption[] options = new AntlrRunOption[]{
        AntlrRunOption.GENERATE_LEXER,
        AntlrRunOption.GENERATE_VISITOR
    };
    private JFSFileObject grammarFileObject;
    private JFS jfs;
    private JFSFileObject extractorFileObject;
    public static final String TEXT_1
            = "{ skiddoo : 23, \nmeaningful : true, meaning: '42' }";
    public static final String TEXT_2
            = "{ skiddoo : 53, meaningful : false, meaning: 'hey' }";
    public static final String BAD_TEXT
            = "{ skiddoo : x53x, \nmeaningful : false, meaning: 'hey }";

    @Test
    public void testMemoryTool() throws Throwable {
        int ct = 1;
        for (int i = 0; i < ct; i++) {
            doTestMemoryTool(i == ct - 1, TEXT_1);
        }
    }

    @Test
    public void testWithBadInput() throws Throwable {
        ParseTreeProxy prx = doTestMemoryTool(true, BAD_TEXT);
        List<AntlrProxies.ProxySyntaxError> errors = prx.syntaxErrors();
        assertNotNull(errors);
        assertFalse(errors.isEmpty());
        int max = errors.size();
        String[] expected = {"1:12(12,15)=6<12> mismatched input 'x53x' expecting {Number, String, True, False}",
            "2:29 token recognition error at: ''hey }'",
            "2:35(54,53)=19<-1> mismatched input '<EOF>' expecting {Number, String, True, False}"};
        assertEquals("" + errors, 3, errors.size());
        for (int i = 0; i < max; i++) {
            assertEquals(errors.get(i).toString(), expected[i]);
            switch (i) {
                case 1:
                    assertSame("Wrong type at " + i, ProxySyntaxError.class, errors.get(i).getClass());
                    assertFalse(errors.get(i).hasFileOffsetsAndTokenIndex());
                    break;
                default:
                    assertSame("Wrong type at " + i, ProxyDetailedSyntaxError.class, errors.get(i).getClass());
                    assertTrue(errors.get(i).hasFileOffsetsAndTokenIndex());
            }
        }
    }

    public ParseTreeProxy doTestMemoryTool(boolean log, String textToParse) throws Throwable {

        String[] args = antlrArguments();
        long then = System.currentTimeMillis();

        MemoryTool tool = new MemoryTool(jfs, SOURCE_PATH, grammarPath.getParent(), args);
        tool.genPackage = "com.foo";
        tool.gen_listener = true;
        tool.gen_visitor = true;
//        tool.generate_ATN_dot = true;
        tool.grammarEncoding = jfs.encoding().name();
        tool.gen_dependencies = true;
        tool.longMessages = true;
//        tool.processGrammarsOnCommandLine();
        Grammar g = tool.loadGrammar("NestedMapGrammar.g4");
        tool.generateATNs(g);
        tool.process(g, true);
        if (g.implicitLexer != null) {
            tool.process(g.implicitLexer, true);
        }
        long time = System.currentTimeMillis() - then;
        if (log) {
            System.out.println("\nAntlr-generation done in " + time + " ms\n");
        }
//        Iterable<JavaFileObject> fls = jfs.list(SOURCE_PATH, "", EnumSet.allOf(JavaFileObject.Kind.class), true);
//        for (JavaFileObject f : fls) {
//            System.out.println("FILE: " + f);
//        }
        if (log) {
            jfs.listAll((loc, fo) -> {
                System.out.println(loc + "\t" + fo);
            });
        }

        LogIt logIt = new LogIt();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> options = new JavacOptions().withCharset(jfs.encoding()).options(compiler);

        JavaCompiler.CompilationTask task = compiler.getTask(null,
                jfs, logIt, options, null,
                jfs.list(SOURCE_PATH, "", EnumSet.of(SOURCE), true));
        then = System.currentTimeMillis();
        Boolean res = task.call();
        long elapsed = System.currentTimeMillis() - then;
        if (log) {
            System.out.println("\n\ncompiled in " + elapsed + " ms");
        }
        assertTrue(res);
        logIt.assertNoErrors();
        if (log) {
            jfs.listAll((loc, fo) -> {
                System.out.println(loc + "\t" + fo);
            });
        }
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        ParseTreeProxy prx;
        then = System.currentTimeMillis();
        try (JFSClassLoader cl = jfs.getClassLoader(StandardLocation.CLASS_OUTPUT, Thread.currentThread().getContextClassLoader())) {
            Thread.currentThread().setContextClassLoader(cl);
            Class<?> type = Class.forName("com.foo.ParserExtractor", true, cl);
            Method m = type.getMethod("extract", String.class);
            prx = (ParseTreeProxy) m.invoke(null, textToParse);
            elapsed = System.currentTimeMillis() - then;
            assertNoClassLeaksFromClassloader(prx, cl);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }

        if (log) {
            System.out.println("\nextracted in " + elapsed + " ms");
        }
        if (log) {
            System.out.println(prx);
        }
        return prx;
    }

    String[] antlrArguments() {
        List<String> result = new ArrayList<>();
        result.add("-encoding");
        result.add(jfs.encoding().name());
//        if (importDir != null) {
//            result.add("-lib");
//            result.add(importDir.toString());
//        }
//        result.add("-o");
//        result.add(outputDir.toString());
        if (pkg != null) {
            result.add("-package");
            result.add("com.foo");
        }
        for (AntlrRunOption o : options) {
            result.add(o.toString());
        }
        result.add("-message-format");
        result.add("vs2005");
        result.add(grammarPath.toString());
        return result.toArray(new String[result.size()]);
    }

    @Before
    public void setup() throws IOException {
        jfs = JFS.builder().withCharset(UTF_16).build();
        String grammarText;

        try (InputStream in = LanguageReplaceabilityTest.class.getResourceAsStream("NestedMapGrammar.g4")) {
            assertNotNull("NestedMapGrammar.g4 should be next to "
                    + LanguageReplaceabilityTest.class.getName() + " on classpath", in);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            FileUtil.copy(in, baos);
            grammarText = new String(baos.toByteArray(), UTF_8);
        }
        grammarFileObject = jfs.create(grammarPath, SOURCE_PATH, grammarText);
        String sourceCode = ExtractionCodeGenerator.getLexerExtractorSourceCode(grammarPath, "com.foo", "NestedMapGrammar", false, "Lexer");
        extractorFileObject = jfs.create(Paths.get("com/foo/ParserExtractor.java"), SOURCE_PATH, sourceCode);
    }

    @After
    public void closeJfs() throws IOException {
        jfs.close();
        assertTrue(jfs.isReallyClosed());
    }

    private void assertNoClassLeaksFromClassloader(ParseTreeProxy prx, JFSClassLoader cl) throws Throwable {
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        assertNotLoadedBy(prx, cl, seen, "proxy");
    }

    private void assertNotLoadedBy(Object o, ClassLoader ldr, Map<Object, Boolean> seen, String path) throws Throwable {
        if (o == null) {
            return;
        }
        if (seen.containsKey(o)) {
            return;
        }
        if (o.getClass().getClassLoader() == ldr) {
            String msg = "Leaking objects from classloaders: " + o + " (" + o.getClass().getName() + ") via " + path;
            fail(msg);
        }
        seen.put(o, true);
        drill(o, ldr, seen, path);
        if (o.getClass().isArray()) {
            if (isJavaType(o.getClass().getComponentType())) {
                return;
            }
            int max = Array.getLength(o);
            for (int i = 0; i < max; i++) {
                Object element = Array.get(o, i);
                assertNotLoadedBy(element, ldr, seen, path + "[" + i + "]");
            }
        }
    }

    private void drill(Object o, ClassLoader ldr, Map<Object, Boolean> seen, String path) throws Throwable {
        if (o == null || o.getClass() == Object.class) {
            return;
        }
        if (o.getClass().getPackage() != null && o.getClass().getPackage().toString().startsWith("java.")) {
            return;
        }
        Class<?> type = o.getClass();
        Set<Field> allFields = new HashSet<>();
        while (type != null && type != Object.class) {
            for (Field f : type.getDeclaredFields()) {
                f.setAccessible(true);
                if (!isJavaType(f)) {
                    allFields.add(f);
                }
            }
            type = type.getSuperclass();
        }
        for (Field f : allFields) {
            Object val;
            if ((f.getModifiers() & Modifier.STATIC) != 0) {
                val = f.get(null);
            } else {
                val = f.get(o);
            }
            assertNotLoadedBy(val, ldr, seen, path + "." + f.getName());
        }
    }

    Class<?>[] PRIMITIVE_TYPES = new Class<?>[]{
        Integer.TYPE, Character.TYPE, Long.TYPE, Short.TYPE, Byte.TYPE, Double.TYPE, Float.TYPE, Boolean.TYPE
    };

    private boolean isJavaType(Field field) {
        return isJavaType(field.getType());
    }

    private boolean isJavaType(Class<?> c) {
        boolean result = c.getPackage() != null && c.getPackage().toString().startsWith("java");
        if (!result) {
            for (Class<?> pt : PRIMITIVE_TYPES) {
                if (pt == c) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    public static final class LogIt implements BiConsumer<JavaFileManager.Location, FileObject>, DiagnosticListener<JavaFileObject> {

        private Set<String> errors = new HashSet<>();

        @Override
        public void accept(JavaFileManager.Location t, FileObject u) {
            System.out.println("jfs: " + t + ": " + u);
        }

        @Override
        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            System.out.println("javac: " + diagnostic);
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(diagnostic.toString());
            }
        }

        public void assertNoErrors() {
            assertTrue(errors.toString(), errors.isEmpty());
        }
    }
}
