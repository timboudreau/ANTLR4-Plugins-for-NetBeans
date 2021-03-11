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

import java.awt.EventQueue;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.spi.FoldersLookupStrategyImplementationFactory;
import org.nemesis.antlr.project.spi.addantlr.NewAntlrConfigurationInfo;
import org.nemesis.antlr.projectupdatenotificaton.ProjectUpdates;
import org.nemesis.antlr.wrapper.AntlrVersion;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.Sources;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.w3c.dom.Document;

/**
 *
 * @author Tim Boudreau
 */
final class AddAntlrSupportCallable implements Runnable {

    private final Project project;
    private final FileObject dir;
    private final FileObject pom;
    private static final String DEFAULT_VERSION = AntlrVersion.version("4.9.1");
    private final NewAntlrConfigurationInfo info;
    private final CompletableFuture<Boolean> whenDone;
    private Runnable postRun;

    AddAntlrSupportCallable(Project project, FileObject dir, FileObject pom, NewAntlrConfigurationInfo info, CompletableFuture<Boolean> whenDone) {
        this.project = project;
        this.dir = dir;
        this.pom = pom;
        this.info = info;
        this.whenDone = whenDone;
    }

    @Override
    public void run() {
        try {
            Boolean result = addAntlr();
            whenDone.complete(result);
            if (result != null && result && postRun != null) {
                postRun.run();
            }
        } catch (Exception | Error e) {
            whenDone.completeExceptionally(e);
        }
    }

    @Messages({
        "addingAntlr=Adding Antlr to project..."
    })
    private boolean addAntlr() throws Exception {
        ProgressHandle progress = ProgressHandle.createHandle(Bundle.addingAntlr());
        progress.setInitialDelay(1);
        progress.start(8, 2000);
        try {
            PomFileAnalyzer ana = new PomFileAnalyzer(FileUtil.toFile(pom));
            Boolean result = ProjectManager.mutex(true, project).writeAccess(new Mutex.ExceptionAction<Boolean>() {
                @Override
                public Boolean run() throws Exception {
                    if (!ana.antlrPluginInfo().isEmpty()) {
                        return false;
                    }
                    progress.progress(2);
                    Document doc = ana.addAntlrSupport(info.antlrVersion()
                            == null ? DEFAULT_VERSION : info.antlrVersion(),
                            info.generateListener(), info.generateVisitor());
                    String text = PomFileAnalyzer.stringify(doc);
                    Charset charset = FileEncodingQuery.getEncoding(pom);
                    progress.progress(3);
                    try (OutputStream out = pom.getOutputStream()) {
                        out.write(text.getBytes(charset));
                    }
                    progress.progress(4);
                    FileObject srcFolder = dir.getFileObject("src/main/antlr4");
                    if (srcFolder == null) {
                        srcFolder = FileUtil.createFolder(dir, "src/main/antlr4");
                    }
                    progress.progress(5);
                    FileObject imports = srcFolder.getFileObject("imports");
                    if (imports == null) {
                        imports = srcFolder.createFolder("imports");
                    }
                    progress.progress(6);
                    if (info.hasJavaPackage()) {
                        FileUtil.createFolder(srcFolder, info.javaPackage().replace('.', File.separatorChar));
                    }
                    progress.progress(7);
                    if (info.skeletonType().isGenerate()) {
                        postRun = info.skeletonType().generate(info.generatedGrammarName(), project, srcFolder, imports, info.javaPackage(), charset);
                    }
                    progress.progress(8);
                    return true;
                }
            });
            boolean res = result == null ? false : result;
            if (res) {
                ProjectUpdates.notifyPathChanged(ana.projectFolder());
                EventQueue.invokeLater(this::kickProjectSourceGroups);
            }
            return res;
        } finally {
            progress.finish();
        }
    }

    void kickProjectSourceGroups() {
        // Force the project source groups to refresh - otherwise the
        // source groups node may not show up until the first build, so it
        // looks like nothing happened
        AntlrConfiguration config = AntlrConfiguration.forProject(project);
        Path path = FileUtil.toFile(dir).toPath();
        FoldersLookupStrategyImplementationFactory.evict(path);
        Sources src = ProjectUtils.getSources(project);
        if (src != null) {
            src.getSourceGroups("java");
            src.getSourceGroups("antlr");
        }
        LogicalViewProvider log = project.getLookup().lookup(LogicalViewProvider.class);
        Node view = log.createLogicalView();
        Children kids = view.getChildren();
        kids.getNodes(true);
    }
}
