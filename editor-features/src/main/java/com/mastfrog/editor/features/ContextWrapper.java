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

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.spi.editor.typinghooks.DeletedTextInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedBreakInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor;

/**
 * Hammers back together a bunch of unrelated classes which are all identical
 * save for 1-2 methods.
 */
abstract class ContextWrapper {

    abstract Document document();

    abstract JTextComponent component();

    abstract int getOffset();

    abstract String getText();

    static ContextWrapper wrap(Object ctx) {
        if (ctx instanceof TypedTextInterceptor.MutableContext) {
            return new MutableTypedTextContextWrapper((TypedTextInterceptor.MutableContext) ctx);
        } else if (ctx instanceof TypedBreakInterceptor.MutableContext) {
            return new MutableTypedBreakContextWrapper((TypedBreakInterceptor.MutableContext) ctx);
        } else if (ctx instanceof TypedTextInterceptor.Context) {
            return new TypedTextContextWrapper((TypedTextInterceptor.Context) ctx);
        } else if (ctx instanceof DeletedTextInterceptor.Context) {
            return new DeletedTextContextWrapper((DeletedTextInterceptor.Context) ctx);
        } else if (ctx instanceof TypedBreakInterceptor.Context) {
            return new TypedBreakContextWrapper((TypedBreakInterceptor.Context) ctx);
        } else {
            throw new UnsupportedOperationException(ctx + "");
        }
    }

    boolean isMutable() {
        return false;
    }

    boolean isBackwardDelete() {
        return false;
    }

    String getReplacedText() {
        return getText();
    }

    void setText(String text, int caretPosition) {
        throw new UnsupportedOperationException();
    }

    void setText(String text, int caretPosition, boolean formatNewLines) {
        throw new UnsupportedOperationException();
    }

    private static class TypedTextContextWrapper extends ContextWrapper {

        private final TypedTextInterceptor.Context ctx;

        public TypedTextContextWrapper(TypedTextInterceptor.Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public Document document() {
            return ctx.getDocument();
        }

        @Override
        public JTextComponent component() {
            return ctx.getComponent();
        }

        @Override
        public int getOffset() {
            return ctx.getOffset();
        }

        @Override
        public String getText() {
            return ctx.getText();
        }
    }

    private static class MutableTypedTextContextWrapper extends ContextWrapper {

        private final TypedTextInterceptor.MutableContext ctx;

        public MutableTypedTextContextWrapper(TypedTextInterceptor.MutableContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public boolean isMutable() {
            return true;
        }

        @Override
        public Document document() {
            return  ctx.getDocument();
        }

        @Override
        public JTextComponent component() {
            return ctx.getComponent();
        }

        @Override
        public int getOffset() {
            return ctx.getOffset();
        }

        @Override
        public String getText() {
            return ctx.getText();
        }

        @Override
        public void setText(String text, int caretPosition) {
            ctx.setText(text, caretPosition);
        }

        @Override
        public void setText(String text, int caretPosition, boolean formatNewLines) {
            ctx.setText(text, caretPosition, formatNewLines);
        }

        @Override
        public String getReplacedText() {
            return ctx.getReplacedText();
        }
    }

    private static class DeletedTextContextWrapper extends ContextWrapper {

        private final DeletedTextInterceptor.Context ctx;

        public DeletedTextContextWrapper(DeletedTextInterceptor.Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public Document document() {
            return ctx.getDocument();
        }

        @Override
        public JTextComponent component() {
            return ctx.getComponent();
        }

        @Override
        public int getOffset() {
            return ctx.getOffset();
        }

        @Override
        public String getText() {
            return ctx.getText();
        }

        @Override
        public boolean isBackwardDelete() {
            return ctx.isBackwardDelete();
        }
    }

    private static class TypedBreakContextWrapper extends ContextWrapper {

        private final TypedBreakInterceptor.Context ctx;

        public TypedBreakContextWrapper(TypedBreakInterceptor.Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public Document document() {
            return ctx.getDocument();
        }

        @Override
        public JTextComponent component() {
            return ctx.getComponent();
        }

        @Override
        public int getOffset() {
            return ctx.getBreakInsertOffset();
        }

        @Override
        public String getText() {
            throw new UnsupportedOperationException();
        }
    }

    private static class MutableTypedBreakContextWrapper extends ContextWrapper {

        private final TypedBreakInterceptor.MutableContext ctx;

        public MutableTypedBreakContextWrapper(TypedBreakInterceptor.MutableContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public Document document() {
            return ctx.getDocument();
        }

        @Override
        public JTextComponent component() {
            return ctx.getComponent();
        }

        @Override
        public int getOffset() {
            return ctx.getBreakInsertOffset();
        }

        @Override
        public String getText() {
            throw new UnsupportedOperationException();
        }

        public void setText(String text, int breakInsertPosition, int caretPosition, int... reindentBlocks) {
            ctx.setText(text, breakInsertPosition, caretPosition, reindentBlocks);
        }

        @Override
        public boolean isMutable() {
            return true;
        }

        @Override
        public void setText(String text, int caretPosition) {
            setText(text, ctx.getBreakInsertOffset(), caretPosition);
        }

        @Override
        public void setText(String text, int caretPosition, boolean formatNewLines) {
            // XXX reindent blocks should be - ?
            setText(text, ctx.getBreakInsertOffset(), caretPosition);
        }

        public int getCaretOffset() {
            return ctx.getCaretOffset();
        }
    }
}
