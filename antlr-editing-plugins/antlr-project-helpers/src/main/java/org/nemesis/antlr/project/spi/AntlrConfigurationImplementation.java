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
package org.nemesis.antlr.project.spi;

import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * Implementation of Antlr configuration, created by reading build files to
 * determine how Antlr is configured, what directories its sources are in, etc.
 *
 * @author Tim Boudreau
 */
public interface AntlrConfigurationImplementation {

    /**
     * Whether the -atn argument is passed to antlr.
     *
     * @return True if it is passed
     */
    boolean atn();

    /**
     * The directory where built files are placed.
     *
     * @return A path
     */
    Path buildDir();

    /**
     * The character encoding used for Antlr sources in the project - either the
     * default or the one set in the configuration of the project.
     *
     * @return A character set
     */
    Charset encoding();

    /**
     * The pattern passed to Antlr for what files NOT to recognize as Antlr
     * files.
     *
     * @return A pattern or null
     */
    String excludePattern();

    /**
     * Whether -forceATN is passed to Antlr.
     *
     * @return True if the parameter is passed
     */
    boolean forceATN();

    /**
     * The Antlr import folder - either the default, or one set in the project's
     * antlr configuration.
     *
     * @return
     */
    Path antlrImportDir();

    /**
     * The pattern passed to Antlr for what files to recognize as Antlr files.
     *
     * @return A pattern or null
     */
    String includePattern();

    /**
     * Return whether or not Antlr in this project is configured to generate
     * listener classes.
     *
     * @return True if listener classes are generated
     */
    boolean listener();

    /**
     * The folder that Antlr sources are generated into for this project, which
     * may be the default or one configured in the project's Antlr
     * configuration.
     *
     * @return A path
     */
    Path antlrOutputDir();

    /**
     * The folder where primary Antlr sources are located in this project,
     * either from the project's Antlr configuration, or the default if none.
     *
     * @return A path
     */
    Path antlrSourceDir();

    /**
     * Determine whether the Antlr configuration for this project is set up to
     * generate visitor classes.
     *
     * @return True if visitor classes are generated when the project is built
     */
    boolean visitor();

    /**
     * Determine whether this configuration was determined by heuristic - some
     * folders that may be Antlr folders were found, sufficient to assume it is
     * an Antlr project for some purposes, but no specific IDE project type
     * support recognized the project.
     *
     * @return True if the configuration was guessed.
     */
    boolean isGuessedConfig();

    /**
     * The build output directory of the project.
     *
     * @return A path
     */
    Path buildOutput();

    /**
     * The test output directory of the project.
     *
     * @return A path
     */
    Path testOutput();

    /**
     * The Java source directory of the project.
     *
     * @return A path
     */
    Path javaSources();

    /**
     * The Java <i>test</i> source directory of the project.
     *
     * @return A path
     */
    Path testSources();
}
