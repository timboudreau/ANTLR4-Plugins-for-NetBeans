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
package org.nemesis.antlr.v4.netbeans.v8.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristicImpl.ALWAYS_SUCCEED;
import static org.nemesis.antlr.v4.netbeans.v8.util.FileLocationHeuristicImpl.NO_PATH;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
final class ForwardScanHeuristicImpl implements ForwardScanHeuristic {

    private final String path;

    ForwardScanHeuristicImpl(String path) {
        this.path = path;
    }

    @Override
    public Optional<Path> locate(Path relativeTo, Predicate<Path> stoppingAt) {
        assert stoppingAt == null || stoppingAt == ALWAYS_SUCCEED :
                "cannot use stop predicate with a forward search";
        Path test = relativeTo.resolve(path);
        if (Files.exists(test)) {
            return Optional.of(test);
        } else {
            return NO_PATH;
        }
    }

    public FileLocationHeuristic relativeToProjectRoot() {
        return new FileLocationHeuristic() {
            @Override
            public Optional<Path> locate(Path relativeTo, Predicate<Path> stoppingAt) {
                Project project = FileOwnerQuery.getOwner(FileUtil.toFileObject(relativeTo.toFile()));
                if (project == null) {
                    return NO_PATH;
                }
                Path projectPath = FileUtil.toFile(project.getProjectDirectory()).toPath();
                return ForwardScanHeuristicImpl.this.locate(projectPath, stoppingAt);
            }
        };
    }
}
