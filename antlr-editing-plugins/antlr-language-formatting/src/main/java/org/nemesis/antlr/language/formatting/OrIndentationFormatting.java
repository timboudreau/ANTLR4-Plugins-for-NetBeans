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

import static org.nemesis.antlr.ANTLRv4Lexer.*;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.OrHandling;
import org.nemesis.antlrformatting.api.FormattingRules;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.*;

/**
 *
 * @author Tim Boudreau
 */
public class OrIndentationFormatting extends AbstractFormatter {

    public OrIndentationFormatting(AntlrFormatterConfig config) {
        super(config);
    }

    @Override
    protected void rules(FormattingRules rules) {
        if (config.getOrHandling() != OrHandling.NO_HANDLING) {
            boolean align = config.getOrHandling() == OrHandling.ALIGN_WITH_PARENTHESES;
            rules.whenInMode(grammarRuleModes, rls -> {
                rls.onTokenType(OR).when(AntlrCounters.PARENS_DEPTH).isGreaterThan(0)
                        .whereNextTokenType(LPAREN)/* .wherePreviousTokenType(RPAREN) */
                        .format(align ? PREPEND_NEWLINE_AND_INDENT.bySpaces(AntlrCounters.LEFT_PAREN_POS)
                                : PREPEND_NEWLINE_AND_INDENT.by(AntlrCounters.PARENS_DEPTH)).priority(1000);
            });
        }
    }
}
