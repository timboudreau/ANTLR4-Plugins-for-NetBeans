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
package org.nemesis.antlr.file.impl;

import com.mastfrog.abstractions.Named;
import com.mastfrog.range.IntRange;
import java.util.Objects;
import org.nemesis.antlr.common.extractiontypes.GrammarType;

/**
 * Represents a grammar declaration and captures the position of the name.
 *
 * @author Tim Boudreau
 */
public final class GrammarDeclaration implements IntRange<GrammarDeclaration>, Named {

    private final GrammarType grammarType;
    private final String grammarName;
    private final int grammarNameStart;
    private final int grammarNameEnd;

    public GrammarDeclaration(GrammarType grammarType, String grammarName, int grammarNameStart, int grammarNameEnd) {
        this.grammarType = grammarType;
        this.grammarName = grammarName;
        this.grammarNameStart = grammarNameStart;
        this.grammarNameEnd = grammarNameEnd;
    }

    public String name() {
        return grammarName;
    }

    @Override
    public int start() {
        return grammarNameStart;
    }

    @Override
    public int size() {
        return grammarNameEnd - grammarNameStart;
    }

    @Override
    public GrammarDeclaration newRange(int start, int size) {
        return new GrammarDeclaration(grammarType, grammarName, start, start + size);
    }

    @Override
    public GrammarDeclaration newRange(long start, long size) {
        return new GrammarDeclaration(grammarType, grammarName, (int) start, (int) (start + size));
    }

    @Override
    public String toString() {
        return grammarType + " grammar " + grammarName + "@" + grammarNameStart + ":" + grammarNameEnd;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.grammarType);
        hash = 47 * hash + Objects.hashCode(this.grammarName);
        hash = 47 * hash + this.grammarNameStart;
        hash = 47 * hash + this.grammarNameEnd;
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
        final GrammarDeclaration other = (GrammarDeclaration) obj;
        if (this.grammarNameStart != other.grammarNameStart) {
            return false;
        }
        if (this.grammarNameEnd != other.grammarNameEnd) {
            return false;
        }
        if (!Objects.equals(this.grammarName, other.grammarName)) {
            return false;
        }
        return this.grammarType == other.grammarType;
    }
}
