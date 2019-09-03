package org.nemesis.antlr.project.spi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.netbeans.api.project.Project;

/**
 * Factory registered in the default lookup for looking up specific folders
 * Antlr needs on varying project types. A heuristic fallback implementation
 * exists but should not be relied upon; real implementations can do this more
 * accurately. These are implemented for particular project types, and should
 * respond with null for projects that are not recognized, so that the next
 * registered factory can attempt to reply. A default implementation that uses
 * heuristics is available as a fallback.
 * <p>
 * The factory also specifies a list of possible build file names for this
 * project type, and can optionally provide a function that returns non-null if
 * antlr support is not present but it knows how to modify the build file to add
 * it.
 * </p>
 *
 * @author Tim Boudreau
 */
public interface FoldersLookupStrategyImplementationFactory {

    /**
     * Create a folder lookup strategy implementation, which can look up the
     * folders of various types, such as antlr sources, belonging to a
     * particular project type.
     *
     * @param project A project - <b>may be null</b> for the case of querying
     * about an isolated Antlr file with no corresponding project
     * @param initialQuery The initial query which may be used to store hints
     * that are useful but expensive to compute
     * @return An implementation of the folder lookup strategy for this project,
     * or null if the project is not of the type this implementation factory
     * handles
     */
    FolderLookupStrategyImplementation create(Project project, FolderQuery initialQuery);

    /**
     * Add the relative path to a project root directory for the build files
     * that should be monitored for changes which might need to be reflected in
     * the UI (set of Antlr folders under the project node, etc.).
     *
     * @param buildFileNameGatherer
     */
    default void buildFileNames(Collection<? super Path> buildFileNameGatherer) {
        // do nothing
    }

    /**
     * If a project does not have Antlr support and this strategy knows how to
     * add it, it can return a supplier that will run that work on a background
     * thread.
     *
     * @param project A project
     * @return A supplier or null if the project is not recognized or already
     * has Antlr support
     * @throws IOException If something goes wrong
     */
    default Function<NewAntlrConfigurationInfo, CompletionStage<Boolean>> antlrSupportAdder(Project project) throws IOException {
        return null;
    }
}
