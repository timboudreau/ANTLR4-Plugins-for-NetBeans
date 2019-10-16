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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract;

import org.nemesis.jfs.javac.CompileJavaSources;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.AntlrLibrary;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ForeignInvocationEnvironment;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ForeignInvocationResult;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ReflectiveInvoker;

/**
 *
 * @author Tim Boudreau
 */
public class CompiledParserRunner {

    private final String pkg;
    private final AntlrLibrary lib;

    public CompiledParserRunner(Path classpathRoot, String pkg, AntlrLibrary lib) {
        this.pkg = pkg;
        try {
            this.lib = lib.with(CompileJavaSources.class
                    .getProtectionDomain().getCodeSource()
                    .getLocation().toURI().toURL(),
                    classpathRoot.toUri().toURL());
        } catch (URISyntaxException | MalformedURLException ex) {
            throw new AssertionError(ex);
        }
    }

    public ParserRunResult parseAndExtract(String text) {
        ForeignInvocationEnvironment env = new ForeignInvocationEnvironment(lib);
        // Create the AntlrProxies instance here, to ensure it isn't actually
        // loaded by the invocation classloader, causing it to leak all its classes
        // forever
        Invoker iv = new Invoker(text);
        return new ParserRunResult(env.invoke(iv));
    }

    private final class Invoker implements ReflectiveInvoker<ParseTreeProxy> {

        private final String textToParse;

        public Invoker(String textToParse) {
            this.textToParse = textToParse;
        }

        @Override
        public ParseTreeProxy invoke(ClassLoader isolatedClassLoader, ForeignInvocationResult<ParseTreeProxy> addTo) throws Exception {
            String className = pkg + ".ParserExtractor";
            Class<?> type = Class.forName(className, true, isolatedClassLoader);
            Method method = type.getMethod("extract", String.class);
            return (ParseTreeProxy) method.invoke(null, textToParse);
        }

        @Override
        public boolean captureStandardOutputAndError() {
            return false;
        }

        @Override
        public boolean blockSystemExit() {
            return false;
        }

        public String toString() {
            return "Run antlr parser in package " + pkg;
        }
    }
}
