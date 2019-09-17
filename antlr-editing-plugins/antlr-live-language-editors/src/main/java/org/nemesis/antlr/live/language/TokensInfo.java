/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
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
            = new TokensInfo(Collections.emptyList(), Collections.emptyMap());
    private final List<AdhocTokenId> tokens;
    private final Map<String, Collection<AdhocTokenId>> categories;
    private final long id = infoIds.getAndIncrement();

    public TokensInfo(List<AdhocTokenId> tokens, Map<String, Collection<AdhocTokenId>> categories) {
        this.tokens = tokens;
        this.categories = categories;
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
