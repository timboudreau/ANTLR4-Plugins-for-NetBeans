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

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import org.nemesis.antlr.project.spi.NewAntlrConfigurationInfo;
import org.nemesis.antlr.projectupdatenotificaton.ProjectUpdates;
import org.netbeans.api.project.Project;
import org.netbeans.api.queries.FileEncodingQuery;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.w3c.dom.Document;

/**
 *
 * @author Tim Boudreau
 */
final class AddAntlrSupportCallable implements Runnable {

    private final Project project;
    private final FileObject dir;
    private final FileObject pom;
    private static final String DEFAULT_VERSION = "4.7.2";
    private final NewAntlrConfigurationInfo info;
    private final CompletableFuture<Boolean> whenDone;

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
            whenDone.complete(addAntlr());
        } catch (Exception e) {
            whenDone.completeExceptionally(e);
        }
    }

    private boolean addAntlr() throws Exception {
        PomFileAnalyzer ana = new PomFileAnalyzer(FileUtil.toFile(pom));
        if (!ana.antlrPluginInfo().isEmpty()) {
            return false;
        }
        Document doc = ana.addAntlrSupport(info.antlrVersion()
                == null ? DEFAULT_VERSION : info.antlrVersion(),
                info.generateListener(), info.generateVisitor());
        String text = PomFileAnalyzer.stringify(doc);
        Charset charset = FileEncodingQuery.getEncoding(pom);
        try (OutputStream out = pom.getOutputStream()) {
            out.write(text.getBytes(charset));
        }
        FileObject srcFolder = dir.getFileObject("src/main/antlr4");
        if (srcFolder == null) {
            srcFolder = FileUtil.createFolder(dir, "src/main/antlr4");
        }
        FileObject imports = srcFolder.getFileObject("imports");
        if (imports == null) {
            srcFolder.createFolder("imports");
        }
        ProjectUpdates.notifyPathChanged(ana.projectFolder());
        return true;
    }
}
