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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.io.IOException;
import java.nio.file.Files;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ForeignInvocationEnvironment;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ForeignInvocationResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Runs the provided Antlr library against a grammar file, in an isolated
 * classloader.
 *
 * @author Tim Boudreau
 */
final class GrammarJavaSourceGenerator {

    private final boolean cmdLine;

    private final Set<AntlrRunOption> options = EnumSet.noneOf(AntlrRunOption.class);
    private final Path sourceFile;
    private final String pkg;
    private final Path outputDir;
    private final Path importDir;

    public GrammarJavaSourceGenerator(Path sourceFile, String pkg, Path outputDir, Path importDir, AntlrRunOption... options) {
        this(sourceFile, pkg, outputDir, importDir, false, options);
    }

    public GrammarJavaSourceGenerator(Path sourceFile, String pkg, Path outputDir, Path importDir, boolean captureOutputStreams, AntlrRunOption... options) {
        this(sourceFile, pkg, outputDir, importDir, captureOutputStreams, Arrays.asList(options));
    }

    public GrammarJavaSourceGenerator(Path sourceFile, String pkg, Path outputDir, Path importDir, boolean captureOutputStreams, Collection<AntlrRunOption> options) {
        this.options.addAll(options);
        this.sourceFile = sourceFile;
        this.pkg = pkg;
        this.outputDir = outputDir;
        this.importDir = importDir;
        this.cmdLine = captureOutputStreams;
    }

    public static GrammarJavaSourceGenerator withDefaultOptions(Path sourcefile, String pkg, Path outputDir, Path importDir) {
        return new GrammarJavaSourceGenerator(sourcefile, pkg, outputDir, importDir, AntlrRunOption.GENERATE_LEXER, AntlrRunOption.GENERATE_VISITOR);
    }

    public ForeignInvocationResult<AntlrInvocationResult> run(AntlrLibrary classpath) throws IOException {
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        ForeignInvocationEnvironment env = new ForeignInvocationEnvironment(classpath.getClasspath());
        return env.invoke(new AntlrInvoker(cmdLine, antlrArguments()));
    }

    public static GrammarJavaSourceGeneratorBuilder builder(Path sourceFile) {
        return new GrammarJavaSourceGeneratorBuilder(sourceFile);
    }

    String[] antlrArguments() {
        List<String> result = new ArrayList<>(8 + options.size());
        result.add("-encoding");
        result.add("utf-8");
        if (importDir != null) {
            result.add("-lib");
            result.add(importDir.toString());
        }
        result.add("-o");
        result.add(outputDir.toString());
        if (pkg != null) {
            result.add("-package");
            result.add(pkg);
        }
        for (AntlrRunOption o : options) {
            result.add(o.toString());
        }
        result.add("-message-format");
        result.add("vs2005");
        result.add(sourceFile.toString());
        return result.toArray(new String[result.size()]);
    }

    public String toString() {
        String[] args = antlrArguments();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i]);
            if (i != args.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
