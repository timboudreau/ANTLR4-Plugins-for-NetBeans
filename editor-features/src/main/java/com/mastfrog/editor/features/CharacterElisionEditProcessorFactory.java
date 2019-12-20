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

import java.util.function.IntPredicate;
import javax.swing.text.BadLocationException;
import org.netbeans.editor.BaseDocument;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class CharacterElisionEditProcessorFactory extends EnablableEditProcessorFactory<TypedTextInterceptor.Context> {

    private final boolean backwards;
    private final char what;
    private final IntPredicate currentTokenNot;
    private final EditorFeatureUtils utils;

    public CharacterElisionEditProcessorFactory(String mimeType, boolean backwards, char what, IntPredicate currentTokenNot, Class<?> ownerType, String name, String desc, String category) {
        super(ownerType, name, desc, category);
        this.backwards = backwards;
        this.what = what;
        this.currentTokenNot = currentTokenNot;
        this.utils = new EditorFeatureUtils(mimeType);
    }

    @Override
    protected String id() {
        return "eld-" + what + (backwards ? "B" : "F");
    }

    @Override
    public Class<TypedTextInterceptor.Context> type() {
        return TypedTextInterceptor.Context.class;
    }

    @Override
    public EditPhase initiatesFrom() {
        return EditPhase.ON_BEFORE_TYPING_INSERT;
    }

    @Override
    public EditProcessor apply(EditPhase t, ContextWrapper u) {
        String txt = u.getText();
        if (txt.charAt(0) != what) {
            return null;
        }
        if (utils.nextTokenMatches(u.document(), u.getOffset(), currentTokenNot)) {
            return null;
        }
        return new Elision();
    }

    class Elision implements EditProcessor {

        int caretPos = -1;

        @Override
        public void onBeforeInsert(ContextWrapper ctx) {
            int ix = ctx.getOffset();
            BaseDocument doc = ctx.document();
            doc.readLock();
            try {
                int len = doc.getLength();
                try {
                    if (backwards) {
                        CharSequence seq = DocumentUtilities.getText(doc, 0, ix);
                        int max = seq.length();
                        for (int i = max - 1; i >= 0; i--) {
                            char c = seq.charAt(i);
                            if (what == c) {
                                caretPos = 0;
                                break;
                            } else if (!Character.isWhitespace(c)) {
                                break;
                            }
                        }
                    } else {
                        CharSequence seq = DocumentUtilities.getText(doc, ix, len - ix);
                        int max = seq.length();
                        for (int i = 0; i < max; i++) {
                            char c = seq.charAt(i);
                            if (c == what) {
                                caretPos = ix + i + 1;
                                break;
                            } else if (!Character.isWhitespace(c)) {
                                break;
                            }
                        }
                        if (caretPos != -1) {
                            ctx.component().getCaret().setDot(caretPos);
                        }
                    }
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            } finally {
                doc.readUnlock();
            }
        }

        @Override
        public boolean consumesInitialEvent() {
            return caretPos != -1;
        }
    }

}
