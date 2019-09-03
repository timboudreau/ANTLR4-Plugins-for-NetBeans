/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.project.helpers.maven;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.project.MavenAntlrSourceFactoryPresent;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.spi.nodes.OtherSourcesExclude;
import org.netbeans.modules.maven.spi.queries.JavaLikeRootProvider;
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
        MavenInfo info = MavenFolderStrategy.infoForProject(project);
        MavenAntlrConfiguration pluginInfo = info.pluginInfo();
        Path dir = pluginInfo.sourceDir();
        return dir == null ? "antlr4" : dir.getFileName().toString();
    }
}
