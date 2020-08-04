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
package com.mastfrog.antlr.project.helpers.ant;

import java.nio.charset.Charset;
import java.nio.file.Path;
import org.nemesis.antlr.project.spi.AntlrConfigurationImplementation;

/**
 *
 * @author Tim Boudreau
 */
class AntConfigImpl implements AntlrConfigurationImplementation {

    private final AntFoldersHelperImplementation impl;

    AntConfigImpl(AntFoldersHelperImplementation impl) {
        this.impl = impl;
    }

    @Override
    public boolean atn() {
        return impl.booleanProperty(AntFoldersHelperImplementation.GENERATE_ATN_PROPERTY);
    }

    @Override
    public Path buildDir() {
        return impl.projectFolder(AntFoldersHelperImplementation.J2SE_PROJECT_BUILD_CLASSES, false);
    }

    @Override
    public Charset encoding() {
        return impl.charset();
    }

    @Override
    public String excludePattern() {
        return null;
    }

    @Override
    public boolean forceATN() {
        return impl.booleanProperty(AntFoldersHelperImplementation.FORCE_ATN_PROPERTY);
    }

    @Override
    public Path antlrImportDir() {
        return impl.projectFolder(AntFoldersHelperImplementation.IMPORT_DIR_PROPERTY, false);
    }

    @Override
    public String includePattern() {
        return null;
    }

    @Override
    public boolean listener() {
        return impl.booleanProperty(AntFoldersHelperImplementation.GENERATE_LISTENER_PROPERTY);
    }

    @Override
    public Path antlrOutputDir() {
        return impl.projectFolder(AntFoldersHelperImplementation.OUTPUT_DIR_PROPERTY, false);
    }

    @Override
    public Path antlrSourceDir() {
        return impl.projectFolder(AntFoldersHelperImplementation.SOURCE_DIR_PROPERTY, false);
    }

    @Override
    public boolean visitor() {
        return impl.booleanProperty(AntFoldersHelperImplementation.GENERATE_VISITOR_PROPERTY);
    }

    @Override
    public boolean isGuessedConfig() {
        return false;
    }

    @Override
    public Path buildOutput() {
        return impl.projectFolder(AntFoldersHelperImplementation.J2SE_PROJECT_BUILD_CLASSES, false);
    }

    @Override
    public Path testOutput() {
        return impl.projectFolder(AntFoldersHelperImplementation.J2SE_PROJECT_TEST_BUILD_CLASSES, false);
    }

    @Override
    public Path javaSources() {
        return impl.projectFolder(AntFoldersHelperImplementation.J2SE_PROJECT_MAIN_SOURCES, false);
    }

    @Override
    public Path testSources() {
        return impl.projectFolder(AntFoldersHelperImplementation.J2SE_PROJECT_UNIT_TEST_SOURCES, false);
    }

    @Override
    public String toString() {
        return "AntConfigImpl(" + impl + ")";
    }
}
