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
package org.nemesis.antlrformatting.grammarfile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer.*;
import org.nemesis.antlrformatting.api.Criteria;
import org.nemesis.antlrformatting.api.Criterion;
import static org.nemesis.antlrformatting.api.Criterion.anyOf;
import static org.nemesis.antlrformatting.api.Criterion.matching;
import static org.nemesis.antlrformatting.api.Criterion.noneOf;
import org.nemesis.antlrformatting.api.FormattingAction;
import org.nemesis.antlrformatting.api.FormattingContext;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingState;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import org.nemesis.antlrformatting.api.ModalToken;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.*;
import org.nemesis.antlrformatting.grammarfile.AntlrFormatterSettings.NewlineStyle;
import org.nemesis.antlrformatting.spi.AntlrFormatterProvider;
import org.netbeans.modules.editor.indent.spi.Context;

/**
 *
 * @author Tim Boudreau
 */
//@ServiceProvider(service = AntlrFormatterProvider.class, path = AntlrFormatters.BASE + ANTLR_MIME_TYPE)
public class AntlrGrammarLanguageFormatter extends AntlrFormatterProvider<AntlrFormatterSettings, AntlrCounters> {

    private static final String MODE_DEFAULT = "DEFAULT_MODE";
    private static final String MODE_ARGUMENT = "Argument";
    private static final String MODE_HEADER_PRELUDE = "HeaderPrelude";
    private static final String MODE_HEADER_ACTION = "HeaderAction";
    private static final String MODE_HEADER_PACKAGE = "HeaderPackage";
    private static final String MODE_HEADER_IMPORT = "HeaderImport";
    private static final String MODE_ACTION = "Action";
    private static final String MODE_OPTIONS = "Options";
    private static final String MODE_TOKENS = "Tokens";
    private static final String MODE_CHANNELS = "Channels";
    private static final String MODE_IMPORT = "Import";
    private static final String MODE_IDENTIFIER = "Identifier";
    private static final String MODE_TOKEN_DECLARATION = "TokenDeclaration";
    private static final String MODE_FRAGMENT_DECLARATION = "FragmentDeclaration";
    private static final String MODE_PARSER_RULE_DECLARATION = "ParserRuleDeclaration";
    private static final String MODE_PARSER_RULE_OPTIONS = "ParserRuleOptions";
    private static final String MODE_LEXER_COMMANDS = "LexerCommands";
    private static final String MODE_TYPE_LEXER_COMMAND = "TypeLexerCommand";
    private static final String MODE_LEXER_CHAR_SET = "LexerCharSet";

    private static final String[] EXPECTED_MODE_NAMES = {
        MODE_DEFAULT, MODE_ARGUMENT, MODE_HEADER_PRELUDE, MODE_HEADER_ACTION, MODE_HEADER_PACKAGE,
        MODE_HEADER_IMPORT, MODE_ACTION, MODE_OPTIONS, MODE_TOKENS, MODE_CHANNELS,
        MODE_IMPORT, MODE_IDENTIFIER, MODE_TOKEN_DECLARATION, MODE_FRAGMENT_DECLARATION,
        MODE_PARSER_RULE_DECLARATION, MODE_PARSER_RULE_OPTIONS, MODE_LEXER_COMMANDS,
        MODE_TYPE_LEXER_COMMAND, MODE_LEXER_CHAR_SET
    };

    private static String[] modeNames = {
        MODE_DEFAULT, MODE_ARGUMENT, MODE_HEADER_PRELUDE, MODE_HEADER_ACTION, MODE_HEADER_PACKAGE,
        MODE_HEADER_IMPORT, MODE_ACTION, MODE_OPTIONS, MODE_TOKENS, MODE_CHANNELS, MODE_IMPORT, MODE_IDENTIFIER,
        MODE_TOKEN_DECLARATION, MODE_FRAGMENT_DECLARATION, MODE_PARSER_RULE_DECLARATION, MODE_PARSER_RULE_OPTIONS,
        MODE_LEXER_COMMANDS, MODE_TYPE_LEXER_COMMAND, MODE_LEXER_CHAR_SET
    };


    static void checkExpectedModeNames(BiConsumer<Set<String>,Set<String>> consumer) {
        Set<String> expectedModeNames = new HashSet<>(Arrays.asList(EXPECTED_MODE_NAMES));
        Set<String> actualModeNames = new HashSet<>(Arrays.asList(ANTLRv4Lexer.modeNames));
        if (!expectedModeNames.equals(actualModeNames)) {
            System.err.println("ANTLRv4Lexer mode names have changed.");
            Set<String> missing = new HashSet<>(expectedModeNames);
            missing.removeAll(actualModeNames);
            if (!missing.isEmpty()) {
                System.err.println("Missing modes: " + missing);
            }
            Set<String> added = new HashSet<>(actualModeNames);
            added.removeAll(expectedModeNames);
            if (!added.isEmpty()) {
                System.err.println("Added modes: " + added);
            }
            if (!added.isEmpty() || !missing.isEmpty()) {
                consumer.accept(missing, added);
            }
        }
    }

    static {
        checkExpectedModeNames((missing, added) -> {
            Logger log = Logger.getLogger(AntlrGrammarLanguageFormatter.class.getName());
            log.log(Level.SEVERE, "Modes now available from Antlr grammar language grammar "
                    + "no longer conform to the list of mode names this formatter "
                    + "was written against.\nMissing:{0}\nAdded:{1}", new Object[] {missing, added});
        });
    }

    static Predicate<Token> LOG_TOKEN = t -> {
        System.out.println("\n'" + t.getText() + "' " + VOCABULARY.getSymbolicName(t.getType()));
        return true;
    };

    @Override
    protected void configure(LexingStateBuilder<AntlrCounters, ?> stateBuilder, FormattingRules rules, AntlrFormatterSettings config) {
        state(stateBuilder, config);
        rules(rules, config);
    }


