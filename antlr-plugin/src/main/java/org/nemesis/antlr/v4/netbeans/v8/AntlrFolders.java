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
package org.nemesis.antlr.v4.netbeans.v8;

import java.nio.file.Path;
import java.util.Optional;
import org.nemesis.antlr.v4.netbeans.v8.project.ProjectType;
import static org.nemesis.antlr.v4.netbeans.v8.project.helper.ProjectHelper.getProjectType;
import org.netbeans.api.project.Project;

/**
 * Folders which are significant to antlr.
 *
 * @author Tim Boudreau
 */
public enum AntlrFolders {

    SOURCE,
    IMPORT,
    OUTPUT;

    private static final String DEFAULT_MAVEN_ANTLR_SOURCE_DIR = "src/main/antlr4"; //NOI18N
    private static final String DEFAULT_MAVEN_ANTLR_IMPORT_DIR_NAME = DEFAULT_MAVEN_ANTLR_SOURCE_DIR + "/imports";
    private static final String DEFAULT_MAVEN_ANTLR_OUTPUT_DIR = "target/generated-sources/antlr4";

    private static final String ANT_SOURCE_PROJECT_PROPERTY = "antlr.generator.src.dir";
    private static final String ANT_IMPORT_PROJECT_PROPERTY = "antlr.generator.import.dir";
    private static final String ANT_OUTPUT_PROJECT_PROPERTY = "antlr.generator.dest.dir";

    public Optional<Path> getPath(Optional<Project> project, Optional<Path> grammarFilePath) {
        if (!grammarFilePath.isPresent() && project.isPresent()) {
            return getPath(project);
        }
        Optional<Path> result;
        if (grammarFilePath.isPresent() && project.isPresent()) {
            result = getProjectType(project.get()).antlrArtifactFolder(grammarFilePath.get(), this);
        } else if (project.isPresent()) {
            result = getProjectType(project.get()).sourcePath(project.get(), this);
        } else if (grammarFilePath.isPresent()) {
            result = ProjectType.UNDEFINED.antlrArtifactFolder(grammarFilePath.get(), this);
        } else {
            result = Optional.empty();
        }
        return result;
    }

    public Optional<Path> getPath(Optional<Project> project) {
        if (!project.isPresent()) {
            return Optional.empty();
        }
        ProjectType type = getProjectType(project.get());
        return type.sourcePath(project.get(), this);
    }

    public String antProjectPropertyName() {
        switch (this) {
            case SOURCE:
                return ANT_SOURCE_PROJECT_PROPERTY;
            case IMPORT:
                return ANT_IMPORT_PROJECT_PROPERTY;
            case OUTPUT:
                return ANT_OUTPUT_PROJECT_PROPERTY;
            default:
                throw new AssertionError(this);
        }
    }

    public String antDefaultPath() {
        switch (this) {
            case SOURCE:
                return "grammar";
            case IMPORT:
                return "grammar/imports";
            case OUTPUT:
                return "build";
            default:
                throw new AssertionError(this);
        }
    }

    public String mavenPluginDefaultProjectRelativePath() {
        switch (this) {
            case IMPORT:
                return DEFAULT_MAVEN_ANTLR_IMPORT_DIR_NAME;
            case OUTPUT:
                return DEFAULT_MAVEN_ANTLR_OUTPUT_DIR;
            case SOURCE:
                return DEFAULT_MAVEN_ANTLR_SOURCE_DIR;
            default:
                throw new AssertionError(this);
        }
    }

    public String mavenPluginPropertyName() {
        switch (this) {
            case SOURCE:
                return "sourceDirectory";
            case OUTPUT:
                return "outputDirectory";
            case IMPORT:
                return "libDirectory";
            default:
                throw new AssertionError(this);
        }
    }
}
