/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
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
