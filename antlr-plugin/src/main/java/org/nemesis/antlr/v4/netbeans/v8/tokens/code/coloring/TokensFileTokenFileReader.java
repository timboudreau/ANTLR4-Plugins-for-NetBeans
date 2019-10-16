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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring.ANTLRv4TokenId;

import org.openide.util.Exceptions;


/**
 *
 * @author Fredéric Yvon Vinet inspired from James Reid work
 */
public class TokensFileTokenFileReader {
    private final HashMap<String, String>   tokenTypes;
    private final ArrayList<ANTLRv4TokenId> tokens;

    public TokensFileTokenFileReader() {
        tokenTypes = new HashMap<>();
        tokens = new ArrayList<>();
        init();
    }

    /**
     * Initializes the map to include any keywords in the ANTLR language.
     */
    private void init() {
        tokenTypes.put("TOKEN_ID", "tokenIdentifier");
        tokenTypes.put("TOKEN_LITERAL", "tokenLiteral");
        tokenTypes.put("TOKEN_VALUE", "tokenValue");
        tokenTypes.put("EQUAL", "equal");
        
        tokenTypes.put("WS", "");
        
        tokenTypes.put("ERROR", "");
    }

    /**
     * Reads the token file from the ANTLR parser and generates
     * appropriate tokens.
     *
     * @return
     */
    public List<ANTLRv4TokenId> readTokenFile() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inp = classLoader.getResourceAsStream("org/nemesis/antlr/v4/netbeans/v8/tokens/code/coloring/impl/TokensFileLexer.tokens");
        BufferedReader input = new BufferedReader(new InputStreamReader(inp));
        readTokenFile(input);
        return tokens;
    }

    /**
     * Reads in the token file.
     *
     * @param buff
     */
    private void readTokenFile(BufferedReader buff) {
        String line;
        try {
            while ((line = buff.readLine()) != null) {
                String[] splLine = line.split("=");
                String name = splLine[0];
             // If the token name starts with ' character then it is a doublon
             // so it is not added in the token list
                if (!name.startsWith("'")) {
                    int tok = Integer.parseInt(splLine[1].trim());
                    ANTLRv4TokenId id;
                    String tokenCategory = tokenTypes.get(name);
                    if (tokenCategory != null) {
                      //if the value exists, put it in the correct category
                        id = new ANTLRv4TokenId(name, tokenCategory, tok);
                    } else {
                      //if we don't recognize the token, consider it to a separator
                        id = new ANTLRv4TokenId(name, "separator", tok);
                    }
                 // add it into the vector of tokens
                    tokens.add(id);
                }
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}