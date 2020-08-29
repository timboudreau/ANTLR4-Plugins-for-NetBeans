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
import org.antlr.v4.runtime.Token;
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import static org.nemesis.antlr.language.formatting.AntlrCounters.*;
import static org.nemesis.antlr.language.formatting.AntlrCriteria.lineComments;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.ColonHandling;
import org.nemesis.antlr.language.formatting.config.OrHandling;
import org.nemesis.antlrformatting.api.FormattingAction;
import org.nemesis.antlrformatting.api.FormattingContext;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingState;
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
                frr.withAdjustedPriority(200, rules -> {
                    int minOrDepth = 0;
                    switch (ors) {
                        case INDENT:
//                            rules.onTokenType(LPAREN)
//                                    .whenCombinationOf(NEXT_OR_DISTANCE)
//                                    .isLessThan(NEXT_CLOSE_PAREN_DISTANCE)
//                                    .and(NEXT_OR_DISTANCE)
//                                    .isGreaterThan(0)
//                                    .and(NEXT_OPEN_PAREN_DISTANCE)
//                                    .isLessThan(NEXT_OR_DISTANCE)
//                                    .then()
//                                    .format(PREPEND_NEWLINE_AND_INDENT.by(1, PARENS_DEPTH));
                            FormattingAction afterOpenParen
                                    = config.isSpacesInsideParens()
                                    ? APPEND_SPACE : FormattingAction.EMPTY;

                            rules.onTokenType(LPAREN)
                                    .named("newline-before-parens-with-ors")
                                    .whereNextTokenTypeNot(criteria.anyOf(LPAREN, OR))
                                    .wherePreviousTokenTypeNot(COLON)
                                    .whenCombinationOf(NEXT_OR_DISTANCE)
                                    .isLessThan(NEXT_OPEN_PAREN_DISTANCE)
                                    .and(NEXT_CLOSE_PAREN_DISTANCE)
                                    .isGreaterThan(NEXT_OR_DISTANCE)
                                    .and(OR_COUNT)
                                    .isGreaterThan(minOrDepth)
                                    .then()
                                    .format(PREPEND_NEWLINE_AND_INDENT.by(1, PARENS_DEPTH).and(afterOpenParen));

                            if (config.getColonHandling() == ColonHandling.STANDALONE || config.getColonHandling() == ColonHandling.NEWLINE_AFTER) {
                                rules.onTokenType(LPAREN)
                                        .wherePreviousTokenType(COLON)
                                        .adjustPriority(100)
                                        .format(PREPEND_NEWLINE_AND_INDENT.by(0, PARENS_DEPTH).and(afterOpenParen));
                            }

                            rules.onTokenType(OR)
                                    .named("newline-before-or-in-parens")
                                    .when(PARENS_DEPTH)
                                    .isSet()
                                    .format(PREPEND_NEWLINE_AND_INDENT.by(1, PARENS_DEPTH));

                            rules.onTokenType(OR)
                                    .named("indent-ors-to-match-parens-when-outside")
                                    .whereNextTokenType(LPAREN)
                                    .wherePreviousTokenType(RPAREN)
                                    .when(PARENS_DEPTH)
                                    .isUnset()
                                    .format(PREPEND_NEWLINE_AND_INDENT.by(2));
                            ;
                            rules.onTokenType(OR)
                                    .named("indent-ors-to-match-parens")
                                    .whereNextTokenType(LPAREN)
                                    .wherePreviousTokenType(RPAREN)
                                    .when(PARENS_DEPTH)
                                    .isGreaterThan(0)
                                    .format(PREPEND_NEWLINE_AND_INDENT.by(1, PARENS_DEPTH));

                            rules.onTokenType(AntlrCriteria.lineComments())
                                    .named("parent-depth-indent-after-line-comment")
                                    .whereNextTokenType(LPAREN, OR)
                                    .when(PARENS_DEPTH)
                                    .isGreaterThan(0)
                                    .format(APPEND_NEWLINE_AND_INDENT.by(2, PARENS_DEPTH));

                            rules.onTokenType(lineComments().negate())
                                    .named("ensure-newline-after-line-comment-indenting")
                                    .wherePreviousTokenType(lineComments())
                                    .when(PARENS_DEPTH)
                                    .isGreaterThan(0)
                                    .format(PREPEND_NEWLINE_AND_INDENT.by(1, PARENS_DEPTH));

                            rules.onTokenType(LPAREN)
                                    .named("newline-before-initial-paren-with-ors")
                                    .wherePreviousTokenTypeNot(OR, COLON)
                                    .whenCombinationOf(PARENS_DEPTH)
                                    .isEqualTo(1)
                                    .and(OR_COUNT)
                                    .isGreaterThan(0)
                                    .then()
                                    .format(PREPEND_NEWLINE_AND_INDENT.by(1, PARENS_DEPTH).and(afterOpenParen));

                            rules.onTokenType(LPAREN)
                                    .named("newline-after-initial-paren-with-ors")
                                    .wherePreviousTokenType(COLON)
                                    .whenCombinationOf(PARENS_DEPTH)
                                    .isEqualTo(1)
                                    .and(OR_COUNT)
                                    .isGreaterThan(0)
                                    .then()
                                    .format(PREPEND_SPACE.and(APPEND_NEWLINE_AND_INDENT.by(1, PARENS_DEPTH)));

                            rules.onTokenType(OR)
                                    .named("newline-before-or")
                                    .when(PARENS_DEPTH)
                                    .isUnset()
                                    .format(PREPEND_NEWLINE_AND_INDENT);

                            rules.onTokenType(OR)
                                    .whereNextTokenType(LPAREN)
                                    .named("newline-before-or")
                                    .whenCombinationOf(PARENS_DEPTH)
                                    .isUnset()
                                    .and(PENDING_OR_COUNT)
                                    .isGreaterThan(1)
                                    .then()
                                    .format(PREPEND_NEWLINE_AND_INDENT.by(2));

                            rules.onTokenType(RPAREN)
                                    .named("newline-after-rparen-with-ors-when-no-ebnf")
                                    .whereNextTokenTypeNot(SEMI, RPAREN, STAR, PLUS, QUESTION)
                                    .whenCombinationOf(NEXT_OPEN_PAREN_DISTANCE)
                                    .isGreaterThan(1)
                                    .and(PARENS_DEPTH)
                                    .isGreaterThanOrEqualTo(1)
                                    .and(DISTANCE_TO_PREV_EBNF)
                                    .isGreaterThan(1)
                                    .and(OR_COUNT)
                                    .isGreaterThan(minOrDepth)
                                    .then()
                                    .format(APPEND_NEWLINE_AND_INDENT.by(1, PARENS_DEPTH));

                            rules.onTokenType(RPAREN)
                                    .named("newline-after-rparen-no-ebnf")
                                    .whereNextTokenTypeNot(SEMI, RPAREN, STAR, PLUS, QUESTION)
                                    .when(PARENS_DEPTH)
                                    .isGreaterThan(0)
                                    .format(APPEND_NEWLINE_AND_INDENT.by(1, PARENS_DEPTH));

                            rules.onTokenType(STAR, PLUS, QUESTION)
                                    .named("newline-after-ebnf")
                                    .wherePreviousTokenType(STAR, PLUS, QUESTION)
                                    .whereNextTokenTypeNot(RPAREN, SEMI)
                                    .when(DISTANCE_TO_PRECEDING_RPAREN)
                                    .isEqualTo(1)
                                    .when(PARENS_DEPTH)
                                    .isGreaterThan(0)
                                    .format(APPEND_NEWLINE_AND_INDENT.by(PARENS_DEPTH));

                            rules.onTokenType(STAR, PLUS, QUESTION)
                                    .named("newline-after-ebnf-2")
                                    .wherePreviousTokenType(RPAREN)
                                    .whereNextTokenTypeNot(RPAREN, STAR, PLUS, QUESTION, SEMI)
                                    .when(PARENS_DEPTH)
                                    .isGreaterThan(0)
                                    .format(APPEND_NEWLINE_AND_INDENT.by(1, PARENS_DEPTH));

                            rules.onTokenType(AntlrCriteria.lineComments())
                                    .named("indent-after-line-comment-to-pdepth-in-parens")
                                    .when(PARENS_DEPTH)
                                    .isGreaterThan(0)
                                    .ifPrecededByNewline(false)
                                    .format(PREPEND_SPACE.and(APPEND_NEWLINE_AND_INDENT.by(1, PARENS_DEPTH)));

                            rules.onTokenType(AntlrCriteria.lineComments())
                                    .named("or-handling-post-line-comments-indenting")
                                    .when(PARENS_DEPTH)
                                    .isGreaterThan(0)
                                    .ifPrecededByNewline(false)
                                    .format(spaceOrWrap.and(APPEND_NEWLINE_AND_INDENT.by(PARENS_DEPTH)));

                            if (config.isSpacesInsideParens()) {
                                rules.onTokenType(STAR, PLUS, QUESTION)
                                        .named("space-after-end-ebnf")
                                        .whereNextTokenTypeNot(SEMI, STAR, PLUS, QUESTION)
                                        .format(APPEND_SPACE);
                            }

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

    static FormattingAction logIt(String msg, FormattingAction other, Enum<?>... keys) {
        return (Token token, FormattingContext ctx, LexingState state) -> {
            StringBuilder sb = new StringBuilder();
            for (Enum<?> e : keys) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(e).append("=").append(state.get((Enum) e));
            }
            other.accept(token, ctx, state);
        };
    }

    static FormattingAction replaceIt(String msg) {
        return (Token token, FormattingContext ctx, LexingState state) -> {
            ctx.replace(msg);
        };
    }

    @Override
    protected void state(LexingStateBuilder<AntlrCounters, ?> state) {
        boolean inUse;
        boolean isIndent = false;
        switch (config.getOrHandling()) {
            case INDENT:
                isIndent = true;
            // fallthrough
            case ALIGN_WITH_PARENTHESES:
                inUse = true;
                break;
            default:
                inUse = false;
        }
        if (inUse) {
//        if (config.getOrHandling().isAlignParentheses()) {
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
            if (isIndent) {
                state.computeTokenDistance(NEXT_OR_DISTANCE)
                        .onEntering(criteria.matching(ANTLRv4Lexer.LPAREN))
                        .toNext(OR).includingWhitespace().build();

                state.computeTokenDistance(NEXT_CLOSE_PAREN_DISTANCE)
                        .onEntering(criteria.matching(ANTLRv4Lexer.RPAREN))
                        .toNext(RPAREN)
                        .ignoringWhitespace().build();
                state.computeTokenDistance(NEXT_OPEN_PAREN_DISTANCE)
                        .onEntering(criteria.anyOf(RPAREN, LPAREN, OR))
                        .toNext(RPAREN)
                        .ignoringWhitespace().build();

                state.computeTokenDistance(DISTANCE_TO_NEXT_SEMICOLON)
                        .onEntering(criteria.anyOf(COLON, LPAREN, RPAREN))
                        .toNext(SEMI)
                        .ignoringWhitespace().build();

                state.computeTokenDistance(DISTANCE_TO_PREV_EBNF)
                        .onEntering(LPAREN)
                        .toPreceding(criteria.anyOf(STAR, QUESTION, PLUS))
                        .ignoringWhitespace();

                state.count(OR_COUNT).onEntering(LPAREN)
                        .countTokensMatching(OR)
                        .scanningForwardUntil(RPAREN)
                        .build();

                state.count(PENDING_OR_COUNT).onEntering(OR)
                        .countTokensMatching(OR)
                        .scanningForwardUntil(RPAREN);
            }
        }
    }
}
