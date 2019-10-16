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
