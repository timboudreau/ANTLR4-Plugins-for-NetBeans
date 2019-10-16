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

import com.mastfrog.function.TriFunction;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import javax.tools.JavaFileManager;
import org.nemesis.antlr.memory.AntlrGenerator;
import org.nemesis.antlr.memory.AntlrGeneratorBuilder;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.nemesis.jfs.javac.JavacOptions;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrGeneratorAndCompilerBuilder<T> {

    private final JFS jfs;
    private final JFSCompileBuilder compileBuilder;
    private final AntlrGenerator runner;
    private final TriFunction<? super AntlrGeneratorAndCompilerBuilder<T>, ? super JFSCompileBuilder, ? super AntlrGenerator, T> converter;

    AntlrGeneratorAndCompilerBuilder(JFS jfs, AntlrGenerator runner, TriFunction<? super AntlrGeneratorAndCompilerBuilder<T>, ? super JFSCompileBuilder, ? super AntlrGenerator, T> converter) {
        this.jfs = jfs;
        this.runner = runner;
        assert jfs != null : "jfs";
        assert runner != null : "runner";
        compileBuilder = new JFSCompileBuilder(jfs);
        this.converter = converter;
    }

    public static <T> AntlrGeneratorAndCompilerBuilder<T> create(AntlrGenerator runner, TriFunction<? super AntlrGeneratorAndCompilerBuilder<T>, ? super JFSCompileBuilder, ? super AntlrGenerator, T> func) {
        return new AntlrGeneratorAndCompilerBuilder<>(runner.jfs(), runner, func);
    }

    public static AntlrGeneratorBuilder<AntlrGeneratorAndCompilerBuilder<AntlrGeneratorAndCompiler>> compilerBuilder(JFS jfs) {
        return builder(jfs, (AntlrGeneratorAndCompilerBuilder<AntlrGeneratorAndCompiler> bldr, JFSCompileBuilder compileBuilder1, AntlrGenerator runner1)
                -> {
            return new AntlrGeneratorAndCompiler(jfs, compileBuilder1, runner1);
        });
    }

    public static AntlrGeneratorBuilder<AntlrGeneratorAndCompilerBuilder<AntlrRunBuilder>> runnerBuilder(JFS jfs) {
        return builder(jfs, (AntlrGeneratorAndCompilerBuilder<AntlrRunBuilder> bldr, JFSCompileBuilder compileBuilder1, AntlrGenerator runner1) 
                -> new AntlrRunBuilder(new AntlrGeneratorAndCompiler(bldr.jfs, bldr.compileBuilder, bldr.runner)));
    }

    public static <T> AntlrGeneratorBuilder<AntlrGeneratorAndCompilerBuilder<T>> builder(JFS jfs,
            TriFunction<AntlrGeneratorAndCompilerBuilder<T>, JFSCompileBuilder, AntlrGenerator, T> converter) {
        return AntlrGenerator.builder(jfs, bldr -> {
            return new AntlrGeneratorAndCompilerBuilder(jfs, AntlrGenerator.create(bldr), converter);
        });
    }

    public T build() {
        return converter.apply(this, compileBuilder, runner);
    }

    public AntlrGeneratorAndCompilerBuilder<T> addSourceLocation(JavaFileManager.Location location) {
        compileBuilder.addSourceLocation(location);
        return this;
    }

    public AntlrGeneratorAndCompilerBuilder<T> addToClasspath(Class<?> type) {
        compileBuilder.addToClasspath(type);
        return this;
    }

    public AntlrGeneratorAndCompilerBuilder<T> addToClasspath(Path path) {
        compileBuilder.addToClasspath(path);
        return this;
    }

    public AntlrGeneratorAndCompilerBuilder<T> addToClasspath(File file) {
        compileBuilder.addToClasspath(file);
        return this;
    }

    public AntlrGeneratorAndCompilerBuilder<T> addToClasspath(URL url) {
        compileBuilder.addToClasspath(url);
        return this;
    }

    public AntlrGeneratorAndCompilerBuilder<T> clearClasspath() {
        compileBuilder.clearClasspath();
        return this;
    }

    public AntlrGeneratorAndCompilerBuilder<T> sourceAndTargetLevel(int tgt) {
        compileBuilder.sourceAndTargetLevel(tgt);
        return this;
    }

    public AntlrGeneratorAndCompilerBuilder<T> withDebugInfo(JavacOptions.DebugInfo debug) {
        compileBuilder.withDebugInfo(debug);
        return this;
    }
}
