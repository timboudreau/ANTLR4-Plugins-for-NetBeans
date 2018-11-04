/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract;

import org.nemesis.antlr.v4.netbeans.v8.util.ReplanningStatusConsumer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.AntlrLibrary;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.AntlrSourceGenerationResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.GrammarJavaSourceGeneratorBuilder;

/**
 * Given an Antlr source file, compiles it, runs it, parses some text and
 * returns lexer and parser results. Instances are reusable - parse() can be
 * called more than once; if the grammar has changed (something called
 * regenerateAntlrCodeOnNextCall()), it will trigger a rebuild and recompile; if
 * not, it will invoke the existing code with new text to parse.
 *
 * @author Tim Boudreau
 */
public class ParserRunnerBuilder implements ParseProxyBuilder {

    private final GrammarJavaSourceGeneratorBuilder genBuilder;
    private AntlrSourceGenerationResult genResult;
    private CompileResult lastCompileResult;
    private long sourceFileLastModified = -1;
    private AtomicBoolean cancellation;

    public ParserRunnerBuilder(GrammarJavaSourceGeneratorBuilder genBuilder, AtomicBoolean cancellation) {
        this.genBuilder = genBuilder;
        this.cancellation = cancellation;
    }

    public ParserRunnerBuilder(AntlrSourceGenerationResult result) {
        this.genResult = result;
        this.genBuilder = null;
        this.cancellation = result.cancellation();
    }

    @Override
    public synchronized ParserRunnerBuilder regenerateAntlrCodeOnNextCall() {
        this.genResult = null;
        return this;
    }

    @Override
    public GenerateBuildAndRunGrammarResult buildNoParse() throws IOException {
        return parse(null);
    }

    private GenerateBuildAndRunGrammarResult checkCancellation(Optional<CompileResult> compileResult, boolean compiled) {
        if (cancellation.get() || Thread.interrupted()) {
            return new GenerateBuildAndRunGrammarResult(genResult, compileResult,
                    Optional.empty(), "", compiled, false);

        }
        return null;
    }

    @Override
    public GenerateBuildAndRunGrammarResult parse(String text) throws IOException {
        return parse(text, null);
    }

    @Override
    public GenerateBuildAndRunGrammarResult parse(String text, Consumer<String> status) throws IOException {
        Path path = genBuilder.sourceFile().getFileName();
        final Consumer<String> statusUpdate = ReplanningStatusConsumer.wrapConsumer(status);
        Optional<ParserRunResult> parseResult = Optional.empty();
        Optional<CompileResult> compileResult = Optional.empty();
        AntlrSourceGenerationResult oldResult;
        long oldLastModified;
        synchronized (this) {
            oldResult = this.genResult;
            oldLastModified = sourceFileLastModified;
        }
        if (oldResult != null) {
            Path src = oldResult.sourceFile();
            if (oldLastModified != -1) {
                FileTime time = Files.getLastModifiedTime(src);
                long val = time.to(TimeUnit.MILLISECONDS);
                if (val != oldLastModified) {
                    oldResult = null;
                }
            }
            if (oldResult != null) {
                for (Path p : oldResult.generatedFiles()) {
                    if (!Files.exists(p)) {
                        oldResult = null;
                        break;
                    }
                }
            }
        }
        boolean compiled = false;
        boolean parsed = false;

        GenerateBuildAndRunGrammarResult res = checkCancellation(compileResult, false);
        if (res != null) {
            statusUpdate.accept(Bundle.CANCELLED());
            return res;
        }
        if (oldResult == null) {
            statusUpdate.accept(Bundle.GENERATING_ANTLR_SOURCES(path));
        }
        AntlrSourceGenerationResult genResult = oldResult == null ? genBuilder.build() : oldResult;
        if (genResult.isUsable()) {
            synchronized (this) {
                this.genResult = genResult;
            }
            res = checkCancellation(compileResult, false);
            if (res != null) {
                statusUpdate.accept(Bundle.CANCELLED());
                return res;
            }

            CompileResult cr;
            if (genResult != oldResult) {
                boolean hasParser = false;
                for (Path p : genResult.generatedFiles()) {
                    if (p.getFileName().toString().endsWith("Parser.java")) {
                        hasParser = true;
                        break;
                    }
                }
                ExtractionCodeGenerator.saveExtractorSourceTo(genResult.sourceFile(),
                        genResult.packageName(), genResult.grammarName(),
                        genResult.outputPackageFolder(), !hasParser);
                CompileAntlrSources cp = new CompileAntlrSources();
                statusUpdate.accept(Bundle.COMPILING_ANTLR_SOURCES(path));
                cr = cp.compile(
                        genResult.outputClasspathRoot(),
                        genResult.outputClasspathRoot(),
                        genResult.antlrLibrary().paths());
                compileResult = Optional.of(cr);
                compiled = true;
                res = checkCancellation(compileResult, false);
                if (res != null) {
                    statusUpdate.accept(Bundle.CANCELLED());
                    return res;
                }

                synchronized (this) {
                    FileTime time = Files.getLastModifiedTime(genResult.sourceFile());
                    sourceFileLastModified = time.to(TimeUnit.MILLISECONDS);
                    lastCompileResult = cr;
                }
            } else {
                synchronized (this) {
                    cr = lastCompileResult;
                }
            }
            if (cr.ok() && text != null) {
                AntlrLibrary lib = genResult.antlrLibrary().with(genResult.outputClasspathRoot());
                CompiledParserRunner runner = new CompiledParserRunner(genResult.outputClasspathRoot(),
                        genResult.packageName(), lib);
                statusUpdate.accept(Bundle.EXTRACTING_PARSE(path));
                ParserRunResult result = runner.parseAndExtract(text);
                if (cancellation.get()) {
                    return new GenerateBuildAndRunGrammarResult(genResult, compileResult,
                            Optional.empty(), text, compiled, false);
                }
                parsed = true;
                parseResult = Optional.of(result);
            }
        }
        cancellation.set(false);
        return new GenerateBuildAndRunGrammarResult(genResult, compileResult,
                parseResult, text, compiled, parsed);
    }
}