    private static final Predicate<Token> DEBUG
            //            = Criterion.<Token>anyOf(VOCABULARY, AT, RBRACE, END_ACTION, SEMI)
            //                    .firstNmatches(5)
            //            .<Token>convertedBy(tok -> tok.getType()).and(LOG_TOKEN);
            //            = SequenceIntPredicate.matchingAnyOf(SEMI, END_ACTION).then(AT)
            //                    .or(SequenceIntPredicate.matchingAnyOf(RBRACE, END_ACTION))
            //                    .convertedBy(tok -> {
            //                        return tok.getType();
            //                    });
            = t -> {
                return false;
            };
    // Debug.builder().build();
//            Debug.builder().onTokenTypes(LBRACE, BEGIN_ACTION, RBRACE, END_ACTION, ACTION_CONTENT)
//            .enablingOn(ACTION_CONTENT).disablingOn(RBRACE,END_ACTION).build();

    private final AntlrFormatterSettings settings;

    public AntlrGrammarLanguageFormatter() {
        this(AntlrFormatterSettings.getDefault());
    }

    AntlrGrammarLanguageFormatter(AntlrFormatterSettings settings) {
        super(AntlrCounters.class);
        this.settings = settings;
    }

    @Override
    public int indentSize(AntlrFormatterSettings s) {
        return settings.getIndentSize();
    }

    @Override
    protected AntlrFormatterSettings configuration(Context ctx) {
        return settings;
    }

    @Override
    protected Lexer createLexer(CharStream stream) {
        return new ANTLRv4Lexer(stream);
    }

    @Override
    protected Vocabulary vocabulary() {
        return ANTLRv4Lexer.VOCABULARY;
    }

    @Override
    protected String[] modeNames() {
        return ANTLRv4Lexer.modeNames;
    }

    @Override
    protected Criterion whitespace() {
        return AntlrCriteria.whitespace();
    }

    @Override
    protected Predicate<Token> debugLogPredicate() {
        return DEBUG;
    }

    static String indentString(int count) {
        char[] c = new char[Math.max(0, count)];
        Arrays.fill(c, ' ');
        return new String(c);
    }

    static String spacesString(int count) {
        char[] c = new char[count];
        Arrays.fill(c, ' ');
        return new String(c);
    }

    static BiFunction<List<? extends ModalToken>, LexingState, String> unmangleActionContent(AntlrFormatterSettings config) {
        return (List<? extends ModalToken> list, LexingState lexingState) -> {
            if (list.size() == 1) {
                ModalToken only = list.get(0);
                if (only.getText().trim().isEmpty()) {
                    return null;
                }
            }
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (ModalToken tok : list) {
                sb.append(tok.getText());
            }
            String[] lines = sb.toString().split("\n");
            sb.setLength(0);
            int indent = lexingState.get(AntlrCounters.LEFT_BRACE_POSITION, lexingState.get(AntlrCounters.COLON_POSITION, config.getIndentSize()));
            String indentString = spacesString(indent);
            int lineIndex = 0;
            // Preserve relative indenting between lines:
            int[] indentDiffs = new int[lines.length];
            int minIndent = 0;
            int maxIndent = 0;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int trimmed = 0;
                if (line.trim().startsWith("//")) {
                    line = line.trim().substring(2);
                    trimmed = lines[i].length() - line.length();
                }
                int ix = indexOfFirstNonWhitespaceCharacter(line) + trimmed;
                if (ix >= 0) {
                    minIndent = Math.min(minIndent, ix);
                    maxIndent = Math.max(maxIndent, ix);
                }
            }
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int trimmed = 0;
                if (line.trim().startsWith("//")) {
                    line = line.trim().substring(2);
                    trimmed = lines[i].length() - line.length();
                }
                int ix = indexOfFirstNonWhitespaceCharacter(line) + trimmed;
                if (ix > 0) {
                    indentDiffs[i] = ix - minIndent;
                }
            }

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }
                String localIndent = spacesString(indentDiffs[i]);
                boolean leadingNewline = leadingNewline(lines[i]);
                if (line.startsWith("//")) {
                    line = line.substring(2).trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (lineIndex == 0) {
                        sb.append("\n");
                    }
                    localIndent = spacesString(Math.max(0, indentDiffs[i] - 5));
                    sb.append("//").append(indentString).append(localIndent);
                    sb.append(line).append('\n');
                    lineIndex++;
                    continue;
                }
                if (lineIndex > 0 || leadingNewline) {
                    sb.append(indentString).append(localIndent);
                }
                sb.append(line);
                sb.append('\n');
                lineIndex++;
            }
