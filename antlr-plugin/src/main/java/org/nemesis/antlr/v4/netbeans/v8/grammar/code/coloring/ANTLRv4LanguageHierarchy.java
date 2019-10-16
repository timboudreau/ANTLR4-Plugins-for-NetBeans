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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;

import org.netbeans.api.lexer.Language;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerRestartInfo;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class ANTLRv4LanguageHierarchy extends LanguageHierarchy<ANTLRv4TokenId> {
    private static List<ANTLRv4TokenId>               tokens;
    private static final Map<Integer, ANTLRv4TokenId> idToToken = new HashMap<>();
    private static final Language<ANTLRv4TokenId>     language
            = new ANTLRv4LanguageHierarchy().language();

    private static void init() {
        ANTLRv4TokenFileReader reader = new ANTLRv4TokenFileReader();
        tokens = reader.readTokenFile();
        for (ANTLRv4TokenId token : tokens) {
            idToToken.put(token.ordinal(), token);
        }
    }

    /**
     * Returns an actual ANTLRv4TokenId from an id. This essentially allows
     * the syntax highlighter to decide the color of specific words.
     * @param id
     * @return
     */
    static synchronized ANTLRv4TokenId getToken(int id) {
        if (idToToken == null) {
            init();
        }
        return idToToken.get(id);
    }

    @Override
    protected synchronized Collection<ANTLRv4TokenId> createTokenIds() {
        if (tokens == null) {
            init();
        }
        return tokens;
    }
    
    public static Language<ANTLRv4TokenId> getLanguage() {
        return language;
    }

    /**
     * Creates a lexer object for use in syntax highlighting.
     *
     * @param info
     * @return
     */
    @Override
    protected synchronized Lexer<ANTLRv4TokenId> createLexer(LexerRestartInfo<ANTLRv4TokenId> info) {
        return new NBANTLRv4Lexer(info);
    }

    @Override
    protected String mimeType() {
        return ANTLR_MIME_TYPE;
    }
}