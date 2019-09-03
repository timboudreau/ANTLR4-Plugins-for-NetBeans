package org.nemesis.antlr.project.helpers.maven;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.nemesis.antlr.project.spi.FolderLookupStrategyImplementation;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.nemesis.antlr.project.spi.FoldersLookupStrategyImplementationFactory;
import org.nemesis.antlr.project.spi.NewAntlrConfigurationInfo;
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

    @Override
    public FolderLookupStrategyImplementation create(Project project, FolderQuery initialQuery) {
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
}
