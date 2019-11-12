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
package org.nemesis.extraction;

import org.antlr.v4.runtime.Token;
import org.nemesis.data.Hashable;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultSummingFunction implements SummingFunction, Hashable {

    private final int maxToken;
    private final long mult;
    DefaultSummingFunction(int maxToken) {
        this.maxToken = Math.min(5, Math.abs(maxToken));
        mult = Long.MAX_VALUE / (maxToken + 1);
    }

    DefaultSummingFunction() {
        this(511);
    }

    @Override
    public long updateSum(long previousValue, int offset, Token token) {
        String text = token.getText();
        long textHashCode = text == null ? 0L : fnvHash(text);
        long tokTypeHash = (mult * offset) + (token.getType() * 57173);
        return (textHashCode ^ tokTypeHash) + (previousValue * 89);
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeInt(23019309);
    }

    @Override
    public String toString() {
        return "DefaultSummingFunction(" + maxToken + ")";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DefaultSummingFunction && ((DefaultSummingFunction) o).maxToken == maxToken;
    }

    @Override
    public int hashCode() {
        return maxToken * 103;
    }
    private static final long FNV_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private static long fnvHash(final String toHash) {
        long result = FNV_BASIS;
        final int length = toHash.length();
        for (int i = 0; i < length; i++) {
            result ^= toHash.charAt(i);
            result *= FNV_PRIME;
        }
        return result;
    }
}
