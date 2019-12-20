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

import java.util.function.BiPredicate;
import java.util.function.IntPredicate;
import javax.swing.text.BadLocationException;
import org.nemesis.charfilter.CharPredicate;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class InsertBoilerplateEditProcessorFactory extends EnablableEditProcessorFactory<TypedTextInterceptor.Context> implements BiPredicate<EditPhase, ContextWrapper>, EditProcessorFactory<TypedTextInterceptor.Context> {

    private final CharPredicate keyTest;
    private final String toInsert;
    private final IntPredicate precedingToken;
    private final Boolean requireLineEnd;
    private final EditorFeatureUtils utils;
    private final TokenPattern precedingPattern;
    private final TokenPattern followingPattern;
    private final int caretBackup;
    private final IntPredicate currentTokenNot;

    InsertBoilerplateEditProcessorFactory(String mimeType, CharPredicate keyTest, String toInsert, IntPredicate precedingToken, Boolean requireLineEnd, TokenPattern precedingPattern, TokenPattern followingPattern, int caretBackup, String name, String description, Class<?> ownerType, String category, IntPredicate currentTokenNot) {
        super(ownerType, name, description, category);
        this.utils = new EditorFeatureUtils(mimeType);
        this.keyTest = keyTest;
        this.toInsert = toInsert;
        this.precedingToken = precedingToken;
        this.requireLineEnd = requireLineEnd;
        this.precedingPattern = precedingPattern;
        this.followingPattern = followingPattern;
        this.caretBackup = caretBackup;
        this.currentTokenNot = currentTokenNot;
    }

    public String id() {
        StringBuilder sb = new StringBuilder("i-");
        for (int i = 0; i < toInsert.length(); i++) {
            char c = toInsert.charAt(i);
            sb.append(Integer.toHexString(c));
        }
        return sb.toString();
    }

    @Override
    public EditPhase initiatesFrom() {
        return EditPhase.ON_TYPING_INSERT;
    }

    @Override
    public String toString() {
        return "InsertBoilerplateOp('" + toInsert + "' prec " + precedingToken + " char " + keyTest + " rle " + requireLineEnd + " for " + utils.mimeType() + (name == null ? "" : " - " + name) + ")";
    }

    @Override
    public boolean test(EditPhase op, ContextWrapper t) {
        boolean result = op == EditPhase.ON_TYPING_INSERT;
        if (result) {
            try {
                if (requireLineEnd != null) {
                    boolean atLineEnd = utils.isAtRowEnd(t.document(), t.getOffset());
                    result = requireLineEnd.booleanValue() == atLineEnd;
                }
                if (result) {
                    char c = t.getText().charAt(0);
                    result = keyTest.test(c);
                }
                if (result && currentTokenNot != null) {
                    result = utils.withTokenSequence(t.document(), t.getOffset(), true, (ts) -> {
                        ts.move(t.getOffset());
                        if (ts.moveNext()) {
                            int type = ts.token().id().ordinal();
                            boolean mismatch = currentTokenNot.test(type);
                            return !mismatch;
                        }
                        return true;
                    });
                }
                if (result) {
                    result = utils.previousTokenMatches(t.document(), t.getOffset(), precedingToken);
                }
                if (result && precedingPattern != null) {
                    result = precedingPattern.test(utils, t, t.getOffset());
                }
                if (result && followingPattern != null) {
                    result = followingPattern.test(utils, t, t.getOffset());
                }
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
                result = false;
            }
        }
        return result;
    }

    @Override
    public Class<TypedTextInterceptor.Context> type() {
        return TypedTextInterceptor.Context.class;
    }

    @Override
    public EditProcessor apply(EditPhase op, ContextWrapper ctx) {
        if (test(op, ctx)) {
            return new IbOp();
        }
        return null;
    }

    class IbOp implements EditProcessor {

        int caretTo = -1;

        @Override
        public void onInsert(ContextWrapper ctx) {
            caretTo = ctx.getOffset() + toInsert.length();
            ctx.setText(toInsert, 0, true);
        }

        @Override
        public void onAfterInsert(ContextWrapper ctx) {
            if (caretTo != -1) {
                ctx.component().getCaret().setDot(caretTo - caretBackup);
            }
        }
    }

}
