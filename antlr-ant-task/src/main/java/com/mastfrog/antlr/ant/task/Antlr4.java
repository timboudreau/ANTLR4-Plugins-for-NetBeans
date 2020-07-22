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
package com.mastfrog.antlr.ant.task;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.antlr.v4.Tool;
import org.antlr.v4.tool.Grammar;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import static org.apache.tools.ant.Project.MSG_DEBUG;
import static org.apache.tools.ant.Project.MSG_INFO;
import static org.apache.tools.ant.Project.MSG_WARN;

/**
 * A quick'n'dirty Antlr task; can be used in a few ways:
 * <ul>
 * <li>Point it at a directory and optional imports directory (if not directly under the
 * root directory) and it will find and generate code from all grammars underneath it,
 * figuring out their dependencies in order to build in the right order</li>
 * <li>Point it at a single file and it will build that (set the sourceRootProperty
 * property for it to infer packages correctly)</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public class Antlr4 extends org.apache.tools.ant.Task {

    private final EnumSet<AntlrGenerationOption> opts = EnumSet.noneOf(AntlrGenerationOption.class);
    private String logFormat = "vs2005";
    private Charset charset = StandardCharsets.UTF_8;
    private Path source;
    private Path importDir;
    private Path outputDir;
    private String pkg;
    private String language = "Java";
    private final Set<Path> discoveredGrammarFiles = new HashSet<>();
    private List<String> grammars;
    private int scanDepth = 32;
    private boolean failIfNoGrammars = false;

    public Antlr4() {
        opts.add(AntlrGenerationOption.GENERATE_VISITOR);
        opts.add(AntlrGenerationOption.GENERATE_LISTENER);
        opts.add(AntlrGenerationOption.GENERATE_DEPENDENCIES);
    }

    public void setScanDepth(int value) {
        this.scanDepth = value;
    }

    private boolean validate() throws BuildException {
        if (scanDepth <= 0) {
            throw new BuildException("Illegal scan depth " + scanDepth);
        }
        if (source == null) {
            throw new BuildException("Source not set");
        } else if (!Files.exists(source)) {
            throw new BuildException("Specified source '" + source + "' does not exist");
        }
        if (!Files.exists(source)) {
            throw new BuildException("Source file or folder does not exist: " + source);
        }
        if (!Files.isDirectory(source)) {
            if (grammars != null) {
                throw new BuildException("A list of grammars " + grammars
                        + " was specified, but src was set to a file, not a directory: "
                        + source);
            }
        }
        if (source.equals(importDir)) {
            throw new BuildException("Source and import directory cannot be the same");
        }
        if (importDir == null && Files.isDirectory(source)) {
            Path presumedImportDir = source.resolve("imports");
            if (Files.exists(presumedImportDir)) {
                importDir = presumedImportDir;
                log("Found what looks like an import directory " + presumedImportDir + " - set it "
                        + "explicitly if this is wrong.");
            }
            presumedImportDir = source.resolve("import");
            if (Files.exists(presumedImportDir)) {
                importDir = presumedImportDir;
                log("Found what looks like an import directory " + presumedImportDir + " - set it "
                        + "explicitly if this is wrong.");
            }
        }
        boolean[] grammarFilesFound = new boolean[1];
        try {
            Set<Path> scanned = new HashSet<>();
            FileVisitor<Path> vis = new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (scanned.contains(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (isGrammarFile(file)) {
                        grammarFilesFound[0] = true;
                        discoveredGrammarFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    log(exc, Project.MSG_ERR);
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    scanned.add(dir);
                    if (exc != null) {
                        log(exc, Project.MSG_ERR);
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            };

            Files.walkFileTree(source, EnumSet.noneOf(FileVisitOption.class), scanDepth, vis);
            if (importDir != null && !scanned.contains(importDir)) {
                Files.walkFileTree(importDir, EnumSet.noneOf(FileVisitOption.class), scanDepth, vis);
            }
        } catch (IOException ex) {
            throw new BuildException(ex);
        }
        if (!grammarFilesFound[0] && failIfNoGrammars) {
            throw new BuildException("No grammar files found in " + source);
        }
        if (outputDir == null) {
            log("Output directory not set - nothing will be generated", MSG_WARN);
        }
        if (importDir != null && !Files.exists(importDir)) {
            log("Import dir '" + importDir + "' does not exist", MSG_WARN);
        }
        if (pkg != null) {
            checkPackage(pkg);
        }
        return grammarFilesFound[0];
    }

    private void checkPackage(String pkg) throws BuildException {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        String[] parts = pkg.split("\\.");
        for (String part : parts) {
            for (int i = 0; i < part.length(); i++) {
                boolean valid;
                switch (i) {
                    case 0:
                        valid = Character.isJavaIdentifierStart(part.charAt(i));
                        break;
                    default:
                        valid = Character.isJavaIdentifierPart(part.charAt(i));
                }
                if (!valid) {
                    throw new BuildException("'" + part.charAt(i) + "' in '" + part + "' of '" + pkg
                            + " is not a valid Java identifier");
                }
            }
        }
    }

    private static boolean isGrammarFile(Path path) {
        String fn = path.getFileName().toString();
        return (fn.endsWith(".g4") || fn.endsWith(".g"))
                && Files.isRegularFile(path);
    }

    /**
     * If set to true, throw a BuildException if no grammars are found to
     * generate.
     *
     * @param failIfNoGrammars
     */
    public void setFailIfNoGrammars(boolean failIfNoGrammars) {
        this.failIfNoGrammars = failIfNoGrammars;
    }

    /**
     * Set the language to generated, such as "Java" or "Javascript" - see the
     * Antlr documentation for possible options.
     *
     * @param language A language, as Antlr understands it
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Set the package the generated files will live in; this will also cause
     * appropriate package directories to be created. If unset, the default
     * package will be used.
     *
     * @param pkg The package
     */
    public void setPackage(String pkg) {
        this.pkg = pkg;
    }

    /**
     * Set the source - this can be a directory, in which case you can either
     * specify (all of!) the grammars to build using the <code>grammars</code>
     * property, or the task will attempt to figure out their dependency order
     * and build them in the appropriate order to succeed; or if you have a
     * single grammar (including a combined grammar with an implicit lexer), you
     * can either point to that file, or pointing to its parent directory will
     * do.
     * <p>
     * If you don't specify grammars and their order and do specify a directory,
     * not a file, the Antlr4 task will make a best-effort attempt to sort them
     * into the correct order using simple regular expressions to detect grammar
     * type and imports.
     * </p>
     *
     * @param src
     */
    public void setSrc(File src) {
        source = src.toPath();
    }

    /**
     * Set the base directory for generated files.
     *
     * @param output The output directory
     */
    public void setOutput(File output) {
        this.outputDir = output.toPath();
    }

    /**
     * Set the import, or, in Antlr's parlance, "lib" directory. Optional.
     *
     * @param imports The lib directory
     */
    public void setImports(File imports) {
        this.importDir = imports.toPath();
    }

    /**
     * If set, Antlr will treat warnings as errors.
     *
     * @param werror The setting
     */
    public void setWarningsAreErrors(boolean werror) {
        addRemove(AntlrGenerationOption.WARNINGS_ARE_ERRORS, werror);
    }

    /**
     * Generate all files into the specified output directory regardless of
     * package.
     *
     * @param exactOutputDir the value
     */
    public void setExactOutputDir(boolean exactOutputDir) {
        addRemove(AntlrGenerationOption.EXACT_OUTPUT_DIR, exactOutputDir);
    }

    /**
     * Set the encoding for input files
     *
     * @param encoding The encoding
     */
    public void setEncoding(String encoding) {
        charset = Charset.forName(encoding);
    }

    /**
     * Set the build output format - see the Antlr docs for possible values;
     * since this task was written in concert with the NetBeans antlr suppport
     * module, that module prefers <i>vs2005</i> which is the default.
     *
     * @param logFormat
     */
    public void setLogFormat(String logFormat) {
        this.logFormat = logFormat;
    }

    /**
     * Unset this if you don't want to generate visitor support for your
     * grammar.
     *
     * @param visitor Whether or not to generate a visitor class
     */
    public void setVisitor(boolean visitor) {
        addRemove(AntlrGenerationOption.GENERATE_VISITOR, visitor);
    }

    /**
     * Unset this if you don't want to generate listener support for your
     * grammar.
     *
     * @param visitor Whether or not to generate a visitor class
     */
    public void setListener(boolean listener) {
        addRemove(AntlrGenerationOption.GENERATE_LISTENER, listener);
    }

    // this option actually causes Antlr to generate nothing, so
    // commenting out for now
