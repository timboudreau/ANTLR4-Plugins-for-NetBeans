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

package org.nemesis.editor.utils;

import javax.swing.text.BadLocationException;

/**
 *
 * @author Tim Boudreau
 */
final class ChainedPreAndPostProcessor implements DocumentPreAndPostProcessor {

    final DocumentPreAndPostProcessor a;
    final DocumentPreAndPostProcessor b;

    public ChainedPreAndPostProcessor(DocumentPreAndPostProcessor a, DocumentPreAndPostProcessor b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public void before(DocumentOperationContext ctx) throws BadLocationException {
        a.before(ctx);
        b.before(ctx);
    }

    @Override
    public void after(DocumentOperationContext ctx) throws BadLocationException {
        a.after(ctx);
        b.after(ctx);
    }

    @Override
    public String toString() {
        return a + ", " + b;
    }

}
