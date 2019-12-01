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

import java.util.BitSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Consumer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNConfig;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.BlockEndState;
import org.antlr.v4.runtime.atn.LL1Analyzer;
import org.antlr.v4.runtime.atn.PredictionContext;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 *
 * @author Tim Boudreau
 */
final class TrackingLL1Analyzer extends LL1Analyzer {

    private final LinkedList<LookInfo> lookInfo = new LinkedList<>();
    private final Consumer<LinkedList<LookInfo>> onEpsilon;

    public TrackingLL1Analyzer(ATN atn, Consumer<LinkedList<LookInfo>> onEpsilon) {
        super(atn);
        this.onEpsilon = onEpsilon;
    }
    private int epsilonCount;

    protected void _LOOK(ATNState s, ATNState stopState, PredictionContext ctx, IntervalSet look, Set<ATNConfig> lookBusy, BitSet calledRuleStack, boolean seeThruPreds, boolean addEOF) {
        LookInfo info = new LookInfo(s, stopState, ctx);
        lookInfo.push(info);
        try {
            boolean hadEpsilon = look.contains(Token.EPSILON);
            boolean epsilonAdded = false;
            int oldEpsilonCount = epsilonCount;
            super._LOOK(s, stopState, ctx, look, lookBusy, calledRuleStack, seeThruPreds, addEOF);
            boolean hasEpsilon = look.contains(Token.EPSILON);
            epsilonAdded = !hadEpsilon && hasEpsilon;
            if (epsilonAdded && oldEpsilonCount == epsilonCount && !(s instanceof BlockEndState)) {
                onEpsilon.accept(lookInfo);
                epsilonCount++;
            }
        } finally {
            lookInfo.pop();
        }
    }

}
