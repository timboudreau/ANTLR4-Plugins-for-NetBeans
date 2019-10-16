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
package org.nemesis.antlr.v4.netbeans.v8.tokens.code.coloring;

import org.antlr.v4.runtime.Token;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring.ANTLRv4CharStream;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring.ANTLRv4TokenId;
import org.nemesis.antlr.v4.netbeans.v8.tokens.code.coloring.impl.TokensFileLexer;

import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerRestartInfo;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class TokensFileLexerAdaptor implements Lexer<ANTLRv4TokenId> {
    private final LexerRestartInfo<ANTLRv4TokenId> info;
    private final TokensFileLexer                  tokensFileLexer;

    public TokensFileLexerAdaptor(LexerRestartInfo<ANTLRv4TokenId> info) {
//        System.out.print("- TokensFileLexerAdaptor : constructor");
        this.info = info;
        ANTLRv4CharStream charStream = new ANTLRv4CharStream
                                                          (info.input()       ,
                                                           "ANTLRTokensEditor");
        this.tokensFileLexer = new TokensFileLexer(charStream);
    }

    @Override
    public org.netbeans.api.lexer.Token<ANTLRv4TokenId> nextToken() {
        org.netbeans.api.lexer.Token<ANTLRv4TokenId> returnedNBToken;
        Token currentToken = tokensFileLexer.nextToken();
        int currentTokenType = currentToken.getType();
//        System.out.print("- TokensFileLexerAdaptor : currentTokenType=" + currentTokenType);
        if (currentTokenType != TokensFileLexer.EOF) {
            ANTLRv4TokenId currentTokenId = TokensFileLanguageHierarchy.getToken(currentTokenType);
//            System.out.println("  TokensFileLexerAdaptor : currentTokenId=" + currentTokenId.name());
            if (currentTokenId != null)
                returnedNBToken = info.tokenFactory().createToken(currentTokenId);
            else
                returnedNBToken = null;
        } else
            returnedNBToken = null;
        return returnedNBToken;
    }

    @Override
    public Object state() {
        return null;
    }

    @Override
    public void release() {}
}