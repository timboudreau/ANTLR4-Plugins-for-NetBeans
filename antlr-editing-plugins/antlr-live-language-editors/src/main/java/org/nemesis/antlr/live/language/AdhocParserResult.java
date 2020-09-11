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
package org.nemesis.antlr.live.language;

import java.util.function.Consumer;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.debug.api.Trackables;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser;

/**
 *
 * @author Tim Boudreau
 */
public final class AdhocParserResult extends Parser.Result {

    private EmbeddedAntlrParserResult embeddedResult;
    private final Consumer<AdhocParserResult> invalidator;

    public AdhocParserResult(Snapshot sn, EmbeddedAntlrParserResult embeddedResult, Consumer<AdhocParserResult> invalidator) {
        super(sn);
        this.embeddedResult = embeddedResult;
        this.invalidator = invalidator;
        Trackables.track(AdhocParserResult.class, this);
    }

    public EmbeddedAntlrParserResult result() {
        return embeddedResult;
    }

    public AntlrProxies.ParseTreeProxy parseTree() {
        return embeddedResult.proxy();
    }

    public String grammarHash() {
        return embeddedResult.grammarTokensHash();
    }

    @Override
    protected void invalidate() {
        invalidator.accept(this);
    }
}
