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

import java.util.function.IntPredicate;
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import static org.nemesis.antlr.language.formatting.AntlrCounters.COLON_POSITION;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import com.mastfrog.antlr.utils.Criteria;
import com.mastfrog.antlr.utils.Criterion;
import org.nemesis.antlrformatting.api.FormattingAction;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_NEWLINE_AND_INDENT;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.PREPEND_SPACE;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractFormatter {

    public static final String MODE_DEFAULT = "DEFAULT_MODE";
    public static final String MODE_ARGUMENT = "Argument";
    public static final String MODE_HEADER_PRELUDE = "HeaderPrelude";
    public static final String MODE_HEADER_ACTION = "HeaderAction";
    public static final String MODE_HEADER_PACKAGE = "HeaderPackage";
    public static final String MODE_HEADER_IMPORT = "HeaderImport";
    public static final String MODE_ACTION = "Action";
    public static final String MODE_OPTIONS = "Options";
    public static final String MODE_TOKENS = "Tokens";
    public static final String MODE_CHANNELS = "Channels";
    public static final String MODE_IMPORT = "Import";
    public static final String MODE_IDENTIFIER = "Identifier";
    public static final String MODE_TOKEN_DECLARATION = "TokenDeclaration";
    public static final String MODE_FRAGMENT_DECLARATION = "FragmentDeclaration";
    public static final String MODE_PARSER_RULE_DECLARATION = "ParserRuleDeclaration";
    public static final String MODE_PARSER_RULE_OPTIONS = "ParserRuleOptions";
    public static final String MODE_LEXER_COMMANDS = "LexerCommands";
    public static final String MODE_TYPE_LEXER_COMMAND = "TypeLexerCommand";
    public static final String MODE_LEXER_CHAR_SET = "LexerCharSet";
    public static final String INDENT = "indent";
    public static final String MAX_LINE_LENGTH = "maxLineLength";

    final AntlrFormatterConfig config;
    protected final FormattingAction doubleIndentForWrappedLines;
    protected final FormattingAction indentCurrent;
    protected final FormattingAction indentToColonPosition;
    protected final FormattingAction spaceOrWrap;
    protected final Criteria criteria = Criteria.forVocabulary(VOCABULARY);
    protected final Criterion allIds;
    protected final Criterion allKeywords;
    protected final Criterion keywordsOrIds;
    protected final Criterion ruleOpeners;
    protected final IntPredicate grammarRuleModes;
    protected final FormattingAction prependNewlineAndIndent;
    protected final FormattingAction prependNewlineAndDoubleIndent;
    protected final Criterion ruleEnders;

    boolean isFloatingIndent() {
        if (config.isFloatingIndent()) {
            switch (config.getColonHandling()) {
                case INLINE:
                case NEWLINE_AFTER:
                    return true;
                case STANDALONE:
                case NEWLINE_BEFORE:
                    return false;
            }
        }
        return false;
    }

    public AbstractFormatter(AntlrFormatterConfig config) {
        this.config = config;
        if (config.isFloatingIndentReallyEnabled()) {
            prependNewlineAndIndent = PREPEND_NEWLINE_AND_INDENT.bySpaces(1, COLON_POSITION);
            prependNewlineAndDoubleIndent
                    = PREPEND_NEWLINE_AND_INDENT.bySpaces(config.getIndent() + 1, COLON_POSITION);

            doubleIndentForWrappedLines = PREPEND_NEWLINE_AND_INDENT
                    .bySpaces(config.getIndent() + 1, COLON_POSITION);
            if (config.isWrap()) {
                indentCurrent = PREPEND_NEWLINE_AND_INDENT
                        .bySpaces(1, COLON_POSITION)
                        .wrappingLines(config.getMaxLineLength(), doubleIndentForWrappedLines);
                spaceOrWrap = PREPEND_SPACE.wrappingLines(config.getMaxLineLength(),
                        doubleIndentForWrappedLines);
            } else {
                spaceOrWrap = PREPEND_SPACE;
                indentCurrent = PREPEND_NEWLINE_AND_INDENT
                        .bySpaces(config.getIndent());
            }
        } else {
            prependNewlineAndIndent = PREPEND_NEWLINE_AND_INDENT.bySpaces(config.getIndent());
            prependNewlineAndDoubleIndent
                    = PREPEND_NEWLINE_AND_INDENT.bySpaces(config.getIndent() * 2);
            doubleIndentForWrappedLines = PREPEND_NEWLINE_AND_INDENT
                    .bySpaces(config.getIndent() * 2);
            if (config.isWrap()) {
                indentCurrent = PREPEND_NEWLINE_AND_INDENT
                        .bySpaces(config.getIndent())
                        .wrappingLines(config.getMaxLineLength(), doubleIndentForWrappedLines);
                spaceOrWrap = PREPEND_SPACE.wrappingLines(config.getMaxLineLength(),
                        doubleIndentForWrappedLines);
            } else {
                spaceOrWrap = PREPEND_SPACE;
                indentCurrent = PREPEND_NEWLINE_AND_INDENT
                        .bySpaces(config.getIndent());
            }
        }

        indentToColonPosition = PREPEND_NEWLINE_AND_INDENT
                .bySpaces(config.getIndent(), COLON_POSITION);

        allIds = criteria.anyOf(PARSER_RULE_ID, TOKEN_OR_PARSER_RULE_ID,
                TOKEN_ID, PARDEC_ID, TOK_ID, ID, TOKDEC_ID, FRAGDEC_ID);

        ruleOpeners = allIds.or(criteria.anyOf(STRING_LITERAL, PARDEC_BEGIN_ARGUMENT, BEGIN_ARGUMENT, RPAREN, LEXER_CHAR_SET));
        ruleEnders = allIds.or(criteria.anyOf(STRING_LITERAL, END_ARGUMENT, RPAREN, PLUS, STAR, QUESTION, ID, LEXER_CHAR_SET));

        allKeywords = criteria.anyOf(CHANNELS, FAIL,
                FINALLY, FRAGMENT, GRAMMAR,
                TOKENS, RETURNS, HEADER, PARSER,
                LEXER, LANGUAGE, LOCALS, TOKENS,
                SKIP, AFTER, IMPORT, OPTIONS,
                THROWS, SUPER_CLASS, TOKEN_VOCAB, AFTER, ASSOC);

        keywordsOrIds = allIds.or(allKeywords);

        grammarRuleModes = AntlrCriteria.mode(
                MODE_PARSER_RULE_DECLARATION,
                MODE_TOKEN_DECLARATION,
                MODE_FRAGMENT_DECLARATION
        );
    }

    public final void configure(LexingStateBuilder<AntlrCounters, ?> stateBuilder, FormattingRules rules) {
        state(stateBuilder);
        rules(rules);
    }

    protected final int indent() {
        return config.getIndent();
    }

    protected final int maxLineLength() {
        return config.getMaxLineLength();
    }

    protected void state(LexingStateBuilder<AntlrCounters, ?> stateBuilder) {

    }

    protected void rules(FormattingRules rules) {

    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