//            System.out.println("ACTION CONTENT REWRITTEN TO:\n-------------------------");
//            System.out.println(sb);
//            System.out.println("------------------------------------\n");
            return sb.toString();
        };
    }

    private static int indexOfFirstNonWhitespaceCharacter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean leadingNewline(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                return true;
            } else if (!Character.isWhitespace(c)) {
                break;
            }
        }
        return false;
    }

    static boolean canSafelyReflowLineHeuristic(String line) {
        line = line.trim();
        if (line.contains("(c)")) {
            return false;
        }
        if (line.startsWith("*") || line.startsWith("-")) {
            return false;
        }
        if (line.length() > 1 && line.charAt(1) == '.') {
            return false;
        }
        return true;
    }

    static FormattingAction blockCommentRewriter(AntlrFormatterSettings settings, boolean willGetPrecedingNewline) {
        return (Token tok, FormattingContext ctx, LexingState st) -> {
            String textContent = tok.getText().trim().substring(2, tok.getText().length() - 2).trim();
            int origLinePosition = ctx.origCharPositionInLine();
            int currPosition = ctx.currentCharPositionInLine();
            String result = null;
            boolean isLikelyLicenseHeader = tok.getTokenIndex() < 4;
            if (textContent.indexOf('\n') < 0) {
                if (currPosition < origLinePosition && origLinePosition + textContent.length() + 6 < settings.getWrapPoint()) {
                    result = spacesString(origLinePosition - currPosition) + "/* " + textContent + " */";
                } else if (currPosition + textContent.length() < settings.getWrapPoint()) {
                    result = "/* " + textContent + " */";
                }
            } else {
                StringBuilder sb = new StringBuilder();
                int textStart = settings.isWrapLines() ? origLinePosition : origLinePosition;
                String indentToCurrPosition = spacesString(textStart);
                if (willGetPrecedingNewline) {
                    sb.append(indentToCurrPosition);
                }
                String[] lines = textContent.split("\n");
                boolean asteriskPrefixed = true;
                if (lines.length > 0) {
                    String first = lines[0].trim();
                    if (first.startsWith("*")) {
                        lines[0] = first.substring(1);
                    }
                }
                for (int i = 1; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    boolean ap0 = "*".equals(line);
                    boolean ap1 = line.startsWith("* ") || line.startsWith("*\t");
                    boolean ap2 = line.startsWith("/**");
                    boolean ap3 = line.startsWith("**/");
                    boolean lineIsAsteriskPrefixed = ap0 || ap1 || ap2 || ap3;
                    asteriskPrefixed &= lineIsAsteriskPrefixed;
                    if (lineIsAsteriskPrefixed) {
                        lines[i] = ap0 ? "" : ap1 ? line.substring(2) : line.substring(3);
                    }
                    if (!asteriskPrefixed) {
                        break;
                    }
                }
                if (asteriskPrefixed) {
                    StringBuilder replaceContent = new StringBuilder();
                    for (String line : lines) {
                        replaceContent.append(line).append('\n');
                    }
                    textContent = replaceContent.toString();
                }
                boolean newlineDelimitPrefixAndSuffix = settings.isNewlineAfterColon() && (willGetPrecedingNewline || textContent.length() > settings.getWrapPoint());
                String infix;
                String prefix = asteriskPrefixed ? "/**" : "/*";
                if (newlineDelimitPrefixAndSuffix) {
                    infix = "";
                    sb.append(prefix).append('\n').append(indentToCurrPosition).append(" ");
                } else {
                    infix = "  ";
                    sb.append(prefix).append(' ');
                }
                if (!isLikelyLicenseHeader && settings.isWrapLines()) {
                    int usableLineLength = settings.getWrapPoint() - textStart;
                    int lastLineStart = 0;
                    int linePos = textStart + 3;
                    boolean first = true;
                    for (String word : new SimpleCollator(textContent)) {
                        if (word == null) {
                            if (asteriskPrefixed) {
                                sb.append('\n').append(" *").append('\n').append(" *");
                            } else {
                                sb.append('\n').append('\n');
                            }
                            sb.append(indentToCurrPosition).append(infix);
                            linePos = textStart + (asteriskPrefixed ? 4 : 2);
                            continue;
                        } else if (linePos + word.length() > settings.getWrapPoint() && !first) {
                            sb.append('\n');
                            if (asteriskPrefixed) {
                                sb.append(" * ");
                            }
                            sb.append(indentToCurrPosition).append(infix);
                            linePos = textStart + (asteriskPrefixed ? 4 : 2);
                        }
                        if (linePos > textStart && !first) {
                            sb.append(' ');
                            linePos++;
                        }
                        first = false;
                        sb.append(word);
                        linePos += word.length();
                    }
                } else {
                    boolean first = true;
                    for (String line : lines) {
                        line = line.trim();
                        if (!first) {
                            if (asteriskPrefixed) {
                                sb.append(indentToCurrPosition).append(" * ");
                            } else {
                                sb.append(indentToCurrPosition).append("   ");
                            }
                        }
                        sb.append(line).append('\n');
                        first = false;
                    }
                }
                if (newlineDelimitPrefixAndSuffix) {
                    if (asteriskPrefixed) {
                        sb.append(" *").append('\n').append(indentToCurrPosition).append("**/");
                    } else {
                        sb.append('\n').append(indentToCurrPosition).append("*/");
                    }
                } else {
                    if (asteriskPrefixed) {
                        sb.append(" **/");
                    } else {
                        sb.append(" */");
                    }
                }
                result = sb.toString();
            }
            if (result != null) {
                ctx.replace(result);
            }
        };

    }


    private static boolean splitOrs = false;

    static void state(LexingStateBuilder<AntlrCounters, ?> bldr, AntlrFormatterSettings config) {
        // anyOf(VOCABULARY, SEMI, ACTION_CONTENT), anyOf(VOCABULARY, END_ACTION, RBRACE)
        bldr
                // Record the position of the rule colon
                .recordPosition(AntlrCounters.COLON_POSITION)
                .onTokenType(COLON)
                .clearingOnTokenType(-1)
                // Record position of the most recent left brace,
                // keeping a stack of them so we pop our way out,
                // for indenting nested braces
                .pushPosition(AntlrCounters.LEFT_BRACE_POSITION)
                .onTokenType(LBRACE, BEGIN_ACTION)
                .poppingOnTokenType(RBRACE, END_ACTION)
                // Count the number of ;'s in actions and header blocks to see
                // if they are single line safe
                .count(AntlrCounters.SEMICOLON_COUNT).onEntering(LBRACE, BEGIN_ACTION)
                .countTokensMatching(anyOf(VOCABULARY, SEMI, ACTION_CONTENT))
                .scanningForwardUntil(anyOf(VOCABULARY, END_ACTION, RBRACE))
                // Increment a counter for nested left braces
                .increment(AntlrCounters.LEFT_BRACES_PASSED).onTokenType(LBRACE, BEGIN_ACTION)
                .decrementingWhenTokenEncountered(RBRACE, END_ACTION)
                // Increment a counter for nested parens
                .increment(AntlrCounters.PARENS_DEPTH).onTokenType(LPAREN)
                .decrementingWhenTokenEncountered(RPAREN)
                // And push the position of the left paren
                .pushPosition(AntlrCounters.LEFT_PAREN_POS).onTokenType(LPAREN)
                .poppingOnTokenType(RPAREN)
                // Record the indent position of standalone line comments
                .recordPosition(AntlrCounters.LINE_COMMENT_INDENT)
                .beforeProcessingToken()
                .usingPositionFromInput()
                .onTokenType(AntlrCriteria.lineComments())
                .clearingOnTokenType(-1)
                // Record the number of tokens from the current one to the next semicolon
                .computeTokenDistance(AntlrCounters.DISTANCE_TO_NEXT_SEMICOLON)
                .onEntering(AntlrCriteria.whitespace().negate())
                .toNext(SEMI).ignoringWhitespace()
                // Record the number of tokens from the current one to the preceding semicolon
                .computeTokenDistance(AntlrCounters.DISTANCE_TO_PRECEDING_SEMICOLON)
                .onEntering(RBRACE, END_ACTION)
                .toPreceding(SEMI).ignoringWhitespace()
                // Record the token offset backward to the preceding colon
                .computeTokenDistance(AntlrCounters.DISTANCE_TO_PRECEDING_COLON)
                .onEntering(AntlrCriteria.whitespace().negate()) // XXX this could be less general
                .toPreceding(COLON).ignoringWhitespace()
                // Count line comments
                .count(AntlrCounters.LINE_COMMENT_COUNT).onEntering(LBRACE, BEGIN_ACTION/*, PARDEC_ID, TOKDEC_ID, FRAGDEC_ID*/)
                .countTokensMatching(AntlrCriteria.lineComments())
                .scanningForwardUntil(LBRACE, END_ACTION)
                // Track when we are in an Options statement
                .set(AntlrCounters.IN_OPTIONS)
                .onTokenType(ANTLRv4Lexer.OPTIONS)
                .clearingAfterTokenType(RBRACE, END_ACTION)
                // Record the token offset backward to the preceding colon
                .computeTokenDistance(AntlrCounters.DISTANCE_TO_RBRACE)
                .onEntering(AntlrCriteria.whitespace().negate())
                .toNext(RBRACE, END_ACTION).ignoringWhitespace();
    }

    private static FormattingAction wrapIfNeeded(FormattingAction a, AntlrFormatterSettings c, AntlrCounters wrapAt) {
        if (!c.isWrapLines()) {
            return a;
        }
        return new FormattingAction() {
            @Override
            public void accept(Token token, FormattingContext ctx, LexingState state) {

                if (ctx.currentCharPositionInLine() + token.getText().length() + 1 > c.getWrapPoint()) {
                    if (wrapAt != null) {
                        int amt = state.get(wrapAt);
                        if (amt <= 0) {
                            ctx.prependNewlineAndDoubleIndent();
                        } else {
                            ctx.prependNewlineAndIndentBy(amt + c.getIndentSize());
                        }
                    }
                } else {
                    a.accept(token, ctx, state);
                }
            }

            public String toString() {
                return "<wrap on " + wrapAt + " | " + a + ">";
            }
        };
    }

    static FormattingRules rules(FormattingRules rules, AntlrFormatterSettings config) {
        final Criterion lineComments = AntlrCriteria.lineComments();

        Criteria cv = Criteria.forVocabulary(VOCABULARY);

        rules.replaceAdjacentTokens(cv.matching(ACTION_CONTENT), unmangleActionContent(config));

        rules.onTokenType(ID)
                .wherePreviousTokenTypeNot(SHARP, LPAREN, HEADER_IMPORT)
                // Ensure we don't get import java. util. Blah with spaces inserted
                .whereModeNot(MODE_DEFAULT, MODE_HEADER_PRELUDE, MODE_HEADER_IMPORT, MODE_HEADER_ACTION, MODE_OPTIONS, MODE_TOKENS)
                .format(wrapIfNeeded(PREPEND_SPACE, config, AntlrCounters.COLON_POSITION));

        rules.onTokenType(ID)
                .whereMode(MODE_HEADER_IMPORT, MODE_HEADER_ACTION)
                .wherePreviousTokenTypeNot(DOT, LPAREN, ASSIGN, COLONCOLON)
                .whereNextTokenTypeNot(AT)
                .format(PREPEND_SPACE);

        rules.onTokenType(AT).format(PREPEND_DOUBLE_NEWLINE);

        // Line comments in ACTION_CONTENT will get *split* on { / } chars,
        // meaning if we prepend a newline, we will wind up with something
        // that is not a line comment.  This is simply a bug in the grammar.
        rules.onTokenType(ACTION_CONTENT)
                .wherePreviousTokenType(ACTION_CONTENT)
                .whereMode(MODE_ACTION)
                .priority(8)
                .format(FormattingAction.EMPTY);

        rules.onTokenType(BEGIN_ACTION).whereMode(MODE_ACTION)
                .wherePreviousTokenType(ID, TOKEN_ID, FRAGDEC_ID, PARSER_RULE_ID)
                .priority(6)
                .named("action.spaces.a")
                .whereNextTokenType(ACTION_CONTENT)
                .when(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1)
                .when(AntlrCounters.LEFT_BRACES_PASSED).isLessThanOrEqualTo(1)
                .format(PREPEND_SPACE.and(APPEND_NEWLINE_AND_DOUBLE_INDENT.by(AntlrCounters.LEFT_BRACE_POSITION)));

        rules.onTokenType(BEGIN_ACTION).whereMode(MODE_ACTION)
                .priority(6)
                .named("action.spaces.b")
                .wherePreviousTokenType(ID, TOKEN_ID, FRAGDEC_ID, PARSER_RULE_ID)
                .whereNextTokenType(ACTION_CONTENT)
                .when(AntlrCounters.SEMICOLON_COUNT).isLessThanOrEqualTo(1)
                .when(AntlrCounters.LEFT_BRACES_PASSED).isLessThanOrEqualTo(1)
                .format(PREPEND_SPACE);

        rules.onTokenType(SEMI).whereMode(MODE_HEADER_ACTION)
                .named("action.semi.spacing")
                .whenCombinationOf(AntlrCounters.SEMICOLON_COUNT).isLessThanOrEqualTo(1)
                .and(AntlrCounters.DISTANCE_TO_RBRACE).isEqualTo(0).then()
                .format(APPEND_SPACE);

        rules.onTokenType(CATCH, FINALLY)
                .format(PREPEND_NEWLINE_AND_INDENT.by(AntlrCounters.COLON_POSITION).and(APPEND_SPACE));

        rules.onTokenType(DOC_COMMENT).format(PREPEND_NEWLINE);

        // Options block handling
        rules.onTokenType(LBRACE, BEGIN_ACTION).whereMode(MODE_OPTIONS, MODE_DEFAULT,
                MODE_HEADER_PRELUDE, MODE_HEADER_ACTION, MODE_ACTION, MODE_PARSER_RULE_DECLARATION)
                .priority(5)
                .named("lbrace.a")
                .when(AntlrCounters.SEMICOLON_COUNT).isLessThanOrEqualTo(1)
                .format(APPEND_SPACE);

        if (!config.isNewlineAfterColon()) {
            rules.onTokenType(cv.noneOf(RBRACE, END_ACTION)).whereMode(MODE_OPTIONS, MODE_DEFAULT,
                    MODE_HEADER_PRELUDE, MODE_HEADER_ACTION, MODE_ACTION, MODE_PARSER_RULE_DECLARATION, MODE_TOKENS)
                    .named("indent.header.brace.content.a")
                    .wherePreviousTokenType(LBRACE, BEGIN_ACTION)
                    .whenCombinationOf(AntlrCounters.LEFT_BRACES_PASSED).isSet()
                    .and(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1).then()
                    //                    .when(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1)
                    //                    .when(AntlrCounters.LEFT_BRACES_PASSED).isGreaterThan(0)
                    .format(INDENT.by(config.getIndentSize() - 1));
        } else {
            rules.onTokenType(cv.noneOf(RBRACE, END_ACTION)).whereMode(MODE_OPTIONS, MODE_DEFAULT,
                    MODE_HEADER_PRELUDE, MODE_HEADER_ACTION, MODE_ACTION, MODE_PARSER_RULE_DECLARATION, MODE_TOKENS)
                    .wherePreviousTokenType(LBRACE, BEGIN_ACTION)
                    .when(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1)
                    .when(AntlrCounters.LEFT_BRACES_PASSED).isGreaterThanOrEqualTo(1)
                    .named("indent.header.brace.content.b")
                    .format(PREPEND_NEWLINE_AND_DOUBLE_INDENT.by(AntlrCounters.LEFT_BRACE_POSITION));
        }

        rules.onTokenType(TOKENS)
                .format(APPEND_SPACE)
                .and().wherePreviousTokenType(RBRACE, END_ACTION)
                .priority(8)
                .format(PREPEND_NEWLINE.and(APPEND_SPACE));

        // XXX prepend space will prepend one even following a newline - likely wrong
        rules.onTokenType(LBRACE, BEGIN_ACTION).whereMode(MODE_OPTIONS, MODE_DEFAULT,
                MODE_HEADER_PRELUDE, MODE_HEADER_ACTION, MODE_ACTION, MODE_PARSER_RULE_DECLARATION, MODE_TOKENS)
                .priority(5)
                .named("lbrace.b")
                .when(AntlrCounters.LEFT_BRACES_PASSED).isLessThan(1)
                .format(PREPEND_SPACE)
                .and()
                .named("lbrace.b1")
                .priority(6)
                .when(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1)
                .when(AntlrCounters.LEFT_BRACES_PASSED).isUnset()
                .formatIf(config.isNewlineAfterColon(), APPEND_NEWLINE_AND_INDENT.by(AntlrCounters.LEFT_BRACE_POSITION))
                //                .formatIfNot(config.isNewlineAfterColon(), INDENT.by(config.getIndentSize()));
                .formatIfNot(config.isNewlineAfterColon(), INDENT);

        rules.onTokenType(LBRACE, BEGIN_ACTION).whereMode(MODE_OPTIONS, MODE_DEFAULT,
                MODE_HEADER_PRELUDE, MODE_HEADER_ACTION, MODE_ACTION, MODE_PARSER_RULE_DECLARATION)
                .priority(5)
                .when(AntlrCounters.LEFT_BRACES_PASSED).isUnset()
                .named("lbrace.c")
                .format(PREPEND_SPACE)
                .and()
                .named("lbrace.c1")
                .whereModeNot(MODE_TOKENS)
                .priority(6)
                .when(AntlrCounters.SEMICOLON_COUNT).isLessThanOrEqualTo(1)
                .format(PREPEND_SPACE.and(APPEND_SPACE));
        rules.onTokenType(ID)
                .whereMode(MODE_HEADER_PRELUDE, MODE_HEADER_ACTION, MODE_ACTION, MODE_OPTIONS)
                .wherePreviousTokenType(LPAREN, ASSIGN, DOT)
                .priority(4)
                .named("no.whitespace.after.parens.and.dots")
                .format(FormattingAction.EMPTY);

        rules.onTokenType(RBRACE, END_ACTION)
                .whereMode(MODE_OPTIONS, MODE_DEFAULT, MODE_HEADER_PRELUDE, MODE_HEADER_ACTION,
                        MODE_ACTION, MODE_PARSER_RULE_DECLARATION, MODE_TOKENS)
                .named("rbrace.a")
                .whereNextTokenTypeNot(-1)
                .when(AntlrCounters.SEMICOLON_COUNT).isLessThanOrEqualTo(1)
                .when(AntlrCounters.DISTANCE_TO_PRECEDING_SEMICOLON).isGreaterThan(1)
                .priority(5)
                .format(PREPEND_SPACE.and(APPEND_NEWLINE));
        rules.onTokenType(RBRACE, END_ACTION)
                .whereMode(MODE_DEFAULT, MODE_HEADER_PRELUDE, MODE_HEADER_ACTION,
                        MODE_ACTION, MODE_PARSER_RULE_DECLARATION)
                .when(AntlrCounters.IN_OPTIONS).isFalse()
                .named("rbrace.b")
                .whereNextTokenTypeNot(-1, SEMI, CATCH, FINALLY, QUESTION)
                .when(AntlrCounters.SEMICOLON_COUNT).isLessThanOrEqualTo(1)
                .when(AntlrCounters.DISTANCE_TO_PRECEDING_SEMICOLON).isLessThanOrEqualTo(1)
                .priority(5)
                //                .format(PREPEND_SPACE.and(APPEND_NEWLINE));
                .format(APPEND_NEWLINE);

        rules.onTokenType(RBRACE, END_ACTION)
                .whereMode(MODE_DEFAULT)
                .when(AntlrCounters.IN_OPTIONS).isTrue()
                .named("rbrace.b1")
                .whereNextTokenTypeNot(-1, SEMI, CATCH, FINALLY, QUESTION)
                .when(AntlrCounters.SEMICOLON_COUNT).isLessThanOrEqualTo(1)
                .when(AntlrCounters.DISTANCE_TO_PRECEDING_SEMICOLON).isLessThanOrEqualTo(1)
                .priority(7)
                .format(PREPEND_SPACE.and(APPEND_NEWLINE));

        rules.onTokenType(RBRACE, END_ACTION)
                .whereMode(MODE_OPTIONS, MODE_DEFAULT, MODE_HEADER_PRELUDE, MODE_HEADER_ACTION,
                        MODE_ACTION, MODE_PARSER_RULE_DECLARATION, MODE_TOKENS)
                .whereNextTokenTypeNot(-1, SEMI, CATCH, FINALLY, TOKENS)
                .named("rbrace.c")
                .when(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1)
                .when(AntlrCounters.DISTANCE_TO_PRECEDING_SEMICOLON).isLessThanOrEqualTo(1)
                .when(AntlrCounters.LEFT_BRACES_PASSED).isEqualTo(1)
                .priority(5)
                .format(PREPEND_NEWLINE_AND_INDENT.by(AntlrCounters.LEFT_BRACE_POSITION).and(APPEND_NEWLINE));
        rules.onTokenType(RBRACE, END_ACTION)
                .whereMode(MODE_OPTIONS, MODE_DEFAULT, MODE_HEADER_PRELUDE, MODE_HEADER_ACTION,
                        MODE_ACTION, MODE_PARSER_RULE_DECLARATION, MODE_TOKENS)
                .named("rbrace.d")
                .whereNextTokenTypeNot(-1, SEMI, CATCH, FINALLY)
                .when(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1)
                .when(AntlrCounters.DISTANCE_TO_PRECEDING_SEMICOLON).isLessThanOrEqualTo(1)
                .when(AntlrCounters.LEFT_BRACES_PASSED).isEqualTo(1)
                .priority(5)
                .format(PREPEND_NEWLINE_AND_INDENT.by(AntlrCounters.LEFT_BRACE_POSITION).and(APPEND_NEWLINE));

        rules.onTokenType(SEMI).whereMode(MODE_DEFAULT)
                .whereNextTokenTypeNot(
                        AntlrCriteria.lineComments()
                                .or(AntlrCriteria::isBlockComment)
                                .or(anyOf(VOCABULARY, -1, FINALLY, CATCH)))
                .format(APPEND_DOUBLE_NEWLINE);

        rules.onTokenType(SEMI).whereMode(MODE_DEFAULT)
                .whereNextTokenType(
                        AntlrCriteria.lineComments()
                                .or(AntlrCriteria::isBlockComment)
                                .or(matching(VOCABULARY, -1)))
                .format(APPEND_SPACE);

        rules.onTokenType(cv.noneOf(SEMI, BEGIN_ACTION, END_ACTION, LBRACE, RBRACE, OPTIONS, AT))
                .ifPrecededByNewline(true)
                .named("catch.all.header.braces.indent")
                .whereMode(MODE_OPTIONS, MODE_HEADER_PRELUDE, MODE_HEADER_ACTION, MODE_HEADER_IMPORT, MODE_ACTION,
                        "ParserRuleContext")
                .when(AntlrCounters.LEFT_BRACE_POSITION).isGreaterThan(0)
                .whenCombinationOf(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1)
                .or(AntlrCounters.LINE_COMMENT_COUNT).isGreaterThan(0).then()
                // XXX original code always used indent size * 2
                .format(PREPEND_NEWLINE_AND_DOUBLE_INDENT.by(AntlrCounters.LEFT_BRACE_POSITION));

        // Parser and lexer headers
        rules.onTokenType(PARSER, LEXER).whereMode(MODE_DEFAULT)
                .whereNextTokenType(COLONCOLON)
                .wherePreviousTokenType(AT)
                .priority(3)
                .format(FormattingAction.EMPTY); // Do nothing we just want to defeat space insertion

        rules.onTokenType(HEADER_IMPORT)
                .named("hdr.imp.a")
                .format(APPEND_SPACE);

        rules.onTokenType(HEADER_IMPORT)
                .named("hdr.imp.b")
                .when(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1)
                .format(APPEND_SPACE
                        .and(PREPEND_NEWLINE_AND_DOUBLE_INDENT
                                .by(AntlrCounters.LEFT_BRACE_POSITION)));

        rules.onTokenType(SHARP).format(PREPEND_SPACE);

        rules.onTokenType(BEGIN_ARGUMENT)
                .formatIf(config.isSpacesInsideParentheses(), PREPEND_SPACE.and(APPEND_SPACE))
                .formatIfNot(config.isSpacesInsideParentheses(), PREPEND_SPACE);

        rules.onTokenType(END_ARGUMENT)
                .formatIf(config.isSpacesInsideParentheses(), PREPEND_SPACE.and(APPEND_SPACE))
                .formatIfNot(config.isSpacesInsideParentheses(), APPEND_SPACE);

        if (config.getNewlineStyle().isDoubleNewline()) {
            rules.onTokenType(SEMI)
                    .whereModeNot(MODE_OPTIONS, MODE_DEFAULT, MODE_HEADER_PRELUDE, MODE_ACTION)
                    .whereNextTokenTypeNot(-1, LINE_COMMENT, PARDEC_LINE_COMMENT, TOK_LINE_COMMENT, FRAGDEC_LINE_COMMENT)
                    .when(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1)
                    .formatIf(config.getNewlineStyle() == NewlineStyle.IF_COMPLEX, APPEND_DOUBLE_NEWLINE);

        } else {
            rules.onTokenType(SEMI)
                    .whereModeNot(MODE_OPTIONS, MODE_DEFAULT, MODE_HEADER_PRELUDE, MODE_ACTION)
                    .whereNextTokenTypeNot(-1, LINE_COMMENT, PARDEC_LINE_COMMENT, TOK_LINE_COMMENT, FRAGDEC_LINE_COMMENT)
                    .when(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1)
                    .format(APPEND_NEWLINE);
        }

        rules.onTokenType(QUESTION)
                .whereNextTokenTypeNot(SEMI, RPAREN)
                .format(APPEND_SPACE);

        rules.onTokenType(TOKEN_ID, PARSER_RULE_ID, FRAGDEC_ID, ID)
                .whereModeNot(MODE_HEADER_IMPORT, MODE_HEADER_ACTION, MODE_HEADER_PRELUDE, MODE_ACTION, MODE_OPTIONS)
                .wherePreviousTokenType(DOC_COMMENT)
                .priority(6)
                .format(PREPEND_NEWLINE);

        rules.onTokenType(TOKEN_ID).whereMode(MODE_TOKENS)
                .wherePreviousTokenType(COMMA)
                .whereNextTokenType(COMMA)
                .when(AntlrCounters.DISTANCE_TO_RBRACE).isGreaterThan(1)
                .format((tok, ctx, st) -> {
                    if (config.isWrapLines() && ctx.currentCharPositionInLine() > config.getWrapPoint() - (tok.getText().length() + 1)) {
                        ctx.prependNewlineAndDoubleIndent();
                    } else {
                        ctx.prependSpace();
                    }
                });

        rules.onTokenType(TOKEN_ID).whereMode(MODE_TOKENS)
                .wherePreviousTokenType(LBRACE)
                .named("lbrace.tokens")
                .priority(3)
                .whereNextTokenType(COMMA)
                .when(AntlrCounters.DISTANCE_TO_RBRACE).isGreaterThan(1)
                .formatIf(config.isSpacesInsideParentheses(), PREPEND_SPACE)
                .formatIfNot(config.isSpacesInsideParentheses(), FormattingAction.EMPTY);

        rules.onTokenType(TOKEN_ID).whereMode(MODE_TOKENS)
                .wherePreviousTokenType(COMMA)
                .priority(3)
                .whereNextTokenType(RBRACE)
                .when(AntlrCounters.DISTANCE_TO_RBRACE).isLessThanOrEqualTo(1)
                .formatIf(config.isSpacesInsideParentheses(), PREPEND_SPACE.and(APPEND_SPACE))
                .formatIfNot(config.isSpacesInsideParentheses(), PREPEND_SPACE);

        rules.onTokenType(TOKEN_ID, PARSER_RULE_ID, FRAGDEC_ID, ID)
                .whereModeNot(MODE_HEADER_IMPORT, MODE_HEADER_ACTION, MODE_HEADER_PRELUDE, MODE_ACTION, MODE_TOKENS)
                .wherePreviousTokenType(TOKEN_ID, PARSER_RULE_ID, FRAGDEC_ID, ID)
                .wherePreviousTokenTypeNot(SEMI, ASSIGN, SHARP)
                .ifPrecededByNewline(false)
                .format(wrapIfNeeded(PREPEND_SPACE, config, AntlrCounters.COLON_POSITION)).named("Precede id with space");

        rules.onTokenType(FRAGMENT).format(APPEND_SPACE);

        rules.onTokenType(TOKEN_ID, PARSER_RULE_ID, FRAGDEC_ID)
                .whereModeNot(MODE_TOKENS)
                .wherePreviousTokenTypeNot(LPAREN, ASSIGN, SHARP)
                .whereNextTokenTypeNot(RPAREN, SEMI, PLUS, QUESTION, STAR)
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
                    .wherePreviousTokenType(LPAREN)
                    .format(PREPEND_SPACE);
        }

        rules.onTokenType(RARROW).format(PREPEND_SPACE.and(APPEND_SPACE));
        rules.onTokenType(COMMA).format(APPEND_SPACE);

        rules.onTokenType(STAR, PLUS, QUESTION)
                .whereNextTokenTypeNot(QUESTION, SEMI, RPAREN)
                .format(APPEND_SPACE);

        rules.onTokenType(IMPORT)
                .wherePreviousTokenTypeNot(RBRACE, END_ACTION)
                .format(PREPEND_NEWLINE.and(APPEND_SPACE));

        rules.onTokenType(IMPORT)
                .wherePreviousTokenType(RBRACE, END_ACTION)
                .format(PREPEND_DOUBLE_NEWLINE.and(APPEND_SPACE));

        rules.onTokenType(ANTLRv4Lexer.BEGIN_ACTION)
                .wherePreviousTokenTypeNot(ACTION_CONTENT)
                .whereModeNot(MODE_HEADER_PRELUDE, MODE_HEADER_ACTION)
                .when(AntlrCounters.LEFT_BRACES_PASSED).isLessThan(2)
                .format(PREPEND_NEWLINE_AND_INDENT
                        .and(APPEND_NEWLINE));

        rules.onTokenType(ANTLRv4Lexer.BEGIN_ACTION)
                .whereModeNot(MODE_HEADER_ACTION, MODE_DEFAULT, MODE_HEADER_PRELUDE, MODE_HEADER_IMPORT)
                .format(PREPEND_NEWLINE_AND_INDENT);

        // In the case of, e.g. Dot { ... }? don't put the ? or ; on its own line
        rules.onTokenType(ANTLRv4Lexer.END_ACTION)
                .whereModeNot(MODE_HEADER_ACTION, MODE_HEADER_PRELUDE, MODE_DEFAULT, MODE_HEADER_IMPORT)
                .named("end.action.a")
                .whereNextTokenTypeNot(STAR, QUESTION, PLUS, SHARP, SEMI)
                .when(AntlrCounters.LEFT_BRACES_PASSED).isEqualTo(1)
                .format(PREPEND_NEWLINE_AND_INDENT.and(APPEND_NEWLINE));

        rules.onTokenType(ANTLRv4Lexer.END_ACTION)
                .whereModeNot(MODE_HEADER_ACTION, MODE_HEADER_PRELUDE, MODE_DEFAULT, MODE_HEADER_IMPORT)
                .named("end.action.b")
                .whereNextTokenType(STAR, QUESTION, PLUS)
                .format(PREPEND_SPACE);
