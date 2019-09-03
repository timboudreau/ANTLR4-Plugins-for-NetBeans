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
