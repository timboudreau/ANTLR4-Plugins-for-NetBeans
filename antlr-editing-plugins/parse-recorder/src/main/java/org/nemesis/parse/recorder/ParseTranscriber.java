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
package org.nemesis.parse.recorder;

/**
 *
 * @author Tim Boudreau
 */
interface ParseTranscriber {

    void enterRule(int ruleIndex, int tokenIndex);

    void decision(int decision, int stateNumber, int ruleIndex, boolean epsilonOnly, int[] nextTokenWithinRule, int tokenIndex);

    void state(int stateNumber, boolean epsilonOnly, int ruleIndex, int[] nextTokenWithinRule, int tokenIndex);

    void exitRule(int tokenIndex);

    void recurse(int state, int ruleIndex, int precedence, int index);

    public void ruleStop(int ruleIndex, int stateNumber, boolean epsilonOnlyTransitions, int index);

}
