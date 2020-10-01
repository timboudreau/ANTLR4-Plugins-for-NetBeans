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
package com.mastfrog.antlr.cc;

import java.util.Objects;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Vocabulary;

/**
 * JDO returning information about matching tokens and rules.
 */
public final class CandidatesCollection {

    /**
     * Collection of Token ID candidates, each with a follow-on List of
     * subsequent tokens.
     */
    public final IntSetMapping tokens;
    /**
     * Collection of Rule candidates, each with the callstack of rules to reach
     * the candidate.
     */
    public final IntArrayMapping rules;
    /**
     * Collection of matched Preferred Rules each with their start and end
     * offsets.
     */
    public final IntArrayMapping rulePositions;

    private static String nameOfToken(int token, Vocabulary vocab) {
        String result = vocab.getSymbolicName(token);
        if (result == null) {
            result = vocab.getDisplayName(token);
        }
        if (result == null) {
            result = Integer.toString(token);
        }
        return result;
    }

    private static String nameOfRule(String[] rules, int ix) {
        if (ix < 0 || ix >= rules.length) {
            return "unknown(" + ix + ")";
        }
        return rules[ix];
    }

    public String toString(String[] ruleNames, Vocabulary vocab) {
        StringBuilder sb = new StringBuilder(256);
        if (!tokens.isEmpty()) {
            sb.append("Candidate tokens with subsequent:");
            tokens.forEach((tok, following) -> {
                sb.append('\n').append(nameOfToken(tok, vocab)).append(":\n\t");
                for (int i = 0; i < following.size(); i++) {
                    if (i > 0 && i != following.size() - 1) {
                        sb.append(", ");
                    }
                    sb.append(nameOfToken(following.valueAt(i), vocab));
                }
            });
        }
        if (!rules.isEmpty()) {
            sb.append("Rules with paths:");
            rules.forEach((rule, stack) -> {
                sb.append('\n').append(nameOfRule(ruleNames, rule)).append(":\n\t");
                for (int i = 0; i < stack.size(); i++) {
                    int currRule = stack.getAsInt(i);
                    if (i > 0 && i != stack.size() - 1) {
                        sb.append(" -> ");
                    }
                    sb.append(nameOfRule(ruleNames, currRule));
                }
            });
        }
        if (!rulePositions.isEmpty()) {
            sb.append("Rule positions:");
            rulePositions.forEach((rule, positions) -> {
                sb.append('\n').append(nameOfRule(ruleNames, rule)).append(":\n\t");
                for (int i = 0; i < positions.size(); i += 2) {
                    if (i > 0 && i != positions.size() - 2) {
                        sb.append(", ");
                    }
                    int start = positions.getAsInt(i);
                    int end = positions.getAsInt(i + 1);
                    sb.append('[').append(start).append(':').append(end).append(']');
                }
            });
        }
        if (sb.length() == 0) {
            sb.append("empty-candidates");
        } else {
            sb.insert(0, "Candidates:\n");
        }
        return sb.toString();
    }

    public String toString(Parser parser) {
        return toString(parser.getRuleNames(), parser.getVocabulary());
    }

    public boolean isEmpty() {
        return tokens.isEmpty() && rules.isEmpty() && rulePositions.isEmpty();
    }

    @Override
    public String toString() {
        return "CandidatesCollection{" + "tokens=" + tokens + ", rules=" + rules + ", ruleStrings=" + rulePositions + '}';
    }

    public void clear() {
        tokens.clear();
        rules.clear();
        rulePositions.clear();
    }

    public CandidatesCollection copy() {
        return new CandidatesCollection(this);
    }

    private CandidatesCollection(CandidatesCollection orig) {
        this(orig.tokens.copy(), orig.rules.copy(), orig.rulePositions.copy());
    }

    public CandidatesCollection(IntSetMapping tokens, IntArrayMapping rules, IntArrayMapping rulePositions) {
        this.tokens = tokens;
        this.rules = rules;
        this.rulePositions = rulePositions;
    }

    public CandidatesCollection() {
        tokens = new IntSetMapping();
        rules = new IntArrayMapping();
        rulePositions = new IntArrayMapping();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.tokens);
        hash = 97 * hash + Objects.hashCode(this.rules);
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
        // We do not want to include rule positions - the same results
        // against the same token/parser state should be the equal even
        // if tokens have been inserted or removed from the document
        final CandidatesCollection other = (CandidatesCollection) obj;
        return tokens.equals(other.tokens) && rules.equals(other.rules);
    }
}
