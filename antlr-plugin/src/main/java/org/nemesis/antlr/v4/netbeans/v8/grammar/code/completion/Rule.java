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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.completion;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class Rule {
    protected String ruleStack;
    protected int    beginOffset;
    protected int    endOffset;

    public String getRuleStack() {
        return ruleStack;
    }

    public int getBeginOffset() {
        return beginOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }
    
    
    public Rule(String ruleStack, int beginOffset) {
        this.ruleStack = ruleStack;
        this.beginOffset = beginOffset;
        this.endOffset = -1;
    }
    
    public Rule(String ruleStack, int beginOffset, int endOffset) {
        this.ruleStack = ruleStack;
        this.beginOffset = beginOffset;
        this.endOffset = endOffset;
    }
}
