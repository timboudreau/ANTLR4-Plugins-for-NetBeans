/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class RuleDeclaration implements RuleElement {
    private final String ruleID;
    private       int    startOffset;
    private       int    endOffset;
    private final RuleElementKind kind;
    private List<RuleDeclaration> namedAlternatives;
    private int ruleStartOffset;
    private int ruleEndOffset;

    public List<RuleDeclaration> namedAlternatives() {
        if (namedAlternatives == null) {
            return Collections.emptyList();
        }
        return namedAlternatives;
    }

    public RuleDeclaration addNamedAlternative(String name, int tokenStart, int tokenEnd) {
        if (namedAlternatives == null) {
            namedAlternatives = new ArrayList<>(5);
        }
        RuleDeclaration result = new RuleDeclaration(RuleElementKind.PARSER_NAMED_ALTERNATIVE_SUBRULE,
            name, tokenStart, tokenEnd);
        namedAlternatives.add(result);
        return result;
    }

    public boolean hasNamedAlternatives() {
        return namedAlternatives != null;
    }
    
    public String getRuleID() {
        return ruleID;
    }

    public int getStartOffset() {
        return startOffset;
    }
    
    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }
    
    public int getEndOffset() {
        return endOffset;
    }

    public int getRuleStartOffset() {
        return ruleStartOffset;
    }

    public int getRuleEndOffset() {
        return ruleEndOffset;
    }
    
    public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }

    public boolean overlaps(int position) {
        return position >= startOffset && position < endOffset;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
         sb.append(ruleID).append("@").append(startOffset).append(":").append(endOffset);
         if (hasNamedAlternatives()) {
             sb.append('{');
             for (Iterator<RuleDeclaration> it=namedAlternatives.iterator(); it.hasNext();) {
                 RuleDeclaration alt = it.next();
                 sb.append(alt.getRuleID());
                 if (it.hasNext()) {
                     sb.append(',');
                 }
             }
         }
         return sb.append(" (").append(kind).append(')').toString();
    }
    
    public RuleElementKind kind() {
        return kind;
    }

    @Override
    public int hashCode() {
        int hash = 53;
        hash = 23 * hash + Objects.hashCode(this.ruleID);
        hash = 23 * hash + this.startOffset;
        hash = 23 * hash + this.endOffset;
        hash = 23 * hash + kind.ordinal();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || !(obj instanceof RuleDeclaration)) {
            return false;
        }
        final RuleDeclaration other = (RuleDeclaration) obj;
        return this.startOffset == other.startOffset &&
                this.endOffset == other.endOffset &&
                this.kind == other.kind
                && this.ruleID.equals(ruleID);
    }

    public RuleDeclaration
            (RuleElementTarget target,
             String ruleID     ,
             int    startOffset,
             int    endOffset,
             int    ruleStartOffset,
             int    ruleEndOffset) {
        this.kind = target.declarationKind();
        this.ruleID = ruleID;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.ruleStartOffset = ruleStartOffset;
        this.ruleEndOffset = ruleEndOffset;
    }

    private RuleDeclaration
            (RuleElementKind kind,
             String ruleID     ,
             int    startOffset,
             int    endOffset  ) {
        this.kind= kind;
        this.ruleID = ruleID;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

}