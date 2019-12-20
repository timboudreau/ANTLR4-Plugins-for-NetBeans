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

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;

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

    boolean isAtRowEnd(BaseDocument doc, int position) throws BadLocationException {
        LineDocument ld = LineDocumentUtils.as(doc, LineDocument.class);
        int last = LineDocumentUtils.getLineLastNonWhitespace(ld, position);
        return last <= position;
    }

    boolean isAtRowStart(BaseDocument doc, int position) throws BadLocationException {
        LineDocument ld = LineDocumentUtils.as(doc, LineDocument.class);
        int start = LineDocumentUtils.getLineStart(ld, position);
        return start <= position;
    }

    boolean previousTokenMatches(BaseDocument doc, int position, IntPredicate tokenIdTest) {
        TokenSequence<?> seq = tokenSequence(mimeType, doc, position, true);
        seq.move(position);
        if (seq.movePrevious()) {
            Token<?> tok = seq.token();
            int ord = tok.id().ordinal();
            boolean result = tokenIdTest.test(ord);
            return result;
        }
        return false;
    }

    boolean nextTokenMatches(BaseDocument doc, int position, IntPredicate tokenIdTest) {
        TokenSequence<?> seq = tokenSequence(mimeType, doc, position, false);
        if (seq.moveNext()) {
            Token<?> tok = seq.token();
            int ord = tok.id().ordinal();
            return tokenIdTest.test(ord);
        }
        return false;
    }

    boolean nearbyTokenMatches(int distance, BaseDocument doc, int position, IntPredicate tokenIdTest) {
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

    boolean withTokenSequence(Document doc, int caretPosition, boolean backwardBias, Predicate<TokenSequence<?>> p) {
        TokenSequence<?> seq = tokenSequence(mimeType, doc, caretPosition, backwardBias);
        if (seq == null) {
            return false;
        }
        return p.test(seq);
    }

    int tokenBalance(Document doc, int leftTokenId, int rightTokenId) {
        TokenBalance tb = TokenBalance.get(doc);
        tb.ensureTracked(mimeType, leftTokenId, rightTokenId);
        int balance = tb.balance(mimeType, leftTokenId);
        return balance;
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
}
