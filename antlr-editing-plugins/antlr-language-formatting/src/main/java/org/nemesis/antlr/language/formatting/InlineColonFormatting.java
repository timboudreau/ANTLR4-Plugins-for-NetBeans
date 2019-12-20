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

import com.mastfrog.function.IntBiPredicate;
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import static org.nemesis.antlr.language.formatting.AntlrCriteria.lineComments;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import com.mastfrog.antlr.utils.Criterion;
import org.nemesis.antlrformatting.api.FormattingRules;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_DOUBLE_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_SPACE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_SPACE;

/**
 *
 * @author Tim Boudreau
 */
class InlineColonFormatting extends AbstractFormatter {

    InlineColonFormatting(AntlrFormatterConfig config) {
        super(config);
    }

    @Override
    protected void rules(FormattingRules rules) {
        rules.onTokenType(Criterion.ALWAYS).wherePreviousTokenType(lineComments())
                .priority(100)
                .format(PREPEND_NEWLINE);

        rules.whenInMode(grammarRuleModes, rls -> {

            rules.onTokenType(ruleOpeners).wherePreviousTokenType(COLON)
                    .format(PREPEND_SPACE);

            rules.onTokenType(LPAREN)
                    .wherePreviousTokenType(COLON, STAR, QUESTION, PLUS)
                    .format(PREPEND_SPACE);

            if (config.isWrap()) {
                rules.onTokenType(OR)
                        .wherePreviousTokenTypeNot(RPAREN)
                        .format(spaceOrWrap.and(APPEND_SPACE));

                rules.onTokenType(ID, STRING_LITERAL, TOKEN_ID, PARSER_RULE_ID)
                        .wherePreviousTokenTypeNot(RANGE, SEMI, ASSIGN, RPAREN, DOT)
                        .format(spaceOrWrap);
            } else {
                rules.onTokenType(OR)
                        .format(PREPEND_SPACE.and(APPEND_SPACE));

                rules.onTokenType(ID, STRING_LITERAL, TOKEN_ID, PARSER_RULE_ID)
                        .wherePreviousTokenTypeNot(RANGE, SEMI, ASSIGN, RPAREN, DOT)
                        .format(PREPEND_SPACE);
            }

            rls.onTokenType(COLON).format(PREPEND_SPACE);
            rls.onTokenType(LPAREN).priority(100).wherePreviousTokenType(COLON).format(PREPEND_SPACE);

            rls.onTokenType(allIds)
                    .wherePreviousTokenTypeNot(criteria.anyOf(RPAREN, SEMI, ASSIGN, LPAREN))
                    .format(spaceOrWrap);

            rls.onTokenType(allIds).wherePreviousTokenType(RPAREN)
                    .format(spaceOrWrap);

//            rls.onTokenType(LPAREN).wherePreviousTokenType(ruleEnders.or(criteria.matching(COLON)))
//                    .format(spaceOrWrap);
        });

        rules.onTokenType(SEMI).whereModeTransition(
                IntBiPredicate.fromPredicates(grammarRuleModes.or(AntlrCriteria.mode(MODE_LEXER_COMMANDS)),
                        AntlrCriteria.mode(MODE_DEFAULT)))
                .format(config.isBlankLineBeforeRules() ? APPEND_DOUBLE_NEWLINE : APPEND_NEWLINE);

    }
}
