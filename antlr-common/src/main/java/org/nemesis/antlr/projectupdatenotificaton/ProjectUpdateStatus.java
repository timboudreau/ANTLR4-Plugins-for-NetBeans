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
package org.nemesis.antlr.projectupdatenotificaton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Allows for querying if a pom file or one of its known dependencies
 * has been updated since the last time it was queried.
 *
 * @author Tim Boudreau
 */
public final class ProjectUpdateStatus {

    private final Path projectDir;
    private long lastCheck;
    private int lastSize;

    public ProjectUpdateStatus(Path projectDir) {
        this.projectDir = projectDir;
    }

    public synchronized void refresh() {
        _refresh();
    }

    private void _refresh() {
        Set<Path> paths = ProjectUpdates.dependencies(projectDir);
        paths.add(projectDir);
        long result = 0;
        int size = 0;
        for (Path p : paths) {
            if (Files.isDirectory(p)) {
                p = p.resolve("pom.xml");
            }
            if (!Files.exists(p)) {
                continue;
            }
            try {
                result = Math.max(result, Files.getLastModifiedTime(p).toMillis());
                size++;
            } catch (IOException ex) {
                // do nothing, deletions happen
            }
        }
        lastCheck = result;
        lastSize = size;
    }

    public boolean isChanged() {
        long oldTimestamp, newTimestamp;
        int oldSize, newSize;
        synchronized (this) {
            oldTimestamp = lastCheck;
            oldSize = lastSize;
            _refresh();
            newTimestamp = lastCheck;
            newSize = lastSize;
        }
        return oldTimestamp != newTimestamp || oldSize != newSize;
    }
}
