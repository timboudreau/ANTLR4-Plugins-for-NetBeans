/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.memory.alt;

import static com.mastfrog.util.preconditions.Checks.greaterThanZero;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Objects;

/**
 * Models one alternative in one rule which may have a label.
 *
 * @author Tim Boudreau
 */
public final class RuleAlt implements Comparable<RuleAlt> {

    public final int altIndex;
    public final String label;
    public final String rule;

    RuleAlt(int altIndex, String label, String inRule) {
        this.altIndex = greaterThanZero("altIndex", altIndex);
        this.rule = notNull("inRule", inRule);
        this.label = label;
    }

    public String displayName() {
        return label == null ? Integer.toString(altIndex) : label;
    }

    @Override
    public String toString() {
        return rule + ":" + altIndex + (label == null
                ? ""
                : ("(" + label + ")"));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + this.altIndex;
        hash = 67 * hash + Objects.hashCode(this.rule);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null && getClass() != obj.getClass()) {
            return false;
        }
        final RuleAlt other = (RuleAlt) obj;
        if (this.altIndex != other.altIndex) {
            return false;
        }
        return altIndex == other.altIndex
                && rule.equals(other.rule);
    }

    @Override
    public int compareTo(RuleAlt o) {
        if (o.rule.equals(rule)) {
            return Integer.compare(altIndex, o.altIndex);
        }
        return rule.compareTo(o.rule);
    }
}
