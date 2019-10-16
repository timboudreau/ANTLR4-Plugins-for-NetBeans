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
package org.nemesis.antlr.v4.netbeans.v8.tokens.integration;

import org.nemesis.antlr.v4.netbeans.v8.tokens.code.coloring.TokensFileLanguageHierarchy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring.ANTLRv4TokenId;
import org.nemesis.antlr.v4.netbeans.v8.tokens.code.checking.TokensParserAdapter;

import org.netbeans.api.lexer.Language;

import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

import org.netbeans.modules.parsing.spi.Parser;


@LanguageRegistration(mimeType = "text/x-tokens")
public class TokensFileLanguage extends DefaultLanguageConfig {

    @Override
    public Language<ANTLRv4TokenId> getLexerLanguage() {
        return TokensFileLanguageHierarchy.getLanguage();
    }

    @Override
    public String getDisplayName() {
        return "ANTLR v4 tokens";
    }
    
    @Override
    public Parser getParser() {
        return new TokensParserAdapter();
    }
}