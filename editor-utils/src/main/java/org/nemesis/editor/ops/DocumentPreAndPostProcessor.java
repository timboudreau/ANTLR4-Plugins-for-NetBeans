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

import javax.swing.text.BadLocationException;

/**
 * Item which processes a document somehow, and is called in a specific order,
 * prior to and after processing of the document.
 *
 * @author Tim Boudreau
 */
public interface DocumentPreAndPostProcessor {

    /**
     * Called after the before() methods of items added to the operator prior to
     * this one, and before those of ones called after - for locking documents,
     * turning off things that shouldn't listen to the document during a series
     * of changes, initiating undo transactions and so forth.
     *
     * @throws BadLocationException
     */
    default void before(DocumentOperationContext ctx) throws BadLocationException {
    }

    /**
     * Called when all before() operations have been run, in reverse order of
     * addition to the operation - for unlocking documents that were locked,
     * turning back on things that were turned off, finalizing undo transactions
     * and similar.
     *
     * @throws BadLocationException
     */
    default void after(DocumentOperationContext ctx) throws BadLocationException {
    }

    default <T, E extends Exception> DocumentProcessor<T, E> wrap(DocumentProcessor<T, E> toWrap) {
        return new DocumentProcessorAdapter<>(this, toWrap);
    }

    default DocumentPreAndPostProcessor then(DocumentPreAndPostProcessor next) {
        return new ChainedPreAndPostProcessor(this, next);
    }

    static final DocumentPreAndPostProcessor NO_OP = new DocumentPreAndPostProcessor(){};

}
