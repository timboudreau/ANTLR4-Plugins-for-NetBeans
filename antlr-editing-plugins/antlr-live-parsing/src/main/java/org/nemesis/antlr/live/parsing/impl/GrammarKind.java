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

import org.nemesis.antlr.ANTLRv4Parser;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
@Messages({
    "LEXER=lexer",
    "PARSER=parser",
    "COMBINED=combined",
    "UNKNOWN=unknown-type"
})
public enum GrammarKind {
    PARSER, LEXER, COMBINED, UNKNOWN;

    @Override
    public String toString() {
        return NbBundle.getMessage(GrammarKind.class, name());
    }

    public boolean isLexerOnly() {
        return this == LEXER;
    }

    public static GrammarKind forTree(ANTLRv4Parser.GrammarFileContext tree) {
        if (tree == null || tree.grammarSpec() == null || tree.grammarSpec().grammarType() == null) {
            return UNKNOWN;
        }
        boolean isLexer = tree.grammarSpec().grammarType().LEXER() != null;
        boolean isParser = tree.grammarSpec().grammarType().PARSER() != null;
        if (isLexer) {
            return LEXER;
        } else if (isParser) {
            return PARSER;
        } else {
            return COMBINED;
        }
    }

}
