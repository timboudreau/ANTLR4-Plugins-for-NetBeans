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

import static org.nemesis.antlr.ANTLRv4Lexer.COLON;
import static org.nemesis.antlr.ANTLRv4Lexer.ID;
import static org.nemesis.antlr.ANTLRv4Lexer.LPAREN;
import static org.nemesis.antlr.ANTLRv4Lexer.RPAREN;
import static org.nemesis.antlr.language.formatting.AntlrCriteria.lineComments;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_SPACE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_SPACE;

/**
 *
 * @author Tim Boudreau
 */
final class ParenthesesSpacing extends AbstractFormatter {

    ParenthesesSpacing(AntlrFormatterConfig config) {
        super(config);
    }

    @Override
    protected void rules(FormattingRules rules) {
        rules.onTokenType(LPAREN).wherePrevTokenTypeNot(lineComments().or(criteria.matching(LPAREN)))
                .whereNextTokenTypeNot(LPAREN)
                .priority(100)
                .format(APPEND_SPACE);
        rules.onTokenType(LPAREN).wherePrevTokenType(COLON).format(PREPEND_SPACE);
        rules.onTokenType(ID)
                .wherePrevTokenType(COLON)
                .priority(100)
                .format(PREPEND_SPACE);
        rules.onTokenType(ruleOpeners).wherePrevTokenType(LPAREN)
                .priority(100)
                .format(PREPEND_SPACE);
        rules.onTokenType(RPAREN).wherePrevTokenTypeNot(lineComments().or(criteria.anyOf(RPAREN, LPAREN)))
                .priority(100)
                .format(PREPEND_SPACE);
    }

    @Override
    protected void state(LexingStateBuilder<AntlrCounters, ?> stateBuilder) {
    }
}
