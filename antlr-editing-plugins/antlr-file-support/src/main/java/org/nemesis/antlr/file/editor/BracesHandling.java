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
package org.nemesis.antlr.file.editor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.file.AntlrHierarchy;
import org.nemesis.antlr.file.AntlrToken;
import org.nemesis.antlr.file.AntlrTokens;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.PartType;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.editor.indent.api.Indent;
import org.netbeans.modules.editor.indent.spi.CodeStylePreferences;
import org.netbeans.spi.editor.typinghooks.DeletedTextInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedBreakInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public class BracesHandling {

    public static class AntlrTypedTextInterceptor implements TypedTextInterceptor {

        private int caretPosition = -1;

        @Override
        public boolean beforeInsert(Context cntxt) throws BadLocationException {
            return false;
        }

        @Override
        public void insert(MutableContext context) throws BadLocationException {
            char insertedChar = context.getText().charAt(0);
            System.out.println("BRACE HANDLING INSERT '" + insertedChar + "'");
            switch (insertedChar) {
                case ' ':
//                    caretPosition = maybeCompleteColon(context);
                    break;
                case '(':
                case '[':
//                    completeOpeningBracket(context);
                    break;
                case ']':
                case ')':
                    caretPosition = skipClosingBracket(context);
                    break;
                case ';':
                    caretPosition = moveOrSkipSemicolon(context);
                    break;
                case '\'':
                case '"':
                    caretPosition = completeQuote(context);
                    break;
            }
        }

        @Override
        public void afterInsert(Context context) throws BadLocationException {
            if (caretPosition != -1) {
                context.getComponent().setCaretPosition(caretPosition);
                caretPosition = -1;
            }
        }

        @Override
        public void cancelled(Context cntxt) {
            // do nothing
        }

        @MimeRegistration(mimeType = ANTLR_MIME_TYPE,
                service = TypedTextInterceptor.Factory.class)
        public static class Factory implements TypedTextInterceptor.Factory {

            @Override
            public TypedTextInterceptor createTypedTextInterceptor(MimePath mp) {
                return new AntlrTypedTextInterceptor();
            }
        }
    }

    public static class AntlrDeletedTextInterceptor implements DeletedTextInterceptor {

        @Override
        public boolean beforeRemove(Context context) throws BadLocationException {
            return false;
        }

        @Override
        public void remove(Context context) throws BadLocationException {
            char removedChar = context.getText().charAt(0);
            switch (removedChar) {
                case '(':
                case '[':
                    if (isCompletionSettingEnabled()) {
//                        removeBrackets(context);
                    }
                    break;
                case '\"':
                case '\'':
                    if (isCompletionSettingEnabled()) {
//                        removeCompletedQuote(context);
                    }
                    break;
            }
        }

        @Override
        public void afterRemove(Context context) throws BadLocationException {
        }

        @Override
        public void cancelled(Context context) {
        }

        @MimeRegistration(mimeType = ANTLR_MIME_TYPE, service = DeletedTextInterceptor.Factory.class)
        public static class Factory implements DeletedTextInterceptor.Factory {

            @Override
            public DeletedTextInterceptor createDeletedTextInterceptor(MimePath mimePath) {
                return new AntlrDeletedTextInterceptor();
            }
        }
    }

    public static class AntlrTypedBreakInterceptor implements TypedBreakInterceptor {

        private boolean isJavadocTouched = false;
        private int pos = -1;

        @Override
        public boolean beforeInsert(Context context) throws BadLocationException {
            return false;
        }

        @Override
        public void insert(MutableContext context) throws BadLocationException {
            int dotPos = context.getCaretOffset();
            Document doc = context.getDocument();

            if (posWithinString(doc, dotPos)) {
//                if (CodeStyle.getDefault(doc).wrapAfterBinaryOps()) {
                if (true) {
                    context.setText("\" +\n \"", 3, 6); // NOI18N
                } else {
                    context.setText("\"\n + \"", 1, 6); // NOI18N
                }
                return;
            }

            BaseDocument baseDoc = (BaseDocument) context.getDocument();
            if (isCompletionSettingEnabled() && isAddRightBrace(baseDoc, dotPos)) {
                boolean insert[] = {true};
                int end = getRowOrBlockEnd(baseDoc, dotPos, insert);
                if (insert[0]) {
                    doc.insertString(end, "}", null); // NOI18N
                    Indent.get(doc).indentNewLine(end);
                }
                context.getComponent().getCaret().setDot(dotPos);
            } else {
                if (blockCommentCompletion(context)) {
                    blockCommentComplete(doc, dotPos, context);
                }
                isJavadocTouched = javadocBlockCompletion(context);
                if (isJavadocTouched) {
                    blockCommentComplete(doc, dotPos, context);
                }
            }
        }

        @Override
        public void afterInsert(Context context) throws BadLocationException {
            if (isJavadocTouched) {
//                Lookup.Result<TextAction> res = MimeLookup.getLookup(MimePath.parse("text/x-javadoc")).lookupResult(TextAction.class); // NOI18N
//                ActionEvent newevt = new ActionEvent(context.getComponent(), ActionEvent.ACTION_PERFORMED, "fix-javadoc"); // NOI18N
//                for (TextAction action : res.allInstances()) {
//                    action.actionPerformed(newevt);
//                }
//                isJavadocTouched = false;
            }
            if (pos != -1) {
                context.getComponent().getCaret().setDot(pos);
            }
        }

        @Override
        public void cancelled(Context context) {
        }

        private void blockCommentComplete(Document doc, int dotPos, MutableContext context) throws BadLocationException {
            // note that the formater will add one line of javadoc
            doc.insertString(dotPos, "*/", null); // NOI18N
            Indent.get(doc).indentNewLine(dotPos);
            context.getComponent().getCaret().setDot(dotPos);
        }

        @MimeRegistration(mimeType = ANTLR_MIME_TYPE, service = TypedBreakInterceptor.Factory.class)
        public static class AntlrFactory implements TypedBreakInterceptor.Factory {

            @Override
            public TypedBreakInterceptor createTypedBreakInterceptor(MimePath mimePath) {
                return new AntlrTypedBreakInterceptor();
            }
        }
    }

    static boolean blockCommentCompletion(TypedBreakInterceptor.Context context) {
        return blockCommentCompletionImpl(context, false);
    }

    static boolean javadocBlockCompletion(TypedBreakInterceptor.Context context) {
        return blockCommentCompletionImpl(context, true);
    }

    private static boolean isDocComment(AntlrToken tok) {
        return tok == AntlrTokens.TOK_DOC_COMMENT;
    }

    private static boolean isBlockComment(AntlrToken tok) {
        return tok == AntlrTokens.TOK_BLOCK_COMMENT
                || tok == AntlrTokens.TOK_HEADER_BLOCK_COMMENT
                || tok == AntlrTokens.TOK_CHN_BLOCK_COMMENT
                || tok == AntlrTokens.TOK_HEADER_P_BLOCK_COMMENT
                || tok == AntlrTokens.TOK_IMPORT_BLOCK_COMMENT
                || tok == AntlrTokens.TOK_TOK_BLOCK_COMMENT
                || tok == AntlrTokens.TOK_PARDEC_OPT_BLOCK_COMMENT
                || tok == AntlrTokens.TOK_OPT_BLOCK_COMMENT
                || tok == AntlrTokens.TOK_LEXCOM_BLOCK_COMMENT;
    }

    protected static Preferences configuration(TypedTextInterceptor.MutableContext ctx) {
        return CodeStylePreferences.get(ctx.getDocument(), ANTLR_MIME_TYPE).getPreferences();
    }

    static String spaces(int count) {
        char[] c = new char[count];
        Arrays.fill(c, ' ');
        return new String(c);
    }

    protected static String colonInsertionString(TypedTextInterceptor.MutableContext context) {
        Preferences prefs = configuration(context);
        int val = prefs.getInt("colonHandling", 0);
        int indent = prefs.getInt("indentBy", 4);
        switch (val) {
            case 1: // STANDALONE
                return "\n" + spaces(indent) + ": ;";
            case 2:// ON NEW LINE
                return "\n" + spaces(indent) + ":\n" + spaces(indent) + ";";
            case 3:// ON NEW LINE
                return ":\n" + spaces(indent) + ";";
            case 0: // INLINE
            default:
                return ": ;";
        }
    }

    static int maybeCompleteColon(TypedTextInterceptor.MutableContext context) throws BadLocationException {
        TokenSequence<AntlrToken> ts = javaTokenSequence(context, true);
        if (ts == null) {
            return -1;
        }
        int dotPosition = context.getOffset();
        ts.move(dotPosition);
        if (ts.movePrevious()) {
            if (!isRuleOpening(ts.token().id())) {
                return -1;
            }
//            if (ts.moveNext() && ts.moveNext()) {
//                System.out.println("FOLLOWING: " + (ts.token() == null ? "null" : ts.token().id()) );
//            }
            CharSequence content = org.netbeans.lib.editor.util.swing.DocumentUtilities.getText(context.getDocument());
            if (!isAtRowEnd(content, dotPosition)) {
                return -1;
            }
            String toInsert = colonInsertionString(context);
            context.getDocument().insertString(dotPosition, toInsert, null);
            return dotPosition + toInsert.length();
        }
        return -1;
    }

    static boolean isRuleOpening(AntlrToken tok) {
        return tok == AntlrTokens.TOK_PARSER_RULE_ID
                || tok == AntlrTokens.TOK_TOKEN_ID;
    }

    static boolean blockCommentCompletionImpl(TypedBreakInterceptor.Context context, boolean javadoc) {
        TokenSequence<AntlrToken> ts = javaTokenSequence(context, false);
        if (ts == null) {
            return false;
        }
        int dotPosition = context.getCaretOffset();
        ts.move(dotPosition);
        if (!((ts.moveNext() || ts.movePrevious())
                && (javadoc ? isDocComment(ts.token().id()) : isBlockComment(ts.token().id())))) {
            return false;
        }

        int jdoffset = dotPosition - (javadoc ? 3 : 2);
        if (jdoffset >= 0) {
            CharSequence content = org.netbeans.lib.editor.util.swing.DocumentUtilities.getText(context.getDocument());
            if (isOpenBlockComment(content, dotPosition - 1, javadoc) && !isClosedBlockComment(content, dotPosition) && isAtRowEnd(content, dotPosition)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAtRowEnd(CharSequence txt, int pos) {
        int length = txt.length();
        for (int i = pos; i < length; i++) {
            char c = txt.charAt(i);
            if (c == '\n') {
                return true;
            }
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOpenBlockComment(CharSequence content, int pos, boolean javadoc) {
        for (int i = pos; i >= 0; i--) {
            char c = content.charAt(i);
            if (c == '*' && (javadoc ? i - 2 >= 0 && content.charAt(i - 1) == '*' && content.charAt(i - 2) == '/' : i - 1 >= 0 && content.charAt(i - 1) == '/')) {
                // matched /*
                return true;
            } else if (c == '\n') {
                // no javadoc, matched start of line
                return false;
            } else if (c == '/' && i - 1 >= 0 && content.charAt(i - 1) == '*') {
                // matched javadoc enclosing tag
                return false;
            }
        }

        return false;
    }

    private static boolean isClosedBlockComment(CharSequence txt, int pos) {
        int length = txt.length();
        int quotation = 0;
        for (int i = pos; i < length; i++) {
            char c = txt.charAt(i);
            if (c == '*' && i < length - 1 && txt.charAt(i + 1) == '/') {
                if (quotation == 0 || i < length - 2) {
                    return true;
                }
                // guess it is not just part of some text constant
                boolean isClosed = true;
                for (int j = i + 2; j < length; j++) {
                    char cc = txt.charAt(j);
                    if (cc == '\n') {
                        break;
                    } else if (cc == '"' && j < length - 1 && txt.charAt(j + 1) != '\'') {
                        isClosed = false;
                        break;
                    }
                }

                if (isClosed) {
                    return true;
                }
            } else if (c == '/' && i < length - 1 && txt.charAt(i + 1) == '*') {
                // start of another comment block
                return false;
            } else if (c == '\n') {
                quotation = 0;
            } else if (c == '"' && i < length - 1 && txt.charAt(i + 1) != '\'') {
                quotation = ++quotation % 2;
            }
        }

        return false;
    }

    /**
     * Resolve whether pairing right curly should be added automatically at the
     * caret position or not.
     * <br>
     * There must be only whitespace or line comment or block comment between
     * the caret position and the left brace and the left brace must be on the
     * same line where the caret is located.
     * <br>
     * The caret must not be "contained" in the opened block comment token.
     *
     * @param doc document in which to operate.
     * @param caretOffset offset of the caret.
     * @return true if a right brace '}' should be added or false if not.
     */
    static boolean isAddRightBrace(BaseDocument doc, int caretOffset) throws BadLocationException {
        if (tokenBalance(doc, AntlrTokens.TOK_LBRACE) <= 0) {
            return false;
        }
        int caretRowStartOffset = org.netbeans.editor.Utilities.getRowStart(doc, caretOffset);
        TokenSequence<AntlrToken> ts = javaTokenSequence(doc, caretOffset, true);
        if (ts == null) {
            return false;
        }
        boolean first = true;
        do {
            if (ts.offset() < caretRowStartOffset) {
                return false;
            }
            if (isWhitespace(ts.token().id())) {
                continue;
            }
            if (isComment(ts.token().id())) {
                if (first && caretOffset > ts.offset() && caretOffset < ts.offset() + ts.token().length()) {
                    // Caret contained within block comment -> do not add anything
                    return false;
                }

            }
            if (isLBrace(ts.token().id())) {
                return true;
            }
            first = false;
        } while (ts.movePrevious());
        return false;
    }

    static boolean isLBrace(AntlrToken tok) {
        switch (tok.getNumericID()) {
            case ANTLRv4Lexer.LBRACE:
            case ANTLRv4Lexer.TOKDEC_LBRACE:
            case ANTLRv4Lexer.PARDEC_LBRACE:
            case ANTLRv4Lexer.FRAGDEC_LBRACE:
            case ANTLRv4Lexer.BEGIN_ACTION:
                return true;
            default:
                return "{".equals(tok.literalName());
        }
    }

    static boolean isRBrace(AntlrToken tok) {
        switch (tok.getNumericID()) {
            case ANTLRv4Lexer.RBRACE:
            case ANTLRv4Lexer.END_ACTION:
                return true;
            default:
                return "}".equals(tok.symbolicName());
        }
    }

    static boolean isSemi(AntlrToken tok) {
        return tok == AntlrTokens.TOK_SEMI;
    }

    /**
     * Returns position of the first unpaired closing paren/brace/bracket from
     * the caretOffset till the end of caret row. If there is no such element,
     * position after the last non-white character on the caret row is returned.
     */
    static int getRowOrBlockEnd(BaseDocument doc, int caretOffset, boolean[] insert) throws BadLocationException {
        int rowEnd = org.netbeans.editor.Utilities.getRowLastNonWhite(doc, caretOffset);
        if (rowEnd == -1 || caretOffset >= rowEnd) {
            return caretOffset;
        }
        rowEnd += 1;
        int parenBalance = 0;
        int braceBalance = 0;
        int bracketBalance = 0;
        TokenSequence<AntlrToken> ts = javaTokenSequence(doc, caretOffset, false);
        if (ts == null) {
            return caretOffset;
        }
        boolean firstToken = true;
        while (ts.offset() < rowEnd) {
            AntlrToken tok = ts.token().id();
            if (isSemi(tok) || AntlrTokens.TOK_LPAREN == tok) {
                parenBalance++;
            } else if (AntlrTokens.TOK_RPAREN == tok) {
                if (parenBalance-- == 0) {
                    return ts.offset();
                }
            } else if (isLBrace(tok)) {
                braceBalance++;
            } else if (isRBrace(tok)) {
                if (braceBalance-- == 0) {
                    return ts.offset();
                }
            } else if (tok == AntlrTokens.TOK_COMMA) {
                if (firstToken) {
                    return caretOffset;
                }
            }
            firstToken = false;
            if (!ts.moveNext()) {
                break;
            }
        }
        insert[0] = false;
        return rowEnd;
    }

    /**
     * Check for various conditions and possibly remove two quotes.
     *
     * @param context
     * @throws BadLocationException
     */
    static void removeCompletedQuote(DeletedTextInterceptor.Context context) throws BadLocationException {
        TokenSequence<AntlrToken> ts = javaTokenSequence(context, false);
        if (ts == null) {
            return;
        }
        char removedChar = context.getText().charAt(0);
        int caretOffset = context.isBackwardDelete() ? context.getOffset() - 1 : context.getOffset();
        if (removedChar == '\"') {
            if ((isStringLiteral(ts.token().id()) && ts.offset() == caretOffset) /* ||
                (ts.token().id() == AntlrToken.MULTILINE_STRING_LITERAL && ts.offset() <= caretOffset - 2)*/) {
                context.getDocument().remove(caretOffset, 1);
            }
        } else if (removedChar == '\'') {
            if (ts.token().id() == AntlrTokens.TOK_STRING_LITERAL && ts.offset() == caretOffset) {
                context.getDocument().remove(caretOffset, 1);
            }
        }
    }

    /**
     * Check for various conditions and possibly remove two brackets.
     *
     * @param context
     * @throws BadLocationException
     */
    static void removeBrackets(DeletedTextInterceptor.Context context) throws BadLocationException {
        int caretOffset = context.isBackwardDelete() ? context.getOffset() - 1 : context.getOffset();
        TokenSequence<AntlrToken> ts = javaTokenSequence(context.getDocument(), caretOffset, false);
        if (ts == null) {
            return;
        }

        switch (ts.token().id().getNumericID()) {
            case ANTLRv4Lexer.RPAREN:
                if (tokenBalance(context.getDocument(), AntlrTokens.TOK_LPAREN) != 0) {
                    context.getDocument().remove(caretOffset, 1);
                }
                break;
            case ANTLRv4Lexer.RBRACE:
                if (tokenBalance(context.getDocument(), AntlrTokens.TOK_LBRACE) != 0) {
                    context.getDocument().remove(caretOffset, 1);
                }
                break;
        }
    }

    private static int tokenBalance(Document doc, AntlrToken leftTokenId) {
        TokenBalance tb = TokenBalance.get(doc);
        Language<AntlrToken> lang = AntlrHierarchy.antlrLanguage();
        if (!tb.isTracked(AntlrHierarchy.antlrLanguage())) {
            tb.addTokenPair(lang, AntlrTokens.TOK_LPAREN, AntlrTokens.TOK_RPAREN);
//            tb.addTokenPair(lang, AntlrToken.LBRACKET, AntlrToken.RBRACKET);
            tb.addTokenPair(lang, AntlrTokens.TOK_LBRACE, AntlrTokens.TOK_RBRACE);
        }
        int balance = tb.balance(lang, leftTokenId);
        assert (balance != Integer.MAX_VALUE);
        return balance;
    }

    /**
     * Called to add semicolon after bracket for some conditions
     *
     * @param context
     * @return relative caretOffset change
     * @throws BadLocationException
     */
    static int moveOrSkipSemicolon(TypedTextInterceptor.MutableContext context) throws BadLocationException {
        TokenSequence<AntlrToken> javaTS = javaTokenSequence(context, false);
        if (javaTS == null || isStringOrComment(javaTS.token().id())) {
            return -1;
        }
        if (javaTS.token().id() == AntlrTokens.TOK_SEMI) {
            context.setText("", 0); // NOI18N
            return javaTS.offset() + 1;
        }
        int lastParenPos = context.getOffset();
        int index = javaTS.index();
        // Move beyond semicolon
        while (javaTS.moveNext()
                && !(isWhitespace(javaTS.token().id()) && javaTS.token().text().toString().contains("\n"))
                && !("}".equals(javaTS.token().id().literalName()))) {  // NOI18N
            switch (javaTS.token().id().getNumericID()) {
                case ANTLRv4Lexer.RPAREN:
                    lastParenPos = javaTS.offset();
                    break;
                default:
                    if (isWhitespace(javaTS.token().id())) {
                        break;
                    }
                    return -1;
            }
        }
        // Restore javaTS position
        javaTS.moveIndex(index);
        javaTS.moveNext();
        if (/*isForLoopTryWithResourcesOrLambdaSemicolon(javaTS) || */posWithinAnyQuote(context, javaTS) || (lastParenPos == context.getOffset() && !javaTS.token().id().equals(AntlrTokens.TOK_RPAREN))) {
            return -1;
        }
        context.setText("", 0); // NOI18N
        context.getDocument().insertString(lastParenPos + 1, ";", null); // NOI18N
        return lastParenPos + 2;
    }

    /**
     * Generalized posWithingString to any token and delimiting character. It
     * works for tokens are delimited by *quote* and extend up to the other
     * *quote* or whitespace in case of an incomplete token.
     *
     * @param doc the document
     * @param caretOffset position of typed quote
     */
    static boolean posWithinString(Document doc, int caretOffset) {
        return posWithinQuotes(doc, caretOffset, AntlrTokens.TOK_STRING_LITERAL);
    }

    private static boolean posWithinQuotes(Document doc, int caretOffset, AntlrToken tokenId) {
        TokenSequence<AntlrToken> javaTS = javaTokenSequence(doc, caretOffset, false);
        if (javaTS != null) {
            if (javaTS.token().id() != tokenId) {
                return false;
            } else if (caretOffset > javaTS.offset() && caretOffset < javaTS.offset() + javaTS.token().length()) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private static boolean isStringLiteral(AntlrToken tok) {
        return tok == AntlrTokens.TOK_STRING_LITERAL;
    }

    private static boolean posWithinAnyQuote(TypedTextInterceptor.MutableContext context, TokenSequence<AntlrToken> javaTS) throws BadLocationException {
        if (isStringLiteral(javaTS.token().id())) {
            char chr = context.getDocument().getText(context.getOffset(), 1).charAt(0);
            return (context.getOffset() - javaTS.offset() == 1 || (chr != '"' && chr != '\''));
        }
        return false;
    }

    private static boolean isComment(AntlrToken tok) {
        return COMMENT_TOKENS.contains(tok);
    }

    /**
     * Called to insert either single bracket or bracket pair.
     *
     * @param context
     * @return relative caretOffset change
     * @throws BadLocationException
     */
    static int completeQuote(TypedTextInterceptor.MutableContext context) throws BadLocationException {
        if (isEscapeSequence(context)) {
            return -1;
        }
        // Examine token id at the caret offset
        TokenSequence<AntlrToken> javaTS = javaTokenSequence(context, true);
        AntlrToken id = (javaTS != null) ? javaTS.token().id() : null;

        // If caret within comment return false
        boolean caretInsideToken = (id != null)
                && (javaTS.offset() + javaTS.token().length() > context.getOffset()
                || javaTS.token().partType() == PartType.START);
        if (caretInsideToken && isComment(id)) {
            return -1;
        }

        boolean completablePosition = isQuoteCompletablePosition(context);
        boolean insideString = caretInsideToken
                && isStringLiteral(id);

        int lastNonWhite = org.netbeans.editor.Utilities.getRowLastNonWhite((BaseDocument) context.getDocument(), context.getOffset());
        // eol - true if the caret is at the end of line (ignoring whitespaces)
        boolean eol = lastNonWhite < context.getOffset();
        if (insideString) {
            if (eol) {
                return -1;
            } else {
                //#69524
                char chr = context.getDocument().getText(context.getOffset(), 1).charAt(0);
                if (chr == context.getText().charAt(0)) {
                    //#83044
                    if (context.getOffset() > 0) {
                        javaTS.move(context.getOffset() - 1);
                        if (javaTS.moveNext()) {
                            id = javaTS.token().id();
                            if (isStringLiteral(id)) {
                                context.setText("", 0); // NOI18N
                                return context.getOffset() + 1;
                            }
                        }
                    }
                }
            }
        }

        if ((completablePosition && !insideString) || eol) {
            if (context.getText().equals("\"") && context.getOffset() >= 2
                    && context.getDocument().getText(context.getOffset() - 2, 2).equals("\"\"")
                    && isTextBlockSupported(getFileObject((BaseDocument) context.getDocument()))) {
                context.setText("\"\n\"\"\"", 2, true);  // NOI18N
            } else {
                context.setText(context.getText() + context.getText(), 1);
            }
        } else if (context.getText().equals("\"")
                && isTextBlockSupported(getFileObject((BaseDocument) context.getDocument()))) {
            if (javaTS.moveNext()) {
                id = javaTS.token().id();
                if ((id == AntlrTokens.TOK_STRING_LITERAL) && (javaTS.token().text().toString().equals("\"\""))) {
                    if (context.getDocument().getText(context.getOffset(), 2).equals("\"\"")) {
                        context.setText("\"\"\"\n\"", 4, true);
                    }
                }
                javaTS.movePrevious();
                id = javaTS.token().id();
            }
        }
        return -1;
    }

    /**
     * Check for various conditions and possibly skip a closing bracket.
     *
     * @param context
     * @return relative caretOffset change
     * @throws BadLocationException
     */
    static int skipClosingBracket(TypedTextInterceptor.MutableContext context) throws BadLocationException {
        TokenSequence<AntlrToken> javaTS = javaTokenSequence(context, false);
        if (javaTS == null || (javaTS.token().id() != AntlrTokens.TOK_RPAREN /*&& javaTS.token().id() != AntlrTokens.TOK_RBRACE)*/ || isStringOrComment(javaTS.token().id()))) {
            return -1;
        }

        AntlrToken bracketId = bracketCharToId(context.getText().charAt(0));
        if (isSkipClosingBracket(context, javaTS, bracketId)) {
            context.setText("", 0);  // NOI18N
            return context.getOffset() + 1;
        }
        return -1;
    }

    private static boolean isQuoteCompletablePosition(TypedTextInterceptor.MutableContext context) throws BadLocationException {
        if (context.getOffset() == context.getDocument().getLength()) {
            return true;
        } else {
            for (int i = context.getOffset(); i < context.getDocument().getLength(); i++) {
                char chr = context.getDocument().getText(i, 1).charAt(0);
                if (chr == '\n') {
                    break;
                }
                if (!Character.isWhitespace(chr)) {
                    return (chr == ')' || chr == ',' || chr == '+' || chr == '}' || chr == ';');
                }

            }
            return false;
        }
    }

    private static boolean isEscapeSequence(TypedTextInterceptor.MutableContext context) throws BadLocationException {
        if (context.getOffset() <= 0) {
            return false;
        }

        char[] previousChars;
        for (int i = 2; context.getOffset() - i >= 0; i += 2) {
            previousChars = context.getDocument().getText(context.getOffset() - i, 2).toCharArray();
            if (previousChars[1] != '\\') {
                return false;
            }
            if (previousChars[0] != '\\') {
                return true;
            }
        }
        return context.getDocument().getText(context.getOffset() - 1, 1).charAt(0) == '\\';
    }

    private static boolean isTextBlockSupported(
            @NullAllowed FileObject fileObject) {
        return true;
    }

    static Set<AntlrToken> STOP_TOKENS_FOR_SKIP_CLOSING_BRACKET = new HashSet<>(
            Arrays.asList(
                    AntlrTokens.TOK_LBRACE, AntlrTokens.TOK_RBRACE, AntlrTokens.TOK_SEMI));

    private static final int[] WHITESPACE_TOKEN_IDS = new int[]{
        ANTLRv4Lexer.WS, ANTLRv4Lexer.HEADER_P_WS,
        ANTLRv4Lexer.HEADER_WS, ANTLRv4Lexer.HDR_PCKG_WS,
        ANTLRv4Lexer.HDR_IMPRT_WS, ANTLRv4Lexer.OPT_WS,
        ANTLRv4Lexer.TOK_WS, ANTLRv4Lexer.CHN_WS, ANTLRv4Lexer.IMPORT_WS,
        ANTLRv4Lexer.ID_WS, ANTLRv4Lexer.TOKDEC_WS,
        ANTLRv4Lexer.FRAGDEC_WS, ANTLRv4Lexer.PARDEC_WS,
        ANTLRv4Lexer.PARDEC_OPT_WS, ANTLRv4Lexer.LEXCOM_WS,
        ANTLRv4Lexer.TYPE_WS};

    private static boolean isWhitespace(AntlrToken tok) {
        return Arrays.binarySearch(WHITESPACE_TOKEN_IDS, tok.getNumericID()) >= 0;
    }

    private static boolean isSkipClosingBracket(TypedTextInterceptor.MutableContext context, TokenSequence<AntlrToken> javaTS, AntlrToken rightBracketId) {
        if (context.getOffset() == context.getDocument().getLength()) {
            return false;
        }

        boolean skipClosingBracket = false;

        if (javaTS != null && javaTS.token().id() == rightBracketId) {
            AntlrToken leftBracketId = matching(rightBracketId);
            // Skip all the brackets of the same type that follow the last one
            do {
                if (STOP_TOKENS_FOR_SKIP_CLOSING_BRACKET.contains(javaTS.token().id())
                        || (isWhitespace(javaTS.token().id()) && javaTS.token().text().toString().contains("\n"))) {  // NOI18N
                    while (javaTS.token().id() != rightBracketId) {
                        boolean isPrevious = javaTS.movePrevious();
                        if (!isPrevious) {
                            break;
                        }
                    }
                    break;
                }
            } while (javaTS.moveNext());

            // token var points to the last bracket in a group of two or more right brackets
            // Attempt to find the left matching bracket for it
            // Search would stop on an extra opening left brace if found
            int braceBalance = 0; // balance of '{' and '}'
            int bracketBalance = -1; // balance of the brackets or parenthesis
            int numOfSemi = 0;
            boolean finished = false;
            while (!finished && javaTS.movePrevious()) {
                AntlrToken id = javaTS.token().id();
                switch (id.getNumericID()) {
                    case ANTLRv4Lexer.LPAREN:
//                    case ANTLRv4Lexer.LBRACE:
                        if (id == leftBracketId) {
                            bracketBalance++;
                            if (bracketBalance == 1) {
                                if (braceBalance != 0) {
                                    // Here the bracket is matched but it is located
                                    // inside an unclosed brace block
                                    // e.g. ... ->( } a()|)
                                    // which is in fact illegal but it's a question
                                    // of what's best to do in this case.
                                    // We chose to leave the typed bracket
                                    // by setting bracketBalance to 1.
                                    // It can be revised in the future.
                                    bracketBalance = 2;
                                }
                                finished = javaTS.offset() < context.getOffset();
                            }
                        }
                        break;

                    case ANTLRv4Lexer.RPAREN:
//                    case ANTLRv4Lexer.RBRACE:
                        if (id == rightBracketId) {
                            bracketBalance--;
                        }
                        break;

                    case ANTLRv4Lexer.LBRACE:
                    case ANTLRv4Lexer.PARDEC_LBRACE:
                    case ANTLRv4Lexer.FRAGDEC_LBRACE:
                    case ANTLRv4Lexer.TOKDEC_LBRACE:
                    case ANTLRv4Lexer.BEGIN_ACTION:
                        braceBalance++;
                        if (braceBalance > 0) { // stop on extra left brace
                            finished = true;
                        }
                        break;

                    case ANTLRv4Lexer.RBRACE:
                    case ANTLRv4Lexer.END_ACTION:
                        braceBalance--;
                        break;
                    case ANTLRv4Lexer.SEMI:
                        numOfSemi++;
                        break;
                }
            }

            if (bracketBalance == 1 && numOfSemi < 2) {
                finished = false;
                while (!finished && javaTS.movePrevious()) {
                    AntlrToken tokId = javaTS.token().id();
                    if (isWhitespace(tokId)) {
                        continue;
                    }
                    if (COMMENT_TOKENS.contains(tokId)) {
                        continue;
                    }
                    finished = true;
                    break;
//
//                    switch (javaTS.token().id()) {
//                        case WHITESPACE:
//                        case LINE_COMMENT:
//                        case BLOCK_COMMENT:
//                        case JAVADOC_COMMENT:
//                            break;
//                        case FOR:
//                            bracketBalance--;
//                        default:
//                            finished = true;
//                            break;
//                    }
                }
            }

            skipClosingBracket = bracketBalance != 1;
        }
        return skipClosingBracket;

    }

    private static AntlrToken bracketCharToId(char bracket) {
        Optional<AntlrToken> result = AntlrTokens.forSymbol(bracket);
        assert result.isPresent() : "No Antlr token for '" + bracket + "'";
        return result.get();
    }

    static boolean isCompletionSettingEnabled() {
        return true;
    }

    private static char matching(char bracket) {
        switch (bracket) {
            case '(':
                return ')';
            case '[':
                return ']';
            case '\"':
                return '\"'; // NOI18N
            case '\'':
                return '\'';
            default:
                return ' ';
        }
    }

    private static AntlrToken matching(AntlrToken id) {
        switch (id.getNumericID()) {
            case ANTLRv4Lexer.LPAREN:
                return AntlrTokens.TOK_RPAREN;
            case ANTLRv4Lexer.LBRACE:
                return AntlrTokens.TOK_RBRACE;
            case ANTLRv4Lexer.RPAREN:
                return AntlrTokens.TOK_LPAREN;
            case ANTLRv4Lexer.RBRACE:
                return AntlrTokens.TOK_RBRACE;
            default:
                return null;
        }
    }

    static void completeOpeningBracket(TypedTextInterceptor.MutableContext context) throws BadLocationException {
        if (isStringOrComment(javaTokenSequence(context, false).token().id())) {
            return;
        }

        char chr = context.getDocument().getText(context.getOffset(), 1).charAt(0);
        if (chr == ')' || chr == ',' || chr == '\"' || chr == '\'' || chr == ' ' || chr == ']' || chr == '}' || chr == '\n' || chr == '\t' || chr == ';') {
            char insChr = context.getText().charAt(0);
            context.setText("" + insChr + matching(insChr), 1);  // NOI18N
        }
    }

    private static TokenSequence<AntlrToken> javaTokenSequence(TypedTextInterceptor.MutableContext context, boolean backwardBias) {
        return javaTokenSequence(context.getDocument(), context.getOffset(), backwardBias);
    }

    private static TokenSequence<AntlrToken> javaTokenSequence(DeletedTextInterceptor.Context context, boolean backwardBias) {
        return javaTokenSequence(context.getDocument(), context.getOffset(), backwardBias);
    }

    private static TokenSequence<AntlrToken> javaTokenSequence(TypedBreakInterceptor.Context context, boolean backwardBias) {
        return javaTokenSequence(context.getDocument(), context.getCaretOffset(), backwardBias);
    }

    /**
     * Get token sequence positioned over a token.
     *
     * @param doc
     * @param caretOffset
     * @param backwardBias
     * @return token sequence positioned over a token that "contains" the offset
     * or null if the document does not contain any java token sequence or the
     * offset is at doc-or-section-start-and-bwd-bias or
     * doc-or-section-end-and-fwd-bias.
     */
    @SuppressWarnings("unchecked")
    private static TokenSequence<AntlrToken> javaTokenSequence(Document doc, int caretOffset, boolean backwardBias) {
        TokenHierarchy<?> hi = TokenHierarchy.get(doc);
        List<TokenSequence<?>> tsList = hi.embeddedTokenSequences(caretOffset, backwardBias);
        // Go from inner to outer TSes
        for (int i = tsList.size() - 1; i >= 0; i--) {
            TokenSequence<?> ts = tsList.get(i);
            if (ts.languagePath().innerLanguage() == AntlrHierarchy.antlrLanguage()) {
                TokenSequence<AntlrToken> javaInnerTS = (TokenSequence<AntlrToken>) ts;
                return javaInnerTS;
            }
        }
        return null;
    }

    private static FileObject getFileObject(BaseDocument doc) {
        return doc != null ? NbEditorUtilities.getFileObject(doc) : null;
    }

    private static boolean isStringOrComment(AntlrToken javaTokenId) {
        int val = javaTokenId.getNumericID();
        return Arrays.binarySearch(tokenIds, val) >= 0;
//        return STRING_AND_COMMENT_TOKENS.contains(javaTokenId);
    }

    static final Set<AntlrToken> STRING_AND_COMMENT_TOKENS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(AntlrTokens.TOK_DOC_COMMENT,
            AntlrTokens.TOK_BLOCK_COMMENT, AntlrTokens.TOK_LINE_COMMENT,
            AntlrTokens.TOK_HEADER_P_LINE_COMMENT, AntlrTokens.TOK_HEADER_P_BLOCK_COMMENT,
            AntlrTokens.TOK_HEADER_LINE_COMMENT, AntlrTokens.TOK_HEADER_BLOCK_COMMENT,
            AntlrTokens.TOK_HDR_PCKG_LINE_COMMENT, AntlrTokens.TOK_HDR_IMPRT_LINE_COMMENT,
            AntlrTokens.TOK_OPT_BLOCK_COMMENT, AntlrTokens.TOK_OPT_LINE_COMMENT,
            AntlrTokens.TOK_TOK_BLOCK_COMMENT, AntlrTokens.TOK_TOK_LINE_COMMENT,
            AntlrTokens.TOK_CHN_BLOCK_COMMENT, AntlrTokens.TOK_CHN_LINE_COMMENT,
            AntlrTokens.TOK_IMPORT_LINE_COMMENT, AntlrTokens.TOK_IMPORT_BLOCK_COMMENT,
            AntlrTokens.TOK_ID_LINE_COMMENT, AntlrTokens.TOK_ID_BLOCK_COMMENT,
            AntlrTokens.TOK_FRAGDEC_LINE_COMMENT, AntlrTokens.TOK_PARDEC_LINE_COMMENT,
            AntlrTokens.TOK_PARDEC_BLOCK_COMMENT, AntlrTokens.TOK_PARDEC_OPT_BLOCK_COMMENT,
            AntlrTokens.TOK_PARDEC_OPT_LINE_COMMENT, AntlrTokens.TOK_LEXCOM_BLOCK_COMMENT,
            AntlrTokens.TOK_LEXCOM_LINE_COMMENT, AntlrTokens.TOK_TYPE_LINE_COMMENT,
            //                AntlrTokens.TOK_UNTERMINATED_STRING_LITERAL,
            AntlrTokens.TOK_STRING_LITERAL
    )));

    static final Set<AntlrToken> COMMENT_TOKENS;

    static final int[] tokenIds = new int[STRING_AND_COMMENT_TOKENS.size()];

    static {
        int cursor = 0;
        for (AntlrToken tk : STRING_AND_COMMENT_TOKENS) {
            tokenIds[cursor++] = tk.getNumericID();
        }
        Arrays.sort(tokenIds);
        Set<AntlrToken> cmts = new HashSet<>(STRING_AND_COMMENT_TOKENS);
        cmts.remove(AntlrTokens.TOK_STRING_LITERAL);
        COMMENT_TOKENS = Collections.unmodifiableSet(cmts);
    }

}
