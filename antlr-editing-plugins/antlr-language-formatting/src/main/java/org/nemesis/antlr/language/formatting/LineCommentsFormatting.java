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

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import org.antlr.v4.runtime.Token;
import static org.nemesis.antlr.ANTLRv4Lexer.BEGIN_ACTION;
import static org.nemesis.antlr.ANTLRv4Lexer.END_ACTION;
import static org.nemesis.antlr.ANTLRv4Lexer.FRAGMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.LBRACE;
import static org.nemesis.antlr.ANTLRv4Lexer.LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.PARSER_RULE_ID;
import static org.nemesis.antlr.ANTLRv4Lexer.SEMI;
import static org.nemesis.antlr.ANTLRv4Lexer.TOKEN_ID;
import static org.nemesis.antlr.language.formatting.AntlrCounters.LINE_COMMENT_COUNT;
import static org.nemesis.antlr.language.formatting.AntlrCounters.LINE_COMMENT_INDENT;
import static org.nemesis.antlr.language.formatting.AntlrCriteria.lineComments;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlrformatting.api.Criterion;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingState;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import org.nemesis.antlrformatting.api.ModalToken;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_NEWLINE_AND_INDENT;

/**
 *
 * @author Tim Boudreau
 */
public class LineCommentsFormatting extends AbstractFormatter {

    private final Criterion lc = lineComments();

    public LineCommentsFormatting(AntlrFormatterConfig config) {
        super(config);
    }

    @Override
    protected void rules(FormattingRules rules) {
        rules.onTokenType(LINE_COMMENT).wherePrevTokenType(LINE_COMMENT)
                .format(PREPEND_NEWLINE_AND_INDENT.bySpaces(LINE_COMMENT_INDENT).and(APPEND_NEWLINE));

        rules.onTokenType(PARSER_RULE_ID, TOKEN_ID, FRAGMENT)
                .wherePreviousTokenType(lineComments())
                .format(PREPEND_NEWLINE);

//        rules.replaceAdjacentTokens(new SemiAfterLineComment(), new RewriteOddlyOrderedLineComments());

        rules.onTokenType(lc).ifPrecededByNewline(true)
                .wherePrevTokenTypeNot(lc)
                .whereNextTokenType(lc)
                .format(PREPEND_NEWLINE_AND_INDENT.bySpaces(LINE_COMMENT_INDENT));
        rules.onTokenType(lc)
                .wherePreviousTokenType(lc)
                .whereNextTokenType(lc)
                .format(PREPEND_NEWLINE_AND_INDENT.bySpaces(LINE_COMMENT_INDENT).and(APPEND_NEWLINE));
//        rules.onTokenType(lc)
//                .wherePreviousTokenType(lc)
//                .whereNextTokenTypeNot(lc)
//                .format(PREPEND_NEWLINE_AND_INDENT.bySpaces(LINE_COMMENT_INDENT).and(APPEND_NEWLINE));

//        rules.onTokenType(lineComments())
//                .wherePreviousTokenType(lineComments())
//                .format(PREPEND_NEWLINE_AND_INDENT
//                        .bySpaces(AntlrCounters.LINE_COMMENT_INDENT));
//

    }

    @Override
    protected void state(LexingStateBuilder<AntlrCounters, ?> stateBuilder) {
        stateBuilder // Record the indent position of standalone line comments
                // Count line comments
                .count(LINE_COMMENT_COUNT).onEntering(LBRACE, BEGIN_ACTION/*, PARDEC_ID, TOKDEC_ID, FRAGDEC_ID*/)
                .countTokensMatching(lineComments())
                .scanningForwardUntil(LBRACE, END_ACTION);
    }

    private class RewriteOddlyOrderedLineComments implements BiFunction<List<? extends ModalToken>, LexingState, String> {

        @Override
        public String apply(List<? extends ModalToken> tokens, LexingState lexingState) {
            int lcpos = lexingState.get(LINE_COMMENT_INDENT);
            char[] ind = lcpos <= 0 ? new char[0] : new char[lcpos];
            Arrays.fill(ind, ' ');
            if (tokens.size() > 1 && tokens.get(tokens.size() - 1).getType() == SEMI) {
                StringBuilder sb = new StringBuilder("; ");
                for (int i = 0; i < tokens.size(); i++) {
                    Token tk = tokens.get(i);
                    if (lineComments().test(tk.getType())) {
                        String txt = tk.getText();
                        if (txt.startsWith("// ")) {
                            txt = "//" + txt.substring(3);
                        }
                        if (tokens.size() > 2 && i == 0) {
                            txt = "\n" + new String(ind) + txt;
                        }
                        sb.append(txt).append("\n");
                    }
                }
                return sb.toString();
            }
            return null;
        }

        @Override
        public String toString() {
            return "reorder-semicolon-following-line-comment";
        }
    }

    static class SemiAfterLineComment implements Criterion {

        private boolean lastWasLineComment;

        @Override
        public boolean test(int value) {
            if (lastWasLineComment) {
                lastWasLineComment = false;
                return value == SEMI;
            }
            lastWasLineComment = lineComments().test(value);
            return lastWasLineComment;
        }

        @Override
        public String toString() {
            return "semicolon-following-line-comment";
        }
    }

}
