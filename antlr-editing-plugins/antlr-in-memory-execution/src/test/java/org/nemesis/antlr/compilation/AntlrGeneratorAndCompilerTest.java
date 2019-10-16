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

import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;
import static javax.tools.JavaFileObject.Kind.CLASS;
import javax.tools.StandardLocation;
import org.antlr.v4.tool.Grammar;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.javac.JavacDiagnostic;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrGeneratorAndCompilerTest {

    private JFS jfs;
    public static final String pkg = "com.foo.bar";
    public static UnixPath sourceFilePath = UnixPath.get("com/foo/bar/NestedMapGrammar.g4");
    private JFSFileObject grammarSourceFO;
    private AntlrGeneratorAndCompiler compiler;
    private final Set<String> generatedClassFiles = new HashSet<>();
    private static final Set<String> EXPECTED_CLASS_FILES = new HashSet<>(Arrays.asList(
            "com/foo/bar/NestedMapGrammarParser$StringValueContext.class",
            "com/foo/bar/NestedMapGrammarParser.class",
            "com/foo/bar/NestedMapGrammarParser$NumberValueContext.class",
            "com/foo/bar/NestedMapGrammarParser$BooleanValueContext.class",
            "com/foo/bar/NestedMapGrammarParser$NumContext.class",
            "com/foo/bar/NestedMapGrammarParser$ItemsContext.class",
            "com/foo/bar/NestedMapGrammarParser$BoolContext.class",
            "com/foo/bar/NestedMapGrammarVisitor.class",
            "com/foo/bar/NestedMapGrammarParser$MapContext.class",
            "com/foo/bar/NestedMapGrammarLexer.class",
            "com/foo/bar/NestedMapGrammarParser$StrContext.class",
            "com/foo/bar/NestedMapGrammarBaseVisitor.class",
            "com/foo/bar/NestedMapGrammarParser$ValueContext.class",
            "com/foo/bar/NestedMapGrammarParser$MapItemContext.class",
            "com/foo/bar/NestedMapGrammarListener.class",
            "com/foo/bar/NestedMapGrammarBaseListener.class"
    ));

    @Test
    public void testCompilation() throws IOException {
        AntlrGenerationAndCompilationResult res = compiler.compile("NestedMapGrammar.g4", System.out);
        assertNotNull(res);
        for (JavacDiagnostic diag : res.javacDiagnostics()) {
            System.out.println("D: " + diag);
        }
        assertTrue(res.javacDiagnostics().isEmpty(), () -> Strings.join('\n', res.javacDiagnostics()));
        assertFalse(res.compileFailed());

        assertTrue(res.isUsable());
        Set<String> found = new HashSet<>();
        jfs.list(StandardLocation.CLASS_OUTPUT, "", EnumSet.of(CLASS), true)
                .forEach(fo -> {
                    found.add(fo.getName());
                });

        assertEquals(EXPECTED_CLASS_FILES, found);
        assertEquals(EXPECTED_CLASS_FILES, generatedClassFiles);
    }

    private void onFileCreated(Location loc, FileObject file) {
//        System.out.println("\"" + file + "\",");
        if (loc == StandardLocation.CLASS_OUTPUT && file.getName().endsWith(".class")) {
            generatedClassFiles.add(file.getName());
        }
    }

    @BeforeEach
    public void setup() throws IOException {
        jfs = baseJFS(this::onFileCreated);
        grammarSourceFO = addGrammarToJFS(jfs, sourceFilePath, StandardLocation.SOURCE_PATH);

        compiler = AntlrGeneratorAndCompilerBuilder.compilerBuilder(jfs)
                .generateIntoJavaPackage(pkg)
                .javaSourceOutputLocation(StandardLocation.SOURCE_OUTPUT)
                .building(sourceFilePath.getParent())
                .addToClasspath(Grammar.class)
                .addToClasspath(org.antlr.v4.runtime.tree.ParseTree.class)
                .build();
        assertNotNull(compiler);
    }

    static JFSFileObject addToJFS(JFS jfs, UnixPath sourceFilePath, Location location, String content) throws IOException {
        JFSFileObject grammarSourceFO = jfs.create(sourceFilePath, StandardLocation.SOURCE_PATH, content);
        assertNotNull(grammarSourceFO);
        assertEquals(content, grammarSourceFO.getCharContent(true).toString());
        return grammarSourceFO;
    }

    static JFSFileObject addGrammarToJFS(JFS jfs, UnixPath sourceFilePath, Location location) throws IOException {
        String content = Streams.readResourceAsUTF8(AntlrGeneratorAndCompilerTest.class, "NestedMapGrammar.g4");
        return addToJFS(jfs, sourceFilePath, location, content);
    }

    static JFS baseJFS(BiConsumer<Location, FileObject> listener) throws IOException {
        return JFS.builder()
                .withListener(listener)
                .withCharset(UTF_8)
                .build();
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (jfs != null) {
            jfs.close();
        }
    }
}
