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
package org.nemesis.antlr.memory.tool.ext;

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
public final class EpsilonRuleInfo implements Comparable<EpsilonRuleInfo> {

    private final String culpritRuleName;
    private final int culpritRuleStart;
    private final int culpritRuleEnd;
    private final int culpritRuleLineOffset;
    private final boolean isLexerRule;
    private final String victimRuleName;
    private final int victimTokenIndex;
    private final int victimTokenLine;
    private final int victimLineOffset;
    private final int culpritRuleLine;
    private final EpsilonErrorKind kind;
    private final String grammarName;
    private final String[] rulePath;
    private final String alternativeLabel;
    private final int victimStart;
    private final int victimEnd;
    private final ProblematicEbnfInfo problem;

    public EpsilonRuleInfo(String grammarName, ErrorType errorType, String name,
            int ruleStart, int ruleEnd, int ruleLine, int ruleLineOffset,
            boolean lexerRule, String ruleName, int tokenIndex,
            int victimStart, int victimEnd,
            int victimLine,
            int victimLineOffset, List<String> rulePath, String alternativeLabel,
            ProblematicEbnfInfo problem) {
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
        this.problem = problem;
        this.victimEnd = victimEnd;
        if (null == errorType) {
            throw new AssertionError("Not a supported ErrorType: " + errorType);
        } else {
            switch (errorType) {
                case EPSILON_TOKEN:
                    kind = EpsilonErrorKind.EPSILON_TOKEN;
                    break;
                case EPSILON_CLOSURE:
                    kind = EpsilonErrorKind.EPSILON_CLOSURE;
                    break;
                case EPSILON_LR_FOLLOW:
                    kind = EpsilonErrorKind.EPSILON_LR_FOLLOW;
                    break;
                case EPSILON_OPTIONAL:
                    kind = EpsilonErrorKind.EPSILON_OPTIONAL;
                    break;
                default:
                    throw new AssertionError("Not a supported ErrorType: " + errorType);
            }
        }
    }

    public ProblematicEbnfInfo problem() {
        return problem;
    }

    public int victimLength() {
        return victimEnd - victimStart;
    }

    public int culpritLength() {
        return culpritRuleEnd - culpritRuleStart;
    }

    public String culpritErrorMessage() {
        if (isSelfViolation()) {
            return "Rule '" + victimRuleName + "' can "
                    + "match the empty string via '" + problem.text() + "' - consider "
                    + "changing wildcards ? or * to + - ";
        }
        return "Rule '" + culpritRuleName + "' causes rule '"
                + victimRuleName + "' to be able to match the empty string "
                + "via '" + problem.text() + "'"
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

    public String victimRuleName() {
        return victimRuleName;
    }

    public String culpritRuleName() {
        return culpritRuleName;
    }

    public EpsilonErrorKind kind() {
        return kind;
    }

    public boolean isLexerRule() {
        return isLexerRule;
    }

    public String grammarName() {
        return grammarName;
    }

    public String alternativeLabel() {
        return alternativeLabel;
    }

    public List<String> rulePath() {
        return Arrays.asList(rulePath);
    }

    public int victimStart() {
        return victimStart;
    }

    public int victionEnd() {
        return victimEnd;
    }

    public int culpritStart() {
        return culpritRuleStart;
    }

    public int culpritEnd() {
        return culpritRuleEnd;
    }

    public int victimEnd() {
        return victimEnd;
    }

    public int victimLine() {
        return victimTokenLine;
    }

    public int victimLineOffset() {
        return victimLineOffset;
    }

    public int culpritLine() {
        return culpritRuleLine;
    }

    public int culpritLineOffset() {
        return this.culpritRuleLineOffset;
    }
}
