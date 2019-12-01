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
package org.nemesis.antlr.memory.tool.epsilon;

import java.util.List;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.misc.Triple;
import org.antlr.v4.tool.Rule;

/**
 *
 * @author Tim Boudreau
 */
 enum Changed {
    OPT_BLOCKS, CLOSURE_BLOCKS, BOTH, NONE;

    boolean isChanged() {
        return this != NONE;
    }

    Triple<Rule, ATNState, ATNState> get(List<Triple<Rule, ATNState, ATNState>> preventEpsilonClosureBlocks, List<Triple<Rule, ATNState, ATNState>> preventEpsilonOptionalBlocks) {
        switch (this) {
            case NONE:
                return null;
            case CLOSURE_BLOCKS:
            case BOTH:
                return preventEpsilonClosureBlocks.get(preventEpsilonClosureBlocks.size() - 1);
            case OPT_BLOCKS:
                return preventEpsilonOptionalBlocks.get(preventEpsilonOptionalBlocks.size() - 1);
            default:
                throw new AssertionError();
        }
    }

    static Changed of(int oldOs, int newOs, int oldCs, int newCs) {
        if (oldOs == newOs && oldCs == newCs) {
            return NONE;
        } else if (oldOs != newOs && oldCs == newCs) {
            return OPT_BLOCKS;
        } else if (oldOs == newOs && oldCs != newCs) {
            return CLOSURE_BLOCKS;
        } else if (oldOs != newOs && oldCs != newCs) {
            return BOTH;
        } else {
            throw new AssertionError(oldOs + "," + newOs + "," + oldCs + "," + newCs);
        }
    }

}
