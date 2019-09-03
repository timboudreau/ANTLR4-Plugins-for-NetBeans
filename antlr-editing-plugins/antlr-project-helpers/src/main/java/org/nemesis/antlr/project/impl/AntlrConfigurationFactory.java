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
}
