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

import org.netbeans.api.lexer.TokenId;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class ANTLRv4TokenId implements TokenId {
    private final String name;
    private final String primaryCategory;
    private final int    id;

    public ANTLRv4TokenId
        (String name           ,
         String primaryCategory,
         int    id             ) {
        this.name = name;
        this.primaryCategory = primaryCategory;
        this.id = id;
    }

    @Override
    public String primaryCategory() {
        return primaryCategory;
    }

    @Override
    public int ordinal() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    // Equals and HashCode MUST be overridden, or copy / pasting
    // an Antlr file that has been opened gets
    // "IllegalArgumentException: org.nemesis.antlr.v4.netbeans.v8
    //.grammar.code.coloring.ANTLRv4TokenId@4ce62095 does not belong to language text/x-g4"

    @Override
    public int hashCode() {
        return id * 103;
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
        final ANTLRv4TokenId other = (ANTLRv4TokenId) obj;
        return this.id == other.id;
    }


}