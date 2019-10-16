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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.openide.util.Exceptions;


/**
 *
 * @author Fred Yvon Vinet inspired from James Reid work
 */
public class ANTLRv4TokenFileReader {
    private final HashMap<String, String> tokenTypes;
    private final ArrayList<ANTLRv4TokenId> tokens;

    public ANTLRv4TokenFileReader() {
        tokenTypes = new HashMap<>();
        tokens = new ArrayList<>();
        init();
    }

    /**
     * Initializes the map to include any keywords in the ANTLR language.
     */
    private void init() {
        tokenTypes.put("TOKEN_ID", "token");
        tokenTypes.put("PARSER_RULE_ID", "parserRuleIdentifier");
        tokenTypes.put("LEXER_CHAR_SET", "lexerCharSet");
        
        tokenTypes.put("DOC_COMMENT", "comment");
        tokenTypes.put("BLOCK_COMMENT", "comment");
        tokenTypes.put("LINE_COMMENT", "comment");
        
        tokenTypes.put("INT", "literal");
        tokenTypes.put("STRING_LITERAL", "literal");
        tokenTypes.put("UNTERMINATED_STRING_LITERAL", "literal");
        
        tokenTypes.put("BEGIN_ARGUMENT", "punctuation");
        tokenTypes.put("BEGIN_ACTION", "punctuation");
        
        tokenTypes.put("OPTIONS", "keyword");
        tokenTypes.put("LANGUAGE", "keyword");
        tokenTypes.put("SUPER_CLASS", "keyword");
        tokenTypes.put("TOKEN_VOCAB", "keyword");
        tokenTypes.put("TOKEN_LABEL_TYPE", "keyword");
        tokenTypes.put("TOKENS", "keyword");
        tokenTypes.put("CHANNELS", "keyword");
        tokenTypes.put("IMPORT", "keyword");
        tokenTypes.put("FRAGMENT", "keyword");
        tokenTypes.put("LEXER", "keyword");
        tokenTypes.put("PARSER", "keyword");
        tokenTypes.put("GRAMMAR", "keyword");
        tokenTypes.put("PROTECTED", "keyword");
        tokenTypes.put("PUBLIC", "keyword");
        tokenTypes.put("PRIVATE", "keyword");
        tokenTypes.put("RETURNS", "keyword");
        tokenTypes.put("LOCALS", "keyword");
        tokenTypes.put("INIT", "keyword");
        tokenTypes.put("AFTER", "keyword");
        tokenTypes.put("THROWS", "keyword");
        tokenTypes.put("CATCH", "keyword");
        tokenTypes.put("FINALLY", "keyword");
        tokenTypes.put("MODE", "keyword");
        tokenTypes.put("LEXCOM_SKIP", "keyword");
        tokenTypes.put("LEXCOM_MORE", "keyword");
        tokenTypes.put("LEXCOM_TYPE", "keyword");
        tokenTypes.put("LEXCOM_CHANNEL", "keyword");
        tokenTypes.put("LEXCOM_MODE", "keyword");
        tokenTypes.put("LEXCOM_PUSHMODE", "keyword");
        tokenTypes.put("LEXCOM_POPMODE", "keyword");
        tokenTypes.put("ASSOC", "keyword");
        tokenTypes.put("RIGHT", "keyword");
        tokenTypes.put("LEFT", "keyword");
        tokenTypes.put("FAIL", "keyword");
        tokenTypes.put("HEADER", "keyword");
        tokenTypes.put("MEMBERS", "keyword");
        
        tokenTypes.put("COLON", "punctuation");
        tokenTypes.put("COLONCOLON", "punctuation");
        tokenTypes.put("COMMA", "punctuation");
        tokenTypes.put("SEMI", "punctuation");
        tokenTypes.put("LPAREN", "punctuation");
        tokenTypes.put("RPAREN", "punctuation");
        tokenTypes.put("LBRACE", "punctuation");
        tokenTypes.put("RBRACE", "punctuation");
        tokenTypes.put("RARROW", "punctuation");
        tokenTypes.put("LT", "punctuation");
        tokenTypes.put("GT", "punctuation");
        tokenTypes.put("ASSIGN", "punctuation");
        tokenTypes.put("QUESTION", "punctuation");
        tokenTypes.put("STAR", "punctuation");
        tokenTypes.put("PLUS_ASSIGN", "punctuation");
        tokenTypes.put("PLUS", "punctuation");
        tokenTypes.put("OR", "punctuation");
        tokenTypes.put("DOLLAR", "punctuation");
        tokenTypes.put("RANGE", "punctuation");
        tokenTypes.put("DOT", "punctuation");
        tokenTypes.put("AT", "punctuation");
        tokenTypes.put("SHARP", "punctuation");
        tokenTypes.put("NOT", "punctuation");
        
        tokenTypes.put("ID", "identifier");
        
        tokenTypes.put("WS", "");
        
        tokenTypes.put("ERRCHAR", "");
        tokenTypes.put("END_ARGUMENT", "punctuation");
        tokenTypes.put("UNTERMINATED_ARGUMENT", "");
        tokenTypes.put("ARGUMENT_CONTENT", "");
        tokenTypes.put("END_ACTION", "punctuation");
        tokenTypes.put("UNTERMINATED_ACTION", "");
        tokenTypes.put("ACTION_CONTENT", "");
        tokenTypes.put("UNTERMINATED_CHAR_SET", "");
    }

    /**
     * Reads the token file from the ANTLR parser and generates
     * appropriate tokens.
     *
     * @return
     */
    public List<ANTLRv4TokenId> readTokenFile() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inp = classLoader.getResourceAsStream("org/nemesis/antlr/v4/netbeans/v8/grammar/code/coloring/impl/ANTLRv4Lexer.tokens");
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