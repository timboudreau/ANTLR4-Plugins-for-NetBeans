/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.jfs;

import com.mastfrog.util.path.UnixPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.nemesis.jfs.javac.JavacDiagnostic;
import org.nemesis.jfs.javac.JavacOptions;

/**
 *
 * @author Tim Boudreau
 */
public class CompileSingleTest {

    private JFS jfs;
    private static final UnixPath TESTIT = UnixPath.get("com/foo/TestIt.java");
    private static final UnixPath THING = UnixPath.get("com/foo/Thing.java");
    private static final UnixPath LATER = UnixPath.get("com/foo/Later.java");

    @Test
    public void tryCompileSingle() throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompileResult result;
        try (OutputStreamWriter wr = new OutputStreamWriter(out, UTF_8)) {
            result = new JFSCompileBuilder(jfs)
                    .addSourceLocation(SOURCE_PATH)
                    .setOptions(new JavacOptions()
                            .nonIdeMode()
                            .onlyRebuildNewerSources()
                            .sourceLevel(8)
                            .targetLevel(8)
                            .verbose()
                            .withCharset(jfs.encoding()))
                    .withMaxErrors(1000)
                    .withMaxWarnings(1000)
                    .withDebugInfo(JavacOptions.DebugInfo.VARS)
                    .compilerOutput(wr)
                    .runAnnotationProcessors(false)
                    .compile();
        }

        String output = new String(out.toByteArray());
        if (!result.diagnostics().isEmpty()) {
            for (JavacDiagnostic diag : result.diagnostics()) {
                System.out.println("DIAG: " + diag);
            }
            fail("Should be no diagostics in " + result);
        }
        assertTrue(result.ok());
        assertFalse(result.outputFiles().isEmpty());
        assertEquals(2, result.outputFiles().size());
        assertTrue(result.outputFiles().contains(JFSCoordinates.create(
                CLASS_OUTPUT, TESTIT.getParent().resolve(TESTIT.rawName() + ".class"))));
        assertTrue(result.outputFiles().contains(JFSCoordinates.create(
                CLASS_OUTPUT, THING.getParent().resolve(THING.rawName() + ".class"))));
        String text = null;
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try (JFSClassLoader ldr = jfs.getClassLoader(true, old, CLASS_OUTPUT, SOURCE_PATH)) {
            Class<?> type = Class.forName("com.foo.TestIt", true, ldr);
            Method method = type.getMethod("getTheThing");
            text = (String) method.invoke(null);
        }
        assertEquals("I am 23, hear me roar", text);
        jfs.create(LATER, SOURCE_PATH, "package com.foo;\n"
                + "public final class Later {\n"
                + "  public static String getTheThing() {\n"
                + "    return TestIt.getTheThing();\n"
                + "  }\n"
                + "}\n");

        // Now delete the sources, so the compiler can only link
        // using previously compiled class files
        JFSFileObject thingFile = jfs.get(SOURCE_PATH, THING);
        assertNotNull(thingFile);
        JFSFileObject testitFile = jfs.get(SOURCE_PATH, TESTIT);
        assertNotNull(testitFile);
        assertTrue(thingFile.delete());
        assertTrue(testitFile.delete());

        out = new ByteArrayOutputStream();
        try (OutputStreamWriter wr = new OutputStreamWriter(out, UTF_8)) {
            try (JFSClassLoader ldr = jfs.getClassLoader(CLASS_OUTPUT)) {
                Thread.currentThread().setContextClassLoader(ldr);
                result = new JFSCompileBuilder(jfs)
                        .addSourceLocation(SOURCE_PATH)
                        .addSourceLocation(CLASS_OUTPUT)
                        .setOptions(new JavacOptions()
                                .nonIdeMode()
                                .onlyRebuildNewerSources()
                                .sourceLevel(8)
                                .targetLevel(8)
                                .verbose()
                                .withCharset(jfs.encoding()))
                        .withMaxErrors(1000)
                        .withMaxWarnings(1000)
                        .withDebugInfo(JavacOptions.DebugInfo.ALL)
                        .compilerOutput(wr)
                        .runAnnotationProcessors(false)
                        .compileSingle(LATER);
            } finally {
                Thread.currentThread().setContextClassLoader(old);
            }
        }

        output = new String(out.toByteArray());
        if (!result.diagnostics().isEmpty()) {
            for (JavacDiagnostic diag : result.diagnostics()) {
                System.out.println("DIAG: " + diag);
            }
            fail("Should be no diagostics in " + result + ". Output: " + output);
        }
        assertTrue(result.ok());
        assertFalse(result.outputFiles().isEmpty());

        assertEquals(1, result.outputFiles().size());
        assertEquals(JFSCoordinates.create(CLASS_OUTPUT, LATER.getParent().resolve("Later.class")),
                result.outputFiles().iterator().next());

        text = null;
        try (JFSClassLoader ldr = jfs.getClassLoader(true, old, CLASS_OUTPUT, SOURCE_PATH)) {
            Class<?> type = Class.forName("com.foo.Later", true, ldr);
            Method method = type.getMethod("getTheThing");
            text = (String) method.invoke(null);
        }
        assertEquals("I am 23, hear me roar", text);
    }

    @Before
    public void setup() throws IOException {
        jfs = JFS.builder().build();
        jfs.create(TESTIT, SOURCE_PATH, "package com.foo;\n"
                + "public final class TestIt {\n"
                + "  public static String getTheThing() {\n"
                + "    return new Thing(23).toString();\n"
                + "  }\n"
                + "}\n");
        jfs.create(THING, SOURCE_PATH, "package com.foo;\n"
                + "class Thing {\n"
                + "  private final int value;\n"
                + "  Thing(int value) {\n"
                + "    this.value = value;\n"
                + "  }\n\n"
                + "  public String toString() {\n"
                + "    return \"I am \" + this.value + \", hear me roar\";\n"
                + "  }\n"
                + "}\n");
    }
}
