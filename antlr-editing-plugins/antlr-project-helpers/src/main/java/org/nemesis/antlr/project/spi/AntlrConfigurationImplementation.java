package org.nemesis.antlr.project.spi;

import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 *
 * @author Tim Boudreau
 */
public interface AntlrConfigurationImplementation {

    boolean atn();

    Path buildDir();

    Charset encoding();

    String excludePattern();

    boolean forceATN();

    Path importDir();

    String includePattern();

    boolean listener();

    Path antlrOutputDir();

    Path sourceDir();

    boolean visitor();

    boolean isGuessedConfig();

    Path buildOutput();

    Path testOutput();

    Path javaSources();

    Path testSources();
}
