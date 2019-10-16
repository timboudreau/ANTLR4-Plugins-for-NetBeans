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
package org.nemesis.antlr.live.parsing;

import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;

/**
 *
 * @author Tim Boudreau
 */
public final class EmbeddedAntlrParserResult {

    private final ParseTreeProxy proxy;
    private final GrammarRunResult<?> runResult;
    private final String grammarTokensHash;

    EmbeddedAntlrParserResult(ParseTreeProxy proxy, GrammarRunResult<?> runResult, String grammarTokensHash) {
        this.proxy = proxy;
        this.runResult = runResult;
        this.grammarTokensHash = grammarTokensHash;
    }

    public ParseTreeProxy proxy() {
        return proxy;
    }

    public GrammarRunResult<?> runResult() {
        return runResult;
    }

    public String grammarTokensHash() {
        return grammarTokensHash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(
                EmbeddedAntlrParserResult.class.getSimpleName())
                .append('(')
                .append(grammarTokensHash)
                .append(", ").append(proxy.loggingInfo())
                .append(", ").append(runResult)
                .append(", text='");

        return EmbeddedAntlrParserImpl.truncated(
                proxy.text() == null ? "--null--" : proxy.text(),
                sb, 20).append(")").toString();
    }
}
