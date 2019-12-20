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

import com.mastfrog.predicates.integer.IntPredicates;
import java.util.function.IntPredicate;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import org.netbeans.api.lexer.Token;
import org.netbeans.editor.BaseDocument;
import org.netbeans.spi.editor.typinghooks.DeletedTextInterceptor;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class DelimiterTokenPairDeletion extends EnablableEditProcessorFactory<DeletedTextInterceptor.Context> {

    private final int openingTokenId;
    private final int closingTokenId;
    private final EditorFeatureUtils utils;
    private final IntPredicate ignore;

    DelimiterTokenPairDeletion(String mimeType, int openingTokenId, int closingTokenId, String name, String description, Class<?> ownerType, IntPredicate ignore, String category) {
        super(ownerType, name, description, category);
        this.openingTokenId = openingTokenId;
        this.closingTokenId = closingTokenId;
        utils = new EditorFeatureUtils(mimeType);
        this.ignore = ignore == null ? IntPredicates.alwaysFalse() : ignore;
    }

    @Override
    protected String id() {
        return "dt" + Integer.toHexString(openingTokenId) + Integer.toHexString(closingTokenId);
    }

    @Override
    public Class<DeletedTextInterceptor.Context> type() {
        return DeletedTextInterceptor.Context.class;
    }

    @Override
    public EditPhase initiatesFrom() {
        return EditPhase.ON_BEFORE_REMOVE;
    }

    @Override
    @SuppressWarnings(value = "empty-statement")
    public EditProcessor apply(EditPhase t, ContextWrapper u) {
        boolean result = t == EditPhase.ON_BEFORE_REMOVE;
        if (result) {
            BaseDocument doc = u.document();
            DeleteToken[] res = new DeleteToken[1];
            doc.readLock();
            try {
                int bal = utils.tokenBalance(doc, openingTokenId, closingTokenId);
                if (bal != 0 || bal == Integer.MAX_VALUE) {
                    return null;
                }
                boolean bwd = u.isBackwardDelete();
                utils.withTokenSequence(doc, u.getOffset(), bwd, (ts) -> {
                    if (ts.token().id().ordinal() == openingTokenId) {
                        int tokId = -1;
                        while (ts.moveNext() && ignore.test(tokId = ts.token().id().ordinal())) {
                            ;
                        }
                        if (closingTokenId == tokId) {
                            try {
                                Token<?> tok = ts.token();
                                int off = ts.offset();
                                int len = tok.length();
                                Position startPos = doc.createPosition(off, Position.Bias.Backward);
                                Position endPos = doc.createPosition(off + len, Position.Bias.Forward);
                                res[0] = new DeleteToken(startPos, endPos);
                            } catch (BadLocationException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                    }
                    return true;
                });
            } finally {
                doc.readUnlock();
            }
            return res[0];
        }
        return null;
    }

    static class DeleteToken implements EditProcessor {

        private final Position start;
        private final Position end;

        public DeleteToken(Position start, Position end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return "Delete token " + start.getOffset() + ":" + end.getOffset();
        }

        @Override
        public void onRemove(ContextWrapper ctx) {
            int st = start.getOffset();
            int en = end.getOffset();
            if (st != en) {
                try {
                    ctx.document().remove(st, en - st);
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

}
