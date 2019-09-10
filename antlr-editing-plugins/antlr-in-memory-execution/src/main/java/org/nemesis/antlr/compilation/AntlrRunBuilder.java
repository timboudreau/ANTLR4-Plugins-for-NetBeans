package org.nemesis.antlr.compilation;

import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrRunBuilder {

    private final AntlrGeneratorAndCompiler compiler;
    private Supplier<ClassLoader> classloaderSupplier = DefaultClassLoaderSupplier.INSTANCE;

    AntlrRunBuilder(AntlrGeneratorAndCompiler compiler) {
        this.compiler = compiler;
    }

    public static AntlrRunBuilder fromGenerationPhase(AntlrGeneratorAndCompiler comp) {
        return new AntlrRunBuilder(comp);
    }

    public AntlrRunBuilder withParentClassLoader(Supplier<ClassLoader> ldr) {
        this.classloaderSupplier = ldr;
        return this;
    }

    public AntlrRunBuilder isolated() {
        return withParentClassLoader(SystemClassLoaderSupplier.INSTANCE);
    }

    public WithGrammarRunner build(String grammarFileName) {
        return new WithGrammarRunner(grammarFileName, compiler, classloaderSupplier);
    }

    static final class DefaultClassLoaderSupplier implements Supplier<ClassLoader> {

        static final Supplier<ClassLoader> INSTANCE = new DefaultClassLoaderSupplier();

        @Override
        public ClassLoader get() {
            return Thread.currentThread().getContextClassLoader();
        }
    }

    static final class SystemClassLoaderSupplier implements Supplier<ClassLoader> {

        static final Supplier<ClassLoader> INSTANCE = new SystemClassLoaderSupplier();

        @Override
        public ClassLoader get() {
            return ClassLoader.getSystemClassLoader();
        }
    }

}
