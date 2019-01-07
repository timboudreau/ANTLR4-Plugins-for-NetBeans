/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
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

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.nemesis.antlr.v4.netbeans.v8.project;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;
import static org.nemesis.antlr.v4.netbeans.v8.AntlrFolders.IMPORT;
import static org.nemesis.antlr.v4.netbeans.v8.AntlrFolders.OUTPUT;
import static org.nemesis.antlr.v4.netbeans.v8.AntlrFolders.SOURCE;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.AntBasedProjectHelper;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.MavenProjectHelper;
import org.nemesis.misc.utils.function.Callback;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristic.ancestorNameContains;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristic.ancestorNamed;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristic.ancestorWithChildNamed;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristic.childOfAncestorNamed;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristic.parentIsProjectRoot;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristic.parentOfTestedFile;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristic.tempDir;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristic.withChildPath;
import org.nemesis.antlr.v4.netbeans.v8.util.FileLocator;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;

/**
 *
 * @author Frédéric Yvon Vinet, Tim Boudreau
 */
public enum ProjectType {
    ANT_BASED(
            FileLocator.<AntlrFolders>map(SOURCE).to(
                    antSourceLocator(SOURCE)
                            .stoppingAtProjectRoot()
                            .withFallback(
                                    ancestorNamed("grammar")
                                            .or(childOfAncestorNamed("imports"))
                                            .or(ancestorNameContains("antlr"))
                                            .or(parentIsProjectRoot())
                                            .or(parentOfTestedFile())))
                    .map(IMPORT).to(
                    antSourceLocator(SOURCE)
                            .stoppingAtProjectRoot()
                            .withFallback(
                                    ancestorNamed("imports")
                                            .or(childOfAncestorNamed("imports"))
                                            .or(parentIsProjectRoot())))
                    .map(OUTPUT).to(
                    antSourceLocator(OUTPUT)
                            .stoppingAtProjectRoot()
                            .withFallback(
                                    childOfAncestorNamed("build")
                                            .or(parentIsProjectRoot())
                                            .or(tempDir())))
                    .build()),
    MAVEN_BASED(
            FileLocator.<AntlrFolders>map(SOURCE).to(
                    mavenSourceLocator(SOURCE)
                            .withFallback(
                                    ancestorWithChildNamed("imports")
                                            .or(ancestorNamed("src"))
                                            .or(ancestorNamed("grammar"))
                                            .or(ancestorNamed("antlr4"))
                                            .or(ancestorNamed("antlr"))
                                            .or(parentIsProjectRoot())
                                            .or(parentOfTestedFile())))
                    .map(IMPORT).to(
                    mavenSourceLocator(IMPORT)
                            .withFallback(
                                    ancestorNamed("imports")
                                            .or(childOfAncestorNamed("imports"))
                                            .or(ancestorNamed("imports"))
                                            .or(parentIsProjectRoot())))
                    .map(OUTPUT).to(
                    mavenSourceLocator(OUTPUT)
                            .withFallback(tempDir()))
                    .build()),
    UNDEFINED(
            FileLocator.<AntlrFolders>map(OUTPUT).to(
                    FileLocator.create(heuristicsOnly()).stoppingAtProjectRoot()
                            .withFallback(
                                    childOfAncestorNamed("build")
                                            .or(childOfAncestorNamed("target"))
                                            .or(parentIsProjectRoot()).or(tempDir())))
                    .map(SOURCE).to(
                    FileLocator.create(heuristicsOnly())
                            .stoppingAtProjectRoot().withFallback(
                                    ancestorNamed("source")
                                            .or(ancestorNamed("src"))
                                            .or(ancestorNamed("java"))
                                            .or(ancestorNamed("code"))
                                            .or(parentIsProjectRoot())
                                            .or(parentOfTestedFile())
                            ))
                    .map(IMPORT).to(
                    FileLocator.create(heuristicsOnly())
                            .withFallback(
                                    ancestorNamed("imports")
                                            .or(childOfAncestorNamed("imports"))
                                            .or(parentIsProjectRoot())))
                    .build());
    private final Map<AntlrFolders, FileLocator> locators;

    static final Function<Path, Optional<Path>> heuristicsOnly() {
        return ignored -> Optional.empty();
    }

    ProjectType(Map<AntlrFolders, FileLocator> map) {
        this.locators = map == null ? new EnumMap<>(
                AntlrFolders.class) : Collections.unmodifiableMap(map);
    }

    static FileLocator antSourceLocator(AntlrFolders fld) {
        return FileLocator.create(path -> {
            Project project = FileOwnerQuery.getOwner(path.toUri());
            return lookupAntFolder(fld, project);
        });
    }

    static Optional<Path> lookupAntFolder(AntlrFolders fld, Project project) {
        if (project != null) {
            String antlrSrc = AntBasedProjectHelper.getAntProjectProperty(project, fld.antProjectPropertyName());
            Path projectPath = Paths.get(project.getProjectDirectory().toURI());
            if (antlrSrc != null) {
                Path targetPath = antlrSrc.startsWith("/") ? Paths.get(antlrSrc)
                        : projectPath.resolve(antlrSrc);
                return Optional.of(targetPath);
            }
        }
        return Optional.empty();
    }

    static FileLocator mavenSourceLocator(AntlrFolders fld) {
        return FileLocator.create(mavenAntlrFoldersFunction(fld)).stoppingAtProjectRoot();
    }

    public Optional<Path> sourcePath(Project project, AntlrFolders fld) {
        if (project == null) {
            return Optional.empty();
        }
        switch (this) {
            case MAVEN_BASED:
                return MavenProjectHelper.forProjects().antlrSourceFolderIfExists(project, fld, Callback.optional());
            case ANT_BASED:
                return lookupAntFolder(fld, project);
            case UNDEFINED:
                return withChildPath("src/main/antlr4")
                        .or(withChildPath("grammar"))
                        .or(withChildPath("antlr"))
                        .or(tempDir())
                        .locate(Paths.get(project.getProjectDirectory().toURI()));
            default:
                throw new AssertionError();
        }
    }

    private static Function<Path, Optional<Path>> mavenAntlrFoldersFunction(AntlrFolders fld) {
        return relativeTo -> MavenProjectHelper.forOwningProjectOfFile()
                .antlrSourceFolderIfExists(relativeTo, fld, Callback.optional());
    }

    public Optional<Path> antlrArtifactFolder(Path relativeTo, AntlrFolders folder) {
        return locators.get(folder).locate(relativeTo);
    }
};
