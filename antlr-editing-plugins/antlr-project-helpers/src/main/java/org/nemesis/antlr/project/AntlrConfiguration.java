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
package org.nemesis.antlr.project;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.File;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.project.impl.AntlrConfigurationFactory;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.nemesis.antlr.project.spi.NewAntlrConfigurationInfo;
import org.nemesis.antlr.common.cachefile.CacheFileUtils;
import org.nemesis.antlr.projectupdatenotificaton.ProjectUpdates;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Antlr configuration for a project, specifying folders and locations for
 * various things.
 *
 * @author Tim Boudreau
 */
public final class AntlrConfiguration {

    final Path importDir;
    final Path sourceDir;
    final Path outputDir;
    final boolean listener;
    final boolean visitor;
    final boolean atn;
    final boolean forceATN;
    final String includePattern;
    final String excludePattern;
    final Charset encoding;
    final Path buildDir;
    final String createdByStrategy;
    final boolean isGuessedConfig;
    final Path buildOutput;
    final Path testOutput;
    final Path sources;
    final Path testSources;

    private static int MAGIC = 12903;

    AntlrConfiguration(Path importDir, Path sourceDir, Path outDir, boolean listener, boolean visitor,
            boolean atn, boolean forceATN, String includePattern, String excludePattern, Charset encoding,
            Path buildDir, String createdByStrategy, boolean isGuessedConfig,
            Path buildOutput, Path testOutput, Path sources, Path testSources) {
        this.importDir = importDir;
        this.sourceDir = sourceDir;
        this.outputDir = outDir;
        this.listener = listener;
        this.visitor = visitor;
        this.atn = atn;
        this.forceATN = forceATN;
        this.includePattern = includePattern;
        this.excludePattern = excludePattern;
        this.encoding = encoding;
        this.buildDir = buildDir;
        this.createdByStrategy = createdByStrategy;
        this.isGuessedConfig = isGuessedConfig;
        this.buildOutput = buildOutput;
        this.testOutput = testOutput;
        this.sources = sources;
        this.testSources = testSources;
    }

    public boolean isImportDirChildOfSourceDir() {
        if (sourceDir == null || importDir == null) {
            return false;
        }
        return importDir.startsWith(sourceDir);
    }

    public static boolean isAntlrProject(Project project) {
        boolean result = FoldersHelperTrampoline.getDefault().isRecognized(project);
        if (result) {
            AntlrConfiguration config = forProject(project);
            result = config != null && !config.isGuessedConfig
                    && config.sourceDir != null && Files.exists(config.sourceDir);
        }
        return result;
    }

    public static Set<Path> potentialBuildFilePaths(Project project) {
        if (project == null) {
            return Collections.emptySet();
        }
        File f = FileUtil.toFile(project.getProjectDirectory());
        if (f == null) {
            return Collections.emptySet();
        }
        Path projectPath = f.toPath();
        Set<Path> result = new HashSet<>();
        for (Path p : FoldersHelperTrampoline.getDefault().buildFileRelativePaths()) {
            result.add(projectPath.resolve(p));
        }
        return result;
    }

    public Path buildOutput() {
        return buildOutput;
    }

    public Path testOutput() {
        return testOutput;
    }

    public Path javaSources() {
        return sources;
    }

    public Path testSources() {
        return testSources;
    }

    /**
     * If true, this is an approximated config generated by heuristic (and may
     * return null for some folder types).
     *
     * @return Whether or not the configuration is guessed
     */
    public boolean isGuessedConfig() {
        return isGuessedConfig;
    }

    /**
     * Name of the project-type plugin that created this config, for logging
     * purposes.
     *
     * @return A name
     */
    public String createdBy() {
        return createdByStrategy;
    }

    private static final Map<Project, AntlrConfiguration> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean LISTENING;
    private static final Consumer<Path> PROJECT_MOD_LISTENER = path -> {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
        if (fo != null) {
            Project prj;
            try {
                prj = ProjectManager.getDefault().findProject(fo);
                CACHE.remove(prj);
            } catch (IOException ioe) {
                Logger.getLogger(AntlrConfiguration.class.getName()).log(Level.SEVERE, path.toString(), ioe);
            }
        }
    };

