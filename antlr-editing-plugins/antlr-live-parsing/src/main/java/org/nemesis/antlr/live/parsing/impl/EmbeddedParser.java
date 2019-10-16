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

import org.nemesis.antlr.live.parsing.extract.AntlrProxies;

/**
 *
 * @author Tim Boudreau
 */
public interface EmbeddedParser {

    default AntlrProxies.ParseTreeProxy parse(String logName, CharSequence body) throws Exception {
        return parse(logName, body, 0);
    }

    AntlrProxies.ParseTreeProxy parse(String logName, CharSequence body, int ruleNo) throws Exception;

    AntlrProxies.ParseTreeProxy parse(String logName, CharSequence body, String ruleName) throws Exception;

    void onDiscard();

}
