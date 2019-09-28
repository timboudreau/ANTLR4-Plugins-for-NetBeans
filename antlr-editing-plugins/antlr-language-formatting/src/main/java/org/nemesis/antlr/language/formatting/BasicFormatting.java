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

import static org.nemesis.antlr.ANTLRv4Lexer.*;
import static org.nemesis.antlr.language.formatting.AntlrCounters.COLON_POSITION;
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
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_SPACE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_DOUBLE_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_NEWLINE_AND_INDENT;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_SPACE;

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
            rules.onTokenType(LINE_COMMENT).wherePrevTokenType(LINE_COMMENT)
                    .format(PREPEND_NEWLINE_AND_INDENT.bySpaces(LINE_COMMENT_INDENT).and(APPEND_NEWLINE));

            rules.onTokenType(PARSER_RULE_ID, TOKEN_ID, FRAGMENT)
                    .wherePreviousTokenType(lineComments())
                    .format(PREPEND_NEWLINE);
        }

        rules.onTokenType(RARROW).format(spaceOrWrap.and(APPEND_SPACE));

        rules.onTokenType(GRAMMAR, LEXER, PARSER)
                .wherePreviousTokenType(-1)
                .format(APPEND_SPACE);

        rules.onTokenType(lineComments().negate())
                .wherePreviousTokenType(lineComments())
                .format(PREPEND_NEWLINE);

        // Put a blank line before a line comment if it starts the line
        // in the original source, and if it was not preceded by another
        // line comment
        rules.onTokenType(lineComments())
                .ifPrecededByNewline(true)
                .wherePrevTokenTypeNot(lineComments())
                .format(PREPEND_DOUBLE_NEWLINE);

        // Newline after block comments
        rules.onTokenType(criteria.noneOf(SEMI).and(AntlrCriteria.whitespace().negate()))
                .wherePreviousTokenType(criteria.anyOf(ALL_BLOCK_COMMENTS))
                .format(PREPEND_NEWLINE);

        // Every token should be preceded by a space
        rules.whenInMode(grammarRuleModes, rls -> {
            rls.onTokenType(LPAREN).wherePreviousTokenType(ruleOpeners).format(PREPEND_SPACE);
            rls.onTokenType(RPAREN).wherePrevTokenType(OR).format(PREPEND_SPACE);
        });

        rules.onTokenType(lineComments()).wherePreviousTokenType(
                criteria.anyOf(ALL_BLOCK_COMMENTS)).format(PREPEND_DOUBLE_NEWLINE);

        rules.onTokenType(criteria.anyOf(ALL_BLOCK_COMMENTS))
                .wherePreviousTokenType(lineComments())
                .format(PREPEND_NEWLINE);

        rules.onTokenType(criteria.anyOf(ALL_BLOCK_COMMENTS)).format(PREPEND_NEWLINE);

        rules.onTokenType(lineComments()).wherePrevTokenType(RBRACE, RPAREN, END_ACTION)
                .ifPrecededByNewline(false).format(PREPEND_SPACE);

        rules.onTokenType(STRING_LITERAL).wherePrevTokenType(STAR, QUESTION, PLUS, DOT)
                .format(spaceOrWrap);

        rules.onTokenType(ID).whereMode(grammarRuleModes)
                .wherePrevTokenType(SHARP)
                .priority(120)
                .format(FormattingAction.EMPTY);

        rules.onTokenType(SHARP)
                .whereNextTokenType(ID)
                .priority(120)
                .format(PREPEND_SPACE);
    }

    @Override
    protected void state(LexingStateBuilder<AntlrCounters, ?> bldr) {
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
                    .beforeProcessingToken()
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
