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
package org.nemesis.antlr.project.helpers.maven;

import com.mastfrog.util.cache.TimedCache;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.nemesis.antlr.project.spi.FolderLookupStrategyImplementation;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.nemesis.antlr.project.spi.FoldersLookupStrategyImplementationFactory;
import org.nemesis.antlr.project.spi.addantlr.AddAntlrCapabilities;
import org.nemesis.antlr.project.spi.addantlr.NewAntlrConfigurationInfo;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = FoldersLookupStrategyImplementationFactory.class, position = 10)
public final class MavenFolderStrategyFactory implements FoldersLookupStrategyImplementationFactory {
    private final TimedCache<Project, Boolean, RuntimeException> answerCache =
            TimedCache.create(24000, (Project request) -> {
                if (request.getProjectDirectory().getFileObject("pom.xml") == null) {
                    return false;
                }
                return true;
    });

    @Override
    public FolderLookupStrategyImplementation create(Project project, FolderQuery initialQuery) {
        if (project == null && initialQuery.hasProject()) {
            project = initialQuery.project();
        }
        if (project == null) {
            return null;
        }
        if (!answerCache.get(project)) {
            return null;
        }
        if (project == null || project.getProjectDirectory()
                .getFileObject("pom.xml") == null) {
            return null;
        }
        return new MavenFolderStrategy(project, initialQuery.project(project));
    }

    @Override
    public void buildFileNames(Collection<? super Path> buildFileNameGatherer) {
        // HeuristicFoldersHelperImplementation adds this too, but don't assume
        // it actually should and always will
        buildFileNameGatherer.add(Paths.get("pom.xml"));
    }

    @Override
    public AddAntlrCapabilities addAntlrCapabilities() {
        return FoldersLookupStrategyImplementationFactory.super.addAntlrCapabilities().canGenerateSkeletonGrammar(true);
    }

    @Override
    public Function<NewAntlrConfigurationInfo, CompletionStage<Boolean>> antlrSupportAdder(Project project) throws IOException {
        FileObject projectDir = project.getProjectDirectory();
        FileObject pom = projectDir.getFileObject("pom.xml");
        if (pom == null) {
            return null;
        }
        if (pom.asText().contains("antlr4-maven-plugin")) {
            return null;
        }
        return (info) -> {
            CompletableFuture<Boolean> fut = new CompletableFuture<>();
            RequestProcessor.getDefault().submit(
                    new AddAntlrSupportCallable(project, projectDir, pom, info, fut));
            return fut;
        };
    }

    @Override
    public void collectImplementationNames(Set<? super String> into) {
        into.add(MavenFolderStrategy.MAVEN);
    }
}
