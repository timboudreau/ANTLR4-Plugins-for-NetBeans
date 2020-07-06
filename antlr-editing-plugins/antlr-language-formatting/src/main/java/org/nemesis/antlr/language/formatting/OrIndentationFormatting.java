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

import com.mastfrog.antlr.utils.Criterion;
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import static org.nemesis.antlr.language.formatting.AntlrCounters.*;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.OrHandling;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.*;

/**
 *
 * @author Tim Boudreau
 */
public class OrIndentationFormatting extends AbstractFormatter {

    private Criterion atomOpeners;

    public OrIndentationFormatting(AntlrFormatterConfig config) {
        super(config);
        atomOpeners = criteria.anyOf(ID, PARSER_RULE_ID, TOK_ID, TOKEN_ID, LEXER_CHAR_SET,
                STRING_LITERAL);
    }

    @Override
    protected void rules(FormattingRules r) {
        OrHandling ors = config.getOrHandling();
        if (!ors.isOff()) {
            r.whenInMode(grammarRuleModes, frr -> {
                frr.withAdjustedPriority(2500, rules -> {
                    switch (ors) {
                        case INDENT:
                            rules.onTokenType(OR)
                                    .whereNextTokenTypeNot(RPAREN)
                                    .when(PARENS_DEPTH).isGreaterThan(0)
                                    .format(PREPEND_NEWLINE_AND_INDENT.by(PARENS_DEPTH));
//                            rules.onTokenType(LPAREN)
//                                    .whenCombinationOf(PARENS_DEPTH)
//                                    .isGreaterThan(1)
//                                    .and(PARENS_DEPTH)
//                                    .
                            break;
                        case ALIGN_WITH_PARENTHESES:
                            rules.onTokenType(OR)
                                    .whereNextTokenTypeNot(RPAREN)
                                    .when(PARENS_DEPTH).isGreaterThan(0)
                                    .format(PREPEND_NEWLINE_AND_INDENT.bySpaces(LEFT_PAREN_POS));
//                                    .format(PREPEND_NEWLINE_AND_INDENT.by(PARENS_DEPTH));
                            break;
                        /*
                        case ALIGN_PARENTHESES_AND_ORS:

                            rules.onTokenType(LPAREN)
                                    .wherePreviousTokenTypeNot(COLON, LPAREN, OR, LPAREN)
                                    .whereNextTokenTypeNot(LPAREN, RPAREN)
                                    //                                    .format(PREPEND_NEWLINE_AND_INDENT.bySpaces(LEFT_PAREN_POS, COLON_POSITION));
                                    .format(PREPEND_NEWLINE_AND_INDENT.by(PARENS_DEPTH));

                            rules.onTokenType(RPAREN)
                                    .whereNextTokenTypeNot(STAR, QUESTION, PLUS, RPAREN, SHARP)
                                    .format(APPEND_NEWLINE_AND_INDENT.by(-1, PARENS_DEPTH));

                            rules.onTokenType(STAR, PLUS, QUESTION)
                                    .wherePreviousTokenType(RPAREN)
                                    .whereNextTokenTypeNot(QUESTION, SHARP, SEMI, RPAREN)
                                    .format(APPEND_NEWLINE_AND_INDENT.by(-1, PARENS_DEPTH));

                            rules.onTokenType(QUESTION)
                                    .wherePreviousTokenType(STAR, PLUS)
                                    .whereNextTokenTypeNot(SHARP, SEMI)
                                    .when(DISTANCE_TO_PRECEDING_RPAREN).isLessThan(3)
                                    .format(APPEND_NEWLINE_AND_INDENT.by(-1, PARENS_DEPTH));

                            rules.onTokenType(atomOpeners)
                                    .wherePreviousTokenType(STAR, PLUS, QUESTION)
                                    .when(DISTANCE_TO_PRECEDING_RPAREN)
                                    .isLessThan(3)
                                    .format(PREPEND_NEWLINE_AND_INDENT.by(PARENS_DEPTH));

                            rules.onTokenType(OR)
                                    .when(PARENS_DEPTH).isGreaterThan(0)
                                    .whereNextTokenTypeNot(RPAREN)
                                    .format(PREPEND_NEWLINE_AND_INDENT.by(PARENS_DEPTH));
                         */
                    }
                });
            });
        }
    }

    @Override
    protected void state(LexingStateBuilder<AntlrCounters, ?> state) {
        if (config.getOrHandling().isAlignParentheses()) {
            state.recordPosition(PRECEDING_NON_WHITESPACE_TOKEN_START)
                    .onTokenType(AntlrCriteria.whitespace().negate())
                    .notClearing();
            state.pushPosition(PRECEDING_OR_POSITION)
                    .onTokenType(OR)
                    .poppingOnTokenType(SEMI, RPAREN);
            state.computeTokenDistance(DISTANCE_TO_PRECEDING_RPAREN)
                    .onEntering(STAR, QUESTION, PLUS)
                    .toPreceding(RPAREN)
                    .ignoringWhitespace();

        }
    }

}
