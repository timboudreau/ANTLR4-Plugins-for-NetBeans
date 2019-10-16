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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ParserRunnerBuilder;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ForeignInvocationResult;
import org.nemesis.antlr.v4.netbeans.v8.util.RandomPackageNames;
import org.openide.util.Parameters;

/**
 * Compiles grammar files into a temporary folder.
 *
 * @author Tim Boudreau
 */
public final class GrammarJavaSourceGeneratorBuilder implements AntlrSourceGenerationBuilder {

    private final Path sourceFile;
    private AntlrLibrary library = AntlrLibrary.getDefault();
    private String pkg = RandomPackageNames.newPackageName();
    private Path importDir;
    private final Set<AntlrRunOption> options = EnumSet.noneOf(AntlrRunOption.class);
    private Path outputRoot;
    private boolean captureOutputStreams;
    private AtomicBoolean cancellation = new AtomicBoolean();
    private final List<Path> additionalClasspath = new ArrayList<>(3);

    GrammarJavaSourceGeneratorBuilder(Path sourceFile) {
        this(sourceFile, new AtomicBoolean());
    }

    GrammarJavaSourceGeneratorBuilder(Path sourceFile, AtomicBoolean cancellation) {
        Parameters.notNull("sourceFile", sourceFile);
        this.sourceFile = sourceFile;
        if (!Files.exists(sourceFile)) {
            throw new IllegalStateException("Does not exist: " + sourceFile);
        }
    }

    public Path sourceFile() {
        return sourceFile;
    }

    @Override
    public ParserRunnerBuilder toParseAndRunBuilder() {
        return new ParserRunnerBuilder(this, cancellation);
    }

