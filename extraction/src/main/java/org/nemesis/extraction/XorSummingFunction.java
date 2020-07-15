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

import java.util.Objects;
import org.antlr.v4.runtime.Token;

/**
 *
 * @author Tim Boudreau
 */
final class XorSummingFunction implements SummingFunction {

    private final SummingFunction b;
    private final SummingFunction a;

    XorSummingFunction(SummingFunction a, SummingFunction b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public long updateSum(long previousValue, int offset, Token token) {
        return a.updateSum(previousValue, offset, token) ^ b.updateSum(previousValue, offset, token);
    }

    @Override
    public String toString() {
        return "XorSummingFunction(" + a + " ^ " + b + ")";
    }

    @Override
    public void hashInto(Hasher hasher) {
        a.hashInto(hasher);
        hasher.writeInt(120910299);
        b.hashInto(hasher);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.b);
        hash = 79 * hash + Objects.hashCode(this.a);
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
        final XorSummingFunction other = (XorSummingFunction) obj;
        if (!Objects.equals(this.b, other.b)) {
            return false;
        }
        return Objects.equals(this.a, other.a);
    }
}
