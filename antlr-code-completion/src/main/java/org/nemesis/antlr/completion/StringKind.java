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
package org.nemesis.antlr.completion;

/**
 * Kinds of text renderings of completion items which are passed to the
 * {@link Stringifier}, if your {@link CompletionBuilder} uses one.
 *
 * @author Tim Boudreau
 */
public enum StringKind {
    /**
     * Text used to sort the item when in alphabetic sort mode.
     */
    SORT_TEXT,
    /**
     * The display name used in code completion - your stringifier must not
     * return null for this.
     */
    DISPLAY_NAME,
    /**
     * If present, will render a string next to the display name of the item,
     * with the text dimmed a bit, separated by a space.
     */
    DISPLAY_DIFFERENTIATOR,
    /**
     * Text used for finding the longest common prefix when completing a
     * partially typed word.
     */
    INSERT_PREFIX,
    /**
     * Get the actual text that should be inserted in the document. This must
     * not return null, and should always return the <i>correct</i> text to
     * insert - if you need to delete tokens around the caret position,
     * use DeletionPolicy for that.
     */
    TEXT_TO_INSERT
}
