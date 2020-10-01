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

import com.mastfrog.util.path.UnixPath;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
import org.nemesis.antlr.memory.AntlrGenerator.RerunInterceptor;
import org.nemesis.antlr.memory.util.RandomPackageNames;
import org.nemesis.jfs.JFS;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrGeneratorBuilder<T> {

    boolean genListener = true;
    boolean genVisitor = true;
    boolean longMessages = true;
    boolean genDependencies = true;
    boolean generateATNDot = false;
    boolean generateAll = true;
    boolean log = false;
    boolean forceAtn = false;
    String packageName = RandomPackageNames.newPackageName();
    Charset grammarEncoding = StandardCharsets.UTF_8;
    Supplier<JFS> jfs;
    JavaFileManager.Location grammarSourceInputLocation = StandardLocation.SOURCE_PATH;
    JavaFileManager.Location javaSourceOutputLocation = StandardLocation.SOURCE_OUTPUT;
    UnixPath sourcePath;
    UnixPath importDir;
    Path originalFile;
    String tokensHash;
    private final Function<? super AntlrGeneratorBuilder<T>, T> convert;
    JFSPathHints pathHints = JFSPathHints.NONE;
    RerunInterceptor interceptor;
    boolean analyzeAlts;

    AntlrGeneratorBuilder(Supplier<JFS> jfs, Function<? super AntlrGeneratorBuilder<T>, T> convert) {
        this.jfs = jfs;
        Charset enc = jfs.get().encoding();
        if (enc != null) {
            grammarEncoding = enc;
        }
        this.convert = convert;
    }

    public AntlrGeneratorBuilder<T> requestAlternativesAnalysis() {
        analyzeAlts = true;
        return this;
    }

    public AntlrGeneratorBuilder<T> withInterceptor(RerunInterceptor icept) {
        this.interceptor = icept;
        return this;
    }

    static AntlrGeneratorBuilder<AntlrGenerationResult> fromResult(AntlrGenerationResult res) {
        AntlrGeneratorBuilder<AntlrGenerationResult> result = new AntlrGeneratorBuilder<>(res.jfsSupplier,
                (AntlrGeneratorBuilder<AntlrGenerationResult> bldr) -> bldr.building(res.sourceDir(), res.importDir()));
        result.pathHints = res.hints;
        result.interceptor = res.interceptor;
        return result;
    }

    public AntlrGeneratorBuilder<T> withTokensHash(String tokensHash) {
        this.tokensHash = tokensHash;
        return this;
    }

    public AntlrGeneratorBuilder<T> withOriginalFile(Path originalFile) {
        this.originalFile = originalFile;
        return this;
    }

    public AntlrGeneratorBuilder<T> javaSourceOutputLocation(Location output) {
        this.javaSourceOutputLocation = output;
        return this;
    }

    public AntlrGeneratorBuilder<T> forceATN(boolean forceAtn) {
        this.forceAtn = forceAtn;
        return this;
    }

    public AntlrGeneratorBuilder<T> log(boolean log) {
        this.log = log;
        return this;
    }

    public AntlrGeneratorBuilder<T> generateIntoJavaPackage(String packageName) {
        this.packageName = packageName;
        return this;
    }

    public AntlrGeneratorBuilder<T> generateAllGrammars(boolean val) {
        generateAll = val;
        return this;
    }

    public AntlrGeneratorBuilder<T> grammarSourceInputLocation(Location loc) {
        grammarSourceInputLocation = loc;
        return this;
    }

    public AntlrGeneratorBuilder<T> generateVisitor(boolean val) {
        genVisitor = val;
        return this;
    }

    public AntlrGeneratorBuilder<T> generateDependencies(boolean val) {
        genDependencies = val;
        return this;
    }

    public AntlrGeneratorBuilder<T> generateAtnDot(boolean val) {
        generateATNDot = val;
        return this;
    }

    public AntlrGeneratorBuilder<T> longMessages(boolean val) {
        longMessages = val;
        return this;
    }

    public AntlrGeneratorBuilder<T> generateListener(boolean val) {
        genListener = val;
        return this;
    }

    public AntlrGeneratorBuilder<T> withPathHints(JFSPathHints hints) {
        this.pathHints = hints;
        return this;
    }

    public T building(UnixPath virtualSourcePath) {
        return building(virtualSourcePath, null);
    }

    public T building(UnixPath virtualSourcePath, UnixPath importDir) {
        this.sourcePath = virtualSourcePath;
        this.importDir = importDir;
        if (originalFile == null) {
            throw new IllegalStateException("Original file not set for " + this);
        }
        return convert.apply(this);
    }

    @Override
    public String toString() {
        return "AntlrGeneratorBuilder{" + "genListener=" + genListener
                + ", genVisitor=" + genVisitor + ", longMessages="
                + longMessages + ", genDependencies=" + genDependencies
                + ", generateATNDot=" + generateATNDot + ", generateAll="
                + generateAll + ", log=" + log + ", forceAtn="
                + forceAtn + ", packageName=" + packageName + ", grammarEncoding="
                + grammarEncoding + ", jfs=" + jfs.get() + ", grammarSourceInputLocation="
                + grammarSourceInputLocation + ", javaSourceOutputLocation="
                + javaSourceOutputLocation + ", sourcePath=" + sourcePath + ", importDir="
                + importDir + ", originalFile=" + originalFile + ", tokensHash="
                + tokensHash + ", convert=" + convert + ", hints=" + pathHints + '}';
    }
}
