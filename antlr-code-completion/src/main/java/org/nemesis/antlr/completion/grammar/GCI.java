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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import com.mastfrog.antlr.code.completion.spi.CaretToken;
import com.mastfrog.antlr.code.completion.spi.CompletionApplier;
import java.util.Objects;
import javax.swing.text.StyledDocument;
import org.nemesis.swing.html.HtmlRenderer;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.spi.editor.completion.CompletionTask;

/**
 *
 * @author Tim Boudreau
 */
final class GCI implements SortableCompletionItem {

    public static final float SCORE_FACTOR = 10000;
    final String name;
    final CaretToken tokenInfo;
    final Document doc;
    final String desc;
    boolean onlyItem;
    private final float relativeScore;
    CompletionApplier applier;
    int sortPriority;

    GCI(String name, CaretToken tokenInfo, Document doc, String desc) {
        this(name, tokenInfo, doc, 0, desc);
    }

    GCI(String name, CaretToken tokenInfo, Document doc, float relativeScore, String desc) {
        this(name, tokenInfo, doc, desc, false, relativeScore, null);
    }

    GCI(String name, CaretToken tokenInfo, Document doc, float relativeScore, String desc, boolean onlyItem) {
        this(name, tokenInfo, doc, desc, onlyItem, relativeScore, null);
    }

    GCI(String name, CaretToken tokenInfo, Document doc, String desc, boolean onlyItem, float relativeScore, CompletionApplier applier) {
        this.name = name;
        this.tokenInfo = tokenInfo;
        this.doc = doc;
        this.desc = desc;
        this.onlyItem = onlyItem;
        this.relativeScore = relativeScore;
        this.applier = applier == null ? new DefaultCompletionApplier(tokenInfo, name) : applier;
    }

    @Override
    public String insertionText() {
        return name;
    }

    @Override
    public float relativeScore() {
        return relativeScore;
    }

    @Override
    public void score(float score) {
        sortPriority = (int) (SCORE_FACTOR * score);
    }

    String itemText() {
        return name;
    }

    public String toString() {
        return "GCI(" + name + ")";
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GCI other = (GCI) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public void defaultAction(JTextComponent component) {
        try {
            applier.accept(component, (StyledDocument) component.getDocument());
        } catch (BadLocationException ex) {
            org.openide.util.Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public boolean matchesPrefix(String text) {
        return name.startsWith(text);
    }

    private boolean dismissed;
    @Override
    public void processKeyEvent(KeyEvent evt) {
        if (dismissed) {
            return;
        }
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_LEFT:
                dismissed = true;
                Completion.get().hideAll();
        }
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
    public boolean instantSubstitution(JTextComponent component) {
        if (onlyItem) {
            defaultAction(component);
            return true;
        }
        return false;
    }

    @Override
    public int getSortPriority() {
        return sortPriority;
    }

    @Override
    public CharSequence getSortText() {
        return name.toLowerCase();
    }

    @Override
    public CharSequence getInsertPrefix() {
        return "";
    }

    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        double width = HtmlRenderer.renderString(name + " (" + desc + ")", g, 5, 0,
                2000, 200, defaultFont, Color.BLACK, HtmlRenderer.STYLE_CLIP, false) + 10;
        return (int) Math.ceil(width);
    }

    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor, Color backgroundColor, int width, int height, boolean selected) {
        Color color = g.getColor();
        int baseline = g.getFontMetrics(defaultFont).getMaxAscent();
        String renderText = desc == null ? name : name + " <i><font color='#888899'>(" + desc + ")";
        HtmlRenderer.renderHTML(renderText, g, 5, baseline, width, height, defaultFont, color,
                HtmlRenderer.STYLE_TRUNCATE, true);
    }

    @Override
    public void setInstant() {
        this.onlyItem = true;
    }

    @Override
    public boolean isInstant() {
        return onlyItem;
    }
}
