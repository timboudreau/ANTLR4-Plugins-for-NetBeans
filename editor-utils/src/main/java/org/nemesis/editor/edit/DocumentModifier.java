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
package org.nemesis.editor.edit;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * A modifier of documents, for either not editing, or performing more
 * complex edits than single inserts and so forth; use with EditBag.modify()
 * if for some reason it is impractical to simply add some inserts,
 * replacements and deletions to the bag.
 */
public interface DocumentModifier {

    /**
     * Callback which can applyToDocument the document in some way, or
     * otherwise interact with it.
     *
     * @param start The current value of the start position passed when it
     * was added
     * @param optionalEnd The end point
     * @param doc A document
     * @throws BadLocationException if something goes wrong
     */
    public void applyToDocument(int start, int optionalEnd, Document doc) throws BadLocationException;

    /**
     * For unit tests, it can be useful to apply the operation to a document
     * and to a StringBuilder and compare, so this method is included to
     * make <code>EditBag.applyToStringBuilder()</code> useful with ad-hoc
     * modifiers.
     *
     * @param start The start
     * @param optionalEnd The end if one was supplied, or -1
     * @param target A string builder to applyToDocument
     */
    default void applyToStringBuilder(int start, int optionalEnd, StringBuilder target) {
        throw new UnsupportedOperationException("Not implemented");
    }

}
