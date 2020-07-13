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
package com.mastfrog.antlr.code.completion.spi;

import org.nemesis.swing.cell.TextCell;

/**
 * Collection of completion items which can be added to; each item can have a
 * <i>score</i> which allows it to be sorted/weighted relative to other items
 * returned by one completer; the values for all completers active for a
 * completion are normalized relative to the minimum and maximum scores seen,
 * multiplied by the originating Completer's <code>scoreMultiplier()</code>, and
 * then merged.
 *
 * @author Tim Boudreau
 */
public interface CompletionItems {

    /**
     * Add an item with its description derived from its kind (as in the
     * <code>kind()</code> from a NamedSemanticRegion representing it); if that
     * enum constant has the &#064;Localize annotation, the object localization
     * infrastructure will use that; otherwise the value of its
     * <code>toString()</code> method will be used.
     *
     * @param itemText The item text
     * @param kind The kind of the item
     * @return this
     */
    default CompletionItems add(String itemText, Enum<?> kind) {
        return add(itemText, kind, 0);
    }

    CompletionItems add(String itemText, Enum<?> kind, float score);

    /**
     * Add an item with its description, where the item text will be used as the
     * insertion or substitution, using a number of built-in heuristics that
     * will usually do the Right Thing&trade;.
     *
     * @param itemText The text of the item
     * @param description A description of the item
     * @return this
     */
    default CompletionItems add(String itemText, String description) {
        return add(itemText, description, 0);
    }

    CompletionItems add(String itemText, String description, float score);

    /**
     * Add an item with custom code to modify the document, where the item text
     * is simply the displayed text.
     *
     * @param itemText The item text
     * @param description The description displayed next to it
     * @param applier An applier which will modify the document
     * @return this
     */
    default CompletionItems add(String itemText, String description, CompletionApplier applier) {
        return add(itemText, description, 0, applier);
    }

    CompletionItems add(String itemText, String description, float score, CompletionApplier applier);

    /**
     * Add a completion item, with full control over its rendering, behavior and
     * insertion text.
     *
     * @param displayText The display text to be used (unless you provide a
     * renderer - but this text is also used for sorting).
     * @return A builder which, when built, will return you to this
     * CompletionItems
     */
    CompletionItemBuilder<? extends TextCell, ? extends CompletionItems> add(String displayText);
}
