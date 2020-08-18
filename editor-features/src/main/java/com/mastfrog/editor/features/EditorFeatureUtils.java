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
package com.mastfrog.editor.features;

import com.mastfrog.function.state.Bool;
import com.mastfrog.function.state.Int;
import com.mastfrog.function.state.Obj;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class EditorFeatureUtils {

    private final String mimeType;

    public EditorFeatureUtils(String mimeType) {
        this.mimeType = mimeType;
    }

    String mimeType() {
        return mimeType;
    }

    boolean isAtRowEnd(Document doc, int position) throws BadLocationException {
        LineDocument ld = LineDocumentUtils.as(doc, LineDocument.class);
        int last = LineDocumentUtils.getLineLastNonWhitespace(ld, position);
        return last <= position;
    }

    boolean isAtRowStart(Document doc, int position) throws BadLocationException {
        LineDocument ld = LineDocumentUtils.as(doc, LineDocument.class);
        int start = LineDocumentUtils.getLineStart(ld, position);
        return start <= position;
    }

    boolean previousTokenMatches(Document doc, int position, IntPredicate tokenIdTest) {
        return withTokenSequence(doc, position, true, seq -> {
            seq.move(position);
            if (seq.movePrevious()) {
                Token<?> tok = seq.token();
                int ord = tok.id().ordinal();
                boolean result = tokenIdTest.test(ord);
                return result;
            }
            return false;
        });
    }

    boolean nextTokenMatches(Document doc, int position, IntPredicate tokenIdTest) {
        return withTokenSequence(doc, position, false, seq -> {
            if (seq.moveNext()) {
                Token<?> tok = seq.token();
                int ord = tok.id().ordinal();
                return tokenIdTest.test(ord);
            }
            return false;
        });
    }

    boolean nearbyTokenMatches(int distance, Document doc, int position, IntPredicate tokenIdTest) {
        TokenSequence<?> seq = tokenSequence(mimeType, doc, position, false);
        BooleanSupplier move = distance < 0 ? seq::movePrevious : seq::moveNext;
        int abs = Math.abs(distance);
        for (int i = 0; i < abs; i++) {
            if (!move.getAsBoolean()) {
                return false;
            }
        }
        Token<?> tok = seq.token();
        int ord = tok.id().ordinal();
        return tokenIdTest.test(ord);
    }

    boolean withTokenSequence(Document doc, int caretPosition, boolean backwardBias, SeqPredicate p) {
        return withTokenSequence(mimeType, doc, caretPosition, backwardBias, p);
//        TokenSequence<?> seq = tokenSequence(mimeType, doc, caretPosition, backwardBias);
//        if (seq == null) {
//            return false;
//        }
//        return p.test(seq);
    }

    int tokenBalance(Document doc, int leftTokenId, int rightTokenId) {
        Int result = Int.create();
        runLocked(doc, () -> {
            TokenBalance tb = TokenBalance.get(doc);
            tb.ensureTracked(mimeType, leftTokenId, rightTokenId);
            int balance = tb.balance(mimeType, leftTokenId);
            result.set(balance);
        });
        return result.get();
    }

    static boolean withTokenSequence(String mimeType, Document doc, int caretOffset, boolean backwardBias, SeqPredicate c) {
        Bool bool = Bool.create();
        BLRun r = () -> {
            TokenSequence<?> seq = tokenSequence(mimeType, doc, caretOffset, backwardBias);
            if (seq != null) {
                bool.set(c.test(seq));
            }
        };
        if (!runLocked(doc, r)) {
            return false;
        }
        return bool.get();
    }

    static TokenSequence<?> tokenSequence(String mimeType, Document doc, int caretOffset, boolean backwardBias) {
        TokenHierarchy<?> hi = TokenHierarchy.get(doc);
        List<TokenSequence<?>> tsList = hi.embeddedTokenSequences(caretOffset, backwardBias);
        // Go from inner to outer TSes
        for (int i = tsList.size() - 1; i >= 0; i--) {
            TokenSequence<?> ts = tsList.get(i);
            if (mimeType.equals(ts.languagePath().innerLanguage().mimeType())) {
                return ts;
            }
        }
        return null;
    }

    @SuppressWarnings("ThrowableResultIgnored")
    static boolean runLocked(@NonNull Document doc, @NonNull BLRun r) {
        // Shoehorns two generations of NetBeans Editor API together.
        // In particular, during the initial formatting of a newly
        // created Document, we can be passed a Document that is neither
        // BaseDocument nor StyledDocument, but some temporary instance
        Obj<BadLocationException> obj = Obj.create();
        if (doc instanceof StyledDocument) {
            NbDocument.runAtomic((StyledDocument) doc, () -> {
                try {
                    r.run();
                } catch (BadLocationException ble) {
                    obj.set(ble);
                }
            });
        } else if (doc instanceof BaseDocument) {
            ((BaseDocument) doc).runAtomic(() -> {
                try {
                    r.run();
                } catch (BadLocationException ble) {
                    obj.set(ble);
                }
            });
        } else {
            doc.render(() -> {
                try {
                    r.run();
                } catch (BadLocationException ble) {
                    obj.set(ble);
                }
            });
        }
        if (obj.isSet()) {
            Exceptions.printStackTrace(obj.get());
            return false;
        }
        return true;
    }

    interface BLRun {

        public void run() throws BadLocationException;
    }

    interface SeqPredicate {

        boolean test(TokenSequence<?> seq) throws BadLocationException;
    }
}
