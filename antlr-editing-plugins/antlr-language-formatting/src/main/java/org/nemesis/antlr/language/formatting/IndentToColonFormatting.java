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
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import static org.nemesis.antlr.language.formatting.AntlrCounters.COLON_POSITION;
import static org.nemesis.antlr.language.formatting.AntlrCounters.PARENS_DEPTH;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.ColonHandling;
import org.nemesis.antlrformatting.api.Criterion;
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
                rls.onTokenType(COLON)
                        .priority(100)
                        .named("newline-before-colon")
                        .format(prependNewlineAndIndent);
                rls.onTokenType(allIds.or(criteria.anyOf(LPAREN, STRING_LITERAL)))
                        .named("ensure-space-after-anything-but-lincomment-semi-lparen-assign")
                        .wherePreviousTokenTypeNot(AntlrCriteria.lineComments().or(criteria.anyOf(SEMI, LPAREN, ASSIGN, RANGE)))
                        .priority(100)
                        .format(spaceOrWrap);
            } else if (colonThenNewline()) {
                rls.onTokenType(allIds.or(criteria.anyOf(LPAREN, STRING_LITERAL)))
                        .named("ensure-space-after-anything-but-lincomment-semi-lparen-assign")
                        .wherePreviousTokenTypeNot(AntlrCriteria.lineComments().or(criteria.anyOf(SEMI, LPAREN, ASSIGN, RANGE)))
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

            rls.onTokenType(ANTLRv4Lexer.OR)
                    .named("outer-or-clauses-on-new-lines")
                    .whenCombinationOf(PARENS_DEPTH).isLessThan(1).and(COLON_POSITION).isSet().then()
                    .format(prependNewlineAndIndent);

            rls.onTokenType(ANTLRv4Lexer.OR)
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

        if (config.isSemicolonOnNewLineReallyEnabled()) {
            rules.onTokenType(SEMI)
                    .named("semicolon-on-new-line")
                    .whereModeTransition(IntBiPredicate.fromPredicates(grammarRuleModes, AntlrCriteria.mode(MODE_DEFAULT)))
                    .priority(180)
                    .format(prependNewlineAndIndent);
        }
    }
}
