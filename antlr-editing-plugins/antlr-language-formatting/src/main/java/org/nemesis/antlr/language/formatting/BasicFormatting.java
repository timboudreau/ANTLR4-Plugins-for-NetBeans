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

import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import static org.nemesis.antlr.language.formatting.AntlrCounters.COLON_POSITION;
import static org.nemesis.antlr.language.formatting.AntlrCounters.DISTANCE_TO_PRECEDING_SHARP;
import static org.nemesis.antlr.language.formatting.AntlrCounters.IN_OPTIONS;
import static org.nemesis.antlr.language.formatting.AntlrCounters.LEFT_BRACES_PASSED;
import static org.nemesis.antlr.language.formatting.AntlrCounters.LEFT_BRACE_POSITION;
import static org.nemesis.antlr.language.formatting.AntlrCounters.LEFT_PAREN_POS;
import static org.nemesis.antlr.language.formatting.AntlrCounters.LINE_COMMENT_INDENT;
import static org.nemesis.antlr.language.formatting.AntlrCounters.OR_POSITION;
import static org.nemesis.antlr.language.formatting.AntlrCounters.PARENS_DEPTH;
import static org.nemesis.antlr.language.formatting.AntlrCounters.SEMICOLON_COUNT;
import static org.nemesis.antlr.language.formatting.AntlrCriteria.ALL_BLOCK_COMMENTS;
import static org.nemesis.antlr.language.formatting.AntlrCriteria.lineComments;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.ColonHandling;
import org.nemesis.antlrformatting.api.FormattingAction;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_NEWLINE_AND_INDENT;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_SPACE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_DOUBLE_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_NEWLINE_AND_INDENT;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_SPACE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_TRIPLE_NEWLINE;

/**
 *
 * @author Tim Boudreau
 */
public class BasicFormatting extends AbstractFormatter {

    public BasicFormatting(AntlrFormatterConfig config) {
        super(config);
    }

