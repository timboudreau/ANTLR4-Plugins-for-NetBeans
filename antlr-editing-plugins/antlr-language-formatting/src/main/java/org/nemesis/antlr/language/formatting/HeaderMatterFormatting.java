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
import static org.nemesis.antlr.language.formatting.AbstractFormatter.MODE_OPTIONS;
import static org.nemesis.antlr.language.formatting.AntlrCounters.SEMICOLON_COUNT;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlrformatting.api.FormattingRules;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_DOUBLE_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_SPACE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_DOUBLE_NEWLINE;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_SPACE;

/**
 *
 * @author Tim Boudreau
 */
class HeaderMatterFormatting extends AbstractFormatter {

    HeaderMatterFormatting(AntlrFormatterConfig config) {
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

        rules.whenInMode(AntlrCriteria.mode(MODE_OPTIONS), rls -> {
            rls.onTokenType(OPTIONS)
                    .format(PREPEND_DOUBLE_NEWLINE);
            rls.onTokenType(SEMI).when(SEMICOLON_COUNT).isEqualTo(1)
                    .format(APPEND_SPACE);
        });

        rules.whenInMode(AntlrCriteria.mode(MODE_DEFAULT), rls -> {
            rls.onTokenType(AT).format(PREPEND_DOUBLE_NEWLINE);
            rls.onTokenType(LEXER, PARSER).whereNextTokenType(GRAMMAR)
                    .format(APPEND_SPACE);
            rls.onTokenType(GRAMMAR).format(APPEND_SPACE);
            rls.onTokenType(criteria
                    .anyOf(AntlrCriteria.ALL_BLOCK_COMMENTS)).format(APPEND_NEWLINE);
        });

        rules.onTokenType(ID).wherePrevTokenType(GRAMMAR, IMPORT)
                .format(PREPEND_SPACE);

        rules.onTokenType(SEMI)
                .whereModeTransition(IntBiPredicate.fromPredicates(AntlrCriteria.mode(MODE_DEFAULT, MODE_IMPORT),
                        AntlrCriteria.mode(MODE_DEFAULT)))
                .format(APPEND_DOUBLE_NEWLINE);

//        rules.onTokenType(SEMI).wherePrevTokenType(ID).format(prependNewlineAndDoubleIndent);
//        rules.onTokenType(ID).whenEnteringMode(Arrays.asList(modeNames).indexOf(MODE_IDENTIFIER))
//                .format(PREPEND_SPACE);
        rules.whenInMode(AntlrCriteria.mode(MODE_ACTION), rls -> {
            rls.onTokenType(BEGIN_ACTION)
                    .when(AntlrCounters.LEFT_BRACES_PASSED).isEqualTo(1)
                    //                    .wherePreviousTokenType(MEMBERS)
                    .format(PREPEND_SPACE.and(APPEND_NEWLINE));
        });
        rules.whenInMode(grammarRuleModes, rls -> {
            rls.onTokenType(keywordsOrIds).wherePrevTokenType(END_ACTION)
                    .priority(100)
                    .format(PREPEND_DOUBLE_NEWLINE);
        });
    }
}
