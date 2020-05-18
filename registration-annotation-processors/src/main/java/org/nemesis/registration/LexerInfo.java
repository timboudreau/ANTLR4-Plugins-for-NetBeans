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
package org.nemesis.registration;

/**
 * Abstraction for LexerProxy allowing code to be generated for token
 * ids whether or not the lexer is specified.
 *
 * @author Tim Boudreau
 */
public interface LexerInfo {

    String lexerClassFqn();

    String lexerClassSimple();

    String tokenFieldReference(int tokenId);

    String tokenFieldReference(String tokenName);

    Integer tokenIndex(String name);

    String tokenName(int type);

    public static LexerInfo UNKNOWN = new LexerInfo() {
        @Override
        public String lexerClassFqn() {
            return null;
        }

        @Override
        public String lexerClassSimple() {
            return null;
        }

        @Override
        public String tokenFieldReference(int tokenId) {
            return Integer.toString(tokenId);
        }

        @Override
        public String tokenFieldReference(String tokenName) {
            return tokenName;
        }

        @Override
        public Integer tokenIndex(String name) {
            return Integer.parseInt(name);
        }

        @Override
        public String tokenName(int type) {
            return Integer.toString(type);
        }
    };
}
