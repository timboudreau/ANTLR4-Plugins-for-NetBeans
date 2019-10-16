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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract;

import java.util.Optional;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ForeignInvocationResult;

/**
 *
 * @author Tim Boudreau
 */
public final class ParserRunResult {

    private final Optional<Throwable> failure;
    private final boolean success;
    private final Optional<AntlrProxies.ParseTreeProxy> parseTree;

    ParserRunResult(ForeignInvocationResult<AntlrProxies.ParseTreeProxy> res) {
        this(res.getFailure(), Optional.ofNullable(res.invocationResult()), res.isSuccess());
    }

    public ParserRunResult(Optional<Throwable> failure, Optional<AntlrProxies.ParseTreeProxy> parseTree, boolean success) {
        this.failure = failure;
        this.success = success;
        this.parseTree = parseTree;
    }

    public void rethrow() throws Throwable {
        if (failure.isPresent()) {
            throw failure.get();
        }
    }

    public boolean isUsable() {
        return success && !failure.isPresent() && parseTree.isPresent();
    }

    public Optional<Throwable> thrown() {
        return failure;
    }

    public Optional<AntlrProxies.ParseTreeProxy> parseTree() {
        return parseTree;
    }
}
