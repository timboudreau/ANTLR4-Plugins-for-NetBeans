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

import com.mastfrog.function.throwing.ThrowingBiConsumer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import static org.nemesis.antlr.completion.grammar.GCI.SCORE_FACTOR;
import org.nemesis.swing.cell.TextCell;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class CompletionItemImpl implements SortableCompletionItem {

    private ThrowingBiConsumer<JTextComponent, StyledDocument> applier;
    private final Consumer<TextCell> renderConfigurer;
    private final Consumer<KeyEvent> keyHandler;
    private final Supplier<String> tooltip;
    private int sortPriority;
    private final String displayText;
    private final CharSequence insertPrefix;
    private boolean instantSubstitution;
    private final float relativeScore;

    public CompletionItemImpl(ThrowingBiConsumer<JTextComponent, StyledDocument> applier,
            Consumer<TextCell> renderConfigurer, Consumer<KeyEvent> keyHandler, Supplier<String> tooltip,
            int sortPriority, String displayText, CharSequence insertPrefix,
            boolean instantSubstitution, float relativeScore) {
        this.applier = applier;
        this.renderConfigurer = renderConfigurer == null ? this::defaultConfigure : renderConfigurer;
        this.keyHandler = keyHandler;
        this.tooltip = tooltip;
        this.sortPriority = sortPriority;
        this.displayText = displayText;
        this.insertPrefix = insertPrefix;
        this.instantSubstitution = instantSubstitution;
        this.relativeScore = relativeScore;
    }

    void ensureApplier(Function<String, ThrowingBiConsumer<JTextComponent, StyledDocument>> applierFactory) {
        if (applier == null) {
            applier = applierFactory.apply(displayText);
        }
    }

    @Override
    public float relativeScore() {
        return relativeScore;
    }

    @Override
    public void score(float normalizedScore) {
        if (sortPriority == 0) {
            sortPriority = (int) (SCORE_FACTOR * normalizedScore);
        }
    }

    @Override
    public boolean isInstant() {
        return instantSubstitution;
    }

    @Override
    public void setInstant() {
        instantSubstitution = true;
    }

    private void defaultConfigure(TextCell cell) {
        cell.withText(displayText);
    }

    @Override
    public void defaultAction(JTextComponent jtc) {
        try {
            applier.accept(jtc, (StyledDocument) jtc.getDocument());
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public void processKeyEvent(KeyEvent ke) {
        if (keyHandler != null) {
            keyHandler.accept(ke);
        }
    }

    @Override
    public int getPreferredWidth(Graphics grphcs, Font font) {
        CellItemRenderer ren = CellItemRenderer.borrow(renderConfigurer);
        return ren.getPreferredWidth(grphcs, font);
    }

    @Override
    public void render(Graphics grphcs, Font font, Color color, Color color1, int i, int i1, boolean bln) {
        CellItemRenderer ren = CellItemRenderer.borrow(renderConfigurer);
        ren.render(grphcs, font, color, color1, i1, i1, bln);
    }

    @Override
    public CompletionTask createDocumentationTask() {
        return null;
    }

    @Override
    public CompletionTask createToolTipTask() {
        if (tooltip != null) {
            return new ToolTipCompletionTask(tooltip);
        }
        return null;
    }

    @Override
    public boolean instantSubstitution(JTextComponent jtc) {
        return instantSubstitution;
    }

    @Override
    public int getSortPriority() {
        return sortPriority;
    }

    @Override
    public CharSequence getSortText() {
        return displayText;
    }

    @Override
    public CharSequence getInsertPrefix() {
        return insertPrefix;
    }

    static class ToolTipCompletionTask implements CompletionTask {

        private final Supplier<String> tip;

        public ToolTipCompletionTask(Supplier<String> tip) {
            this.tip = tip;
        }

        @Override
        public void query(CompletionResultSet crs) {
            crs.addItem(new ToolTipItem(tip.get()));
        }

        @Override
        public void refresh(CompletionResultSet crs) {
            query(crs);
        }

        @Override
        public void cancel() {
            // do nothing
        }

        static class ToolTipItem implements CompletionItem {

            private final String text;

            public ToolTipItem(String text) {
                this.text = text;
            }

            @Override
            public void defaultAction(JTextComponent jtc) {
                // do nothing
            }

            @Override
            public void processKeyEvent(KeyEvent ke) {
                // do nothing
            }

            @Override
            public int getPreferredWidth(Graphics grphcs, Font font) {
                CellItemRenderer ren = CellItemRenderer.borrow(cell -> {
                    cell.withFont(font).withText(text);
                });
                return ren.getPreferredWidth(grphcs, font);
            }

            @Override
            public void render(Graphics grphcs, Font font, Color color, Color color1, int i, int i1, boolean bln) {
                CellItemRenderer ren = CellItemRenderer.borrow(cell -> {
                    cell.withBackground(color1).withForeground(color).withFont(font).withText(text);
                });;
                ren.render(grphcs, font, color, color, i1, i1, bln);
            }

            @Override
            public CompletionTask createDocumentationTask() {
                return null;
            }

            @Override
            public CompletionTask createToolTipTask() {
                return null;
            }

            @Override
            public boolean instantSubstitution(JTextComponent jtc) {
                return false;
            }

            @Override
            public int getSortPriority() {
                return 0;
            }

            @Override
            public CharSequence getSortText() {
                return "";
            }

            @Override
            public CharSequence getInsertPrefix() {
                return "";
            }
        }
    }
}
