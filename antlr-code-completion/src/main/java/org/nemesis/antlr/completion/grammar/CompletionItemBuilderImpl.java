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
package org.nemesis.antlr.completion.grammar;

import com.mastfrog.antlr.code.completion.spi.CompletionApplier;
import com.mastfrog.antlr.code.completion.spi.CompletionItemBuilder;
import com.mastfrog.swing.cell.TextCell;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
final class CompletionItemBuilderImpl<T> implements CompletionItemBuilder<TextCell, T> {

    private Consumer<TextCell> renderConfigurer;
    private Consumer<KeyEvent> keyHandler;
    private Supplier<String> tooltip;
    private int sortPriority;
    private final String displayText;
    private CharSequence insertPrefix;
    private boolean instantSubstitution;
    private final Function<SortableCompletionItem, T> onBuild;
    private float score;

    CompletionItemBuilderImpl(String displayText, Function<SortableCompletionItem, T> onBuild) {
        this.displayText = displayText;
        this.onBuild = onBuild;
    }

    @Override
    public T build(CompletionApplier applier) {
        CompletionItemImpl result = new CompletionItemImpl(applier, renderConfigurer, keyHandler,
                tooltip, sortPriority, displayText, insertPrefix, instantSubstitution, score);
        return onBuild.apply(result);
    }

    @Override
    public T build() {
        return build(null);
    }

    @Override
    public CompletionItemBuilderImpl withScore(float score) {
        this.score = score;
        return this;
    }

    @Override
    public CompletionItemBuilderImpl withRenderer(Consumer<TextCell> renderConfigurer) {
        this.renderConfigurer = renderConfigurer;
        return this;
    }

    @Override
    public CompletionItemBuilderImpl withInsertPrefix(String insertPrefix) {
        this.insertPrefix = insertPrefix;
        return this;
    }

    @Override
    public CompletionItemBuilderImpl instantSubstitution() {
        this.instantSubstitution = true;
        return this;
    }

    @Override
    public CompletionItemBuilderImpl withKeyHandler(Consumer<KeyEvent> keys) {
        keyHandler = keys;
        return this;
    }

    @Override
    public CompletionItemBuilderImpl withPriority(int prio) {
        this.sortPriority = prio;
        return this;
    }

    @Override
    public CompletionItemBuilderImpl withTooltip(String tooltip) {
        this.tooltip = () -> tooltip;
        return this;
    }

    @Override
    public CompletionItemBuilderImpl withTooltip(Supplier<String> tooltip) {
        this.tooltip = tooltip;
        return this;
    }
}
