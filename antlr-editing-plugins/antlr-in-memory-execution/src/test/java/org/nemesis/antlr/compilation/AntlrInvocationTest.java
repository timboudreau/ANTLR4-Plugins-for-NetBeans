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
package org.nemesis.antlr.compilation;

import com.mastfrog.util.streams.Streams;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;
import org.antlr.v4.tool.Grammar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.nemesis.antlr.compilation.AntlrGeneratorAndCompilerTest.addGrammarToJFS;
import static org.nemesis.antlr.compilation.AntlrGeneratorAndCompilerTest.baseJFS;
import static org.nemesis.antlr.compilation.AntlrGeneratorAndCompilerTest.pkg;
import static org.nemesis.antlr.compilation.AntlrGeneratorAndCompilerTest.sourceFilePath;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSClassLoader;
import org.nemesis.jfs.JFSFileObject;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrInvocationTest {

    public static final String TEXT_1
            = "{ skiddoo : 23, meaningful : true,\n"
            + "meaning: '42', \n"
            + "thing: 51 }";
    private JFS jfs;
    private JFSFileObject grammarSourceFO;
    private final Set<String> generatedClassFiles = new HashSet<>();
    private WithGrammarRunner runner;

    @Test
    public void testInvocation() throws Throwable {
        GrammarRunResult<Map<String, Object>> r = runner.run(this, AntlrInvocationTest::doit);
        r.rethrow();
        assertNotNull(r);
        Map<String, Object> map = r.get();
        assertNotNull(map);

        assertEquals("map mapItem value numberValue mapItem value booleanValue mapItem value stringValue mapItem value numberValue", map.get("tree"));
        assertEquals(Collections.emptyList(), map.get("errors"));
        if (CLREF.get() != null) {
            for (int i = 0; i < 10 && CLREF.get() != null; i++) {
                System.gc();
                System.runFinalization();
                Thread.sleep(100);
            }
        }
        assertNull(CLREF.get(), "Classloader was not GC'd");
    }

    @Test
    public void testParseThatFails() throws Throwable {
        GrammarRunResult<Map<String, Object>> r = runner.run(this, AntlrInvocationTest::doitBadly);
        r.rethrow();
        assertNotNull(r);
        Map<String, Object> map = r.get();
        assertEquals("map", map.get("tree"));
        List<String> errs = (List<String>) map.get("errors");
        assertNotNull(errs);
        assertEquals(2, errs.size());
        assertEquals("syntaxError: 1:18 token recognition error at: ']'", errs.get(0));
        assertEquals("syntaxError: 1:19 token recognition error at: '['", errs.get(1));


        if (CLREF.get() != null) {
            for (int i = 0; i < 10 && CLREF.get() != null; i++) {
                System.gc();
                System.runFinalization();
                Thread.sleep(100);
            }
        }
        assertNull(CLREF.get(), "Classloader was not GC'd");
    }

    @Test
    public void testLeaking() throws Throwable {
        subTestLeaking();
        if (CLREF.get() != null) {
            for (int i = 0; i < 10 && CLREF.get() != null; i++) {
                System.gc();
                System.runFinalization();
                Thread.sleep(100);
            }
        }
        assertNull(CLREF.get(), "After nulling last object, classloader should be GC'ds");
    }

    public void subTestLeaking() throws Throwable {
        GrammarRunResult<Object> r = runner.run(this, AntlrInvocationTest::leaky);
        r.rethrow();
        assertTrue(r.isUsable(), r::toString);
        assertNotNull(strongRef);
        Object o = r.get();
        r.disposeResult();
        r = null;
        assertNotNull(o);
        assertTrue(strongRef instanceof JFSClassLoader, strongRef.getClass().getName());
        assertTrue(o.getClass().getClassLoader() == strongRef
                || o.getClass().getClassLoader() == strongRef.getParent());

        strongRef = null;
        if (CLREF.get() != null) {
            for (int i = 0; i < 10 && CLREF.get() != null; i++) {
                System.gc();
                System.runFinalization();
                Thread.sleep(100);
            }
        }
        assertNotNull(CLREF.get(), "Classloader should not have been GC'd"
                + " while an instance it created is alive: " + o.getClass().getClassLoader());

        System.out.flush();
        o = null;
    }

    @Test
    public void testCrashing1() throws Throwable {
        GrammarRunResult<Object> r = runner.run(this, AntlrInvocationTest::smashy);
        assertFalse(r.isUsable());
        assertTrue(r.thrown().isPresent());
        assertTrue(r.thrown().get() instanceof FooException);
    }

    @Test
    public void testCrashing2() throws Throwable {
        GrammarRunResult<Object> r = runner.run(this, AntlrInvocationTest::crashy);
        assertFalse(r.isUsable());
        assertTrue(r.thrown().isPresent());
        assertTrue(r.thrown().get() instanceof IllegalStateException);
    }

    static Reference<ClassLoader> CLREF;
    static ClassLoader strongRef;

    @SuppressWarnings("unchecked")
    public static Map<String, Object> doit() throws Exception {
        Class<?> type = Thread.currentThread().getContextClassLoader().loadClass("com.foo.bar.NestedMapInfoExtractor");
        Method m = type.getMethod("parseText", String.class, String.class);
        CLREF = new WeakReference<>(Thread.currentThread().getContextClassLoader());
        return (Map<String, Object>) m.invoke(null, "Hoogers.boodge", TEXT_1);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> doitBadly() throws Exception {
        Class<?> type = Thread.currentThread().getContextClassLoader().loadClass("com.foo.bar.NestedMapInfoExtractor");
        Method m = type.getMethod("parseText", String.class, String.class);
        CLREF = new WeakReference<>(Thread.currentThread().getContextClassLoader());
        return (Map<String, Object>) m.invoke(null, "Hoogers.boodge", "::hork::hork:hork{][}}{:Mumble} bumble 23 true }}");
    }

    public static Object leaky() throws Exception {
        Class<?> type = Thread.currentThread().getContextClassLoader().loadClass("com.foo.bar.NestedMapInfoExtractor");
        Constructor<?> con = type.getConstructor(String.class, String.class);
        CLREF = new WeakReference<>(strongRef = type.getClassLoader());
        return con.newInstance("hey", "you");
    }

    public static Object smashy() throws Exception {
        throw new FooException();
    }

    public static Object crashy() throws Exception {
        throw new IllegalStateException();
    }

    static class FooException extends Exception {

    }

    private void onFileCreated(JavaFileManager.Location loc, FileObject file) {
        if (loc == StandardLocation.CLASS_OUTPUT && file.getName().endsWith(".class")) {
            generatedClassFiles.add(file.getName());
        }
    }

    @BeforeEach
    public void setup() throws IOException {
        jfs = baseJFS(this::onFileCreated);
        grammarSourceFO = addGrammarToJFS(jfs, sourceFilePath, StandardLocation.SOURCE_PATH);

        String runnerSource = Streams.readResourceAsUTF8(AntlrInvocationTest.class, "NestedMapInfoExtractor.txt");
        AntlrGeneratorAndCompilerTest.addToJFS(jfs, sourceFilePath.getParent().resolve("NestedMapInfoExtractor.java"),
                StandardLocation.SOURCE_OUTPUT, runnerSource);

        runner = AntlrGeneratorAndCompilerBuilder.runnerBuilder(jfs)
                .generateIntoJavaPackage(pkg)
                .javaSourceOutputLocation(StandardLocation.SOURCE_OUTPUT)
                .building(sourceFilePath.getParent())
                .addToClasspath(Grammar.class)
                .addToClasspath(org.antlr.v4.runtime.tree.ParseTree.class)
                .build().isolated().build("NestedMapGrammar.g4");
        assertNotNull(runner);
    }
}
