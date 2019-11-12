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
package org.nemesis.antlr.file.impl;

import java.util.function.Function;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.data.named.NamedSemanticRegionReference;

public final class ColorKeyFromRegionReference implements Function<NamedSemanticRegionReference<RuleTypes>, String> {

    @Override
    public String apply(NamedSemanticRegionReference<RuleTypes> t) {
        switch (t.kind()) {
            case FRAGMENT:
                return "fragment-reference";
            case LEXER:
                return "lexer-rule-reference";
            case PARSER:
                return "parser-rule-reference";
            default:
                return "default";
        }
    }
}