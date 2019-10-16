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
package org.nemesis.antlr.live.execution;

import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.streams.Streams;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import javax.tools.StandardLocation;
import org.antlr.runtime.CommonTokenStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.live.execution.AntlrRunSubscriptionsTest.TEXT_1;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.javac.JFSCompileBuilder;

/**
 *
 * @author Tim Boudreau
 */
public class IR extends InvocationRunner<Map, Void> {

    private static String infoText;
    static IR IR;
    private volatile int callCount;
    private volatile int compileConfigCount;

    Document doc;

    public IR() {
        super(Map.class);
        IR = this;
    }

    public void assertCompileCalls(int ct) {
        assertEquals(ct, compileConfigCount);
    }

    public void assertCallCount(int ct) {
        assertEquals(ct, callCount);
    }

    static String infoText(String pkg) {
        if (infoText == null) {
            try {
                infoText = Streams.readResourceAsUTF8(IR.class, "NestedMapInfoExtractor.txt");
                infoText = infoText.replaceAll("com\\.foo\\.bar", pkg);
            } catch (IOException ex) {
                return Exceptions.chuck(ex);
            }
        }
        return infoText;
    }

    synchronized Document doc(String pkg) {
        if (doc == null) {
            doc = new PlainDocument();
            try {
                doc.insertString(0, infoText(pkg), null);
            } catch (BadLocationException ex) {
                return Exceptions.chuck(ex);
            }
        }
        return doc;
    }

    String gpn = "com.foo.bar";

    @Override
    protected Void onBeforeCompilation(ANTLRv4Parser.GrammarFileContext tree, AntlrGenerationResult res, Extraction extraction, JFS jfs, JFSCompileBuilder bldr, String grammarPackageName, Consumer<Supplier<ClassLoader>> cs) throws IOException {
        gpn = grammarPackageName;
        compileConfigCount++;
        bldr.addToClasspath(CommonTokenStream.class);
        bldr.addToClasspath(org.antlr.v4.runtime.ANTLRErrorListener.class);
//        jfs.create(Paths.get("com/foo/bar/NestedMapInfoExtractor.java"), StandardLocation.SOURCE_PATH, infoText());
        jfs.masquerade(doc(grammarPackageName),
                StandardLocation.SOURCE_PATH, UnixPath.get(grammarPackageName.replace('.', '/') + "/NestedMapInfoExtractor.java"));
        return null;
    }

    @Override
    @SuppressWarnings("rawtype")
    public Map apply(Void ignored) throws Exception {
        callCount++;
        Map m = doit();
        return m;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> doit() throws Exception {
        Class<?> type = Thread.currentThread().getContextClassLoader().loadClass(gpn + ".NestedMapInfoExtractor");
        Method m = type.getMethod("parseText", String.class, String.class);
        return (Map<String, Object>) m.invoke(null, "Hoogers.boodge", TEXT_1);
    }

}
