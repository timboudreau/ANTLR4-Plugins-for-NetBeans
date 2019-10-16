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
package org.nemesis.antlrformatting.impl;

import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlrformatting.api.Criterion;
import org.nemesis.antlrformatting.api.FormattingResult;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingState;
import org.nemesis.antlrformatting.spi.AntlrFormatterProvider;

/**
 *
 * @author Tim Boudreau
 */
public abstract class FormattingAccessor {

    public static FormattingAccessor DEFAULT;

    public static FormattingAccessor getDefault() {
        if (DEFAULT != null) {
            return DEFAULT;
        }
        Class<?> type = LexingState.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(FormattingAccessor.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        assert DEFAULT != null : "The DEFAULT field must be initialized";
        type = AntlrFormatterProvider.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(FormattingAccessor.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
//        assert FMT_CONVERT != null : "The FMT_CONVERT field must be initialized";

        return DEFAULT;
    }

    public abstract FormattingRules createFormattingRules(Vocabulary vocabulary, String[] modeNames, String[] parserRuleNames);

    public abstract FormattingResult reformat(int start, int end, int indentSize,
            FormattingRules rules, LexingState state, Criterion whitespace,
            Predicate<Token> debug, Lexer lexer, String[] modeNames,
            CaretInfo caretPos, CaretFixer updateWithCaretPositionAndLength,
            RuleNode rootRuleNode);
}