    @Override
    protected void rules(FormattingRules rules) {
        // This will just log all tokens for rule development purposes
//        rules.replaceAdjacentTokens(Criterion.ALWAYS, (toks, state) -> {
//            for (ModalToken t : toks) {
//                System.out.println("MODE " + t.modeName() + ": '" + t.getText() + "' "
//                    + VOCABULARY.getSymbolicName(t.getType()));
//            }
//            return null;
//        });
        if (!config.isReflowLineComments()) {
            rules.onTokenType(LINE_COMMENT).wherePreviousTokenType(LINE_COMMENT)
                    .named("newline-before-and-after-line-comment-preceded-by-line-comment")
                    .format(PREPEND_NEWLINE_AND_INDENT.bySpaces(LINE_COMMENT_INDENT).and(APPEND_NEWLINE));

            rules.onTokenType(PARSER_RULE_ID, TOKEN_ID, FRAGMENT)
                    .named("prepend-newline-to-token-after-line-comment")
                    .wherePreviousTokenType(lineComments())
                    .format(PREPEND_NEWLINE);
        }

        rules.whenInMode(AntlrCriteria.mode(MODE_HEADER_IMPORT), rls -> {
            rls.onTokenType(ID)
                    .named("header-import-spaces")
                    .wherePreviousTokenType(HEADER_IMPORT)
                    .format(PREPEND_SPACE);
        });

        rules.whenInMode(AntlrCriteria.mode(MODE_IMPORT), rls -> {
            rls.onTokenType(ID)
                    .named("spaces-between-imports")
                    .wherePreviousTokenType(COMMA, IMPORT)
                    .format(spaceOrWrap);
        });

        rules.whenInMode(AntlrCriteria.mode(MODE_CHANNELS), rls -> {
            rls.onTokenType(ANTLRv4Lexer.LBRACE)
                    .wherePreviousTokenType(CHANNELS)
                    .format(PREPEND_SPACE.and(APPEND_SPACE));
            rls.onTokenType(ANTLRv4Lexer.COMMA)
                    .whereNextTokenTypeNot(RBRACE)
                    .format(APPEND_SPACE);
        });
        rules.onTokenType(CHANNELS).format(PREPEND_NEWLINE);
        rules.onTokenType(RBRACE)
                .wherePreviousTokenType(ID)
                .format(APPEND_NEWLINE.and(PREPEND_SPACE));

        rules.onTokenType(TOKDEC_ID)
                .wherePreviousTokenType(RBRACE)
                .format(PREPEND_DOUBLE_NEWLINE);

        rules.whenInMode(AntlrCriteria.mode(MODE_HEADER_ACTION), rls -> {
            rls.onTokenType(BEGIN_ACTION)
                    .named("spaces-around-header-action-braces")
                    .format(PREPEND_SPACE.and(APPEND_SPACE));
        });

        rules.onTokenType(MODE)
                .format(PREPEND_TRIPLE_NEWLINE.and(APPEND_SPACE));

        rules.onTokenType(LEXCOM_MODE)
                .wherePreviousTokenType(SEMI | END_ACTION)
                .format(PREPEND_NEWLINE);

        rules.onTokenType(PARSER_RULE_ID)
                .wherePreviousTokenType(RBRACE)
                .priority(200)
                .format(PREPEND_NEWLINE);
        ;

        rules.onTokenType(LEXCOM_MODE)
                .format(spaceOrWrap);

        rules.onTokenType(ANTLRv4Lexer.GRAMMAR)
                .whereNotFirstTokenInSource()
                .wherePreviousTokenType(criteria.anyOf(LEXER, PARSER))
                .format(spaceOrWrap);

        rules.onTokenType(ANTLRv4Lexer.PARSER, GRAMMAR, LEXER)
                .whereNotFirstTokenInSource()
                .wherePreviousTokenType(BLOCK_COMMENT, LINE_COMMENT)
                .format(PREPEND_NEWLINE);

        rules.onTokenType(ID, FRAGDEC_ID, TOKEN_ID, PARSER_RULE_ID)
                .wherePreviousTokenType(LEXCOM_MODE, MODE, RPAREN)
                .format(spaceOrWrap);

        rules.onTokenType(MORE, LEXCOM_MORE, LEXCOM_PUSHMODE, LEXCOM_POPMODE, LEXCOM_TYPE, LEXCOM_SKIP)
                .wherePreviousTokenType(ANTLRv4Lexer.COMMA)
                .format(spaceOrWrap);

        rules.onTokenType(LPAREN)
                .wherePreviousTokenType(ID, FRAGDEC_ID, TOKEN_ID, PARSER_RULE_ID)
                .format(spaceOrWrap);

        rules.onTokenType(RARROW)
                .named("spaces-channel-arrow")
                .format(spaceOrWrap.and(APPEND_SPACE));

        rules.onTokenType(ANTLRv4Lexer.NOT, DOT, LEXER_CHAR_SET)
                .wherePreviousTokenTypeNot(LPAREN, LBRACE, NOT, HDR_IMPRT_CONTENT, HDR_IMPRT_STATIC, IMPORT)
                .whereModeNot(ANTLRv4Lexer.HeaderPrelude, ANTLRv4Lexer.HeaderImport, ANTLRv4Lexer.HeaderPackage, ANTLRv4Lexer.HeaderAction)
                .format(spaceOrWrap);

        rules.onTokenType(ANTLRv4Lexer.SEMI)
                .whereMode(ANTLRv4Lexer.HeaderImport, ANTLRv4Lexer.HeaderPrelude, ANTLRv4Lexer.HeaderPackage, ANTLRv4Lexer.HeaderAction)
                .format(APPEND_NEWLINE_AND_INDENT);

        rules.onTokenType(LEXCOM_PUSHMODE, LEXCOM_MORE, LEXCOM_POPMODE, LEXCOM_CHANNEL, LEXCOM_TYPE, LEXCOM_SKIP)
                .wherePreviousTokenType(COMMA)
                .format(spaceOrWrap);

        rules.onTokenType(GRAMMAR, LEXER, PARSER)
                .named("spaces-in-grammar-decl")
                .wherePreviousTokenType(-1)
                .format(APPEND_SPACE);

        rules.onTokenType(lineComments().negate())
                .named("ensure-newline-after-line-comment")
                .wherePreviousTokenType(lineComments())
                .format(PREPEND_NEWLINE);

        rules.onTokenType(criteria.anyOf(ALL_BLOCK_COMMENTS))
                .named("block-comments-on-newline")
                .wherePreviousTokenType(SEMI)
                .priority(100)
                .format(PREPEND_DOUBLE_NEWLINE.and(APPEND_NEWLINE));

        rules.onTokenType(criteria.matching(RETURNS))
                .named("space-before-returns")
                .format(spaceOrWrap.and(APPEND_SPACE));

        // Put a blank line before a line comment if it starts the line
        // in the original source, and if it was not preceded by another
        // line comment
        rules.onTokenType(lineComments())
                .ifPrecededByNewline(true)
                .wherePreviousTokenTypeNot(lineComments())
                .named("double-newline-before-first-line-comment-when-starting-line")
                .format(PREPEND_DOUBLE_NEWLINE);

        // Newline after block comments
        rules.onTokenType(criteria.noneOf(SEMI).and(AntlrCriteria.whitespace().negate()))
                .wherePreviousTokenType(criteria.anyOf(ALL_BLOCK_COMMENTS))
                .format(PREPEND_NEWLINE);

        // Every token should be preceded by a space
        rules.whenInMode(grammarRuleModes, rls -> {
            rls.onTokenType(LPAREN)
                    .named("space-before-open-paren")
                    .wherePreviousTokenType(ruleOpeners.or(criteria.anyOf(OR, COLON))).format(PREPEND_SPACE);

            rls.onTokenType(RPAREN)
                    .named("space-before-close-paren-when-preceded-by-|")
                    .wherePreviousTokenType(OR).format(PREPEND_SPACE);

            rls.onTokenType(SHARP)
                    .named("space-before-labels")
                    .priority(20)
                    .format(PREPEND_SPACE);

//            rules.onTokenType(ID).wherePreviousTokenType(SHARP)
//                    .whereNextTokenType(OR)
//                    .priority(1500)
//                    .format(appendDoubleIndent);
        });

        rules.onTokenType(OR)
                .wherePreviousTokenType(ANTLRv4Lexer.ID)
                .when(DISTANCE_TO_PRECEDING_SHARP)
                .isEqualTo(1)
                .named("newline-double-indent-after-alternative-label")
                .format(prependNewlineAndDoubleIndent);

        rules.onTokenType(lineComments()).wherePreviousTokenType(
                criteria.anyOf(ALL_BLOCK_COMMENTS))
                .named("double-newline-before-line-comment-preceded-by-block-comment")
                .format(PREPEND_DOUBLE_NEWLINE);

        rules.onTokenType(criteria.anyOf(ALL_BLOCK_COMMENTS))
                .wherePreviousTokenType(lineComments())
                .named("block-comment-after-line-comment-on-new-line")
                .format(PREPEND_NEWLINE);

        rules.onTokenType(criteria.anyOf(ALL_BLOCK_COMMENTS))
                .named("newline-before-block-comments")
                .format(PREPEND_NEWLINE);

        rules.onTokenType(lineComments())
                .wherePreviousTokenType(RBRACE, RPAREN, END_ACTION, SEMI)
                .whereNotFirstTokenInSource()
                .named("offset-line-comments-by-one-space-when-inline")
                .ifPrecededByNewline(false).format(PREPEND_SPACE.and(APPEND_NEWLINE_AND_INDENT));

        rules.onTokenType(STRING_LITERAL)
                .named("space-or-wrap-on-string-literal-after-ebnf")
                .wherePreviousTokenType(STAR, QUESTION, PLUS, DOT)
                .format(spaceOrWrap);

        rules.onTokenType(ID).whereMode(grammarRuleModes)
                .wherePreviousTokenType(SHARP)
                .named("no-space-between-#-and-label")
                .priority(120)
                .format(FormattingAction.EMPTY);

        rules.onTokenType(SHARP)
                .named("space-before-#label")
                .whereNextTokenType(ID)
                .priority(120)
                .format(PREPEND_SPACE);
    }

