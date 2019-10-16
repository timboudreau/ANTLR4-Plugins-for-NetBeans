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
package org.nemesis.antlr.completion;

import java.util.EnumSet;
import java.util.Set;
import org.nemesis.antlr.completion.annos.InsertAction;
import static org.nemesis.antlr.completion.annos.InsertAction.INSERT_BEFORE_CURRENT_TOKEN;
import org.nemesis.antlr.completion.annos.InsertPolicy;
import org.nemesis.antlr.completion.annos.DeletionPolicy;

/**
 *
 * @author Tim Boudreau
 */
public final class TokenMatch {

    static final TokenMatch DEFAULT_TOKEN_MATCH = new TokenMatch("no-matcher",
            EnumSet.allOf(InsertPolicy.class),
            INSERT_BEFORE_CURRENT_TOKEN, EnumSet.noneOf(DeletionPolicy.class),
            -1, -1);

    private final String name;
    private final Set<InsertPolicy> insertPolicies;
    private final InsertAction action;
    private final Set<DeletionPolicy> deletionPolicies;
    private final int start;
    private final int end;

    public TokenMatch(String name, Set<InsertPolicy> insertPolicy, InsertAction action,
            Set<DeletionPolicy> deletionPolicy,
            int start, int end) {
        this.name = name;
        this.insertPolicies = insertPolicy;
        this.action = action;
        this.deletionPolicies = deletionPolicy;
        this.start = start;
        this.end = end;
    }

    public boolean isRange() {
        boolean result = action.isRange();
        if (!result) {
            for (DeletionPolicy p : deletionPolicies) {
                result = p.isRange();
                if (result) {
                    break;
                }
            }
        }
        return result;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public int length() {
        return end - start;
    }

    public boolean hasPolicy(DeletionPolicy deletion) {
        return deletionPolicies.contains(deletion);
    }

    public boolean hasPolicy(InsertPolicy policy) {
        return insertPolicies.contains(policy);
    }

    public Set<InsertPolicy> insertPolicies() {
        return insertPolicies;
    }

    public InsertAction action() {
        return action;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name + "{" + action + ":" + insertPolicies + "}";
    }
}
