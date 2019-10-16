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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.Collection;
import java.util.Set;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.ParserFactory;
import org.openide.util.WeakSet;

/**
 *
 * @author Tim Boudreau
 */
class AdhocParserFactory extends ParserFactory {

    private final AntlrProxies.ParseTreeProxy proxy;
    private final Set<AdhocParser> liveParsers = new WeakSet<>();
    private final GenerateBuildAndRunGrammarResult buildResult;

    AdhocParserFactory(GenerateBuildAndRunGrammarResult buildResult, AntlrProxies.ParseTreeProxy proxy) {
        this.proxy = proxy;
        this.buildResult = buildResult;
    }

    void updated(GenerateBuildAndRunGrammarResult buildResult, AntlrProxies.ParseTreeProxy proxy) {
        for (AdhocParser p : liveParsers) {
            p.updated(buildResult, proxy);
        }
    }

    @Override
    public Parser createParser(Collection<Snapshot> clctn) {
        AdhocParser result = new AdhocParser(buildResult, proxy);
        liveParsers.add(result);
        return result;
    }

    public AdhocParser newParser() {
        return new AdhocParser(buildResult, proxy);
    }

}
