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
        rules.onTokenType(LINE_COMMENT).wherePreviousTokenType(LINE_COMMENT)
                .format(PREPEND_NEWLINE_AND_INDENT.bySpaces(LINE_COMMENT_INDENT).and(APPEND_NEWLINE));

        rules.onTokenType(PARSER_RULE_ID, TOKEN_ID, FRAGMENT)
                .wherePreviousTokenType(lineComments())
                .format(PREPEND_NEWLINE);

//        rules.replaceAdjacentTokens(new SemiAfterLineComment(), new RewriteOddlyOrderedLineComments());

        rules.onTokenType(lc).ifPrecededByNewline(true)
                .wherePreviousTokenTypeNot(lc)
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
