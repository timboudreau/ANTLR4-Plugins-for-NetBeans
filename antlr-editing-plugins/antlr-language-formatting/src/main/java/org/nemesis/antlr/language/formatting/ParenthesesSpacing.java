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
package org.nemesis.antlr.language.formatting;

import static org.nemesis.antlr.ANTLRv4Lexer.COLON;
import static org.nemesis.antlr.ANTLRv4Lexer.ID;
import static org.nemesis.antlr.ANTLRv4Lexer.LPAREN;
import static org.nemesis.antlr.ANTLRv4Lexer.RPAREN;
import static org.nemesis.antlr.language.formatting.AntlrCriteria.lineComments;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_SPACE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_SPACE;

/**
 *
 * @author Tim Boudreau
 */
final class ParenthesesSpacing extends AbstractFormatter {

    ParenthesesSpacing(AntlrFormatterConfig config) {
        super(config);
    }

    @Override
    protected void rules(FormattingRules rules) {
        rules.onTokenType(LPAREN).wherePreviousTokenTypeNot(lineComments().or(criteria.matching(LPAREN)))
                .whereNextTokenTypeNot(LPAREN)
                .priority(100)
                .format(APPEND_SPACE);
        rules.onTokenType(LPAREN).wherePreviousTokenType(COLON).format(PREPEND_SPACE);
        rules.onTokenType(ID)
                .wherePreviousTokenType(COLON)
                .priority(100)
                .format(PREPEND_SPACE);
        rules.onTokenType(ruleOpeners).wherePreviousTokenType(LPAREN)
                .priority(100)
                .format(PREPEND_SPACE);
        rules.onTokenType(RPAREN).wherePreviousTokenTypeNot(lineComments().or(criteria.anyOf(RPAREN, LPAREN)))
                .priority(100)
                .format(PREPEND_SPACE);
    }

    @Override
    protected void state(LexingStateBuilder<AntlrCounters, ?> stateBuilder) {
    }
}
