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
package org.nemesis.antlr.v4.netbeans.v8.maven.source;

import java.io.File;

import java.nio.file.Path;

import java.util.HashSet;
import java.util.Set;
import static org.nemesis.antlr.v4.netbeans.v8.project.helper.MavenProjectHelper.DEFAULT_ANTLR_SOURCE_DIR;

import org.netbeans.api.project.Project;

import org.netbeans.modules.maven.spi.nodes.OtherSourcesExclude;

import org.netbeans.spi.project.ProjectServiceProvider;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * src/main/antlr4 must not be considered as an OtherSourceRoot but as a Java
 * source for enabling automatic indexing that is required for error badging 
 * management so we exclude it when this directory is present in the associated
 * project root directory.
 * 
 * @author  Frédéric Yvon Vinet
 */
@ProjectServiceProvider(
        service={OtherSourcesExclude.class     },
        projectType="org-netbeans-modules-maven")
public class ANTLRMavenSourcesImpl
       implements OtherSourcesExclude {
    
    protected final Project       proj;
    protected final FileObject    projectDirectory;
    
    public ANTLRMavenSourcesImpl(Project project) {
//        System.out.println();
//        System.out.println("ANTLRMavenSourcesImpl.ANTLRMavenSourcesImpl(Project) : begin");
        this.proj = project;
        this.projectDirectory = proj.getProjectDirectory();
//        System.out.println("ANTLRMavenSourcesImpl.ANTLRMavenSourcesImpl(Project) : end");
    }
    
  /**
   * Method coming from MavenSourcesImpl and declared in interface
   * OtherSourcesExclude.
   * 
   * @return 
   */
    @Override
    public Set<Path> excludedFolders() {
//        System.out.println("ANTLRMavenSourcesImpl.excludedFolders() -> Set<Path>");
        HashSet<Path> answer = new HashSet<>();
        File projectDir = FileUtil.toFile(projectDirectory);
        File antlr4Dir = new File(projectDir, DEFAULT_ANTLR_SOURCE_DIR);
        if (antlr4Dir.exists()     &&
            antlr4Dir.isDirectory()  )
            answer.add(antlr4Dir.toPath());
/*
        System.out.println("- excluded folder number=" + answer.size());
        for (Path path : answer) {
            System.out.println("  * excluded folder path=" + path);
        }
*/
        return answer;
    }
}