    private static Path scratchDir(boolean create) throws IOException {
        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "nb-antlr-tmp");
        if (create && !Files.exists(path)) {
            Files.createDirectories(path);
        }
        return path;
    }

    /**
     * Create a new builder for an Antlr source file.
     *
     * @param sourceFile The source file
     * @throws IllegalStateException if the file does not exist
     * @return A builder
     */
    public static GrammarJavaSourceGeneratorBuilder forAntlrSource(Path sourceFile) {
        return new GrammarJavaSourceGeneratorBuilder(sourceFile);
    }

    @Override
    public GrammarJavaSourceGeneratorBuilder checkCancellationOn(AtomicBoolean bool) {
        cancellation = bool;
        return this;
    }

    private String sourceFileRawName() {
        String result = sourceFile.getFileName().toString();
        int ix = result.lastIndexOf(".");
        if (ix > 0) {
            result = result.substring(0, ix);
        }
        return result;
    }

    private Path outputRoot() throws IOException {
        if (outputRoot == null) {
            outputRoot = scratchDir(false).resolve(sourceFileRawName());
        }
        return outputRoot;
    }

    private Path packageOutputDir() throws IOException {
        return outputRoot().resolve(pkg.replace('.', '/'));
    }

    private GrammarJavaSourceGenerator buildGrammarJavaSourceGenerator() throws IOException {
        String pk = pkg;
        for (int i = 0; i < pk.length(); i++) {
            char c = pk.charAt(i);
            switch (c) {
                case '.':
                    continue;
                default:
                    switch (i) {
                        case 0:
                            if (!Character.isJavaIdentifierStart(c)) {
                                throw new IllegalStateException("Invalid package name: '" + pk + "' - " + " illegal starting identifier character " + "'" + c + "' at " + i);
                            }
                            continue;
                        default:
                            if (!Character.isJavaIdentifierPart(c)) {
                                throw new IllegalStateException("Invalid package name: '" + pk + "' - " + " illegal identifier character " + "'" + c + "' at " + i);
                            }
                    }
            }
        }
        return new GrammarJavaSourceGenerator(sourceFile, pkg, packageOutputDir(), importDir, captureOutputStreams, options);
    }

    /**
     * Use a specific AntlrLibrary instance, rather than the bundled default
     * one.
     *
     * @param lib The library
     * @return this
     */
    public GrammarJavaSourceGeneratorBuilder withAntlrLibrary(AntlrLibrary lib) {
        Parameters.notNull("lib", lib);
        this.library = lib;
        return this;
    }

    /**
     * Add some additional paths to the Antlr library classpath, for loading
     * foreign classes.
     *
     * @param p The path
     * @param more Additional paths, if any
     * @return this
     */
    @Override
    public GrammarJavaSourceGeneratorBuilder addToClasspath(Path p, Path... more) {
        Parameters.notNull("p", p);
        if (!Files.exists(p)) {
            throw new IllegalArgumentException("Does not exist: " + p);
        }
        additionalClasspath.add(p);
        for (Path p1 : more) {
            Parameters.notNull("p1", p1);
            if (!Files.exists(p1)) {
                throw new IllegalArgumentException("Does not exist: " + p1);
            }
            additionalClasspath.add(p1);
        }
        return this;
    }

    private AntlrLibrary antlrLibrary() {
        AntlrLibrary result = this.library;
        if (!additionalClasspath.isEmpty()) {
            Path[] components = additionalClasspath.toArray(new Path[additionalClasspath.size()]);
            result = result.with(components);
        }
        return result;
    }

    private static Set<Path> existingFiles(Path outputRoot) throws IOException {
        Set<Path> result = new HashSet<>();
        if (Files.exists(outputRoot)) {
            Files.walkFileTree(outputRoot, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isSymbolicLink()) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    result.add(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    result.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return result;
    }

    /**
     * Run antlr and return a result that shows the output status, generated
     * files and any captured messages.
     *
     * @return A result
     * @throws IOException If something goes wrong
     */
    @Override
    public AntlrSourceGenerationResult build() throws IOException {
        Path outputRoot = outputRoot();
        Set<Path> preexisting = existingFiles(outputRoot);
        Path packageOutput = packageOutputDir();
        if (cancellation.get() || Thread.interrupted()) {
            ForeignInvocationResult<AntlrInvocationResult> fakeResult = new ForeignInvocationResult<AntlrInvocationResult>(new PrintStream(new ByteArrayOutputStream(1)),
                     new PrintStream(new ByteArrayOutputStream(1)));
            fakeResult.setThrown(new CancellationException("Cancelled"));
            return new AntlrSourceGenerationResult(sourceFile, outputRoot, packageOutputDir(),
                    pkg, fakeResult, Collections.emptySet(), this.library, sourceFileRawName(), cancellation);
        }
        GrammarJavaSourceGenerator gen = buildGrammarJavaSourceGenerator();
        ForeignInvocationResult<AntlrInvocationResult> res = gen.run(antlrLibrary());
        Set<Path> currentFiles = existingFiles(outputRoot);
        currentFiles.removeAll(preexisting);
        return new AntlrSourceGenerationResult(sourceFile, outputRoot, packageOutput, pkg,
                res, currentFiles, library, this.sourceFileRawName(), cancellation);
    }

    /**
     * Capture System.out and System.err from the Antlr invocation, and parse
     * error messages out of that, rather than using dynamic proxies and
     * reflection. In the case of unknown versions of Antlr, this may be more
     * reliable, but is more expensive since files must be re-read to determine
     * the character offset from the line:offset information provided by Antlr's
     * textual output.
     *
     * @return this
     */
    public GrammarJavaSourceGeneratorBuilder captureOutputStreams() {
        return captureOutputStreams(true);
    }

    /**
     * Set this builder NOT to capture System.out and System.err from the Antlr
     * invocation (the default), and parse error messages out of that, rather
     * than using dynamic proxies and reflection. The default is false. In the
     * case of unknown versions of Antlr, this may be more reliable, but is more
     * expensive since files must be re-read to determine the character offset
     * from the line:offset information provided by Antlr's textual output.
     * <p>
     * You only need to call this method if some other code may have called      <code>captureOutputStream()</code. previously.
     * </p>
     *
     * @return this
     */
    public GrammarJavaSourceGeneratorBuilder extractErrorsReflectively() {
        return captureOutputStreams(false);
    }

    /**
     * Set whether to capture System.out and System.err from the Antlr
     * invocation, and parse error messages out of that, rather than using
     * dynamic proxies and reflection. The default is false. In the case of
     * unknown versions of Antlr, this may be more reliable, but is more
     * expensive since files must be re-read to determine the character offset
     * from the line:offset information provided by Antlr's textual output.
     *
     * @return this
     */
    public GrammarJavaSourceGeneratorBuilder captureOutputStreams(boolean captureOutputStreams) {
        this.captureOutputStreams = captureOutputStreams;
        return this;
    }

    /**
     * Explicitly set the output root (java classpath root for generated java
     * files). If not called, a unique location under the system temporary
     * directory will be used.
     *
     * @param outputRoot The new output root
     * @return this
     */
    public GrammarJavaSourceGeneratorBuilder withOutputRoot(Path outputRoot) {
        this.outputRoot = outputRoot;
        return this;
    }

    /**
     * Set some Antlr generation / runtime options.
     *
     * @param option An option to set
     * @param more More options to set
     * @return this
     */
    @Override
    public GrammarJavaSourceGeneratorBuilder withRunOptions(AntlrRunOption option, AntlrRunOption... more) {
        options.add(option);
        if (more.length > 0) {
            for (AntlrRunOption o : more) {
                options.add(o);
            }
        }
        return this;
    }

    /**
     * Set the import (libDirectory in Antlr parlance) folder for finding
     * grammar files that are dependencies of the one being built.
     *
     * @param importDir The new import dir.
     * @return this
     */
    @Override
    public GrammarJavaSourceGeneratorBuilder withImportDir(Optional<Path> importDir) {
        if (importDir.isPresent()) {
            return withImportDir(importDir.get());
        }
        return this;
    }

    /**
     * Set the import (libDirectory in Antlr parlance) folder for finding
     * grammar files that are dependencies of the one being built.
     *
     * @param importDir The new import dir.
     * @return this
     */
    @Override
    public GrammarJavaSourceGeneratorBuilder withImportDir(Path importDir) {
        Parameters.notNull("importDir", this);
        this.importDir = importDir;
        return this;
    }

    /**
     * Explicitly set the package name of generated java sources. By default,
     * the package name is a unique generated name for this run, so as to
     * clearly differentiate classes from a given run from those output by any
     * previous run (the should not escape from classloader isolation at all,
     * but this provides marginal additional protection from CCEs).
     *
     * @param pkg The package
     * @return this
     */
    @Override
    public GrammarJavaSourceGeneratorBuilder withPackage(String pkg) {
        Parameters.notEmpty("pkg", pkg);
        for (String s : pkg.split("\\.")) {
            Parameters.javaIdentifier("pkg", s);
        }
        this.pkg = pkg;
        return this;
    }
}