//                .format(PREPEND_NEWLINE_AND_INDENT);

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
                .whereNextTokenTypeNot(OR)
                .format(APPEND_NEWLINE)
                .and()
                .priority(6)
                .ifPrecededByNewline(false)
                .format(PREPEND_SPACE.and(APPEND_NEWLINE));

        rules.onTokenType(lineComments)
                .whereNextTokenType(OR)
                .when(AntlrCounters.PARENS_DEPTH).isLessThan(1)
                .format(APPEND_NEWLINE)
                .and()
                .when(AntlrCounters.PARENS_DEPTH).isLessThan(1)
                .priority(6)
                .ifPrecededByNewline(false)
                .format(PREPEND_SPACE);

        rules.onTokenType(OPTIONS, MEMBERS, HEADER)
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
                .format((tok, ctx, st) -> {
//                    int bd = ctx.get("blockDepth", 0);
                    int bd = st.get(AntlrCounters.PARENS_DEPTH, 0);
                    if (bd != 0) {
                        ctx.appendNewline();
                    }
                });

//        rules.onTokenType(AntlrCriteria::isBlockComment)
//                .wherePreviousTokenTypeNot(-1)
//                .whereNextTokenTypeNot(GRAMMAR, LEXER, PARSER)
//                .format(PREPEND_NEWLINE.and(APPEND_NEWLINE));
        rules.onTokenType(LEXER, PARSER).format(APPEND_SPACE);

        if (splitOrs) {
            rules.onTokenType(LPAREN)
                    .priority(8)
                    .wherePreviousTokenTypeNot(OR)
                    .format((tok, ctx, st) -> {
                        int pos = st.get(AntlrCounters.COLON_POSITION, 0) - 1;
                        int pd = st.get(AntlrCounters.PARENS_DEPTH);
                        pos += Math.max(0, ((pd - 1) * ctx.indentSize()));
                        ctx.prependNewlineAndIndentBy(pos);
                    });
            rules.onTokenType(OR)
                    .priority(8)
                    .whereNextTokenType(LPAREN)
                    .format((tok, ctx, st) -> {
                        int pos = st.get(AntlrCounters.COLON_POSITION, 0) - 1;
                        int pd = st.get(AntlrCounters.PARENS_DEPTH) + 1;
                        pos += Math.max(0, ((pd - 1) * ctx.indentSize()));
                        ctx.prependNewlineAndIndentBy(pos);
                        ctx.appendSpace();
                    });

            rules.onTokenType(OR)
                    .whereNextTokenTypeNot(LPAREN)
                    .format((tok, ctx, st) -> {
                        int bd = Math.max(0, st.get(AntlrCounters.PARENS_DEPTH));
                        int colonPos = config.isNewlineAfterColon() ? config.getIndentSize()
                                : st.get(AntlrCounters.COLON_POSITION, config.getIndentSize()) - 1;
                        if (bd == 0) {
                            ctx.prependNewlineAndIndentBy(colonPos);
                            ctx.appendSpace();
                        } else {
                            if (config.isWrapLines() && ctx.currentCharPositionInLine() > config.getWrapPoint() - (tok.getText().length() + 1)) {
                                ctx.prependNewlineAndIndentBy(colonPos + config.getIndentSize());
                            } else {
                                ctx.prependSpace();
                            }
                            ctx.appendSpace();
                        }
                    });

        } else {
            rules.onTokenType(OR).format((tok, ctx, st) -> {
                int bd = Math.max(0, st.get(AntlrCounters.PARENS_DEPTH));
                int colonPos = config.isNewlineAfterColon() ? config.getIndentSize()
                        : st.get(AntlrCounters.COLON_POSITION, config.getIndentSize()) - 1;
                if (bd == 0) {
                    ctx.prependNewlineAndIndentBy(colonPos);
                    ctx.appendSpace();
                } else {
                    if (config.isWrapLines() && ctx.currentCharPositionInLine() > config.getWrapPoint() - (tok.getText().length() + 1)) {
                        ctx.prependNewlineAndIndentBy(colonPos + config.getIndentSize());
                    } else {
                        ctx.prependSpace();
                    }
                    ctx.appendSpace();
                }
            });
        }

        if (config.isSpacesInsideParentheses()) {
            rules.onTokenType(LPAREN)
                    .wherePreviousTokenType(ASSIGN)
                    .priority(1) // ensure this rule is tested before the next one
                    .ifPrecededByNewline(false)
                    .format(APPEND_SPACE);
        }

        rules.onTokenType(HDR_IMPRT_LINE_COMMENT).format(APPEND_NEWLINE);

        rules.onTokenType(lineComments)
                .wherePreviousTokenType(ID, TOKEN_ID, DOT, STRING_LITERAL, PARSER_RULE_ID, FRAGDEC_ID, LPAREN, RPAREN, SEMI)
                .ifPrecededByNewline(false)
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
                    .format(PREPEND_SPACE); // XXX shouldn't this be APPEND?
        }

        rules.onTokenType(RPAREN)
                .whereNextTokenTypeNot(LPAREN, RPAREN, STAR, QUESTION, PLUS)
                .wherePreviousTokenTypeNot(STAR, QUESTION, PLUS, RPAREN)
                .format((tok, ctx, st) -> {
//                    int colonPos = ctx.get("colon", config.getIndentSize()) - 1;
                    int colonPos = st.get(AntlrCounters.COLON_POSITION, config.getIndentSize() - 1);
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
                .wherePreviousTokenTypeNot(lineComments)
                .whereNextTokenType(lineComments)
                .format((tok, ctx, st) -> {
//                    ctx.set("lc", ctx.origCharPositionInLine());
                    ctx.prependNewlineAndIndentBy(st.get(AntlrCounters.LINE_COMMENT_INDENT));
                });

        rules.onTokenType(lineComments).ifPrecededByNewline(true)
                .wherePreviousTokenType(lineComments)
                .whereNextTokenType(lineComments)
                .format((tok, ctx, st) -> {
//                    int amt = ctx.get("lc", config.getIndentSize());
                    ctx.prependNewlineAndIndentBy(st.get(AntlrCounters.LINE_COMMENT_INDENT));
                });

        rules.onTokenType(lineComments).ifPrecededByNewline(true)
                .wherePreviousTokenType(lineComments)
                .whereNextTokenTypeNot(lineComments)
                .format((tok, ctx, st) -> {
//                    int amt = ctx.get("lc", config.getIndentSize());
                    ctx.prependNewlineAndIndentBy(st.get(AntlrCounters.LINE_COMMENT_INDENT));
                    ctx.appendNewline();
                });

        if (config.isReflowBlockComments()) {
            FormattingAction blockComments = blockCommentRewriter(config, false);
            rules.onTokenType(AntlrCriteria::isBlockComment)
                    .ifPrecededByNewline(true)
                    .ifFollowedByNewline(true)
                    .format(blockCommentRewriter(config, true).and(APPEND_NEWLINE).and(PREPEND_NEWLINE));

            rules.onTokenType(AntlrCriteria::isBlockComment)
                    .ifPrecededByNewline(false)
                    .ifFollowedByNewline(true)
                    .format(blockComments.and(APPEND_NEWLINE).and(PREPEND_SPACE));

            rules.onTokenType(AntlrCriteria::isBlockComment)
                    .ifPrecededByNewline(false)
                    .ifFollowedByNewline(false)
                    .format(blockComments.and(APPEND_SPACE));
        }

        rules.onTokenType(COLON)
                .formatIf(config.isNewlineAfterColon(), PREPEND_NEWLINE_AND_INDENT.and(APPEND_SPACE))
                .formatIfNot(config.isNewlineAfterColon(), PREPEND_SPACE.and(APPEND_SPACE));

        return rules;
    }
}
