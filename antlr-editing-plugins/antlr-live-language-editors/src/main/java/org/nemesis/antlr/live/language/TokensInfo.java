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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Tim Boudreau
 */
final class TokensInfo {

    static final AtomicLong infoIds = new AtomicLong();
    static final TokensInfo EMPTY
            = new TokensInfo(Collections.emptyList(), Collections.emptyMap(), "-");
    private final List<AdhocTokenId> tokens;
    private final Map<String, Collection<AdhocTokenId>> categories;
    private final long id = infoIds.getAndIncrement();
    private final String grammarTokensHash;

    public TokensInfo(List<AdhocTokenId> tokens, Map<String, Collection<AdhocTokenId>> categories, String grammarTokensHash) {
        this.tokens = tokens;
        this.categories = categories;
        this.grammarTokensHash = grammarTokensHash;
    }

    public String grammarTokensHash() {
        return grammarTokensHash;
    }

    public boolean isTokensHashUpToDate(String newHash) {
        return Objects.equals(grammarTokensHash, newHash);
    }

    public long id() {
        return id;
    }

    boolean isEmpty() {
        return tokens.isEmpty();
    }

    List<AdhocTokenId> tokens() {
        return tokens;
    }

    Map<String, Collection<AdhocTokenId>> categories() {
        return categories;
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "empty-tokens-info";
        }
        return "TokensInfo(" + Integer.toString(System.identityHashCode(this), 36)
                + " " + tokens.size() + " toks " + categories.size() + " cats)";
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.tokens);
        hash = 23 * hash + Objects.hashCode(this.categories);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TokensInfo other = (TokensInfo) obj;
        if (!Objects.equals(this.tokens, other.tokens)) {
            return false;
        }
        return Objects.equals(this.categories, other.categories);
    }
}
