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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class GrammarDeclaration {
    protected final GrammarType grammarType;
    protected final String      grammarName;
    protected final int         startOffset;
    protected final int         endOffset;

    public GrammarType getGrammarType() {
        return grammarType;
    }

    public String getGrammarName() {
        return grammarName;
    }

    public int getStartOffset() {
        return startOffset;
    }
    
    public int getEndOffset() {
        return endOffset;
    }
    
    public GrammarDeclaration
        (GrammarType grammartype,
         String      grammarName,
         int         startOffset,
         int         endOffset  ) {
        this.grammarType = grammartype;
        this.grammarName = grammarName;
        this.startOffset = startOffset;
        this.endOffset   = endOffset;
    }
}
