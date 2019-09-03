package org.nemesis.antlr.compilation;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrRunBuilder {

    private boolean useClassloaderIsolation;
    private final AntlrGeneratorAndCompiler compiler;

    AntlrRunBuilder(AntlrGeneratorAndCompiler compiler) {
        this.compiler = compiler;
    }

    public static AntlrRunBuilder fromGenerationPhase(AntlrGeneratorAndCompiler comp) {
        return new AntlrRunBuilder(comp);
    }

    public AntlrRunBuilder isolated() {
        useClassloaderIsolation = true;
        return this;
    }

    public WithGrammarRunner build(String grammarFileName) {
        return new WithGrammarRunner(grammarFileName, compiler, useClassloaderIsolation);
    }


}
