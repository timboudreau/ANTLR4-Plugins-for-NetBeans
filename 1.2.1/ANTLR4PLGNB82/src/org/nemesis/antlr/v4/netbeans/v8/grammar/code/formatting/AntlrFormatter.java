package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.Arrays;
import java.util.BitSet;
import javax.swing.text.BadLocationException;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.COMMA;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.ID;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.IMPORT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.LINE_COMMENT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.LPAREN;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.QUESTION;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.SEMI;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.VOCABULARY;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.ACTION_CONTENT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.ASSIGN;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.AT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.CHANNELS;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.COLON;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.DOT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.FRAGDEC_ID;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.FRAGDEC_LINE_COMMENT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.GRAMMAR;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.HDR_IMPRT_LINE_COMMENT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.HEADER_IMPORT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.LEXER;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.LEXER_CHAR_SET;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.OPTIONS;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.LBRACE;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.RBRACE;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.OR;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.PARSER;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.PARSER_RULE_ID;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.PLUS;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.RANGE;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.RARROW;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.RPAREN;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.SHARP;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.STAR;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.STRING_LITERAL;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.TOKEN_ID;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.BEGIN_ACTION;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.COLONCOLON;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.END_ACTION;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.PARDEC_LINE_COMMENT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.TOK_LINE_COMMENT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.FRAGMENT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.AntlrCriteria.mode;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.AntlrCriteria.notMode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.AntlrFormatterSettings.NewlineStyle;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.Criterion.anyOf;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.Criterion.matching;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.Criterion.noneOf;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.SimpleFormattingAction.APPEND_DOUBLE_NEWLINE;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.SimpleFormattingAction.APPEND_NEWLINE;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.SimpleFormattingAction.APPEND_SPACE;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.SimpleFormattingAction.PREPEND_NEWLINE;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.SimpleFormattingAction.PREPEND_NEWLINE_AND_DOUBLE_INDENT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.SimpleFormattingAction.PREPEND_NEWLINE_AND_INDENT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.SimpleFormattingAction.PREPEND_SPACE;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.Formatter;
import org.netbeans.modules.editor.indent.spi.Context;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.spi.lexer.MutableTextInput;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrFormatter implements Formatter {

    private static final BitSet LOG_TYPES
            = new BitSet(ANTLRv4Lexer.VOCABULARY.getMaxTokenType() + 1);

    static {
//        LOG_TYPES.set(SEMI);
//        LOG_TYPES.set(LINE_COMMENT);
//        LOG_TYPES.set(ID);
//        LOG_TYPES.set(LBRACE);
//        LOG_TYPES.set(RBRACE);
//        LOG_TYPES.set(BEGIN_ACTION);
//        LOG_TYPES.set(END_ACTION);
//        LOG_TYPES.set(ACTION_CONTENT);
//        LOG_TYPES.set(ID);
//        LOG_TYPES.set(PARSER);
//        LOG_TYPES.set(MEMBERS);
//        LOG_TYPES.set(IMPORT);
//        LOG_TYPES.set(COLONCOLON);
//        LOG_TYPES.set(ASSIGN);
//        LOG_TYPES.set(GRAMMAR);
//        LOG_TYPES.set(HEADER_IMPORT);
//        LOG_TYPES.set(RPAREN);
//        LOG_TYPES.set(BEGIN_ACTION);
//        LOG_TYPES.set(ACTION_CONTENT);
//        LOG_TYPES.set(PARSER_RULE_ID);
//        LOG_TYPES.set(OPTIONS);
//        LOG_TYPES.set(LBRACE);
//        LOG_TYPES.set(RBRACE);
//        LOG_TYPES.set(STAR);
//        LOG_TYPES.set(QUESTION);
    }

    static boolean isLoggable(int type) {
        if (type < 0) {
            return false;
        }
        return LOG_TYPES.get(type);
    }
    private final AntlrFormatterSettings settings;

    public AntlrFormatter() {
        this(AntlrFormatterSettings.getDefault());
    }

    AntlrFormatter(AntlrFormatterSettings settings) {
        this.settings = settings;
    }

    @Override
    public void reindent(Context cntxt) {
        reformat(cntxt, null);
    }

    @Override
    public boolean needsParserResult() {
        return false;
    }

    @Override
    public int indentSize() {
        return settings.getIndentSize();
    }

    @Override
    public int hangingIndentSize() {
        return settings.getIndentSize() * 2;
    }

    @Override
    public void reformat(Context cntxt, ParserResult pr) {
        BaseDocument document = (BaseDocument) cntxt.document();
        int start = cntxt.startOffset();
        int end = cntxt.endOffset();
        document.runAtomic(() -> {
            MutableTextInput<?> mti = (MutableTextInput<?>) document.getProperty(MutableTextInput.class);
            try {
                mti.tokenHierarchyControl().setActive(false);
                ANTLRv4Lexer lexer = new ANTLRv4Lexer(CharStreams.fromString(document.getText(0, document.getLength())));
                lexer.removeErrorListeners();
                String reformatted = reformat(lexer, start, end, settings);
                document.replace(start, end, reformatted, null);
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                mti.tokenHierarchyControl().setActive(true);
            }
        });
    }

    static String reformat(ANTLRv4Lexer lexer, int start, int end, AntlrFormatterSettings config) {
        EverythingTokenStream tokens = new EverythingTokenStream(lexer, ANTLRv4Lexer.modeNames);
        TokenStreamRewriter rew = new TokenStreamRewriter(tokens);
        return new FormattingContextImpl(rew, start, end, config.getIndentSize(), rules(config))
                .go(lexer, tokens);
    }

    static String indentString(int count) {
        char[] c = new char[Math.max(0, count)];
        Arrays.fill(c, ' ');
        return new String(c);
    }

    static void rewriteBlockComment(Token tok, FormattingContext ctx) {
        String textContent = tok.getText().trim().substring(2, tok.getText().length() - 4);
        int lineOffset = ctx.origCharPositionInLine();
        char[] c = new char[lineOffset];
        Arrays.fill(c, ' ');
        StringBuilder sb = new StringBuilder("\n").append(c).append("/*");
        String[] lines = textContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                sb.append("\n");
            } else {
                sb.append(c);
                sb.append(line);
                sb.append("\n");
            }
        }
        ctx.replace(sb.append("/*\n").toString());
    }

    static FormattingRules rules(AntlrFormatterSettings config) {
        FormattingRules rules = new FormattingRules(ANTLRv4Lexer.VOCABULARY);
        final Criterion lineComments = AntlrCriteria.lineComments();

        rules.onTokenType(ID)
                .wherePreviousTokenTypeNot(SHARP, LPAREN, HEADER_IMPORT)
                // Ensure we don't get import java. util. Blah with spaces inserted
                .whereMode(notMode("DEFAULT_MODE", "HeaderPrelude", "HeaderImport", "HeaderAction"))
                .format(PREPEND_SPACE);

        rules.onTokenType(ID)
                .whereMode(mode("HeaderImport", "HeaderAction"))
                .wherePreviousTokenTypeNot(DOT, LPAREN, ASSIGN)
                .format(PREPEND_SPACE);

        rules.onTokenType(AT).format(PREPEND_NEWLINE);

        // Options block handling
        rules.onTokenType(LBRACE, BEGIN_ACTION).whereMode(mode("Options", "DEFAULT_MODE",
                "HeaderPrelude", "HeaderAction", "Action", "ParserRuleDeclaration"))
                .priority(5)
                .format((tok, ctx) -> {
                    int pos;
                    int numSemicolons = ctx.countForwardOccurrencesUntilNext(anyOf(VOCABULARY, SEMI, ACTION_CONTENT), anyOf(VOCABULARY, END_ACTION, RBRACE));
                    if (numSemicolons <= 1) {
                        pos = -1;
                        ctx.appendSpace();
                    } else if (config.isNewlineAfterColon()) {
                        ctx.appendNewlineAndIndent();
                        pos = ctx.currentCharPositionInLine();
                    } else {
                        pos = ctx.currentCharPositionInLine();
                    }
                    ctx.prependSpace();
                    ctx.set("lbr", pos);
                });

        rules.onTokenType(ID)
                .whereMode(mode("HeaderPrelude", "HeaderAction", "Action", "Options"))
                .wherePrevTokenType(LPAREN, ASSIGN, DOT)
                .priority(4)
                .format(FormattingAction.EMPTY);

        rules.onTokenType(RBRACE, END_ACTION)
                .whereMode(mode("Options", "DEFAULT_MODE", "HeaderPrelude", "HeaderAction",
                        "Action", "ParserRuleDeclaration"))
                .whereNextTokenTypeNot(-1)
                .priority(5)
                .format((tok, ctx) -> {
                    int indentTo = ctx.get("lbr", config.getIndentSize());
                    if (indentTo == -1) {
                        ctx.prependSpace();
                    } else if (config.isNewlineAfterColon()) {
                        ctx.prependNewlineAndIndentBy(indentTo);
                    } else {
                        ctx.prependNewlineAndIndentBy(indentTo);
                    }
                    int nextSemi = ctx.tokenCountToNext(true, SEMI);
                    if (nextSemi > 0) {
                        ctx.appendNewline();
                    }
                    ctx.set("lbr", 0);
                });
        
        rules.onTokenType(SEMI).whereMode(mode("DEFAULT_MODE"))
                .whereNextTokenTypeNot(
                        AntlrCriteria.lineComments()
                                .or(AntlrCriteria::isBlockComment)
                                .or(matching(VOCABULARY, -1)))
                .format(APPEND_DOUBLE_NEWLINE);

        rules.onTokenType(SEMI).whereMode(mode("DEFAULT_MODE"))
                .whereNextTokenType(
                        AntlrCriteria.lineComments()
                                .or(AntlrCriteria::isBlockComment)
                                .or(matching(VOCABULARY, -1)))
                .format(APPEND_SPACE);

        rules.onTokenType(Criterion.noneOf(VOCABULARY, SEMI, BEGIN_ACTION, END_ACTION, LBRACE, RBRACE, OPTIONS, AT))
                .ifPrecededByNewline(true)
                .whereMode(mode("Options", "HeaderPrelude", "HeaderAction", "HeaderImport", "Action",
                        "ParserRuleContext"))
                .format((tok, ctx) -> {
                    int amt = ctx.get("lbr", config.getIndentSize() * 2);
                    if (amt > 0) {
                        ctx.prependNewlineAndIndentBy(amt);
                    }
                });

        // Parser and lexer headers
        rules.onTokenType(PARSER, LEXER).whereMode(mode("DEFAULT_MODE"))
                .whereNextTokenType(COLONCOLON)
                .wherePreviousTokenType(AT)
                .priority(3)
                .format(FormattingAction.EMPTY); // Do nothing we just want to defeat space insertion

        rules.onTokenType(HEADER_IMPORT)
                .format(APPEND_SPACE.and(PREPEND_NEWLINE_AND_DOUBLE_INDENT));

        rules.onTokenType(SHARP).format(PREPEND_SPACE);

        if (config.getNewlineStyle().isDoubleNewline()) {
            rules.onTokenType(SEMI)
                    .whereMode(notMode("Options", "DEFAULT_MODE", "HeaderPrelude", "Action"))
                    .whereNextTokenTypeNot(-1, LINE_COMMENT, PARDEC_LINE_COMMENT, TOK_LINE_COMMENT, FRAGDEC_LINE_COMMENT)
                    .format(APPEND_DOUBLE_NEWLINE.unless(ctx -> {
                        if (ctx.get("lbr", 1) == -1) {
                            return true;
                        }
                        if (config.getNewlineStyle() == NewlineStyle.IF_COMPLEX) {
                            if (ctx.tokenCountToPreceding(true, COLON) < 3) {
                                ctx.appendNewline();
                                return true;
                            }
                        }
                        return false;
                    }));
        } else {
            rules.onTokenType(SEMI)
                    .whereMode(notMode("Options", "DEFAULT_MODE", "HeaderPrelude", "Action"))
                    .whereNextTokenTypeNot(-1, LINE_COMMENT, PARDEC_LINE_COMMENT, TOK_LINE_COMMENT, FRAGDEC_LINE_COMMENT)
                    .format(APPEND_NEWLINE.unless(ctx -> {
                        return ctx.get("lbr", 1) == -1;
                    }));
        }

        rules.onTokenType(QUESTION)
                .whereNextTokenTypeNot(SEMI, RPAREN)
                .format(APPEND_SPACE);

        rules.onTokenType(TOKEN_ID, PARSER_RULE_ID, FRAGDEC_ID, ID)
                .whereMode(notMode("HeaderImport", "HeaderAction", "HeaderPrelude", "Action"))
                .wherePrevTokenType(TOKEN_ID, PARSER_RULE_ID, FRAGDEC_ID, ID)
                .wherePreviousTokenTypeNot(SEMI, ASSIGN, SHARP)
                .ifPrecededByNewline(false)
                .format(PREPEND_SPACE).named("Precede id with space");

        rules.onTokenType(FRAGMENT).format(APPEND_SPACE);

        rules.onTokenType(TOKEN_ID, PARSER_RULE_ID, FRAGDEC_ID)
                .wherePreviousTokenTypeNot(LPAREN, ASSIGN, SHARP)
                .whereNextTokenTypeNot(RPAREN, SEMI)
                .ifPrecededByNewline(false)
                .format(PREPEND_SPACE.and(APPEND_SPACE))
                .named("Prepend space if not assigment, first paren element or label");

        rules.onTokenType(TOKEN_ID, PARSER_RULE_ID, FRAGDEC_ID)
                .whereNextTokenType(OR, TOKEN_ID, PARSER_RULE_ID, FRAGDEC_ID, STRING_LITERAL, LEXER_CHAR_SET)
                .format(APPEND_SPACE)
                .named("Append space to id before OR or another rule ID, literal or charset");

        if (config.isSpacesInsideParentheses()) {
            rules.onTokenType(TOKEN_ID, PARSER_RULE_ID, FRAGDEC_ID)
                    .priority(2)
                    .wherePrevTokenType(LPAREN)
                    .format(PREPEND_SPACE);
        }

        rules.onTokenType(RARROW).format(PREPEND_SPACE.and(APPEND_SPACE));
        rules.onTokenType(COMMA).format(APPEND_SPACE);

        rules.onTokenType(STAR, PLUS, QUESTION)
                .whereNextTokenTypeNot(QUESTION, SEMI)
                .format(APPEND_SPACE);

        rules.onTokenType(IMPORT).format(PREPEND_NEWLINE.and(APPEND_SPACE));

        // The grammar we have inherited identifies EVERY { in an action
        // as another BEGIN_ACTION token, meaning if we aren't careful we
        // can move them out of line comments into code
        rules.onTokenType(ANTLRv4Lexer.BEGIN_ACTION)
                .wherePreviousTokenTypeNot(ACTION_CONTENT)
                .whereMode(notMode("HeaderPrelude", "HeaderAction"))
                .format(PREPEND_NEWLINE_AND_INDENT
                        .and(APPEND_NEWLINE).unless(ctx -> {
                    int braceCount = ctx.get("beginActionBraces", 0);
                    return braceCount > 1;
                }));

        rules.onTokenType(ANTLRv4Lexer.BEGIN_ACTION)
                .whereMode(notMode("HeaderAction", "DEFAULT_MODE", "HeaderPrelude", "HeaderImport"))
                .format(PREPEND_NEWLINE_AND_INDENT);

//        rules.onTokenType(ANTLRv4Lexer.ACTION_CONTENT)
//                .format(PREPEND_NEWLINE_AND_INDENT);
        // In the case of, e.g. Dot { ... }? don't put the ? or ; on its own line
        rules.onTokenType(ANTLRv4Lexer.END_ACTION)
                .whereMode(notMode("HeaderAction", "HeaderPrelude", "DEFAULT_MODE", "HeaderImport"))
                .whereNextTokenTypeNot(STAR, QUESTION, PLUS, SHARP, SEMI)
                .format(PREPEND_NEWLINE_AND_INDENT.and(APPEND_NEWLINE));

        rules.onTokenType(ANTLRv4Lexer.END_ACTION)
                .whereMode(notMode("HeaderAction", "HeaderPrelude", "DEFAULT_MODE", "HeaderImport"))
                .whereNextTokenType(STAR, QUESTION, PLUS)
                .format(PREPEND_NEWLINE_AND_INDENT);

        rules.onTokenType(ANTLRv4Lexer.LEXER_CHAR_SET)
                .whereNextTokenTypeNot(RPAREN, STAR, QUESTION, PLUS, SEMI, LINE_COMMENT)
                .wherePreviousTokenTypeNot(LPAREN)
                .format(PREPEND_SPACE.and(APPEND_SPACE));

        rules.onTokenType(STRING_LITERAL).wherePreviousTokenTypeNot(ASSIGN, LPAREN, RANGE)
                .format(PREPEND_SPACE);

        rules.onTokenType(LEXER, PARSER)
                .wherePreviousTokenType(AntlrCriteria::isBlockComment)
                .format(PREPEND_NEWLINE.and(APPEND_SPACE));

        rules.onTokenType(GRAMMAR)
                .wherePreviousTokenTypeNot(LEXER, PARSER, -1)
                .format(PREPEND_NEWLINE);

        rules.onTokenType(GRAMMAR)
                .wherePreviousTokenType(AntlrCriteria::isBlockComment)
                .format(PREPEND_NEWLINE);

        rules.onTokenType(lineComments)
                .format(APPEND_NEWLINE)
                .and()
                .priority(6)
                .ifPrecededByNewline(false)
                .format(PREPEND_SPACE.and(APPEND_NEWLINE));

        rules.onTokenType(OPTIONS)
                .format(APPEND_SPACE);

        // In the case that we have, e.g.
        //    foo // some comment
        //   | bar // some comment
        // the OR indenter/splitter will take care of appending
        // a newline and doing the indent, so we need a rule
        // to append newlines after line comments only when an
        // OR is not present
        rules.onTokenType(lineComments)
                .whereNextTokenType(OR)
                .ifPrecededByNewline(false)
                .priority(2)
                .format((tok, ctx) -> {
                    int bd = ctx.get("blockDepth", 0);
                    if (bd != 0) {
                        ctx.appendNewline();
                    }
                });

        rules.onTokenType(AntlrCriteria::isBlockComment)
                .wherePreviousTokenTypeNot(-1)
                .whereNextTokenTypeNot(GRAMMAR, LEXER, PARSER)
                .format(PREPEND_NEWLINE.and(APPEND_NEWLINE));

        rules.onTokenType(LEXER, PARSER).format(APPEND_SPACE);

        rules.onTokenType(ANTLRv4Lexer.OR).format((tok, ctx) -> {
            int bd = ctx.get("blockDepth", 0);
            int colonPos = ctx.get("colon", config.getIndentSize());
            if (bd == 0) {
                ctx.prependNewlineAndIndentBy(colonPos);
                ctx.appendSpace();
            } else {
                if (config.isWrapLines() && ctx.currentCharPositionInLine() > config.getWrapPoint() - 12) {
                    ctx.prependNewlineAndIndentBy(colonPos + config.getIndentSize());
                } else {
                    ctx.prependSpace();
                }
                ctx.appendSpace();
            }
        });

        if (config.isSpacesInsideParentheses()) {
            rules.onTokenType(LPAREN)
                    .wherePreviousTokenType(ASSIGN)
                    .priority(1) // ensure this rule is tested before the next one
                    .ifPrecededByNewline(false)
                    .format(APPEND_SPACE);
        }

        rules.onTokenType(HDR_IMPRT_LINE_COMMENT).format(APPEND_NEWLINE);

        rules.onTokenType(lineComments).wherePrevTokenType(ID, TOKEN_ID, DOT, STRING_LITERAL, PARSER_RULE_ID, FRAGDEC_ID, LPAREN, RPAREN)
                .format(PREPEND_SPACE);

        rules.onTokenType(LPAREN)
                .whereNextTokenTypeNot(LPAREN)
                .wherePreviousTokenTypeNot(ASSIGN, CHANNELS)
                .format(!config.isSpacesInsideParentheses() ? PREPEND_SPACE : APPEND_SPACE.and(PREPEND_SPACE));

        if (config.isSpacesInsideParentheses()) {
            rules.onTokenType(ID, PARSER_RULE_ID, FRAGDEC_ID, TOKEN_ID, DOT, STRING_LITERAL)
                    .whereNextTokenType(RPAREN).format(APPEND_SPACE);

            rules.onTokenType(RPAREN)
                    .priority(2)
                    .wherePreviousTokenType(
                            anyOf(VOCABULARY, ID, PARSER_RULE_ID, FRAGDEC_ID, TOKEN_ID, DOT, STRING_LITERAL)
                                    .and(noneOf(VOCABULARY, STAR, QUESTION, PLUS, RPAREN)))
                    .format(PREPEND_SPACE);
        }

        rules.onTokenType(RPAREN)
                .whereNextTokenTypeNot(LPAREN, RPAREN, STAR, QUESTION, PLUS)
                .wherePreviousTokenTypeNot(STAR, QUESTION, PLUS, RPAREN)
                .format((tok, ctx) -> {
                    int colonPos = ctx.get("colon", config.getIndentSize()) - 1;
                    if (colonPos == 0) {
                        colonPos = config.getIndentSize() - 1;
                    }
                    if (config.isWrapLines() && ctx.currentCharPositionInLine() > config.getWrapPoint() - 12) {
                        ctx.prependNewlineAndIndentBy(colonPos + config.getIndentSize());
                    } else {
                        if (config.isSpacesInsideParentheses()) {
                            ctx.prependSpace();
                        }
                    }
                });

        // Keep indentation of standalone line-comments, rather than
        // shifting them to the beginning of the line; this requires
        // three rules for first, middle and last to ensure we don't
        // insert extra newlines and a newline is always inserted
        // after the last, so we don't inadvertently comment out stuff
        rules.onTokenType(lineComments).ifPrecededByNewline(true)
                .wherePrevTokenTypeNot(lineComments)
                .whereNextTokenType(lineComments)
                .format((tok, ctx) -> {
                    ctx.set("lc", ctx.origCharPositionInLine());
                    ctx.prependNewlineAndIndentBy(ctx.origCharPositionInLine());
                });

        rules.onTokenType(lineComments).ifPrecededByNewline(true)
                .wherePreviousTokenType(lineComments)
                .whereNextTokenType(lineComments)
                .format((tok, ctx) -> {
                    int amt = ctx.get("lc", config.getIndentSize());
                    ctx.prependNewlineAndIndentBy(amt);
                });

        rules.onTokenType(lineComments).ifPrecededByNewline(true)
                .wherePreviousTokenType(lineComments)
                .whereNextTokenTypeNot(lineComments)
                .format((tok, ctx) -> {
                    int amt = ctx.get("lc", config.getIndentSize());
                    ctx.prependNewlineAndIndentBy(amt);
                    ctx.appendNewline();
                });

        // Alas, this wreaks unholy havoc - either our stream implementation
        // or something else is wrong - we get the rewritten block comment
        // repeated hundreds of times
//        rules.onTokenType(AntlrCriteria::isBlockComment).ifPrecededByNewline(true)
//                .format(AntlrFormatter::rewriteBlockComment);
        rules.onTokenType(COLON).format((tok, ctx) -> {
            if (config.isNewlineAfterColon()) {
                ctx.prependNewlineAndIndentBy(config.getIndentSize());
                ctx.set("colon", config.getIndentSize());
                ctx.appendSpace();
            } else {
                ctx.set("colon", ctx.currentCharPositionInLine() + 1);
                ctx.prependSpace();
                ctx.appendSpace();
            }
        });
        return rules;
    }
}