//    public void setGenerateDependencies(boolean gen) {
//        addRemove(AntlrGenerationOption.GENERATE_DEPENDENCIES, gen);
//    }
    /**
     * Turn on the -Xlog option for Antlr (fails on some versions of Antlr).
     *
     * @param log Whether or not to log
     */
    public void setLog(boolean log) {
        addRemove(AntlrGenerationOption.LOG, log);
    }

    /**
     * Turn on the long-messages option of Antlr.
     *
     * @param longMessages If true, use long messages
     */
    public void setLongMessages(boolean longMessages) {
        addRemove(AntlrGenerationOption.LONG_MESSAGES, longMessages);
    }

    /**
     * Turn on generation of an atn-dot file by Antlr (fails for some grammars).
     *
     * @param atn Whether or not to generate an atn-dot file
     */
    public void setAtn(boolean atn) {
        addRemove(AntlrGenerationOption.GENERATE_ATN_DOT, atn);
    }

    public void setForceAtn(boolean forceAtn) {
        addRemove(AntlrGenerationOption.FORCE_ATN, forceAtn);
    }

    /**
     * Set the list of grammars to process <i>in order</i> - dependencies before
     * the things that depend on them.
     *
     * @param grammars
     */
    public void setGrammars(String grammars) {
        this.grammars = Arrays.asList(grammars.trim().split("[,\\s]+"));
    }

    private void addRemove(AntlrGenerationOption option, boolean add) {
        if (add) {
            opts.add(option);
        } else {
            opts.remove(option);
        }
    }

    private String sourceRootProperty = "antlr4.source.dir";

    /**
     * Set the name of a property set in the project, which will supply the
     * source root directory - if present and a parent of the source(s) Antlr is
     * asked to generate, then the Java package used for code generation will be
     * based on the relative path. Set this if you are not explicitly passing a
     * package name, and would profer not to generate grammars into the default
     * package (using the default package for <i>anything</i> is generally a bad
     * idea).
     *
     * @param srcRoot Not the source root itself, but a property that should be
     * used to dereference the source root.
     */
    public void setGrammarSourceRootProperty(String srcRoot) {
        sourceRootProperty = srcRoot;
    }

    private String grammarPackage(Path source) {
        if (pkg != null && !pkg.isEmpty()) {
            log("Using explicit package for grammar generation");
            return pkg;
        }
        if (sourceRootProperty != null) {
            String propVal = getProject().getProperty(sourceRootProperty);
            if (propVal != null && !propVal.trim().isEmpty()) {
                Path pth;
                if (!propVal.startsWith(File.separator)) {
                    pth = new File(getProject().getBaseDir(), propVal).toPath();
                } else {
                    pth = Paths.get(propVal);
                }
                if (!Files.exists(pth)) {
                    throw new BuildException("Source root property set to '" + sourceRootProperty
                            + " which has the value '" + propVal + "' "
                            + " but that folder does not exist: " + pth);
                }
                Path src = source;
                if (!Files.isDirectory(src)) {
                    src = src.getParent();
                }
                if (!src.startsWith(pth)) {
                    throw new BuildException(" Grammar source root property set to '" + sourceRootProperty
                            + " which has the value '" + propVal + "' "
                            + " resolving to the folder \n" + pth
                            + "\n but the grammar file(s) we are generating do not live under"
                            + " that folder, but are in\n\t\t" + src + "\n Cannot derive a Java package "
                            + "name that corresponds to that - it would include '..' or similar."
                            + "\n To fix this, either\n\t1. "
                            + "Explicitly set the package name to build on this Ant task, or\n2.\t"
                            + "Correct the value of '" + sourceRootProperty + "' to point to "
                            + "a parent folder of\n\t\t " + src + "\n\tor,\n\t3. Set the "
                            + "grammar source root property passed to this task to the name of "
                            + "some other property that *does* poinnt to a parent folder of \n\t\t"
                            + src + "\n");
                }
                // derive the package
                Path rel = pth.relativize(src);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < rel.getNameCount(); i++) {
                    Path item = rel.getName(i);
                    sb.append(item.toString());
                    if (i != rel.getNameCount() - 1) {
                        sb.append('.');
                    }
                }
                log("Derive relative package from " + rel);
                log("Will generate " + source.getFileName() + " into package " + sb);
                String result = sb.toString();
                checkPackage(result);
                return result;
            } else {
                log("Source root property " + sourceRootProperty + " not set.  Grammars will be "
                        + "generated into the default package.");
            }
        }
        return "";
    }

    @Override
    public void execute() throws BuildException {
        try {
            doExecute();
        } catch (IOException ioe) {
            throw new BuildException(ioe);
        }
    }

    public void doExecute() throws BuildException, IOException {
        boolean buildFilesFound = validate();
        if (!buildFilesFound) {
            log("No Antlr grammar files found in " + source + " - doing nothing.", MSG_INFO);
        }
        log("Will build the following grammars in the following order, by package:", MSG_INFO);
        for (Map.Entry<String, List<GrammarFileEntry>> e : grammarsByPackage().entrySet()) {
            log("\t" + e.getKey() + ":");
            for (GrammarFileEntry en : e.getValue()) {
                log("\t\t" + en, MSG_INFO);
                Set<String> deps = en.dependencies();
                if (!deps.isEmpty()) {
                    for (String dep : deps) {
                        log("\t\t\tdepends on " + dep, MSG_INFO);
                    }
                }
            }
        }
        for (Map.Entry<String, List<GrammarFileEntry>> e : grammarsByPackage().entrySet()) {
            String pkg = e.getKey();
            Path output = outputDir;
            String[] args = AntlrGenerationOption.toAntlrArguments(source,
                    opts, charset, pkg, importDir == null ? null : importDir.toAbsolutePath(), logFormat,
                    output == null ? null : output.toAbsolutePath(), language);
            log("Will run Antlr " + Tool.VERSION + " with arguments: " + Arrays.toString(args));
            Path pkgDir = pkg == null || pkg.isEmpty()
                    ? output : output.resolve(pkg.replace('.', File.separatorChar));
            Tool tool = new ToolExt(args, this);
            TaskToolListener lis = new TaskToolListener(this, this.opts.contains(AntlrGenerationOption.LOG));
            tool.addListener(lis);

            tool.outputDirectory = pkgDir.toAbsolutePath().toString();
            boolean newPackageDir = !Files.exists(pkgDir);
            if (newPackageDir) {
                Files.createDirectories(pkgDir);
            }
            try {
                if (!Files.exists(output)) {
                    Files.createDirectories(output);
                }
                for (GrammarFileEntry entry : e.getValue()) {
                    if (entry.isImport()) {
                        continue;
                    }
                    tool.inputDirectory = entry.parent().toAbsolutePath().toFile();
                    log("Build '" + entry.grammarName() + "' into " + pkgDir, MSG_DEBUG);
                    Grammar g = tool.loadGrammar(entry.fileName());
                    if (g.implicitLexer != null) {
                        tool.process(g.implicitLexer, true);
                    }
                    tool.process(g, true);
                    lis.rethrow();
                }
            } catch (AttemptedExit ex) {
                log("Attempted exit", ex, Project.MSG_ERR);
                if (ex.exitCode != 0) {
                    throw new BuildException(ex);
                }
            } catch (Exception | Error ex) {
                ex.printStackTrace(System.err);
                throw new BuildException(ex);
            }
        }
    }

    private Map<String, List<GrammarFileEntry>> grammarsByPackage() throws IOException {
        Map<String, List<GrammarFileEntry>> result = new TreeMap<>();
        String lastPackage = "";
        for (GrammarFileEntry entry : discoveredGrammars()) {
            String pkg = entry.getPackage();
            if (pkg == null) {
                pkg = lastPackage;
            }
            List<GrammarFileEntry> all = result.get(pkg);
            if (all == null) {
                all = new ArrayList<>();
                result.put(pkg, all);
            }
            all.add(entry);
            lastPackage = pkg;
        }
        return result;
    }

    private List<GrammarFileEntry> entries;

    private List<GrammarFileEntry> discoveredGrammars() throws IOException, BuildException {
        if (this.entries != null) {
            return this.entries;
        }
        List<GrammarFileEntry> entries = new ArrayList<>();
        Set<GrammarFileEntry> imports = new HashSet<>();
        for (Path path : discoveredGrammarFiles) {
            boolean isImport = importDir != null && path.startsWith(importDir);
            GrammarFileEntry entry = new GrammarFileEntry(path, charset, this, isImport);
            if (isImport) {
                imports.add(entry);
            }
            entries.add(entry);
            if (!isImport) {
                String pkg = grammarPackage(path);
                entry.setPackage(pkg);
            }
        }
        for (GrammarFileEntry imp : imports) {
            for (GrammarFileEntry en : entries) {
                if (imp == en || imports.contains(en)) {
                    continue;
                }
                if (en.dependsOn(imp)) {
                    imp.setPackage(en.getPackage());
                }
            }
        }
        Collections.sort(entries);
        return entries = Collections.unmodifiableList(entries);
    }
}
