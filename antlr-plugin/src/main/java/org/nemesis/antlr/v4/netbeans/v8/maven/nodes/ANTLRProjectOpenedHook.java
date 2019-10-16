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
package org.nemesis.antlr.v4.netbeans.v8.maven.nodes;

import java.io.File;
import static org.nemesis.antlr.v4.netbeans.v8.project.helper.MavenProjectHelper.DEFAULT_ANTLR_SOURCE_DIR;

import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ProjectServiceProvider;

import org.netbeans.spi.project.ui.ProjectOpenedHook;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Frédéric Yvon Vinet
 */
@ProjectServiceProvider 
    (service = ProjectOpenedHook.class         ,
     projectType = "org-netbeans-modules-maven")
public class ANTLRProjectOpenedHook extends ProjectOpenedHook {
    protected final Project project;

    public ANTLRProjectOpenedHook(Project project) {
        this.project = project;
    }

    @Override
    protected void projectOpened() {
//        System.out.println();
//        System.out.println("ANTLRProjectOpenedHook.projectOpened()");
        FileObject projectRootDir = project.getProjectDirectory();
        FileObject antlr4FO = projectRootDir.getFileObject(DEFAULT_ANTLR_SOURCE_DIR);
     // If the project is not ANTLR aware then antlr4FO will be null
        if (antlr4FO != null) {
            File antlr4Dir = FileUtil.toFile(antlr4FO);
         // src/main/antlr4 exists but we have to check it is a directory
            if (antlr4Dir.isDirectory())
                prepareNodeRenamingTask();
        }
    }


    @Override
    protected void projectClosed() {
//        System.out.println();
//        System.out.println("ANTLRProjectOpenedHook.projectClosed()");
    }
    
    
    private void prepareNodeRenamingTask() {
        Thread r = new Thread(new ANTLR4NodeRenamer(project));
        r.start();
    }
}