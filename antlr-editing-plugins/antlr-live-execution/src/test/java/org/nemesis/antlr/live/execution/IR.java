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
