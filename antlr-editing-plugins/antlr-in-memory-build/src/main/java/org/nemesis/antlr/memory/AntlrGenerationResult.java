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
package org.nemesis.antlr.memory;

import com.mastfrog.graph.IntGraph;
import com.mastfrog.graph.ObjectGraph;
import com.mastfrog.util.path.UnixPath;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import org.nemesis.jfs.result.UpToDateness;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.tools.JavaFileManager.Location;
import org.antlr.v4.tool.Grammar;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.jfs.JFSFileModifications;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSCoordinates;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.result.ProcessingResult;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrGenerationResult implements ProcessingResult {

    public final boolean success;
    public final int code;
    public final Throwable thrown;
    public final String grammarName;
    public final Grammar mainGrammar;
    public final List<ParsedAntlrError> errors;
    public final long grammarFileLastModified;
    public final List<String> infoMessages;
    public final Set<JFSCoordinates.Resolvable> newlyGeneratedFiles;
    public final Map<JFSCoordinates.Resolvable, Long> modifiedFiles;
    public final Set<Grammar> allGrammars;
    public final JFS jfs;
    public final Location grammarSourceLocation;
    public final Location javaSourceOutputLocation;
    public final String packageName;
    public final JFSCoordinates.Resolvable grammarFile;
    public final UnixPath sourceDir;
    public final UnixPath importDir;
    public final boolean generateAll;
    public final Set<AntlrGenerationOption> options;
    public final Charset grammarEncoding;
    public final JFSFileModifications filesStatus;
    public final String tokensHash;
    public final Path originalFilePath;
    public final Supplier<JFS> jfsSupplier;
    public final Map<UnixPath, Set<UnixPath>> outputFiles;
    public final Map<String, Set<UnixPath>> inputFiles;
    public final Map<String, UnixPath> primaryFiles;
    public final Map<UnixPath, Set<UnixPath>> dependencies;
    public final long timestamp;
    public final JFSPathHints hints;

    AntlrGenerationResult(boolean success, int code, Throwable thrown,
            String grammarName, Grammar grammar, List<ParsedAntlrError> errors,
            JFSCoordinates.Resolvable grammarFile, long grammarFileLastModified,
            List<String> infoMessages, Set<JFSCoordinates.Resolvable> postFiles,
            Map<JFSCoordinates.Resolvable, Long> touched, Set<Grammar> allGrammars,
            JFS jfs, Location inputLocation, Location outputLocation,
            String packageName, UnixPath virtualSourceDir, UnixPath virtualInputDir,
            boolean generateAll, Set<AntlrGenerationOption> options,
            Charset grammarEncoding, String tokensHash, Path originalFilePath,
            Supplier<JFS> jfsSupplier, Map<UnixPath, Set<UnixPath>> outputFiles,
            Map<String, Set<UnixPath>> inputFiles, Map<String, UnixPath> primaryFiles,
            Map<UnixPath, Set<UnixPath>> dependencies,
            long timestamp, JFSPathHints hints) {
        this.success = success;
        this.jfsSupplier = jfsSupplier;
        this.code = code;
        this.thrown = thrown;
        this.timestamp = timestamp;
        this.grammarName = grammarName;
        this.mainGrammar = grammar;
        this.errors = Collections.unmodifiableList(errors);
        this.grammarFileLastModified = grammarFileLastModified;
        this.infoMessages = Collections.unmodifiableList(infoMessages);
        this.newlyGeneratedFiles = Collections.unmodifiableSet(postFiles);
        this.modifiedFiles = Collections.unmodifiableMap(touched);
        this.allGrammars = Collections.unmodifiableSet(allGrammars);
        this.jfs = jfs;
        this.filesStatus = jfs.status(inputLocation);
        this.grammarSourceLocation = inputLocation;
        this.javaSourceOutputLocation = outputLocation;
        this.grammarFile = grammarFile;
        this.packageName = packageName;
        this.sourceDir = virtualSourceDir;
        this.importDir = virtualInputDir;
        this.generateAll = generateAll;
        this.options = Collections.unmodifiableSet(EnumSet.copyOf(options));
        this.grammarEncoding = grammarEncoding;
        this.tokensHash = tokensHash;
        this.originalFilePath = notNull("originalFilePath", originalFilePath);
        this.primaryFiles = primaryFiles;
        this.outputFiles = outputFiles;
        this.inputFiles = inputFiles;
        this.dependencies = dependencies;
        this.hints = hints;
    }

    public AntlrGenerationResult cleanOldOutput() throws IOException {
        if (!jfs.isEmpty()) {
            jfs.whileWriteLocked(() -> {
                Set<JFSCoordinates.Resolvable> all = new HashSet<>(newlyGeneratedFiles.size() +
                        modifiedFiles.size());
                all.addAll(newlyGeneratedFiles);
                all.addAll(modifiedFiles.keySet());
                Set<JFSFileObject> set = new HashSet<>();
                for (JFSCoordinates.Resolvable f : all) {
                    JFSFileObject ob = f.resolveOriginal();
                    if (ob != null) {
                        if (ob.storageKind().isMasqueraded()) {
                            continue;
                        }
                        set.add(ob);
                    }
                }
                for (JFSCoordinates.Resolvable f : newlyGeneratedFiles) {
                    JFSFileObject ob = f.resolveOriginal();
                    if (ob != null) {
                        if (ob.storageKind().isMasqueraded()) {
                            continue;
                        }
                        set.add(ob);
                    }
                }
                for (JFSFileObject fo : set) {
                    fo.delete();
                }
                return null;
            });
        }
        return this;
    }

    /**
     * Create a copy of this result, as if it were for building a sibling
     * grammar that was built when this one was, if that sibling grammar was
     * built at the same time as this one - get a build result for a lexer that
     * had to be built for the grammar to be built.
     *
     * @param originalFilePath
     * @param jfsPath
     * @return
     */
    public AntlrGenerationResult forSiblingGrammar(Path originalFilePath, UnixPath jfsPath, String tokensHash) {
        if (jfsPath.equals(grammarFile.path())) {
            return this;
        }
        String filePath = jfsPath.toString();
        Grammar target = null;
        for (Grammar g : allGrammars) {
            if (filePath.equals(g.fileName)) {
                target = g;
                break;
            }
        }
        if (target == null) {
            return null;
        }
        JFSFileObject fo = jfs.get(grammarSourceLocation, jfsPath);
        if (fo == null) {
            return null;
        }
        List<ParsedAntlrError> filteredErrors = errors.isEmpty()
                ? Collections.emptyList() : new ArrayList<>(errors.size());
        if (!errors.isEmpty()) {
            for (ParsedAntlrError pae : errors) {
                if (jfsPath.equals(pae.path())) {
                    filteredErrors.add(pae);
                }
            }
        }
        Long ts = modifiedFiles.get(fo.toReference());
        if (ts == null) {
            ts = fo.getLastModified();
        }

        return new AntlrGenerationResult(success, code, thrown, target.name, target, filteredErrors,
                fo.toReference(), ts, infoMessages, newlyGeneratedFiles, modifiedFiles,
                allGrammars, jfs, grammarSourceLocation, javaSourceOutputLocation, packageName,
                sourceDir, importDir, generateAll, options,
                grammarEncoding, tokensHash, originalFilePath, jfsSupplier, outputFiles, inputFiles,
                primaryFiles, dependencies, timestamp, hints);
    }

    public boolean areOutputFilesUpdated(UnixPath of) {
        Set<UnixPath> all = outputFiles.get(of);
        if (all == null || all.isEmpty()) {
            return false;
        }
        for (UnixPath p : all) {
            JFSCoordinates coords = JFSCoordinates.create(javaSourceOutputLocation, p);
            JFSFileObject fo = coords.resolve(jfs);
            if (fo != null) {
                Long oldLastModified = this.modifiedFiles.get(fo.toReference());
                if (oldLastModified != null) {
                    if (fo.getLastModified() > oldLastModified) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public AntlrGenerator toGenerator() {
        return new AntlrGenerator(toBuilder());
    }

    public AntlrGenerationResult rebuild() {
        try (PrintStream printStream = AntlrLoggers.getDefault().printStream(originalFilePath, AntlrLoggers.STD_TASK_GENERATE_ANTLR)) {
            return toGenerator().run(grammarName, printStream, true);
        }
    }

    public AntlrGeneratorBuilder<AntlrGenerationResult> toBuilder() {
        AntlrGeneratorBuilder<AntlrGenerationResult> result = new AntlrGeneratorBuilder<>(jfsSupplier, bldr -> {
            return bldr.building(sourceDir, importDir());
        });
        result.forceAtn = options.contains(AntlrGenerationOption.FORCE_ATN);
        result.generateATNDot = options.contains(AntlrGenerationOption.GENERATE_ATN);
        result.genDependencies = options.contains(AntlrGenerationOption.GENERATE_DEPENDENCIES);
        result.generateAll = generateAll;
        result.genListener = options.contains(AntlrGenerationOption.GENERATE_LISTENER);
        result.genVisitor = options.contains(AntlrGenerationOption.GENERATE_VISITOR);
        result.jfs = jfsSupplier;
        result.importDir = importDir;
        result.sourcePath = sourceDir;
        result.grammarSourceInputLocation = grammarSourceLocation;
        result.javaSourceOutputLocation = javaSourceOutputLocation;
        result.log = options.contains(AntlrGenerationOption.LOG);
        result.longMessages = options.contains(AntlrGenerationOption.LONG_MESSAGES);
        result.packageName = packageName;
        result.originalFile = originalFilePath;
        result.tokensHash = tokensHash;
        return result;
    }

    public UnixPath sourceDir() {
        return sourceDir;
    }

    public UnixPath importDir() {
        return importDir;
    }

    public Location grammarSourceLocation() {
        return grammarSourceLocation;
    }

    public Location javaSourceOutputLocation() {
        return javaSourceOutputLocation;
    }

    public String packageName() {
        return packageName;
    }

    public String grammarName() {
        return grammarName;
    }

    public boolean isSuccess() {
        return success;
    }

    public int exitCode() {
        return code;
    }

    public Grammar mainGrammar() {
        return mainGrammar;
    }

    public List<ParsedAntlrError> errors() {
        return errors;
    }

    public List<String> infoMessages() {
        return infoMessages;
    }

    @Override
    public boolean isUsable() {
        return success
                && (!modifiedFiles.isEmpty() || !newlyGeneratedFiles.isEmpty());
    }

    @Override
    public UpToDateness currentStatus() {
        return filesStatus.changes().status();
    }

    public JFS jfs() {
        return jfs;
    }

    public Optional<Throwable> thrown() {
        return thrown == null ? Optional.empty() : Optional.of(thrown);
    }

    public void rethrow() throws Throwable {
        if (thrown != null) {
            throw thrown;
        }
    }

    public Optional<Grammar> findGrammar(String name) {
        if (mainGrammar != null && name.equals(mainGrammar.name)) {
            return Optional.of(mainGrammar);
        }
        for (Grammar g : allGrammars) {
            if (g == mainGrammar) {
                continue;
            }
            if (name.equals(g.name)) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "AntlrRunResult{" + "success=" + success + ", code=" + code
                + ", thrown=" + thrown + ", grammarName=" + grammarName
                + ", grammar=" + mainGrammar + ", errors=" + errors
                + ", infos=" + infoMessages + ", postFiles=" + newlyGeneratedFiles + '}';
    }

    private ObjectGraph<UnixPath> depGraph;

    public ObjectGraph<UnixPath> dependencyGraph() {
        if (depGraph != null) {
            return depGraph;
        }
        Set<UnixPath> all = new HashSet<>();
        for (Map.Entry<String, UnixPath> e : primaryFiles.entrySet()) {
            all.add(e.getValue());
        }
        for (Map.Entry<String, Set<UnixPath>> in : inputFiles.entrySet()) {
            all.addAll(in.getValue());
        }
        for (Map.Entry<UnixPath, Set<UnixPath>> dep : this.outputFiles.entrySet()) {
            all.add(dep.getKey());
            all.addAll(dep.getValue());
        }
        for (Map.Entry<UnixPath, Set<UnixPath>> dep : this.dependencies.entrySet()) {
            all.add(dep.getKey());
            all.addAll(dep.getValue());
        }
        List<UnixPath> sorted = new ArrayList<>(all);
        Collections.sort(sorted);
        BitSet[] references = new BitSet[sorted.size()];
        BitSet[] reverseReferences = new BitSet[sorted.size()];
        for (int i = 0; i < references.length; i++) {
            references[i] = new BitSet(references.length);
            reverseReferences[i] = new BitSet(references.length);
        }
        for (int i = 0; i < sorted.size(); i++) {
            UnixPath path = sorted.get(i);
            Set<UnixPath> direct = dependencies.get(path);
            if (direct != null) {
                for (UnixPath dep : direct) {
                    int dix = sorted.indexOf(dep);
                    if (i == dix) {
                        continue;
                    }
                    references[i].set(dix);
                    reverseReferences[dix].set(i);
                }
            }
//            Set<UnixPath> out = outputFiles.get(path);
//            if (out != null) {
//                for (UnixPath oneOutputFile : out) {
//                    int dix = sorted.indexOf(oneOutputFile);
//                    references[i].set(dix);
//                    reverseReferences[dix].set(i);
//                }
//            }
        }
        IntGraph ig = IntGraph.create(references, reverseReferences);
        ObjectGraph<UnixPath> og = ig.toObjectGraph(sorted);
        return depGraph = og;
    }

    public UnixPath pathForGrammar(String grammarName) {
        for (Grammar g : allGrammars) {
            if (Objects.equals(g.name, grammarName)) {
                UnixPath result = primaryFiles.get(g.name);
                if (result == null) {
                    result = primaryFiles.get(g.fileName);
                }
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    public Set<UnixPath> outputFilesForGrammar(String grammarName) {
        UnixPath path = pathForGrammar(grammarName);
        if (path == null) {
            return Collections.emptySet();
        }
        Set<UnixPath> out = outputFiles.get(path);
        if (out == null) {
            out = Collections.emptySet();
        }
        return out;
    }
}