    public static AntlrConfiguration forProject(Project project) {
        if (project == null) {
            return null;
        }
        AntlrConfiguration config = null;//CACHE.get(project);
        if (config == null) {
            File file = FileUtil.toFile(project.getProjectDirectory());
            if (file == null) { // virtual file
                return null;
            }
            config = AntlrConfigurationCache.instance().get(file.toPath(), () -> {
                return _forProject(project);
            });
//            CACHE.put(project, config);
        }
        return config;
    }

    private static AntlrConfiguration _forProject(Project project) {
        AntlrConfiguration config = null; // CACHE.get(project);
        if (config == null) {
            config = FoldersLookupStrategy.get(project).antlrConfig();
//            if (config != null) {
//                CACHE.put(project, config);
//            }
            if (!LISTENING) {
                LISTENING = true;
                ProjectUpdates.subscribeToChanges(PROJECT_MOD_LISTENER);
            }
        }
        return config;
    }

    public static AntlrConfiguration forFile(FileObject file) {
        Project project = FileOwnerQuery.getOwner(notNull("file", file));
        if (project != null) {
            return forProject(project);
        }
        return FoldersLookupStrategy.get(FileOwnerQuery.getOwner(file)).antlrConfig();
    }

    public static AntlrConfiguration forFile(Path file) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file.toFile()));
        Project project = fo == null ? null : FileOwnerQuery.getOwner(notNull("file", fo));
        if (project != null) {
            return forProject(project);
        }
        return fo == null ? null : forFile(fo);
    }

    public Path antlrImportDir() {
        return importDir;
    }

    public Path antlrSourceDir() {
        return sourceDir;
    }

    public Path antlrSourceOutputDir() {
        return outputDir;
    }

    public Path buildDir() {
        return buildDir;
    }

    public Charset encoding() {
        return encoding;
    }

    public String includePattern() {
        return includePattern;
    }

    public String excludePattern() {
        return excludePattern;
    }

    public boolean listener() {
        return listener;
    }

    public boolean visitor() {
        return visitor;
    }

    public boolean atn() {
        return atn;
    }

    public boolean forceATN() {
        return forceATN;
    }

    /**
     * Get a function which can add Antlr support to the passed project, if a
     * module has registered an object which can modify the build file
     * appropriately, and if the project does not already have Antlr support.
     *
     * @param prj The project
     * @return A function or null if support cannot be added
     */
    public static Function<NewAntlrConfigurationInfo, CompletionStage<Boolean>> antlrAdder(Project prj) {
        return FoldersHelperTrampoline.antlrAdder(prj);
    }

    @Override
    public String toString() {
        return "AntlrPluginInfo{\n" + " importDir\t" + importDir
                + "\n sourceDir\t" + sourceDir + "\n outputDir\t" + outputDir
                + "\n listener\t" + listener + "\n visitor\t" + visitor
                + "\n atn\t" + atn + "\n forceATN\t" + forceATN
                + "\n includePattern\t" + includePattern
                + "\n excludePattern\t" + excludePattern
                + "\n encoding\t" + encoding.name()
                + "\n buildDir\t" + buildDir
                + "\n createdBy\t" + createdByStrategy
                + "\n sources\t" + sources
                + "\n testSources\t" + testSources
                + "\n buildOutput\t" + buildOutput
                + "\n testOutput\t" + testOutput
                + "\n}";
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.importDir);
        hash = 53 * hash + Objects.hashCode(this.sourceDir);
        hash = 53 * hash + Objects.hashCode(this.outputDir);
        hash = 53 * hash + (this.listener ? 1 : 0);
        hash = 53 * hash + (this.visitor ? 1 : 0);
        hash = 53 * hash + (this.atn ? 1 : 0);
        hash = 53 * hash + (this.forceATN ? 1 : 0);
        hash = 53 * hash + Objects.hashCode(this.includePattern);
        hash = 53 * hash + Objects.hashCode(this.excludePattern);
        hash = 53 * hash + Objects.hashCode(this.encoding);
        hash = 53 * hash + Objects.hashCode(this.buildDir);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AntlrConfiguration other = (AntlrConfiguration) obj;
        if (this.listener != other.listener) {
            return false;
        }
        if (this.visitor != other.visitor) {
            return false;
        }
        if (this.atn != other.atn) {
            return false;
        }
        if (this.forceATN != other.forceATN) {
            return false;
        }
        if (!Objects.equals(this.includePattern, other.includePattern)) {
            return false;
        }
        if (!Objects.equals(this.excludePattern, other.excludePattern)) {
            return false;
        }
        if (!Objects.equals(this.importDir, other.importDir)) {
            return false;
        }
        if (!Objects.equals(this.sourceDir, other.sourceDir)) {
            return false;
        }
        if (!Objects.equals(this.outputDir, other.outputDir)) {
            return false;
        }
        if (!Objects.equals(this.encoding, other.encoding)) {
            return false;
        }
        return Objects.equals(this.buildDir, other.buildDir);
    }

    private static final class DefaultConfigFactory extends AntlrConfigurationFactory {

        @Override
        protected AntlrConfiguration create(Path importDir, Path sourceDir,
                Path outDir, boolean listener, boolean visitor, boolean atn,
                boolean forceATN, String includePattern, String excludePattern,
                Charset encoding, Path buildDir, String createdBy,
                boolean isGuessedConfig, Path buildOutput, Path testOutput, Path sources, Path testSources) {
            return new AntlrConfiguration(importDir, sourceDir, outDir, listener,
                    visitor, atn, forceATN, includePattern, excludePattern,
                    encoding, buildDir, createdBy, isGuessedConfig, buildOutput,
                    testOutput, sourceDir, testSources);
        }
    }

    static {
        FoldersHelperTrampoline.antlrConfigFactory = new DefaultConfigFactory();
    }

    <C extends WritableByteChannel & SeekableByteChannel> int writeTo(C channel) throws IOException {
        boolean[] params = new boolean[]{listener, visitor, atn, forceATN, isGuessedConfig};
        return CacheFileUtils.create(MAGIC).write(channel, w -> {
            w.writeString(createdByStrategy)
                    .writeBooleanArray(params)
                    .writePath(importDir)
                    .writePath(sourceDir)
                    .writePath(outputDir)
                    .writePath(buildDir)
                    .writePath(buildOutput)
                    .writePath(testOutput)
                    .writePath(sources)
                    .writePath(testSources)
                    .writeString(includePattern)
                    .writeString(excludePattern)
                    .writeString(encoding.name());
        });
    }

    static <C extends ReadableByteChannel & SeekableByteChannel> AntlrConfiguration readFrom(C channel) throws IOException {
        return CacheFileUtils.create(MAGIC).read(channel, r -> {
            String createdByStrategy = r.readString();
            if (!FoldersHelperTrampoline.isKnownImplementation(createdByStrategy)) {
                return null;
            }
            boolean[] params = r.readBooleans(5);
            boolean listener = params[0];
            boolean visitor = params[1];
            boolean atn = params[2];
            boolean forceATN = params[3];
            boolean isGuessedConfig = params[4];
            Path importDir = r.readPath();
            Path sourceDir = r.readPath();
            Path outputDir = r.readPath();
            Path buildDir = r.readPath();
            Path buildOutput = r.readPath();
            Path testOutput = r.readPath();
            Path sources = r.readPath();
            Path testSources = r.readPath();

            String includePattern = r.readString();
            String excludePattern = r.readString();
            String encodingName = r.readString();
            Charset encoding = Charset.forName(encodingName);
            AntlrConfiguration result = new AntlrConfiguration(importDir, sourceDir, outputDir,
                    listener, visitor, atn, forceATN, includePattern,
                    excludePattern, encoding, buildDir, createdByStrategy,
                    isGuessedConfig, buildOutput, testOutput, sources,
                    testSources);
            return result;
        });
    }
}
