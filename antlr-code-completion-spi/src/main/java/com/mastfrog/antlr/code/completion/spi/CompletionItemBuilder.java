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

import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builder for one completion item.
 *
 * @author Tim Boudreau
 */
public interface CompletionItemBuilder<Ren, T> {

    /**
     * Finish this builder; the completion item to be inserted will be the
     * display text, which will be merged into the document using a heuristic
     * algorithm that takes into account the similarity when the completion
     * point is within the token (typically entirely replacing the token), and
     * prepends or appends whitespace to separate tokens, special-casing the
     * case that the next token is whitespace and ignoring it there.
     *
     * @return Whatever this builder builds
     */
    public T build();

    /**
     * Finish this builder, adding the item and returning whatever the type of T
     * is.
     *
     * @param applier An applier which can update the document
     * @return The return value
     */
    T build(CompletionApplier applier);

    /**
     * Set the score on this item to some value; items are scored relative to
     * all other items prodcued by the same completer; that score is then
     * normalized relative to all others to a value between 0 and 1, multiplied
     * by the originating completer's <code>scoreMultiplier()</code> and merged
     * with the results of all other completers with the score determining the
     * sort order.
     *
     * @param score A score, greater than zero
     * @return this
     */
    CompletionItemBuilder<Ren, T> withScore(float score);

    /**
     * Set this code completion item to use instant substitution, automatically
     * being inserted on invocation.
     *
     * @return this
     */
    CompletionItemBuilder<Ren, T> instantSubstitution();

    /**
     * Set the insert prefix for this item.
     *
     * @param insertPrefix The prefix
     * @return this
     */
    CompletionItemBuilder<Ren, T> withInsertPrefix(String insertPrefix);

    /**
     * Set a handler for keystrokes while code completion of this item is open.
     *
     * @param keys The key handler
     * @return this
     */
    CompletionItemBuilder<Ren, T> withKeyHandler(Consumer<KeyEvent> keys);

    /**
     * Set the sort priority of this item; if this is set to non-zero, the
     * default scoring algorithm will not be used, and this value takes over (a
     * normalized score of 1 is equal to a priority of 10000).
     *
     * @param prio The priority
     * @return this
     */
    CompletionItemBuilder<Ren, T> withPriority(int prio);

    /**
     * Provide custom rendering for this item, configuring the object passed to
     * the passed consumer for rendering. The type is implementation dependent;
     * antlr-code-completion uses TextCell.
     *
     * @param renderConfigurer A consumer that configures the renderer
     * @return this
     */
    CompletionItemBuilder<Ren, T> withRenderer(Consumer<Ren> renderConfigurer);

    /**
     * Add a tooltip for completion.
     *
     * @param tooltip The tooltip
     * @return this
     */
    CompletionItemBuilder<Ren, T> withTooltip(String tooltip);

    /**
     * Add a tooltip for completion, lazily resolving the value.
     *
     * @param tooltip A tooltip supplier
     * @return this
     */
    CompletionItemBuilder<Ren, T> withTooltip(Supplier<String> tooltip);
}
