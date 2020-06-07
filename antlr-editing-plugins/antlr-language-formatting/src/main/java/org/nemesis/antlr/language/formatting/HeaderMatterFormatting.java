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
import com.mastfrog.function.IntBiPredicate;
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import static org.nemesis.antlr.language.formatting.AbstractFormatter.MODE_OPTIONS;
import static org.nemesis.antlr.language.formatting.AntlrCounters.DISTANCE_TO_LBRACE;
import static org.nemesis.antlr.language.formatting.AntlrCounters.SEMICOLON_COUNT;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
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

        rules.whenInMode(AntlrCriteria.mode(MODE_OPTIONS), fr -> {
            fr.onTokenType(criteria.anyOf(SUPER_CLASS, LANGUAGE, TOKEN_VOCAB, TOKEN_LABEL_TYPE)).wherePreviousTokenType(criteria.matching(LBRACE))
                    .format(spaceOrWrap);
            fr.onTokenType(LBRACE).wherePreviousTokenType(OPTIONS)
                    .format(PREPEND_SPACE);
        });

        rules.onTokenType(ID).wherePreviousTokenType(GRAMMAR, IMPORT)
                .format(PREPEND_SPACE);

        rules.onTokenType(SEMI)
                .whereModeTransition(IntBiPredicate.fromPredicates(AntlrCriteria.mode(MODE_DEFAULT, MODE_IMPORT),
                        AntlrCriteria.mode(MODE_DEFAULT)))
                .format(APPEND_DOUBLE_NEWLINE);

//        rules.whenInMode(AntlrCriteria.mode(MODE_OPTIONS), fr -> {
//
//        });
        rules.onTokenType(RBRACE).whereModeTransition(IntBiPredicate.fromPredicates(AntlrCriteria.mode(MODE_OPTIONS), Criterion.ALWAYS))
                .format(APPEND_DOUBLE_NEWLINE);


//        rules.onTokenType(SEMI).wherePreviousTokenType(ID).format(prependNewlineAndDoubleIndent);
//        rules.onTokenType(ID).whenEnteringMode(Arrays.asList(modeNames).indexOf(MODE_IDENTIFIER))
//                .format(PREPEND_SPACE);
        rules.whenInMode(AntlrCriteria.mode(MODE_ACTION), rls -> {
            rls.onTokenType(BEGIN_ACTION)
                    .when(AntlrCounters.LEFT_BRACES_PASSED).isEqualTo(1)
                    .whereNextTokenTypeNot(AntlrCriteria.whitespace())
                    .when(AntlrCounters.SEMICOLON_COUNT).isLessThanOrEqualTo(1)
                    //                    .wherePreviousTokenType(MEMBERS)
                    .format(PREPEND_SPACE);
        });
        rules.whenInMode(AntlrCriteria.mode(MODE_ACTION), rls -> {
            rls.onTokenType(BEGIN_ACTION)
                    .when(AntlrCounters.LEFT_BRACES_PASSED).isEqualTo(1)
                    .whereNextTokenTypeNot(AntlrCriteria.whitespace())
                    .when(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1)
                    //                    .wherePreviousTokenType(MEMBERS)
                    .format(PREPEND_SPACE);
//                    .format(PREPEND_SPACE.and(APPEND_NEWLINE.APPEND_NEWLINE_AND_INDENT));
        });

        rules.whenInMode(grammarRuleModes, rls -> {
            rls.onTokenType(keywordsOrIds).wherePreviousTokenType(END_ACTION)
                    .priority(100)
                    .format(PREPEND_DOUBLE_NEWLINE);
        });
    }

    @Override
    protected void state(LexingStateBuilder<AntlrCounters, ?> stateBuilder) {
        stateBuilder.computeTokenDistance(DISTANCE_TO_LBRACE)
                .onEntering(ANTLRv4Lexer.BEGIN_ACTION)
                .toNext(LBRACE, BEGIN_ACTION)
                .ignoringWhitespace();
    }


}
