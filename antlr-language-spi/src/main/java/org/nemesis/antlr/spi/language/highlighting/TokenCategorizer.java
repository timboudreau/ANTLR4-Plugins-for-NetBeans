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
/**
 * Copyright (C) 2018 Tim Boudreau
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.nemesis.antlr.spi.language.highlighting;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface TokenCategorizer {

    /**
     * Determine a category, used to colorize matching tokens,
     * for a given Antlr token type (these are constant ints on your
     * generated lexer), using parameters returned by the Vocabulary
     * of that lexer.
     *
     * @param tokenType The integer token type
     * @param displayName The display name, as returned by the Vocabulary - may be null
     * @param symbolicName The symbolic name, as returned by the Vocabulary
     * @param literalName The literal name, if this is a token whose contents is
     * fixed, such as plus symbols or language keywords
     * @return A category name ("default") is the do-nothing answer
     */
    String categoryFor(int tokenType, String displayName, String symbolicName, String literalName);

    /**
     * Does minimal best-effort categorizing based on names and contents,
     * based on some naming heuristics (such as the presence of the string
     * "comment" in the symbolic name) and literal name heuristics (1- and
     * 2- character non-digit, non-whitespace, non-alphabetic literal names
     * are assumed to be an operator).
     * <p>
     * This will do at least something for many languages, to allow testing of
     * a module without first deciding on a categorization scheme.
     * </p>
     *
     * @return
     */
    public static TokenCategorizer heuristicCategorizer() {
        return (tokenType, displayName, symbolicName, literalName) -> {
            if (literalName != null) {
                if (literalName.length() == 1 || literalName.length() == 2) {
                    if ("//".equals(literalName)) {
                        return "comment";
                    }
                    if (literalName.length() == 1) {
                        switch (literalName.charAt(0)) {
                            case ';':
                            case '[':
                            case ']':
                            case '{':
                            case '}':
                            case '(':
                            case ')':
                            case ',':
                            case '.':
                                return "separator";
                        }
                    }
                    boolean allLetters = true;
                    for (int i = 0; i < literalName.length(); i++) {
                        char c = literalName.charAt(i);
                        allLetters &= Character.isAlphabetic(c);
                        if (!Character.isWhitespace(c) && !Character.isDigit(c) && !Character.isAlphabetic(c)) {
                            return "operator";
                        }
                    }
                    if (allLetters) {
                        return "keyword";
                    }
                }
            }
            String nm = symbolicName == null ? displayName : symbolicName;
            if (nm != null) {
                nm = nm.toLowerCase();
                if (nm.contains("comment") || nm.contains("cmt")) {
                    return "comment";
                }
                if (nm.contains("ident") || nm.contains("name")) {
                    return "identifier";
                }
                if (nm.contains("keyword") || nm.startsWith("k_")) {
                    return "keyword";
                }
                if (nm.contains("whitespace") || nm.startsWith("ws_") || nm.endsWith("_ws")) {
                    return "whitespace";
                }
            }
            return "default";
        };
    }
}
