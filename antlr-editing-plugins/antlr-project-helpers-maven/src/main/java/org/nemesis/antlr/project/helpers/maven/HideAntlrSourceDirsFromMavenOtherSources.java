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

import static com.mastfrog.util.collections.CollectionUtils.setOf;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.MavenAntlrSourceFactoryPresent;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.spi.nodes.OtherSourcesExclude;
import org.netbeans.modules.maven.spi.queries.JavaLikeRootProvider;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
//@LookupProvider.Registration(projectTypes = @LookupProvider.Registration.ProjectType(id = "org-netbeans-modules-maven")) //XXX what about ant?
//@ServiceProvider(service = OtherSourcesExclude.class, path = "Projects/org-netbeans-modules-maven/Lookup")
public class HideAntlrSourceDirsFromMavenOtherSources implements OtherSourcesExclude, JavaLikeRootProvider {

    private final Lookup projectLookup;
    private static final Logger LOG = Logger.getLogger(HideAntlrSourceDirsFromMavenOtherSources.class.getName());

    public HideAntlrSourceDirsFromMavenOtherSources(Lookup projectLookup) {
        this.projectLookup = projectLookup;
    }

    @Override
    public Set<Path> excludedFolders() {
        if (!MavenAntlrSourceFactoryPresent.isPresent()) {
            LOG.log(Level.FINEST, "No MavenAntlrSourceFactoryPresent in lookup - disabled");
            return Collections.emptySet();
        }
        Project project = projectLookup.lookup(Project.class);
        if (project == null) {
            return Collections.emptySet();
        }
        if (true) {
            // Optimization - avoid parsing poms if the cache has the info
            AntlrConfiguration config = AntlrConfiguration.forProject(project);
            if (config == null) {
                return Collections.emptySet();
            }
            File file = FileUtil.toFile(project.getProjectDirectory());
            if (file == null) {
                return Collections.emptySet();
            }
            Path path = file.toPath();
            Path javaSources = config.javaSources();
            if (javaSources == null) {
                javaSources = path.resolve("src/main");
            }
            Path antlrSources = config.antlrSourceDir();
            Path imports = config.antlrImportDir();
            if (antlrSources == null && imports == null) {
                return Collections.emptySet();
            } else if (antlrSources != null && imports == null) {
                return Collections.singleton(javaSources.relativize(antlrSources));
            } else if (antlrSources == null && imports != null) {
                return Collections.singleton(javaSources.relativize(imports));
            } else {
                if (imports.startsWith(antlrSources)) {
                    return Collections.singleton(javaSources.relativize(antlrSources));
                } else {
                    return setOf(javaSources.relativize(antlrSources),
                            javaSources.relativize(imports));
                }
            }
        }
        // XXX AntlrConfiguration should provide this?
        // XXX get the project source parent dir from its configuration
        // - PomFileAnalyzer can already retrieve it

        // Probable the OtherSourcesExclude interface should be implemented
        // in the Antlr module, although it then would need to communicate
        // with this one, because it should not exclude the folders unless
        // this module is installed too
        MavenInfo info = MavenFolderStrategy.infoForProject(project);
        MavenAntlrConfiguration pluginInfo = info.pluginInfo();

        Path javaSources = pluginInfo.javaSources();

        Path sourceRootDir;
        if (javaSources == null) {
            sourceRootDir = info.projectDir().resolve("src/main");
        } else {
            sourceRootDir = javaSources.getParent();
        }

        Path antlrSources = pluginInfo.sourceDir();
        Path sourcesRelative;
        if (antlrSources == null) {
            sourcesRelative = Paths.get("antlr4");
        } else {
            sourcesRelative = sourceRootDir.relativize(antlrSources);
        }

        Path antlrImports = pluginInfo.importDir();
        Path importsRelative;
        if (antlrImports == null) {
            importsRelative = Paths.get("antlr4/imports");
        } else {
            importsRelative = sourceRootDir.relativize(antlrImports);
        }

        if (importsRelative.startsWith(sourcesRelative)) {
            LOG.log(Level.FINER, "Hidden other sources for {0}: {1}",
                    new Object[]{info.projectDir().getFileName(), sourcesRelative});
            return Collections.singleton(sourcesRelative);
        } else {
            LOG.log(Level.FINER, "Hidden other sources for {0}: {1}, {2}",
                    new Object[]{info.projectDir().getFileName(), sourcesRelative, importsRelative});
            return new HashSet<>(Arrays.asList(sourcesRelative, importsRelative));
        }
    }

    @Override
    public String kind() {
        Project project = projectLookup.lookup(Project.class);
        if (project == null) {
            return "antlr4";
        }
        // Optimization - use the cache so we don't parse poms unless
        // we have to
        if (project.getProjectDirectory().getFileObject("pom.xml") != null) {
            if (AntlrConfiguration.isAntlrProject(project)) {
                return "antlr4";
            } else {
                return null;
            }
        }
        MavenInfo info = MavenFolderStrategy.infoForProject(project);
        MavenAntlrConfiguration pluginInfo = info.pluginInfo();
        Path dir = pluginInfo.sourceDir();
        return dir == null ? "antlr4" : dir.getFileName().toString();
    }
}
