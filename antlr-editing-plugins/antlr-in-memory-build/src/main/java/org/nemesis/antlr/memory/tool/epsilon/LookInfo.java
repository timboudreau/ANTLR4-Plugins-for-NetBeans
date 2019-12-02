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

import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.PredictionContext;

/**
 *
 * @author Tim Boudreau
 */
final class LookInfo {

    final ATNState state;
    final ATNState stopState;
    final PredictionContext ctx;

    public LookInfo(ATNState state, ATNState stopState, PredictionContext ctx) {
        this.state = state;
        this.stopState = stopState;
        this.ctx = ctx;
    }

    @Override
    public String toString() {
        return "LookInfo{" + "state=" + ParserEmptyStringAnalyzer.s2s(state) + ", stopState=" + ParserEmptyStringAnalyzer.s2s(stopState) + ", ctx=" + ctx + '}';
    }

}
