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

import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.CompileJavaSources;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.AntlrLibrary;
import org.nemesis.jfs.JFSClassLoader;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.AntlrSourceGenerationResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.InMemoryAntlrSourceGenerationBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ExtractionCodeGenerator.PARSER_EXTRACTOR;
import org.nemesis.antlr.v4.netbeans.v8.util.ReplanningStatusConsumer;
import org.nemesis.jfs.isolation.IsolationClassLoader;

/**
 *
 * @author Tim Boudreau
 */
public class InMemoryParseProxyBuilder implements ParseProxyBuilder {

    private final InMemoryAntlrSourceGenerationBuilder bldr;
    private static final Logger LOG = Logger.getLogger(InMemoryParseProxyBuilder.class.getName());
//    static {
//        LOG.setLevel(Level.ALL);
//    }

    public InMemoryParseProxyBuilder(InMemoryAntlrSourceGenerationBuilder bldr) {
        this.bldr = bldr;
    }

    @Override
    public GenerateBuildAndRunGrammarResult buildNoParse() throws IOException {
        return parse(null, null);
    }

    @Override
    public GenerateBuildAndRunGrammarResult parse(String text) throws IOException {
        return parse(text, null);
    }

    public boolean isStale() {
        return bldr.isStale();
    }

    public InMemoryParseProxyBuilder onRecompile(Consumer<CompileResult> res) {
        this.onRecompile = res;
        return this;
    }

    private Consumer<CompileResult> onRecompile;
    private Optional<CompileResult> lastCompileResult;

    @Override
    public GenerateBuildAndRunGrammarResult parse(String text, Consumer<String> status) throws IOException {
        synchronized (this) {
            if (!bldr.isStale() && Objects.equals(text, lastText)) {
                if (lastResult.parseResult().isPresent()
                        && lastResult.parseResult().get().parseTree().isPresent()
                        && !lastResult.parseResult().get().parseTree().get().isUnparsed()) {
                }
//                System.out.println("USING LAST RESULT - TEXT IS SAME " + text.length() + " chars");
                return lastResult;
            }
        }
        Consumer<String> statusUpdater = ReplanningStatusConsumer.wrapConsumer(status);
        boolean wasStale = bldr.isStale();
        statusUpdater.accept(Bundle.GENERATING_ANTLR_SOURCES(bldr.sourceFile()));
        AntlrSourceGenerationResult antlrResult;
        antlrResult = bldr.build();
        Optional<CompileResult> compileResult = Optional.empty();
        Optional<ParserRunResult> parse = Optional.empty();
        boolean parsed = false;
        if (antlrResult.isUsable()) {
            CompileResult cr = null;
            if (wasStale) {
                cr = doCompile(statusUpdater);
                compileResult = Optional.of(cr);
            }
            if ((cr == null || cr.isUsable())) {
                synchronized (this) {
                    if (Objects.equals(text, lastText) && !wasStale && lastResult != null && lastResult.isUsable()) {
                        return lastResult;
                    }
                }
                parsed = true;
                ParserRunResult runResult = doRun(text, statusUpdater);
                parse = Optional.of(runResult);
            }
        }
        synchronized (this) {
            if (!wasStale && lastCompileResult != null) {
                compileResult = lastCompileResult;
            }
            lastText = text;
            lastCompileResult = compileResult;
            return lastResult = new GenerateBuildAndRunGrammarResult(antlrResult, compileResult, parse, text, wasStale, parsed);
        }
    }

    private CompileResult doCompile(Consumer<String> status) {
        status.accept(Bundle.COMPILING_ANTLR_SOURCES(bldr.sourceFile()));
        CompileResult res =  new CompileJavaSources().compile(bldr.jfs());
        if (onRecompile != null) {
            onRecompile.accept(res);
        }
        return res;
    }

    private String lastText;
    private GenerateBuildAndRunGrammarResult lastResult;

    private ParserRunResult doRun(String text, Consumer<String> status) {
        status.accept(Bundle.EXTRACTING_PARSE(bldr.sourceFile()));
        boolean success = false;
        ParseTreeProxy prx = null;
        Throwable thrown = null;
        String className = bldr.pkg() + "." + PARSER_EXTRACTOR;
        try {
            long then = System.currentTimeMillis();
            long elapsed = -1;
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            IsolationClassLoader<?> isolated = defaultLoaderSupplier(AntlrLibrary.getDefault().getClasspath()).get();
            try (JFSClassLoader cl = bldr.jfs().getClassLoader(StandardLocation.CLASS_OUTPUT, isolated)) {
                Thread.currentThread().setContextClassLoader(cl);
                Class<?> type = Class.forName(className, true, cl);
                Method m = type.getMethod("extract", String.class);
                prx = (ParseTreeProxy) m.invoke(null, text);
                elapsed = System.currentTimeMillis() - then;
                success = true;
            } finally {
                Thread.currentThread().setContextClassLoader(old);
                LOG.log(Level.FINER, "Parsed {0} characters in {1}ms",
                        new Object[]{text.length(), elapsed});
                LOG.log(Level.FINEST, "Parsed /{0}/ in {1}ms",
                        new Object[]{text, elapsed});

                if ("}".equals(text.trim())) {
                    Thread.dumpStack();
                }
            }
        } catch (Exception e) {
            thrown = e;
        }
        return new ParserRunResult(Optional.ofNullable(thrown), Optional.ofNullable(prx), success);
    }

    static Supplier<IsolationClassLoader<?>> defaultLoaderSupplier(URL... urls) {
        return new Supplier<IsolationClassLoader<?>>() {
            @Override
            public IsolationClassLoader<?> get() {
                return IsolationClassLoader.forURLs(Thread.currentThread().getContextClassLoader(),
                        urls, (String s) -> s.startsWith("org.nemesis.antlr.v4"));
            }

            public String toString() {
                StringBuilder sb = new StringBuilder()
                        .append('{');
                for (int i = 0; i < urls.length; i++) {
                    URL u = urls[i];
                    sb.append(u);
                    if (i != urls.length - 1) {
                        sb.append(", ");
                    }
                }
                return sb.append('}').toString();
            }
        };
    }


    @Override
    public ParseProxyBuilder regenerateAntlrCodeOnNextCall() {
        bldr.touch();
        return this;
    }

    public void fullReset() {
        bldr.fullReset();
    }

}
