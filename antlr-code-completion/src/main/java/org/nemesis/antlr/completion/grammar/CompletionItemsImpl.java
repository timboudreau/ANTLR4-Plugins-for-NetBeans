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

import com.mastfrog.antlr.code.completion.spi.CaretToken;
import com.mastfrog.antlr.code.completion.spi.CompletionApplier;
import com.mastfrog.antlr.code.completion.spi.CompletionItemBuilder;
import com.mastfrog.antlr.code.completion.spi.CompletionItems;
import com.mastfrog.function.throwing.ThrowingRunnable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.localizers.api.Localizers;
import org.nemesis.swing.cell.TextCell;
import org.openide.text.PositionBounds;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
class CompletionItemsImpl implements CompletionItems {

    private final CaretToken tokenInfo;
    private final StyledDocument doc;
    private final Set<SortableCompletionItem> items = new LinkedHashSet<>();
    private float minScore;
    private float maxScore;
    private final Map<Enum<?>, String> descriptors = new HashMap<>();
    private final Supplier<PositionRange> insertedTextToDelete;

    public CompletionItemsImpl(CaretToken tokenInfo, StyledDocument doc, Supplier<PositionRange> insertedTextToDelete) {
        this.tokenInfo = tokenInfo;
        this.doc = doc;
        this.insertedTextToDelete = insertedTextToDelete;
    }

    List<SortableCompletionItem> items(float multiplier, Set<String> seenNames) {
        // Scale the best to worst score on a curve so we get a normalized
        // value between 0 and 1
        float range = maxScore - minScore;
        List<SortableCompletionItem> result = new ArrayList<>(items.size());
        for (SortableCompletionItem ci : items) {
            String name = ci.insertionText().trim();

            if (!seenNames.contains(name)) {
                if (range > 0) {
                    float sc = ci.relativeScore() - minScore;
                    float normScore = (sc / range) * multiplier;
                    ci.score(normScore);
                }
                result.add(ci);
                seenNames.add(name);
            }
        }
        Collections.sort(result);
        return result;
    }

    int size() {
        return items.size();
    }

    @Override
    public CompletionItems add(String itemText, Enum<?> kind, float score) {
        String desc = descriptors.get(kind);
        if (desc == null) {
            desc = Localizers.displayName(kind);
            descriptors.put(kind, desc);
        }
        return add(itemText, desc, score);
    }

    SortableCompletionItem last() {
        return last;
    }

    void removeLast() {
        if (last != null) {
            items.remove(last);
            last = null;
        }
    }

    SortableCompletionItem last;
    void add(SortableCompletionItem item, float score) {
        if (item.insertionText().equals(currentPrefixText)) {
            return;
        }
        last = item;
        minScore = Math.min(score, minScore);
        maxScore = Math.min(score, maxScore);
        items.add(item);
    }

    @Override
    public CompletionItems add(String itemText, String description, float score) {
        add(new GCI(itemText, tokenInfo, doc, description, false, score,
                wrap(new DefaultCompletionApplier(tokenInfo, itemText))), score);
        return this;
    }

    private String currentPrefixText;
    public void withPrefixText(String prefixText, ThrowingRunnable r) throws Exception {
        currentPrefixText = prefixText;
        try {
            r.run();
        } finally {
            currentPrefixText = null;
        }
    }

    @Override
    public CompletionItems add(String itemText, String description, float score, CompletionApplier applier) {
        add(new GCI(itemText, tokenInfo, doc, description, false, score, wrap(applier)), score);
        return this;
    }

    CompletionApplier defaultApplier(String toInsert) {
        return new WrappedApplier(insertedTextToDelete, new DefaultCompletionApplier(tokenInfo, toInsert));
    }

    private CompletionApplier wrap(CompletionApplier orig) {
        if (orig instanceof WrappedApplier) {
            return orig;
        }
        return new WrappedApplier(insertedTextToDelete, orig);
    }

    @Override
    public CompletionItemBuilder<? extends TextCell, ? extends CompletionItems> add(String displayText) {
        return new CompletionItemBuilderImpl<CompletionItems>(displayText, item -> {
            if (item instanceof CompletionItemImpl) {
                ((CompletionItemImpl) item).ensureApplier(this::defaultApplier);
                ((CompletionItemImpl) item).wrapApplier(this::wrap);
            }
            if (item != null) {
                add(item, item.relativeScore());
            }
            return this;
        });
    }

    /**
     * Ensures the text the user inserted while completions were being updated
     * gets deleted as part of the update.
     */
    static final class WrappedApplier implements CompletionApplier {

        private final Supplier<PositionRange> alsoDelete;
        private final CompletionApplier delegate;

        public WrappedApplier(Supplier<PositionRange> alsoDelete, CompletionApplier delegate) {
            this.alsoDelete = alsoDelete;
            this.delegate = delegate;
        }

        @Override
        public void accept(JTextComponent comp, StyledDocument doc) throws BadLocationException {
            PositionRange also = alsoDelete.get();
            if (also != null) {
                try {
                    PositionBounds pb = PositionFactory.toPositionBounds(also);
                    pb.setText("");
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
            delegate.accept(comp, doc);
        }
    }
}
