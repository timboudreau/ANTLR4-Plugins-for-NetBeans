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
package org.nemesis.antlr.memory.tool;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.tool.ErrorType;

/**
 * The default behavior of Antlr when a rule, one of whose descendants can match
 * the empty string is encountered, is simply emit an error saying that the
 * closure of some rule can match the empty string, but giving absolutely no
 * clue as to <i>which</i> rule called from that one is actually the one which
 * <i>can</i> match the empty string. So we have some tooling that does the same
 * ATN analysis but actually figures out where the problem is. This class
 * represents info about one such error, allowing the actual culprit rule to be
 * flagged, rather than some distant ancestor of it.
 *
 * @author Tim Boudreau
 */
final class EpsilonRuleInfo implements Comparable<EpsilonRuleInfo> {

    public final String culpritRuleName;
    public final int culpritRuleStart;
    public final int culpritRuleEnd;
    public final int culpritRuleLineOffset;
    public final boolean isLexerRule;
    public final String victimRuleName;
    public final int victimTokenIndex;
    public final int victimTokenLine;
    public final int victimLineOffset;
    public final int culpritRuleLine;
    public final Kind kind;
    public final String grammarName;
    public final String[] rulePath;
    public final String alternativeLabel;
    public final int victimStart;
    public final int victimEnd;

    EpsilonRuleInfo(String grammarName, ErrorType errorType, String name,
            int ruleStart, int ruleEnd, int ruleLine, int ruleLineOffset,
            boolean lexerRule, String ruleName, int tokenIndex,
            int victimStart, int victimEnd,
            int victimLine,
            int victimLineOffset, List<String> rulePath, String alternativeLabel) {
        this.culpritRuleLine = ruleLine;
        this.culpritRuleName = name;
        this.culpritRuleStart = ruleStart;
        this.culpritRuleEnd = ruleEnd;
        this.culpritRuleLineOffset = ruleLineOffset;
        this.isLexerRule = lexerRule;
        this.victimRuleName = ruleName;
        this.victimTokenIndex = tokenIndex;
        this.victimTokenLine = victimLine;
        this.victimLineOffset = victimLineOffset;
        this.grammarName = grammarName;
        this.rulePath = rulePath.toArray(new String[rulePath.size()]);
        this.alternativeLabel = alternativeLabel;
        this.victimStart = victimStart;
        this.victimEnd = victimEnd;
        if (null == errorType) {
            throw new AssertionError("Not a supported ErrorType: " + errorType);
        } else {
            switch (errorType) {
                case EPSILON_CLOSURE:
                    kind = Kind.EPSILON_CLOSURE;
                    break;
                case EPSILON_LR_FOLLOW:
                    kind = Kind.EPSILON_LR_FOLLOW;
                    break;
                case EPSILON_OPTIONAL:
                    kind = Kind.EPSILON_OPTIONAL;
                    break;
                default:
                    throw new AssertionError("Not a supported ErrorType: " + errorType);
            }
        }
    }

    public int victimLength() {
        return victimEnd - victimStart;
    }

    public int culpritLength() {
        return culpritRuleEnd - culpritRuleStart;
    }

    public String culpritErrorMessage() {
        if (isSelfViolation()) {
            return "Rule '" + victimRuleName + "' causes itself to be able to "
                    + "match the empty string - consider "
                    + "changing wildcards ? or * to +";
        }
        return "Rule '" + culpritRuleName + "' causes rule '"
                + victimRuleName + "' to be able to match the empty string"
                + " - consider changing wildcards ? or * to +";
    }

    public String victimErrorMessage() {
        if (isSelfViolation()) {
            return "Rule '" + victimRuleName
                    + "' can match the empty string via recursion through "
                    + "itself";
        }
        Set<String> seenRules = new HashSet<>();
        StringBuilder sb = new StringBuilder(128);
        sb.append("Rule '").append(victimRuleName)
                .append("' can match the empty string via the following path: ");
        int addedCount = 0;
        for (int i = rulePath.length - 1; i >= 0; i--) {
            String curr = rulePath[i];
            if (seenRules.contains(curr)) {
                continue;
            }
            seenRules.add(curr);
            if (addedCount > 0) {
                sb.append("/");
            }
            sb.append(curr);
            addedCount++;
        }
        return sb.toString();
    }

    public boolean isSelfViolation() {
        return culpritRuleName.equals(victimRuleName);
    }

    @Override
    public String toString() {
        return "EpsilonRuleInfo{" + "culpritRuleName=" + culpritRuleName
                + ", culpritRuleStart=" + culpritRuleStart
                + ", culpritRuleLine=" + culpritRuleLine
                + ", culpritRuleEnd="
                + culpritRuleEnd + ", culpritRuleLineOffset=" + culpritRuleLineOffset
                + ", isLexerRule=" + isLexerRule + ", victimRuleName=" + victimRuleName
                + ", victimTokenIndex=" + victimTokenIndex + ", victimRuleLine="
                + victimTokenLine + ", victimLineOffset=" + victimLineOffset
                + ", victimStartOffset=" + victimStart
                + ", victimEndOFfset=" + victimEnd
                + ", kind=" + kind
                + ", grammarName=" + grammarName + ", rulePath="
                + Arrays.toString(rulePath) + ", alternativeLabel "
                + alternativeLabel + '}';
    }

    @Override
    public int compareTo(EpsilonRuleInfo o) {
        if (o == this) {
            return 0;
        }
        int result = victimRuleName.compareTo(o.victimRuleName);
        if (result == 0) {
            return -Integer.compare(rulePath.length, o.rulePath.length);
        }
        return result;
    }

    /**
     * Avoid exposing Antlr tool types externally.
     */
    public enum Kind {
        EPSILON_CLOSURE,
        EPSILON_LR_FOLLOW,
        EPSILON_OPTIONAL;

        public int antlrErrorCode() {
            switch (this) {
                case EPSILON_CLOSURE:
                    return 153;
                case EPSILON_LR_FOLLOW:
                    return 148;
                case EPSILON_OPTIONAL:
                    return 154;
                default:
                    return -1;
            }
        }
    }
}
