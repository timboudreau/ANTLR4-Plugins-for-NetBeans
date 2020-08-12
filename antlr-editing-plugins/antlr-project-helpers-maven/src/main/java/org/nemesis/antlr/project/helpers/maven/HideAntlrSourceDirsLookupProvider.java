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

import java.nio.file.Files;
import java.nio.file.Path;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.spi.queries.JavaLikeRootProvider;
import org.netbeans.spi.project.LookupProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * Registers an object which hides the antlr source dirs from the maven module's
 * Other Sources node, because otherwise if there is a contributed Sources
 * instance present, all hell breaks loose.
 *
 * @author Tim Boudreau
 */
@LookupProvider.Registration(projectTypes = @LookupProvider.Registration.ProjectType(id = "org-netbeans-modules-maven")) //XXX what about ant?
public class HideAntlrSourceDirsLookupProvider implements LookupProvider {

    @Override
    public Lookup createAdditionalLookup(Lookup baseContext) {
        return Lookups.fixed(new HideAntlrSourceDirsFromMavenOtherSources(baseContext),
                new HideFolder(baseContext, false), new HideFolder(baseContext, true));
    }

    private final class HideFolder implements JavaLikeRootProvider {

        private static final String NO_VALUE = "~///~";
        private final boolean isImports;
        private final Lookup lookup;

        HideFolder(Lookup lookup, boolean isImports) {
            this.lookup = lookup;
            this.isImports = isImports;
        }

        @Override
        public String kind() {
            Project p = lookup.lookup(Project.class);
            if (p != null) {
                AntlrConfiguration config = AntlrConfiguration.forProject(p);
                if (config != null) {
                    Path path = isImports ? config.antlrImportDir() : config.antlrSourceDir();
                    if (path != null && Files.exists(path)) {
                        return path.getFileName().toString();
                    }
                }
            }
            return NO_VALUE;
        }
    }
}
