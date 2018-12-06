package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.JFSClassLoader;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.AntlrSourceGenerationResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.InMemoryAntlrSourceGenerationBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ExtractionCodeGenerator.PARSER_EXTRACTOR;
import org.nemesis.antlr.v4.netbeans.v8.util.ReplanningStatusConsumer;

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
        CompileResult res =  new CompileAntlrSources().compile(bldr.jfs());
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
            try (JFSClassLoader cl = bldr.jfs().getClassLoader(StandardLocation.CLASS_OUTPUT, Thread.currentThread().getContextClassLoader())) {
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

    @Override
    public ParseProxyBuilder regenerateAntlrCodeOnNextCall() {
        bldr.touch();
        return this;
    }

    public void fullReset() {
        bldr.fullReset();
    }

}
