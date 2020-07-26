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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.nemesis.jfs.result.ProcessingResult;
import org.nemesis.jfs.result.UpToDateness;

/**
 *
 * @author Tim Boudreau
 */
final class GenerationAndCompilationResult implements ProcessingResult {

    final ByteArrayOutputStream output;
    final AntlrGenerationAndCompilationResult genAndCompileResult;

    GenerationAndCompilationResult(ByteArrayOutputStream output, AntlrGenerationAndCompilationResult genAndCompileResult) {
        this.output = output;
        this.genAndCompileResult = genAndCompileResult;
    }

    public String output() {
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    public final AntlrGenerationAndCompilationResult genResult() {
        return genAndCompileResult;
    }

    @Override
    public boolean isUsable() {
        return genAndCompileResult.isUsable();
    }

    @Override
    public Optional<Throwable> thrown() {
        return genAndCompileResult.thrown();
    }

    @Override
    public UpToDateness currentStatus() {
        return genAndCompileResult.currentStatus();
    }

}
