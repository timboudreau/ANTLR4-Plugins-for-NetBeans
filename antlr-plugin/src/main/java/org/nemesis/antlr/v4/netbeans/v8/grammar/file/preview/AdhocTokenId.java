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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.nio.file.Path;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyTokenType;
import org.netbeans.api.lexer.TokenId;

/**
 * NetBeans TokenId implementation that wraps tokens extracted by
 * ParserExtractor from running antlr-generated code in isolation. Note that in
 * the generated set of tokens we really use, the 0th in the list is always EOF
 * (it sorts first, with id -1 coming from Antlr), and we always add a __dummy
 * token type which is used in the case that we are trying to lex text that
 * is not the text that was last parsed - meaning we have an out of date
 * syntax tree, and if there is more text than expected, we need *some*
 * token id to use for it.
 *
 * @author Tim Boudreau
 */
public class AdhocTokenId implements TokenId {

    private final AntlrProxies.ProxyTokenType type;
    private final Path grammarPath;
    private final String name;

    public AdhocTokenId(AntlrProxies.ParseTreeProxy proxy, AntlrProxies.ProxyTokenType type) {
        this.type = type;
        this.name = type.name();
        this.grammarPath = proxy.grammarPath();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int ordinal() {
        return type.type + 1;
    }

    /**
     * Attempts some heuristics based on token type name and contents to try to
     * come up with reasonable default categorizations for syntax highlighting.
     *
     * @param type The proxy token
     * @return A category
     */
    static String categorize(ProxyTokenType type) {
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
        if (type.nameContains("number") || type.nameContains("integer") || type.nameContains("float")) {
            return "numbers";
        }
        if (type.nameContains("field")) {
            return "field";
        }
        if (type.nameContains("comment")) {
            return "comment";
        }
        return "default";
    }

    @Override
    public String primaryCategory() {
        return categorize(type);
    }

    public boolean equals(Object o) {
        return o instanceof AdhocTokenId && ((AdhocTokenId) o).ordinal() == ordinal()
                && ((AdhocTokenId) o).grammarPath.equals(grammarPath);
    }

    public int hashCode() {
        return (type.type + 1) * 7 * grammarPath.hashCode();
    }

    @Override
    public String toString() {
        return name() + "(" + ordinal() + " in " + grammarPath.getFileName() + ")";
    }

}
