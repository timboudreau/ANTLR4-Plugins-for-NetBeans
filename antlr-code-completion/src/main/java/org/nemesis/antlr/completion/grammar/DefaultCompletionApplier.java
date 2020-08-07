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
package org.nemesis.antlr.completion.grammar;

import com.mastfrog.antlr.code.completion.spi.CaretToken;
import com.mastfrog.antlr.code.completion.spi.CaretTokenRelation;
import static com.mastfrog.antlr.code.completion.spi.CaretTokenRelation.AT_TOKEN_END;
import static com.mastfrog.antlr.code.completion.spi.CaretTokenRelation.AT_TOKEN_START;
import static com.mastfrog.antlr.code.completion.spi.CaretTokenRelation.WITHIN_TOKEN;
import com.mastfrog.antlr.code.completion.spi.CompletionApplier;
import com.mastfrog.util.strings.Strings;
import java.awt.EventQueue;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.caret.CaretMoveContext;
import org.netbeans.api.editor.caret.EditorCaret;
import org.netbeans.api.editor.caret.MoveCaretsOrigin;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultCompletionApplier implements CompletionApplier {

    private final CaretToken tokenInfo;
    private final String name;

    public DefaultCompletionApplier(CaretToken token, String name) {
        this.tokenInfo = token;
        this.name = name;
    }

    @Override
    public void accept(JTextComponent comp, StyledDocument doc) throws BadLocationException {
        NbDocument.runAtomic((StyledDocument) doc, () -> {
            try {
                int end = applyToDocument(doc);
                int offset = isCommonEnclosingPair() || name.endsWith("()") ? -1 : 0;
                Position endPosition = NbDocument.createPosition(doc, end - offset, Position.Bias.Backward);
                Caret caret = comp.getCaret();
                if (caret instanceof EditorCaret) {
                    EditorCaret ec = (EditorCaret) caret;
                    ec.moveCarets((CaretMoveContext context) -> {
                        context.setDotAndMark(ec.getLastCaret(), endPosition,
                                Position.Bias.Forward, endPosition, Position.Bias.Backward);
                    }, MoveCaretsOrigin.DISABLE_FILTERS);
                } else {
                    EventQueue.invokeLater(() -> {
                        comp.getCaret().setDot(end - offset);
                    });
                }
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
    }

    enum Op {
        // The order of these matters!
        DELETE_SUFFIX,
        DELETE_PREFIX,
        ELIDE_PREFIX,
        PREPEND_PREFIX_AND_SUFFIX,
        APPEND_PREFIX_AND_SUFFIX,
        PREPEND_SPACE,
        APPEND_SPACE,
        MOVE_INSERTION_POSITION_BEFORE_NEWLINES,
        PRESERVE_TRAILING_NEWLINES_AND_WHITESPACE;
    }

    private int subtractNewlines(Position caretPosition) {
        int caretOffset = tokenInfo.caretPositionInDocument() - tokenInfo.tokenStart();
        if (caretOffset > 0) {
            String txt = tokenInfo.leadingTokenText();
            if (Strings.isBlank(txt) && txt != null) {
                int ix = txt.indexOf('\n');
                if (ix >= 0) {
                    return caretOffset - ix;
                }
            }
        }
        return 0;
    }

    private int applyToDocument(Document doc) throws BadLocationException {
//        System.out.println("==================== apply " + Escaper.CONTROL_CHARACTERS.escape(name) + " =================================");
        StringBuilder sb = new StringBuilder(name);
        Set<Op> ops = insertionOps();
        Position pos = NbDocument.createPosition(doc,
                tokenInfo.caretPositionInDocument(), Position.Bias.Backward);
        Position tokenStartPos = NbDocument.createPosition(doc,
                tokenInfo.tokenStart(), Position.Bias.Backward);
        Position insertPosition = pos;
        for (Op o : ops) {
            switch (o) {
                case ELIDE_PREFIX:
                    sb.delete(0, tokenInfo.leadingTokenText().length());
                    break;
                case PREPEND_PREFIX_AND_SUFFIX:
                    sb.insert(0, tokenInfo.leadingTokenText() + tokenInfo.trailingTokenText());
                    break;
                case PREPEND_SPACE:
                    if (sb.length() == 0 || !Character.isWhitespace(sb.charAt(0))) {
                        sb.insert(0, ' ');
                    }
                    break;
                case APPEND_SPACE:
                    if (sb.length() == 0 || !Character.isWhitespace(sb.charAt(sb.length() - 1))) {
                        sb.append(' ');
                    }
                    break;
                case PRESERVE_TRAILING_NEWLINES_AND_WHITESPACE:
                    sb.append(tokenInfo.trailingNewlinesAndWhitespace());
                    break;
                case DELETE_PREFIX:
//                    System.out.println("Remove '" + Escaper.CONTROL_CHARACTERS.escape(doc.getText(tokenInfo.tokenStart(), tokenInfo.leadingTokenText().length()))
//                            + "' at " + tokenInfo.tokenStart() + " length " + tokenInfo.leadingTokenText().length()
//                            + " should be '" + Escaper.CONTROL_CHARACTERS.escape(tokenInfo.leadingTokenText()) + "'");
                    doc.remove(tokenStartPos.getOffset(), tokenInfo.leadingTokenText().length());
                    break;
                case DELETE_SUFFIX:
//                    System.out.println("Remove '" + Escaper.CONTROL_CHARACTERS.escape(doc.getText(tokenInfo.caretPositionInDocument(), tokenInfo.trailingTokenText().length()))
//                            + "' at " + tokenInfo.caretPositionInDocument() + " length " + tokenInfo.trailingTokenText().length()
//                            + " should be '" + Escaper.CONTROL_CHARACTERS.escape(tokenInfo.trailingTokenText()) + "'");
                    doc.remove(pos.getOffset(), tokenInfo.trailingTokenText().length());
                    break;
                case APPEND_PREFIX_AND_SUFFIX:
                    sb.append(tokenInfo.leadingTokenText()).append(tokenInfo.trailingTokenText());
                    break;
                case MOVE_INSERTION_POSITION_BEFORE_NEWLINES:
                    int off = subtractNewlines(pos);
                    if (off > 0) {
                        insertPosition = NbDocument.createPosition(doc, pos.getOffset() - off, Position.Bias.Backward);
                    }
                    break;
            }
        }
        int offset = pos.getOffset();
        String toInsert = sb.toString();
//        System.out.println("  insert at " + offset + ": '" + Escaper.CONTROL_CHARACTERS.escape(toInsert) + "'");
        doc.insertString(insertPosition.getOffset(), toInsert, null);
        return offset + toInsert.length();
    }

    boolean isCommonEnclosingPair() {
        if (name.length() == 2 && isPunctuation()) {
            char first = name.charAt(0);
            char last = name.charAt(1);
            switch (first) {
                case '\'':
                    return last == '\'';
                case '"':
                    return last == '"';
                case '(':
                    return last == ')';
                case '{':
                    return last == '}';
                case '[':
                    return last == ']';
                case ':':
                case '.':
                    return false;
                default:
                    return first == last;
            }
        }
        return false;
    }

    private int sharedStartLength(String a, String b) {
        int max = Math.min(a.length(), b.length());
        for (int i = 0; i < max; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return i;
            }
        }
        return max;
    }

    private Set<Op> insertionOps() {
        Set<Op> result = EnumSet.noneOf(Op.class);
        String pfx = tokenInfo.leadingTokenText();
        String sfx = tokenInfo.trailingTokenText();
        CaretToken prev = tokenInfo.before();
        CaretToken next = tokenInfo.after();
        CaretTokenRelation rel = tokenInfo.caretRelation();
        String name = this.name.trim();
        switch (rel) {
            case WITHIN_TOKEN:
                if (!pfx.isEmpty() && name.startsWith(pfx)) {
                    result.add(Op.ELIDE_PREFIX);
                    result.add(Op.DELETE_SUFFIX);
                } else if (!pfx.isEmpty() && !Strings.isBlank(pfx)) {
                    result.add(Op.DELETE_PREFIX);
                }
                if (!sfx.isEmpty() && !Strings.isBlank(sfx)) {
                    result.add(Op.DELETE_SUFFIX);
                }
                if (pfx.isEmpty() && !sfx.isEmpty() && sharedStartLength(name, sfx) > 3) {
                    result.add(Op.DELETE_SUFFIX);
                }
                if (tokenInfo.isWhitespace()) {
                    result.add(Op.DELETE_PREFIX);
                    result.add(Op.DELETE_SUFFIX);
                    result.add(Op.PRESERVE_TRAILING_NEWLINES_AND_WHITESPACE);
                    if (!isPunctuation()) {
                        result.add(Op.PREPEND_SPACE);
                    } else {
                        if (!isCommonEnclosingPair()) {
                            result.add(Op.APPEND_PREFIX_AND_SUFFIX);
                        }
                    }
                }
                if (isPunctuation() && !tokenInfo.isPunctuation() && !tokenInfo.isWhitespace()) {
                    // We want to add punctuation to the end of the current
                    // word
                    result.add(Op.DELETE_PREFIX);
                    result.add(Op.DELETE_SUFFIX);
                    result.add(Op.PREPEND_PREFIX_AND_SUFFIX);
                }
                break;
            case AT_TOKEN_START:
                if (tokenInfo.isWhitespace() && pfx.isEmpty() && !tokenInfo.isPunctuation()) {
                    result.add(Op.PREPEND_SPACE);
                }
                if (!next.isWhitespace() && !next.isPunctuation() && !tokenInfo.isWhitespace()) {
                    result.add(Op.APPEND_SPACE);
                }
                if (!prev.isWhitespace() && !prev.isPunctuation()) {
                    result.add(Op.PREPEND_SPACE);
                }
                break;
            case AT_TOKEN_END:
                if (tokenInfo.isWhitespace()) {
                    if (next != null && !next.isWhitespace() && !next.isPunctuation()) {
                        result.add(Op.APPEND_SPACE);
                    }
                } else {
                    if (!Strings.isBlank(pfx) && name.startsWith(pfx)) {
                        result.add(Op.DELETE_PREFIX);
                    }
                    if (!Strings.isBlank(pfx) && !name.startsWith(pfx)) {
                        result.add(Op.PREPEND_SPACE);
                    } else if (!tokenInfo.isWhitespace() && !tokenInfo.isPunctuation() && !prev.isWhitespace()) {
                        result.add(Op.PREPEND_SPACE);
                    } else if (next.isPunctuation() && !tokenInfo.isWhitespace()) {
                        result.add(Op.PREPEND_SPACE);
                    } else if (!prev.isWhitespace() && !prev.isPunctuation()) {
                        result.add(Op.PREPEND_SPACE);
                    }
                }
        }
        if (result.contains(Op.APPEND_SPACE) && sfx.length() > 0 && Strings.isBlank(sfx)) {
            result.add(Op.DELETE_SUFFIX);
        }
        if (result.contains(Op.PREPEND_SPACE) && pfx.length() > 0 && Strings.isBlank(pfx)) {
            result.add(Op.DELETE_PREFIX);
        }
        return result;
    }

    private boolean isPunctuation() {
        return Strings.isPunctuation(name);
    }
}