    @Override
    protected void state(LexingStateBuilder<AntlrCounters, ?> bldr) {
        bldr.computeTokenDistance(DISTANCE_TO_PRECEDING_SHARP)
                .onEntering(criteria.anyOf(OR, ID))
                .toPreceding(SHARP)
                .ignoringWhitespace();

        bldr
                // For indenting ors
                .pushPosition(OR_POSITION)
                .onTokenType(OR)
                .poppingOnTokenType(RPAREN);
        // Record the position of the rule colon
        if (config.getColonHandling() == ColonHandling.NEWLINE_AFTER) {
            bldr.recordPosition(COLON_POSITION)
                    .onTokenType(COLON)
                    .clearingOnTokenType(-1);
        } else {
            bldr.recordPosition(COLON_POSITION)
                    //                    .beforeProcessingToken()
                    .onTokenType(COLON)
                    .clearingOnTokenType(-1);
        }

        bldr
                .recordPosition(LINE_COMMENT_INDENT)
                .beforeProcessingToken()
                .usingPositionFromInput()
                .onTokenType(AntlrCriteria.lineComments())
                .clearingOnTokenType(-1)
                // Record position of the most recent left brace,
                // keeping a stack of them so we pop our way out,
                // for indenting nested braces
                .pushPosition(LEFT_BRACE_POSITION)
                .onTokenType(LBRACE, BEGIN_ACTION)
                .poppingOnTokenType(RBRACE, END_ACTION)
                // Increment a counter for nested left braces
                .increment(LEFT_BRACES_PASSED).onTokenType(LBRACE, BEGIN_ACTION)
                .decrementingWhenTokenEncountered(RBRACE, END_ACTION)
                // Increment a counter for nested parens
                .increment(PARENS_DEPTH).onTokenType(LPAREN)
                .decrementingWhenTokenEncountered(RPAREN)
                // And push the position of the left paren
                .pushPosition(LEFT_PAREN_POS).onTokenType(LPAREN)
                .poppingOnTokenType(RPAREN)
                // Record the number of tokens from the current one to the next semicolon
                //                .computeTokenDistance(DISTANCE_TO_NEXT_SEMICOLON)
                //                .onEntering(AntlrCriteria.whitespace().negate())
                //                .toNext(SEMI).ignoringWhitespace()

                // Record the number of tokens from the current one to the preceding semicolon
                //                .computeTokenDistance(DISTANCE_TO_PRECEDING_SEMICOLON)
                //                .onEntering(RBRACE, END_ACTION)
                //                .toPreceding(SEMI).ignoringWhitespace()

                // Record the token offset backward to the preceding colon
                //                .computeTokenDistance(DISTANCE_TO_PRECEDING_COLON)
                //                .onEntering(whitespace().negate()) // XXX this could be less general
                //                .toPreceding(COLON).ignoringWhitespace()

                // Track when we are in an Options statement
                .set(IN_OPTIONS)
                .onTokenType(OPTIONS)
                .clearingAfterTokenType(RBRACE, END_ACTION)
                // Record the token offset backward to the preceding colon
                //                .computeTokenDistance(DISTANCE_TO_RBRACE)
                //                .onEntering(whitespace().negate())
                //                .toNext(RBRACE, END_ACTION).ignoringWhitespace()

                // Count semis within braces
                .count(SEMICOLON_COUNT).onEntering(LBRACE)
                .countTokensMatching(SEMI).scanningForwardUntil(RBRACE);
    }

}
