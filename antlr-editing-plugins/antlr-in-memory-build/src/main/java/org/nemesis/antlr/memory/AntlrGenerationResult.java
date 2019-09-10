package org.nemesis.antlr.memory;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.nemesis.jfs.result.UpToDateness;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.tools.JavaFileManager.Location;
import org.antlr.v4.tool.Grammar;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.jfs.JFS;
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
    private final long grammarFileLastModified;
    public final List<String> infoMessages;
    public final Set<JFSFileObject> newlyGeneratedFiles;
    public final Map<JFSFileObject, Long> modifiedFiles;
    public final Set<Grammar> allGrammars;
    public final JFS jfs;
    public final Location grammarSourceLocation;
    public final Location javaSourceOutputLocation;
    public final String packageName;
    public final JFSFileObject grammarFile;
    public final Path sourceDir;
    public final Path importDir;
    public final boolean generateAll;
    public final Set<AntlrGenerationOption> options;
    public final Charset grammarEncoding;

    AntlrGenerationResult(boolean success, int code, Throwable thrown,
            String grammarName, Grammar grammar, List<ParsedAntlrError> errors,
            JFSFileObject grammarFile, long grammarFileLastModified,
            List<String> infoMessages, Set<JFSFileObject> postFiles,
            Map<JFSFileObject, Long> touched, Set<Grammar> allGrammars,
            JFS jfs, Location inputLocation, Location outputLocation,
            String packageName, Path virtualSourceDir, Path virtualInputDir,
            boolean generateAll, Set<AntlrGenerationOption> options,
            Charset grammarEncoding) {
        this.success = success;
        this.code = code;
        this.thrown = thrown;
        this.grammarName = grammarName;
        this.mainGrammar = grammar;
        this.errors = Collections.unmodifiableList(errors);
        this.grammarFileLastModified = grammarFileLastModified;
        this.infoMessages = Collections.unmodifiableList(infoMessages);
        this.newlyGeneratedFiles = Collections.unmodifiableSet(postFiles);
        this.modifiedFiles = new HashMap<>(touched);
        this.allGrammars = Collections.unmodifiableSet(allGrammars);
        this.jfs = jfs;
        this.grammarSourceLocation = inputLocation;
        this.javaSourceOutputLocation = outputLocation;
        this.grammarFile = grammarFile;
        this.packageName = packageName;
        this.sourceDir = virtualSourceDir;
        this.importDir = virtualInputDir;
        this.generateAll = generateAll;
        this.options = EnumSet.copyOf(options);
        this.grammarEncoding = grammarEncoding;
    }

    public AntlrGenerator toGenerator() {
        return new AntlrGenerator(toBuilder());
    }

    public AntlrGenerationResult rebuild() {
        return toGenerator().run(grammarName, AntlrLoggers.getDefault().forPath(Paths.get(grammarFile.getName())), true);
    }

    public AntlrGeneratorBuilder<AntlrGenerationResult> toBuilder() {
        AntlrGeneratorBuilder<AntlrGenerationResult> result = new AntlrGeneratorBuilder<>(jfs, bldr -> {
            return bldr.building(sourceDir, importDir());
        });
        result.forceAtn = options.contains(AntlrGenerationOption.FORCE_ATN);
        result.generateATNDot = options.contains(AntlrGenerationOption.GENERATE_ATN);
        result.genDependencies = options.contains(AntlrGenerationOption.GENERATE_DEPENDENCIES);
        result.generateAll = generateAll;
        result.genListener = options.contains(AntlrGenerationOption.GENERATE_LISTENER);
        result.genVisitor = options.contains(AntlrGenerationOption.GENERATE_VISITOR);
        result.jfs = jfs;
        result.importDir = importDir;
        result.sourcePath = sourceDir;
        result.grammarSourceInputLocation = grammarSourceLocation;
        result.javaSourceOutputLocation = javaSourceOutputLocation;
        result.log = options.contains(AntlrGenerationOption.LOG);
        result.longMessages = options.contains(AntlrGenerationOption.LONG_MESSAGES);
        result.packageName = packageName;
        return result;
    }

    public Path sourceDir() {
        return sourceDir;
    }

    public Path importDir() {
        return importDir;
    }

    public void clean() {
        modifiedFiles.keySet().forEach(JFSFileObject::delete);
        modifiedFiles.clear();
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
        return success && !modifiedFiles.isEmpty();
    }

    @Override
    public UpToDateness currentStatus() {
        if (grammarFile == null || modifiedFiles.isEmpty()) {
            return UpToDateness.UNKNOWN;
        }
        if (grammarFile.getLastModified() > grammarFileLastModified) {
            return UpToDateness.staleStatus().add(Paths.get(grammarFile.getName())).build();
        }
        return UpToDateness.fromFileTimes(modifiedFiles);
    }

    public Set<JFSFileObject> touched() {
        return Collections.unmodifiableSet(modifiedFiles.keySet());
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

}
