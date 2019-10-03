/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
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

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.language.formatting;

import com.mastfrog.function.IntBiPredicate;
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import static org.nemesis.antlr.language.formatting.AntlrCriteria.lineComments;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlrformatting.api.Criterion;
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
