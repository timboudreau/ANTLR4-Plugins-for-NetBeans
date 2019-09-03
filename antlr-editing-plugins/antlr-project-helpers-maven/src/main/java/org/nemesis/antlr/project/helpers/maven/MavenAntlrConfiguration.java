package org.nemesis.antlr.project.helpers.maven;

import java.nio.charset.Charset;
import java.nio.file.Path;
import org.nemesis.antlr.project.spi.AntlrConfigurationImplementation;

/**
 *
 * @author Tim Boudreau
 */
final class MavenAntlrConfiguration implements AntlrConfigurationImplementation {

    private final Path importDir;
    private final Path sourceDir;
    private final Path outputDir;
    private final boolean listener;
    private final boolean visitor;
    private final boolean atn;
    private final boolean forceATN;
    private final String includePattern;
    private final String excludePattern;
    private final Charset encoding;
    private final Path buildDir;
    private final Path buildOutput;
    private final Path testOutput;
    private final Path sources;
    private final Path testSources;

    MavenAntlrConfiguration(Path importDir, Path sourceDir, Path outDir,
            boolean listener, boolean visitor, boolean atn, boolean forceATN,
            String includePattern, String excludePattern, Charset encoding,
            Path buildDir, Path buildOutput, Path testOutput, Path sources, Path testSources) {
        this.importDir = importDir;
        this.sourceDir = sourceDir;
        this.outputDir = outDir;
        this.listener = listener;
        this.visitor = visitor;
        this.atn = atn;
        this.forceATN = forceATN;
        this.includePattern = includePattern;
        this.excludePattern = excludePattern;
        this.encoding = encoding;
        this.buildDir = buildDir;
        this.buildOutput = buildOutput;
        this.testOutput = testOutput;
        this.sources = sources;
        this.testSources = testSources;
    }

    @Override
    public Path buildOutput() {
        return buildOutput;
    }

    @Override
    public Path testOutput() {
        return testOutput;
    }

    @Override
    public Path javaSources() {
        return sources;
    }

    @Override
    public Path testSources() {
        return testSources;
    }

    @Override
    public Path importDir() {
        return importDir;
    }

    @Override
    public Path sourceDir() {
        return sourceDir;
    }

    @Override
    public Path antlrOutputDir() {
        return outputDir;
    }

    @Override
    public Path buildDir() {
        return buildDir;
    }

    @Override
    public Charset encoding() {
        return encoding;
    }

    @Override
    public String includePattern() {
        return includePattern;
    }

    @Override
    public String excludePattern() {
        return excludePattern;
    }

    @Override
    public boolean listener() {
        return listener;
    }

    @Override
    public boolean visitor() {
        return visitor;
    }

    @Override
    public boolean atn() {
        return atn;
    }

    @Override
    public boolean forceATN() {
        return forceATN;
    }

    @Override
    public String toString() {
        return "AntlrPluginInfo{\n" + " importDir\t" + importDir
                + "\n sourceDir\t" + sourceDir + "\n outputDir\t"
                + outputDir + "\n listener\t" + listener + "\n visitor\t"
                + visitor + "\n atn\t" + atn + "\n forceATN\t" + forceATN
                + "\n includePattern\t" + includePattern
                + "\n excludePattern\t" + excludePattern + "\n encoding\t"
                + encoding.name() + "\n buildDir\t" + buildDir + "\n}";
    }

    @Override
    public boolean isGuessedConfig() {
        return false;
    }
}
