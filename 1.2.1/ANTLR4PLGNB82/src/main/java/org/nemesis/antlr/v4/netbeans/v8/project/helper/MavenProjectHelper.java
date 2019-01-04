package org.nemesis.antlr.v4.netbeans.v8.project.helper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.nemesis.antlr.v4.netbeans.v8.util.Callback;
import java.util.Optional;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.modules.maven.api.PluginPropertyUtils;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.nemesis.antlr.v4.netbeans.v8.util.CallbackTransform;
import org.openide.util.Parameters;

/**
 *
 * @author Tim Boudreau
 */
public class MavenProjectHelper<L> {

    private static final String ANTLR_PLUGIN_GROUP_ID = "org.antlr"; //NOI18N
    private static final String ANTLR_PLUGIN_ARTIFACT_ID = "antlr4-maven-plugin"; //NOI18N
    public static final String DEFAULT_ANTLR_SOURCE_DIR = "src/main/antlr4"; //NOI18N
    public static final String DEFAULT_ANTLR_IMPORT_DIR_NAME = DEFAULT_ANTLR_SOURCE_DIR + "/imports"; //NOI18N
    public static final String DEFAULT_ANTLR_OUTPUT_DIR = "target/generated-sources/antlr4"; //NOI18N
    private final CallbackTransform<L, Lookup> xform;

    private MavenProjectHelper(CallbackTransform<L, Lookup> xform) {
        this.xform = xform;
    }

    public static MavenProjectHelper<Lookup> forLookup() {
        return new MavenProjectHelper<>(new IdentityTransform<>());
    }

    public static MavenProjectHelper<Project> forProjects() {
        return new MavenProjectHelper<>(ProjectXform.INSTANCE);
    }

    public static MavenProjectHelper<Path> forOwningProjectOfFile() {
        return new MavenProjectHelper<>(PathXform.INSTANCE);
    }

    static final class IdentityTransform<T> implements CallbackTransform<T, T> {

        public <Ret> Ret xform(T obj, Callback<T, Ret> cb) {
            return cb.succeed(obj);
        }
    }

    static final class PathXform implements CallbackTransform<Path, Lookup> {

        static final PathXform INSTANCE = new PathXform();

        public <Ret> Ret xform(Path relativeTo, Callback<Lookup, Ret> cb) {
            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(relativeTo.toFile()));
            return cb.failIfNull("Does not exist: " + relativeTo, fo, () -> {
                Project project = FileOwnerQuery.getOwner(fo);
                return cb.failIfNull("No owning project for " + relativeTo + " (" + fo + ")", project,
                        () -> cb.call(Optional.empty(), project.getLookup()));
            });
        }
    }

    static final class ProjectXform implements CallbackTransform<Project, Lookup> {

        static final ProjectXform INSTANCE = new ProjectXform();

        public <Ret> Ret xform(Project project, Callback<Lookup, Ret> cb) {
            Parameters.notNull("project", project);
            Parameters.notNull("cb", cb);
            return cb.succeed(project.getLookup());
        }
    }

    public <R> R antlrSourceFolderPath(L l, AntlrFolders folder, Callback<String, R> cb) {
        Parameters.notNull("l for " + xform, l);
        Parameters.notNull("folder", folder);
        Parameters.notNull("cb", cb);
        return xform.transform((Optional<String> err1, Lookup lookup) -> {
            return cb.ifNotFailed(err1, () -> {
                return pluginParameter(lookup, ANTLR_PLUGIN_GROUP_ID, ANTLR_PLUGIN_ARTIFACT_ID,
                        folder.mavenPluginPropertyName(), null, (Optional<String> err, String path) -> {
                    if (err.isPresent()) {
                        // Explicit entry in pom is missing, try the default
                        // path
                        path = folder.mavenPluginDefaultProjectRelativePath();
                    }
                    String finalPath = path;
                    Project project = lookup.lookup(Project.class);
                    return cb.failIfNull(path, project, () -> {
                        String projectPath = project.getProjectDirectory().getPath();
                        return cb.succeed(Paths.get(projectPath, finalPath).toString());
                    });
                });
            });
        }).succeed(l);
    }

    public <R> R antlrSourceFolderIfExists(L l, AntlrFolders folder, Callback<Path, R> cb) {
        Parameters.notNull("l for " + xform, l);
        Parameters.notNull("folder", folder);
        Parameters.notNull("cb", cb);
        return antlrSourceFolderPath(l, folder, (Optional<String> err, String fileName) -> {
            return cb.ifNotFailed(err, () -> {
                Path path = Paths.get(fileName);
                return cb.ifTrue(err, "Does not exist: " + fileName, Files.exists(path), path);

            });
        });
    }

    <R> R project(Path relativeTo, Callback<NbMavenProject, R> cb) {
        FileObject fo = FileUtil.toFileObject(relativeTo.toFile());
        return cb.failIfNull("Does not exist: " + relativeTo, fo, () -> {
            Project project = FileOwnerQuery.getOwner(fo);
            return cb.failIfNull("No project for " + relativeTo, project, () -> {
                if (project == null) {
                    return cb.fail("No project owns " + relativeTo);
                }
                return project(project.getLookup(), cb);
            });
        });
    }

    <R> R project(Lookup lkp, Callback<NbMavenProject, R> callback) {
        return callback.failIfNull("Not a maven project", lkp.lookup(NbMavenProject.class));
    }

    <R> R pluginVersion(Lookup lkp, String groupId, String artifactId, Callback<String, R> cb) {
        return project(lkp, (Optional<String> failure, NbMavenProject prj) -> {
            return cb.ifNoFailure(failure, "No such plugin",
                    () -> PluginPropertyUtils.getPluginVersion(prj.getMavenProject(),
                            groupId, artifactId));
        });
    }

    public <R> R pluginParameter(Lookup lkp, String groupId, String artifactId, String propName, String expressionProperty, Callback<String, R> cb) {
        return project(lkp, (Optional<String> failure, NbMavenProject prj) -> {
            return cb.ifNoFailure(failure, "Property '" + propName + "' does not exist",
                    () -> PluginPropertyUtils.getPluginProperty(prj.getMavenProject(),
                            groupId, artifactId, propName, "antlr4", expressionProperty));
        });
    }
}
