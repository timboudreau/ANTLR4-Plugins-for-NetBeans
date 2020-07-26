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

package org.nemesis.antlr.project.impl;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
final class ApiAgnosticJavaSourceRootDetector implements FileVisitor<Path> {

    private final Path root;
    private final Set<String> packageRoots;
    private final Set<Path> containingJavaFiles;
    private int encounteredAt = Integer.MAX_VALUE;
    private int currentDepth;

    public ApiAgnosticJavaSourceRootDetector(Path root, Set<String> packageRoots, Set<Path> containingJavaFiles) {
        this.root = root;
        this.packageRoots = packageRoots;
        this.containingJavaFiles = containingJavaFiles;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (root.equals(dir.getParent())) {
            packageRoots.add(dir.getFileName().toString());
        }
        currentDepth++;
        if (currentDepth > encounteredAt) {
            currentDepth--;
            return FileVisitResult.SKIP_SUBTREE;
        } else {
            return FileVisitResult.CONTINUE;
        }
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        String nm = file.getFileName().toString();
        if (nm.endsWith(".java") || nm.endsWith(".class") || nm.endsWith(".groovy")) {
            Path rel = root.relativize(file.getParent());
            if (rel != null && rel.toString().length() > 0) {
                containingJavaFiles.add(rel);
                encounteredAt = Math.min(encounteredAt, currentDepth);
                return FileVisitResult.SKIP_SIBLINGS;
            }
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        return FileVisitResult.TERMINATE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        currentDepth--;
        if (exc != null) {
            return FileVisitResult.TERMINATE;
        }
        return FileVisitResult.CONTINUE;
    }

}
