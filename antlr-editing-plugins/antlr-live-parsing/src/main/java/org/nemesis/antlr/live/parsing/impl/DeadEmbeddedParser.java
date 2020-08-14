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
package org.nemesis.antlr.live.parsing.impl;

import java.nio.file.Path;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;

/**
 *
 * @author Tim Boudreau
 */
public final class DeadEmbeddedParser implements EmbeddedParser {

    private final Path grammarPath;
    private final String grammarName;

    public DeadEmbeddedParser(Path path, String grammarName) {
        this.grammarPath = path;
        this.grammarName = grammarName;
    }

    @Override
    public AntlrProxies.ParseTreeProxy parse(String logName, CharSequence body, int ruleNo) throws Exception {
        return AntlrProxies.forUnparsed(grammarPath, grammarName, body);
    }

    @Override
    public AntlrProxies.ParseTreeProxy parse(String logName, CharSequence body, String ruleName) throws Exception {
        return AntlrProxies.forUnparsed(grammarPath, grammarName, body);
    }

    @Override
    public void onDiscard() {
        // do nothing
    }

    public String toString() {
        return "DeadEmbeddedParser(" + grammarName + ":" + grammarPath + ")";
    }

    public void clean() {
        // do nothing
    }
}
