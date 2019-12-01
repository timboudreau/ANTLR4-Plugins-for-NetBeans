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
import org.antlr.v4.tool.ErrorType;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.Rule;
import org.nemesis.antlr.memory.tool.ext.EpsilonRuleInfo;
import org.nemesis.antlr.memory.tool.ext.ProblematicEbnfInfo;

/**
 *
 * @author Tim Boudreau
 */
class PendingEmission {

    final Grammar grammar;
    final org.antlr.runtime.Token token;
    final String ruleName;
    final Rule startRule;
    final Rule stopRule;
    final List<String> path;

    public PendingEmission(Grammar grammar, org.antlr.runtime.Token token, String ruleName, Rule startRule, Rule stopRule, List<String> path) {
        this.grammar = grammar;
        this.token = token;
        this.ruleName = ruleName;
        this.startRule = startRule;
        this.stopRule = stopRule;
        this.path = path;
    }

    public EpsilonRuleInfo toInfo(ErrorType errorType, ProblematicEbnfInfo problem) {
        int ruleStartingToken = startRule.ast.getTokenStartIndex();
        org.antlr.runtime.CommonToken startRuleStartToken = (org.antlr.runtime.CommonToken) startRule.g.originalTokenStream.get(ruleStartingToken);
        int ruleLine = startRule.ast.getLine();
        int ruleLineOffset = startRule.ast.getCharPositionInLine();
        String altLabel = startRule.ast.getAltLabel();
        org.antlr.runtime.CommonToken tk = (org.antlr.runtime.CommonToken) token;
        int startRuleStart = startRuleStartToken.getStartIndex();
        int startRuleEnd = startRuleStartToken.getStopIndex() + 1; //startRuleStopToken.getStopIndex() + 1;
        int victimStart = tk.getStartIndex();
        int victimEnd = tk.getStopIndex() + 1;
        return new EpsilonRuleInfo(grammar.name, errorType, startRule.name, startRuleStart, startRuleEnd, ruleLine, ruleLineOffset, startRule.ast.isLexerRule(), ruleName, tk.getTokenIndex(), victimStart, victimEnd, token.getLine(), token.getCharPositionInLine(), path, altLabel, problem);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("EpsilonRuleInfo(");
        int ruleStart = startRule.ast.getTokenStartIndex();
        int ruleEnd = startRule.ast.getTokenStopIndex() + 1;
        int ruleLine = startRule.ast.getLine();
        int ruleLineOffset = startRule.ast.getCharPositionInLine();
        org.antlr.runtime.Token startRuleStartToken = startRule.g.originalTokenStream.get(ruleStart);
        org.antlr.runtime.Token startRuleStopToken = startRule.g.originalTokenStream.get(ruleEnd);
        sb.append(grammar.name).append(' ').append(" startRuleName ").append(startRule.name).append(" startRuleLine ").append(startRule.ast.getLine()).append(" startRuleStart ").append(ruleStart).append(" startRuleEnd ").append(ruleEnd).append(" startToken ").append(startRuleStartToken).append(" stopRuleName ").append(stopRule.name).append(" stopRuleLine ").append(stopRule.ast.getLine()).append(" stopRuleStart ").append(stopRule.ast.getTokenStartIndex()).append(" stopRuleStop").append(stopRule.ast.getTokenStartIndex()).append(" stopToken ").append(startRuleStopToken).append(" token ").append(token).append(" lexRule ").append(startRule.ast.isLexerRule()).append(" ruleName ").append(ruleName).append(" ruleLine ").append(ruleLine).append(" ruleLineOffset ").append(ruleLineOffset);
        return sb.append(')').toString();
    }

}
