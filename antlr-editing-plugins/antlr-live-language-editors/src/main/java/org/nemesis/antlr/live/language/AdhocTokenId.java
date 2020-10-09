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

    public String toTokenString() {
        if (type.literalName != null) {
            return "'" + type.literalName + "'";
        }
        return type.symbolicName;
    }

    boolean canBeFlyweight() {
        return type.literalName != null && !type.literalName.isEmpty()
                && !AntlrProxies.ERRONEOUS_TOKEN_NAME.equals(type.literalName);
    }

    @Override
    public String primaryCategory() {
        return type.category();
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
