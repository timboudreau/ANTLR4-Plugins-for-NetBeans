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
package org.nemesis.antlr.live.language;

import java.util.Set;
import static org.nemesis.antlr.live.language.AdhocLanguageHierarchy.DUMMY_TOKEN_ID;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyTokenType;
import org.netbeans.api.lexer.TokenId;

/**
 * NetBeans TokenId implementation that wraps tokens extracted by
 * ParserExtractor from running antlr-generated code in isolation. Note that in
 * the generated set of tokens we really use, the 0th in the list is always EOF
 * (it sorts first, with id -1 coming from Antlr), and we always add a __dummy
 * token type which is used in the case that we are trying to lex text that is
 * not the text that was last parsed - meaning we have an out of date syntax
 * tree, and if there is more text than expected, we need *some* token id to use
 * for it.
 *
 * @author Tim Boudreau
 */
public class AdhocTokenId implements TokenId, Comparable<TokenId> {

    private final AntlrProxies.ProxyTokenType type;
    private final String name;

    public AdhocTokenId(AntlrProxies.ProxyTokenType type, Set<String> usedNames) {
        this.type = type;
        String nm = type.programmaticName();
        String test = nm;
        // A grammar that uses literal tokens, aka '0' instead of a named
        // token rule, can have tokens that have duplicate symbolic names
        int ix = 1;
        while (usedNames.contains(test)) {
            test = nm + "_" + (ix++);
        }
        usedNames.add(test);
        this.name = test;
    }

    public AdhocTokenId(String name, int type) {
        this.type = new ProxyTokenType(type, DUMMY_TOKEN_ID, null, DUMMY_TOKEN_ID);
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int ordinal() {
        return type.type + 1;
    }

    String literalName() {
        return type.literalName;
    }

    String toTokenString() {
        if (type.literalName != null) {
            return "'" + type.literalName + "'";
        }
        return type.symbolicName;
    }

    boolean canBeFlyweight() {
        return type.literalName != null && !type.literalName.isEmpty()
                && !AntlrProxies.ERRONEOUS_TOKEN_NAME.equals(type.literalName);
    }

    /**
     * Attempts some heuristics based on token type name and contents to try to
     * come up with reasonable default categorizations for syntax highlighting.
     *
     * @param type The proxy token
     * @return A category
     */
    @SuppressWarnings("StringEquality")
    public static String categorize(ProxyTokenType type) {
        if (AntlrProxies.ERRONEOUS_TOKEN_NAME== type.name()) {
            return "errors";
        }
        if (type.isDelimiterLike()) {
            return "delimiters";
        }
        if (type.isOperatorLike()) {
            return "operators";
        }
        if (type.isPunctuation()) {
            return "symbols";
        }
        if (type.isKeywordLike()) {
            return "keywords";
        }
        if (type.isSingleCharacter()) {
            return "symbols";
        }
        if (type.nameContains("identifier")) {
            return "identifier";
        }
        if (type.name() != null
                && type.name().toLowerCase().startsWith("id")) {
            return "identifier";
        }
        if (type.nameContains("literal")) {
            return "literal";
        }
        if (type.nameContains("string")) {
            return "string";
        }
        if (type.nameContains("number") 
                || type.nameContains("integer")
                || type.nameContains("float")
                || type.nameContains("int")
                || type.nameContains("num")) {
            return "numbers";
        }
        if (type.nameContains("field")) {
            return "field";
        }
        if (type.nameContains("comment") || type.nameContains("cmt")) {
            return "comment";
        }
        if (type.nameContains("white") || type.nameContains("ws")) {
            return "whitespace";
        }
        return "default";
    }

    @Override
    public String primaryCategory() {
        return categorize(type);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AdhocTokenId && ((AdhocTokenId) o).ordinal() == ordinal()
                && ((AdhocTokenId) o).name.equals(name);
    }

    @Override
    public int hashCode() {
        return (type.type + 1) * 43;
    }

    @Override
    public String toString() {
        return name() + "(" + ordinal() + ")";
    }

    @Override
    public int compareTo(TokenId o) {
        return Integer.compare(ordinal(), o.ordinal());
    }
}
