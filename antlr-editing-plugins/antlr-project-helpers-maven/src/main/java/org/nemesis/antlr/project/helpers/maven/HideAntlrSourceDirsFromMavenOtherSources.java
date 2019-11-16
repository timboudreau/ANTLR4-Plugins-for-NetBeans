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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.MavenAntlrSourceFactoryPresent;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.spi.nodes.OtherSourcesExclude;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
//@LookupProvider.Registration(projectTypes = @LookupProvider.Registration.ProjectType(id = "org-netbeans-modules-maven")) //XXX what about ant?
//@ServiceProvider(service = OtherSourcesExclude.class, path = "Projects/org-netbeans-modules-maven/Lookup")
public class HideAntlrSourceDirsFromMavenOtherSources implements OtherSourcesExclude {

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
        Path sourceRoot = config.javaSources();
        if (sourceRoot == null) {
            sourceRoot = path.resolve("src/main");
        } else {
            sourceRoot = sourceRoot.getParent();
        }
        Path antlrSources = validate(config.antlrSourceDir());
        Path imports = validate(config.antlrImportDir());
        Set<Path> result;
        if (antlrSources == null && imports == null) {
            result = Collections.emptySet();
        } else if (antlrSources != null && imports == null) {
            result = Collections.singleton(sourceRoot.relativize(antlrSources));
        } else if (antlrSources == null && imports != null) {
            result = Collections.singleton(sourceRoot.relativize(imports));
        } else if (antlrSources != null && imports != null) {
            if (imports.startsWith(antlrSources)) {
                result = Collections.singleton(sourceRoot.relativize(antlrSources));
            } else {
                result = setOf(sourceRoot.relativize(antlrSources),
                        sourceRoot.relativize(imports));
            }
        } else {
            result = Collections.emptySet();
        }
        return result;
    }

    private static Path validate(Path path) {
        if (path == null) {
            return null;
        }
        if (!Files.exists(path)) {
            return null;
        }
        return path;
    }
}
