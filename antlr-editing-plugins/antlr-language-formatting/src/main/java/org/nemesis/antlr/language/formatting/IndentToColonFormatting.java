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
import static org.nemesis.antlr.language.formatting.AntlrCounters.COLON_POSITION;
import static org.nemesis.antlr.language.formatting.AntlrCounters.PARENS_DEPTH;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.ColonHandling;
import com.mastfrog.antlr.utils.Criterion;
import static org.nemesis.antlr.language.formatting.AntlrCounters.DISTANCE_TO_PRECEDING_SHARP;
import static org.nemesis.antlr.language.formatting.AntlrCriteria.lineComments;
import org.nemesis.antlrformatting.api.FormattingAction;
import org.nemesis.antlrformatting.api.FormattingRules;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_DOUBLE_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_SPACE;

/**
 *
 * @author Tim Boudreau
 */
final class IndentToColonFormatting extends AbstractFormatter {

    IndentToColonFormatting(AntlrFormatterConfig config) {
        super(config);
    }

    protected boolean standaloneColon() {
        return config.getColonHandling() == ColonHandling.STANDALONE;
    }

    protected boolean newlineBeforeColon() {
        return config.getColonHandling() == ColonHandling.NEWLINE_BEFORE;
    }

    protected boolean colonThenNewline() {
        return config.getColonHandling() == ColonHandling.NEWLINE_AFTER;
    }

    @Override
    protected void rules(FormattingRules rules) {
        rules.whenInMode(grammarRuleModes, rls -> {
            if (standaloneColon()) {
                rls.onTokenType(COLON)
                        .named("standalone-colon-newline")
                        .priority(100)
                        .format(prependNewlineAndIndent);
                rls.onTokenType(Criterion.ALWAYS)
                        .wherePreviousTokenType(COLON)
                        .named("newline-after-colon")
                        .format(prependNewlineAndIndent);
//                rls.onTokenType(allIds.or(criteria.anyOf(LPAREN, STRING_LITERAL)))
//                        .wherePreviousTokenTypeNot(AntlrCriteria.lineComments().or(criteria.anyOf(SEMI, LPAREN, ASSIGN)))
//                        .priority(100)
//                        .format(prependNewlineAndIndent);
            } else if (newlineBeforeColon()) {
                rls.withAdjustedPriority(100, rls2 -> {
                    rls2.onTokenType(COLON)
                            .named("newline-before-colon")
                            .format(prependNewlineAndIndent);
                    rls2.onTokenType(allIds.or(criteria.anyOf(LPAREN, STRING_LITERAL)))
                            .named("ensure-space-after-anything-but-lincomment-semi-lparen-assign")
                            .wherePreviousTokenTypeNot(lineComments().or(criteria.anyOf(SEMI, LPAREN, ASSIGN, RANGE)))
                            .format(spaceOrWrap);
                });
            } else if (colonThenNewline()) {
                rls.onTokenType(allIds.or(criteria.anyOf(LPAREN, STRING_LITERAL)))
                        .named("ensure-space-after-anything-but-lincomment-semi-lparen-assign")
                        .wherePreviousTokenTypeNot(lineComments().or(criteria.anyOf(SEMI, LPAREN, ASSIGN, RANGE)))
                        .priority(100)
                        .format(spaceOrWrap);
                rls.onTokenType(Criterion.ALWAYS)
                        .wherePreviousTokenType(COLON)
                        .named("always-newline-after-colon")
                        .priority(140)
                        .format(prependNewlineAndIndent);
                rls.onTokenType(COLON)
                        .named("space-before-colon")
                        .format(PREPEND_SPACE);
            }

            // XXX figure out what rule is capturing this in these next two
            // first place
            rls.onTokenType(ID)
                    .whereNextTokenType(ASSIGN)
                    .named("no-space-in-assign")
                    .wherePreviousTokenType(LPAREN)
                    .priority(120)
                    .format(FormattingAction.EMPTY);

            rls.onTokenType(LPAREN)
                    .named("no-spaces-in-empty-parens")
                    .wherePreviousTokenType(RPAREN)
                    .priority(120)
                    .format(FormattingAction.EMPTY);

            rls.onTokenType(BEGIN_ARGUMENT, PARDEC_BEGIN_ARGUMENT, LEXER_CHAR_SET)
                    .named("space-after-colon-before-arguments-and-charsets")
                    .wherePreviousTokenType(COLON)
                    .format(PREPEND_SPACE);

            rules.onTokenType(ruleOpeners)
                    .named("space-or-wrap-after-or")
                    .wherePreviousTokenType(OR)
                    .format(spaceOrWrap);

            rules.onTokenType(LPAREN)
                    .named("space-before-lparen-after-ebnf")
                    .wherePreviousTokenType(STAR, PLUS, QUESTION, END_ARGUMENT, DOT)
                    .format(spaceOrWrap);

            rules.onTokenType(ID).whereNextTokenType(ASSIGN)
                    .named("space-before-assign-id")
                    .wherePreviousTokenType(LPAREN)
                    .format(spaceOrWrap);

            rules.onTokenType(allIds)
                    .named("space-after-ebnfs")
                    .wherePreviousTokenType(keywordsOrIds.or(criteria.anyOf(STAR, PLUS, QUESTION, END_ARGUMENT)))
                    .format(PREPEND_SPACE);

            rls.onTokenType(OR)
                    .named("outer-or-clauses-on-new-lines")
                    .whenCombinationOf(PARENS_DEPTH).isLessThan(1).and(COLON_POSITION).isSet().then()
                    .format(prependNewlineAndIndent);

            rls.onTokenType(OR)
                    .named("space-or-wrap-nested-or-clauses")
                    .whereNextTokenTypeNot(SEMI, RPAREN)
                    .whenCombinationOf(PARENS_DEPTH).isGreaterThanOrEqualTo(1).and(COLON_POSITION).isSet()
                    .then()
                    .format(spaceOrWrap);

            rls.whenPreviousTokenType(SEMI, rl -> {
                rl.onTokenType(keywordsOrIds)
                        .named("double-newline-on-id-after-semicolon")
                        .format(PREPEND_DOUBLE_NEWLINE);
            });

            rls.onTokenType(OR)
                    .wherePreviousTokenType(STAR, PLUS, QUESTION, LEXER_CHAR_SET, STRING_LITERAL, RPAREN)
                    .named("space-on-or-after-ebnf-and-similar")
                    .format(spaceOrWrap);
        });
        rules.onTokenType(SHARP)
                .named("space-before-label")
                .whereNextTokenType(ID)
                .wherePreviousTokenType(RPAREN)
                .format(PREPEND_SPACE);

        rules.onTokenType(OR)
                .wherePreviousTokenType(ID)
                .when(DISTANCE_TO_PRECEDING_SHARP)
                .isEqualTo(1)
                .priority(1)
                .named("newline-indent-after-alternative-label")
                .format(prependNewlineAndIndent);

        if (config.isSemicolonOnNewLineReallyEnabled()) {
            rules.onTokenType(SEMI)
                    .named("semicolon-on-new-line")
                    .whereModeTransition(IntBiPredicate.fromPredicates(grammarRuleModes, AntlrCriteria.mode(MODE_DEFAULT)))
                    .priority(180)
                    .format(prependNewlineAndIndent);
        }
    }
}
