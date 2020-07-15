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

package org.nemesis.editor.ops;

import java.util.logging.Level;
import javax.swing.text.BadLocationException;

/**
 *
 * @author Tim Boudreau
 */
final class DocumentProcessorAdapter<T, E extends Exception> implements DocumentProcessor<T, E> {

    private final DocumentPreAndPostProcessor be;
    private final DocumentProcessor<T, E> delegate;

    public DocumentProcessorAdapter(DocumentPreAndPostProcessor be, DocumentProcessor<T, E> delegate) {
        this.be = be;
        this.delegate = delegate;
    }

    @Override
    public T get(DocumentOperationContext ctx) throws E, BadLocationException {
        DocumentOperator.LOG.log(Level.FINEST, "Run {0} wrapped by {1}", new Object[]{delegate, be});
        be.before(ctx);
        try {
            return delegate.get(ctx);
        } finally {
            be.after(ctx);
        }
    }

    @Override
    public String toString() {
        return be + " -> " + delegate;
    }

}
