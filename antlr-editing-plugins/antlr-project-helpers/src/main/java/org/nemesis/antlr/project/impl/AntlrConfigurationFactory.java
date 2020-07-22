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

import java.nio.charset.Charset;
import java.nio.file.Path;
import org.nemesis.antlr.project.AntlrConfiguration;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AntlrConfigurationFactory {

    protected abstract AntlrConfiguration create(Path importDir, Path sourceDir, Path outDir, boolean listener, boolean visitor,
            boolean atn, boolean forceATN, String includePattern, String excludePattern, Charset encoding,
            Path buildDir, String createdByStrategy, boolean isGuessedConfig,
            Path buildOutput, Path testOutput, Path sources, Path testSources);

    protected abstract boolean evict(Path projectPath);

    protected abstract boolean evict(AntlrConfiguration config);
}
