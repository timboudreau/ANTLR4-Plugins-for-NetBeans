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

        rules.onTokenType(ID).wherePreviousTokenType(GRAMMAR, IMPORT)
                .format(PREPEND_SPACE);

        rules.onTokenType(SEMI)
                .whereModeTransition(IntBiPredicate.fromPredicates(AntlrCriteria.mode(MODE_DEFAULT, MODE_IMPORT),
                        AntlrCriteria.mode(MODE_DEFAULT)))
                .format(APPEND_DOUBLE_NEWLINE);

//        rules.onTokenType(SEMI).wherePreviousTokenType(ID).format(prependNewlineAndDoubleIndent);
//        rules.onTokenType(ID).whenEnteringMode(Arrays.asList(modeNames).indexOf(MODE_IDENTIFIER))
//                .format(PREPEND_SPACE);
        rules.whenInMode(AntlrCriteria.mode(MODE_ACTION), rls -> {
            rls.onTokenType(BEGIN_ACTION)
                    .when(AntlrCounters.LEFT_BRACES_PASSED).isEqualTo(1)
                    //                    .wherePreviousTokenType(MEMBERS)
                    .format(PREPEND_SPACE.and(APPEND_NEWLINE));
        });
        rules.whenInMode(grammarRuleModes, rls -> {
            rls.onTokenType(keywordsOrIds).wherePreviousTokenType(END_ACTION)
                    .priority(100)
                    .format(PREPEND_DOUBLE_NEWLINE);
        });
    }
}
