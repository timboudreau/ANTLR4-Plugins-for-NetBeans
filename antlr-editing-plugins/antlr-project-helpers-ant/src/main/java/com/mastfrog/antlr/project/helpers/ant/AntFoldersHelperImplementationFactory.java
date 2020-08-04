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
package com.mastfrog.antlr.project.helpers.ant;

import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.OUTPUT_DIR_PROPERTY;
import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.SOURCE_DIR_PROPERTY;
import static com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementation.asAuxProp;
import com.mastfrog.antlr.project.helpers.ant.AntFoldersHelperImplementationFactory.AntFoldersHelperImplementationResult;
import static com.mastfrog.antlr.project.helpers.ant.LambdaUtils.ifNotNull;
import static com.mastfrog.antlr.project.helpers.ant.LambdaUtils.ifNotPresentOr;
import static com.mastfrog.antlr.project.helpers.ant.LambdaUtils.ifNull;
import static com.mastfrog.antlr.project.helpers.ant.LambdaUtils.ifPresent;
import static com.mastfrog.antlr.project.helpers.ant.LambdaUtils.lkp;
import static com.mastfrog.antlr.project.helpers.ant.LambdaUtils.with;
import com.mastfrog.util.cache.Answerer;
import com.mastfrog.util.cache.MapSupplier;
import com.mastfrog.util.cache.TimedCache;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.nemesis.antlr.project.spi.addantlr.AddAntlrCapabilities;
import org.nemesis.antlr.project.spi.FolderLookupStrategyImplementation;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.nemesis.antlr.project.spi.FoldersLookupStrategyImplementationFactory;
import org.nemesis.antlr.project.spi.addantlr.NewAntlrConfigurationInfo;
import org.nemesis.antlr.wrapper.AntlrVersion;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ant.AntBuildExtender;
import org.netbeans.api.project.libraries.LibraryManager;
import org.netbeans.modules.java.api.common.project.PropertyEvaluatorProvider;
import org.netbeans.spi.java.project.classpath.ProjectClassPathExtender;
import org.netbeans.spi.project.AuxiliaryConfiguration;
import org.netbeans.spi.project.AuxiliaryProperties;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = FoldersLookupStrategyImplementationFactory.class, position = 20)
public final class AntFoldersHelperImplementationFactory implements FoldersLookupStrategyImplementationFactory,
        Answerer<Project, AntFoldersHelperImplementationResult, RuntimeException>, MapSupplier<Project> {

    private static final Path BUILD_PATH = Paths.get("build.xml");
    private static final boolean USE_CACHE = !Boolean.getBoolean("ant.folders.nocache");
    private static final String ANTLR_BUILD_EXTENSION_NAME = "antlr-build-impl.xml";
    static final Logger LOG = Logger.getLogger(AntFoldersHelperImplementation.class.getName());

    private final TimedCache<Project, AntFoldersHelperImplementationResult, RuntimeException> implCache
            = TimedCache.<Project, AntFoldersHelperImplementationResult>create(30000, this, this);

    static AntFoldersHelperImplementationFactory INSTANCE;

    public AntFoldersHelperImplementationFactory() {
        // We will be weakly cached by the default lookup, and the cache is
        // useless if it is continually recreated
        INSTANCE = this;
        UpgradableProjectDetector.ensureListening();
    }

    void wipeCache() { // for tests
        implCache.clear();
    }

    @Override
    public void buildFileNames(Collection<? super Path> buildFileNameGatherer) {
        buildFileNameGatherer.add(BUILD_PATH);
    }

    void evict(Project project) {
        implCache.remove(project);
        File file = FileUtil.toFile(project.getProjectDirectory());
        if (file != null) {
            FoldersLookupStrategyImplementationFactory.evict(file.toPath());
        }
    }

    @Override
    public AddAntlrCapabilities addAntlrCapabilities() {
        return FoldersLookupStrategyImplementationFactory.super.addAntlrCapabilities()
                .canChooseAntlrVersion(false).canSetGrammarSourceDir(true)
                .canSetGrammarImportDir(true).canGenerateSkeletonGrammar(true);
    }

    @Override
    public FolderLookupStrategyImplementation create(Project project, FolderQuery initialQuery) {
        if (USE_CACHE) {
            return implCache.get(project).get();
        } else {
            return answer(project).get();
        }
    }

    static Optional<PropertyEvaluator> evaluator(Project project) {
        PropertyEvaluatorProvider prov = project.getLookup().lookup(PropertyEvaluatorProvider.class);
        if (prov != null) {
            return Optional.ofNullable(prov.getPropertyEvaluator());
        } else {
            PropertyEvaluator eval = project.getLookup().lookup(PropertyEvaluator.class);
            if (eval != null) {
                return Optional.of(eval);
            }
        }
        return Optional.empty();
    }

    @Override
    public void collectImplementationNames(Set<? super String> into) {
        into.add("Ant");
    }

    @Override
    public AntFoldersHelperImplementationResult answer(Project project) throws RuntimeException {
        return with("No build extension file under" + project.getProjectDirectory().getName(), () -> project.getProjectDirectory().getFileObject("nbproject/" + ANTLR_BUILD_EXTENSION_NAME))
                .ifPresent("No PropertyEvaluator", evaluator(project), (ignored, eval) -> {
                    boolean hasSourceDir = eval.getProperty(asAuxProp(SOURCE_DIR_PROPERTY)) != null;
                    boolean hasOutputDir = eval.getProperty(asAuxProp(OUTPUT_DIR_PROPERTY)) != null;
                    LambdaUtils.log("  has SourceDir " + asAuxProp(SOURCE_DIR_PROPERTY) + ": " + eval.getProperty(asAuxProp(SOURCE_DIR_PROPERTY)));
                    LambdaUtils.log("  has OutputDir " + asAuxProp(OUTPUT_DIR_PROPERTY) + ": " + eval.getProperty(asAuxProp(OUTPUT_DIR_PROPERTY)));

                    if (hasSourceDir && hasOutputDir) {
                        return new AntFoldersHelperImplementationResult(new AntFoldersHelperImplementation(project));
                    }
                    return null;
                }).or(AntFoldersHelperImplementationResult::empty).get();
    }

    @Override
    public <V> Map<Project, V> get() {
        return Collections.synchronizedMap(new WeakHashMap<>());
    }

    private static String antlrRuntimeLibraryName() {
        return "antlr-" + AntlrVersion.version() + "-runtime";
    }

    private static String antlrCompleteLibraryName() {
        return "antlr-" + AntlrVersion.version() + "-complete";
    }

    private static final String ANTLR_ANT_TASK_LIB_NAME = "antlr-ant-task";

    @Override
    public Function<NewAntlrConfigurationInfo, CompletionStage<Boolean>> antlrSupportAdder(Project project) throws IOException {
//        Optional<AntFoldersHelperImplementationResult> cv = implCache.cachedValue(project);
//        if (cv.isPresent() && cv.get().isViable()) {
//            // Already is an antlr project, abort search
//            return null;
//        }
        FileObject projectDir = project.getProjectDirectory();
        String pd = projectDir.getName();
        return ifNotPresentOr("Have an impl", implCache.cachedValue(project),
                AntFoldersHelperImplementationResult::isNonViable, ()
                -> ifNotNull("no aux config", lkp(project, AuxiliaryConfiguration.class),
                        acon
                        -> ifNotNull("no aux props", lkp(project, AuxiliaryProperties.class),
                                auxProps
                                -> ifNotNull("no ant build ext", lkp(project, AntBuildExtender.class),
                                        extender
                                        -> ifNotNull("no cp extender" + pd, lkp(project, ProjectClassPathExtender.class),
                                                classpathExtender
                                                -> ifNotNull("no nbproject dir " + pd,
                                                        () -> projectDir.getFileObject("nbproject"),
                                                        nbproject
                                                        -> ifNotNull("no project helper " + pd,
                                                                () -> Hacks.helperFor(project),
                                                                antProjectHelper
                                                                -> ifNull("already has antlr-build-impl.xml",
                                                                        () -> nbproject.getFileObject(ANTLR_BUILD_EXTENSION_NAME),
                                                                        () -> ifNotNull("no antlr runtime library",
                                                                                () -> LibraryManager.getDefault().getLibrary(antlrRuntimeLibraryName()),
                                                                                runtimeLib
                                                                                -> ifNotNull("no antlr task library",
                                                                                        () -> LibraryManager.getDefault().getLibrary(ANTLR_ANT_TASK_LIB_NAME),
                                                                                        taskLib
                                                                                        -> ifNotNull("no antlr tool library",
                                                                                                () -> LibraryManager.getDefault().getLibrary(antlrCompleteLibraryName()),
                                                                                                antlrToolLib
                                                                                                -> ifNotNull("no aux config element",
                                                                                                        () -> acon.getConfigurationFragment("data", "http://www.netbeans.org/ns/j2se-project/3", true),
                                                                                                        configurationElement
                                                                                                        -> ifPresent("no property evaluator", evaluator(project),
                                                                                                                eval
                                                                                                                -> newAntlrConfigInfo -> {
                                                                                                                    CompletableFuture<Boolean> fut = new CompletableFuture<>();
                                                                                                                    RequestProcessor.getDefault().submit(
                                                                                                                            new AddAntBasedAntlrSupport(project,
                                                                                                                                    projectDir,
                                                                                                                                    nbproject,
                                                                                                                                    newAntlrConfigInfo,
                                                                                                                                    fut,
                                                                                                                                    extender,
                                                                                                                                    eval,
                                                                                                                                    acon,
                                                                                                                                    auxProps,
                                                                                                                                    runtimeLib,
                                                                                                                                    taskLib,
                                                                                                                                    antProjectHelper,
                                                                                                                                    classpathExtender,
                                                                                                                                    antlrToolLib,
                                                                                                                                    configurationElement
                                                                                                                            ));
                                                                                                                    return fut;
                                                                                                                }
                                                                                                        )))))))))))));
    }

    static final class AntFoldersHelperImplementationResult implements Supplier<AntFoldersHelperImplementation> {

        private final AntFoldersHelperImplementation impl;
        private static final AntFoldersHelperImplementationResult EMPTY = new AntFoldersHelperImplementationResult();

        static AntFoldersHelperImplementationResult empty() {
            return EMPTY;
        }

        public AntFoldersHelperImplementationResult() {
            this(null);
        }

        public AntFoldersHelperImplementationResult(AntFoldersHelperImplementation impl) {
            this.impl = impl;
        }

        public <T> T withImplementation(Function<AntFoldersHelperImplementation, T> function) {
            if (impl == null) {
                return null;
            }
            return function.apply(impl);
        }

        @Override
        public AntFoldersHelperImplementation get() {
            return impl;
        }

        public boolean isViable() {
            return impl != null;
        }

        public boolean isNonViable() {
            return impl == null;
        }

        @Override
        public String toString() {
            return "AntFoldersHelperImplementationResult(" + (impl == null ? "empty" : impl) + ")";
        }
    }
}
