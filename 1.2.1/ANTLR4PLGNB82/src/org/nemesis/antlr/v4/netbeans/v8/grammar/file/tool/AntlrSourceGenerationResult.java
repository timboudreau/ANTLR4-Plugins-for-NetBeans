package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ForeignInvocationResult;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrSourceGenerationResult {

    private final Path sourceFile;
    private final Path outputRoot;
    private final Path packageOutput;
    private final String pkg;
    private final boolean success;
    private final Optional<Throwable> thrown;
    private final Optional<Integer> attemptedExitCode;
    private final List<ParsedAntlrError> errors;
    private final Set<Path> generatedFiles;
    private final AntlrLibrary library;
    private final String grammarName;
    private final AtomicBoolean cancellation;

    public AntlrSourceGenerationResult(Path sourceFile, Path outputRoot,
            Path packageOutput, String pkg, boolean success, Optional<Throwable> thrown,
            Optional<Integer> attemptedExitCode, List<ParsedAntlrError> errors,
            Set<Path> generatedFiles, AntlrLibrary library,
            String grammarName, AtomicBoolean cancellation) {
        this.sourceFile = sourceFile;
        this.outputRoot = outputRoot;
        this.packageOutput = packageOutput;
        this.pkg = pkg;
        this.success = success;
        this.thrown = thrown;
        this.attemptedExitCode = attemptedExitCode;
        this.errors = errors;
        this.generatedFiles = generatedFiles;
        this.library = library;
        this.grammarName = grammarName;
        this.cancellation = cancellation;
    }

    AntlrSourceGenerationResult(Path sourceFile, Path outputRoot, Path packageOutput,
            String pkg, ForeignInvocationResult<AntlrInvocationResult> res,
            Set<Path> generatedFiles, AntlrLibrary library, String grammarName,
            AtomicBoolean cancellation) {
        this.sourceFile = sourceFile;
        this.outputRoot = outputRoot;
        this.packageOutput = packageOutput;
        this.pkg = pkg;
        this.success = res.isSuccess();
        this.thrown = res.getFailure();
        this.attemptedExitCode = res.getExitCode();
        this.generatedFiles = generatedFiles;
        this.library = library;
        this.grammarName = grammarName;
        AntlrInvocationResult ir = res.invocationResult();
        if (ir != null) {
            this.errors = ir.errors();
        } else {
            this.errors = Collections.emptyList();
        }
        this.cancellation = cancellation;
    }

    @Override
    public String toString() {
        return "AntlrSourceGenerationResult{" + "sourceFile=" + sourceFile + ", outputRoot=" + outputRoot
                + ", packageOutput=" + packageOutput + ", pkg=" + pkg + ", success=" + success + ", thrown="
                + thrown + ", attemptedExitCode=" + attemptedExitCode + ", errors=" + errors + ", generatedFiles="
                + generatedFiles + ", library=" + library + ", grammarName=" + grammarName
                + ", cancellation=" + cancellation + '}';
    }

    public AtomicBoolean cancellation() {
        return cancellation;
    }

    public String grammarName() {
        return grammarName;
    }

    public void rethrow() throws Throwable {
        if (thrown.isPresent()) {
            throw thrown.get();
        }
    }

    public boolean isUsable() {
        if (success) {
            for (ParsedAntlrError e : this.errors) {
                if (e.isError()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public Set<Path> generatedFiles() {
        return generatedFiles;
    }

    public AntlrLibrary antlrLibrary() {
        return library;
    }

    public void delete() throws IOException {
        Set<Path> folders = new HashSet<>();
        for (Path gen : generatedFiles) {
            if (Files.exists(gen)) {
                if (!Files.isDirectory(gen)) {
                    folders.add(gen.getParent());
                    Files.delete(gen);
                } else {
                    folders.add(gen);
                }
            }
        }
        // Sort deepest first so we don't try to delete
        // a parent before a child
        List<Path> foldersSorted = new ArrayList<>(folders);
        Collections.sort(foldersSorted, (a, b) -> {
            int a1 = a.getNameCount();
            int b1 = b.getNameCount();
            int result = a1 > b1 ? -1 : a1 == b1 ? 0 : 1;
            return result;
        });
        for (Path fld : folders) {
            if (Files.list(fld).count() == 0) {
                if (Files.exists(fld)) {
                    Files.delete(fld);
                }
            }
        }
    }

    public boolean isSuccess() {
        return success;
    }

    public List<ParsedAntlrError> diagnostics() {
        return errors;
    }

    public Optional<Integer> attemptedExitCode() {
        return attemptedExitCode;
    }

    public Optional<Throwable> thrown() {
        return thrown;
    }

    public Path sourceFile() {
        return sourceFile;
    }

    public Path outputClasspathRoot() {
        return outputRoot;
    }

    public Path outputPackageFolder() {
        return packageOutput;
    }

    public String packageName() {
        return pkg;
    }
}
