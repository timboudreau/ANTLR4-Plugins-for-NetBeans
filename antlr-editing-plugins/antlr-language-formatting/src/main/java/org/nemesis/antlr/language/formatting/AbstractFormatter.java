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

import java.util.function.IntPredicate;
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import static org.nemesis.antlr.language.formatting.AntlrCounters.COLON_POSITION;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlrformatting.api.Criteria;
import org.nemesis.antlrformatting.api.Criterion;
import org.nemesis.antlrformatting.api.FormattingAction;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_NEWLINE_AND_DOUBLE_INDENT;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.APPEND_NEWLINE_AND_INDENT;
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

    public AbstractFormatter(AntlrFormatterConfig config) {
        this.config = config;
        doubleIndentForWrappedLines = APPEND_NEWLINE_AND_DOUBLE_INDENT
                .by(config.getIndent());

        indentToColonPosition = APPEND_NEWLINE_AND_INDENT
                .bySpaces(config.getIndent() - 1, COLON_POSITION);

        if (config.isWrap()) {
            indentCurrent = PREPEND_NEWLINE_AND_INDENT
                    .by(config.getIndent())
                    .wrappingLines(config.getMaxLineLength(), doubleIndentForWrappedLines);

            spaceOrWrap = PREPEND_SPACE.wrappingLines(config.getMaxLineLength(),
                    PREPEND_NEWLINE_AND_INDENT.bySpaces(config.getIndent() - 1, COLON_POSITION));
        } else {
            indentCurrent = PREPEND_NEWLINE_AND_INDENT
                    .by(config.getIndent());
            spaceOrWrap = PREPEND_SPACE;
        }

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
        prependNewlineAndIndent = config.isFloatingIndent()
                ? PREPEND_NEWLINE_AND_INDENT.bySpaces(-1, COLON_POSITION)
                : PREPEND_NEWLINE_AND_INDENT;
        prependNewlineAndDoubleIndent = config.isFloatingIndent()
                ? PREPEND_NEWLINE_AND_INDENT.bySpaces(config.getIndent() - 1, COLON_POSITION)
                : PREPEND_NEWLINE_AND_INDENT;
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
