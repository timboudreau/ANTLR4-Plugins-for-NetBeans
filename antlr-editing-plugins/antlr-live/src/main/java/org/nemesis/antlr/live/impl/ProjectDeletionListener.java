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
package org.nemesis.antlr.live.impl;

import org.netbeans.api.project.Project;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileUtil;

/**
 * Listens for project folder deletion and runs a runnable if it happens.
 */
final class ProjectDeletionListener extends FileChangeAdapter {

    private final Project project;
    private final Runnable onProjectDeleted;
    private volatile boolean done;

    @SuppressWarnings("LeakingThisInConstructor")
     ProjectDeletionListener(Project project, Runnable onProjectDeleted) {
        this.project = project;
        this.onProjectDeleted = onProjectDeleted;
        project.getProjectDirectory().addFileChangeListener(FileUtil.weakFileChangeListener(this, project.getProjectDirectory()));
    }

    @Override
    public void fileDeleted(FileEvent fe) {
        if (fe.getFile().equals(project.getProjectDirectory()) && !done) {
            done = true;
            onProjectDeleted.run();
        }
    }

}
