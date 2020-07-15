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
package org.nemesis.antlr.error.highlighting.hints.util;

import com.mastfrog.function.IntBiConsumer;
import com.mastfrog.util.strings.Strings;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.Action;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.edit.EditBag;
import org.nemesis.editor.function.DocumentConsumer;
import org.nemesis.editor.ops.CaretInformation;
import org.nemesis.editor.ops.CaretPositionCalculator;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.lib.editor.util.swing.DocumentListenerPriority;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public final class ExtractRule implements Runnable, DocumentListener, ActionListener, DocumentConsumer<EditBag>, CaretPositionCalculator {

    private final List<PositionRange> ranges;
    private final Extraction ext;
    private final PositionRange preferred;
    private final int startPositionInExtration;
    private final String text;
    private final Timer timer;
    private PositionRange toSelect;
    private Document doc;
    private final String newRuleName;
    private final PositionFactory positions;
    private final RuleTypes type;

    public ExtractRule(List<PositionRange> ranges, Extraction ext, PositionRange preferred,
            int startPositionInExtration, String text, String newRuleName,
            PositionFactory positions, RuleTypes type) {
        this.ranges = ranges;
        this.startPositionInExtration = startPositionInExtration;
        this.ext = ext;
        this.preferred = preferred;
        this.text = text.trim();
        timer = new Timer(75, this);
        timer.setRepeats(false);
        this.newRuleName = newRuleName;
        this.positions = positions;
        this.type = type;
    }

    private String newRuleText() {
        String result = Strings.escape(text, (ch) -> {
            switch (ch) {
                case '\n':
                case '\t':
                case 0:
                    return "";
                case ' ':
                    return " ";
            }
            return Strings.singleChar(ch);
        }).replaceAll("\\s+", " ");
        return result;
    }

    private String newRuleName() {
        return newRuleName == null ? "new_rule" : newRuleName;
    }

    private int newRuleInsertPosition() {
        // XXX get the current caret position and find the rule nearest
        NamedSemanticRegions<RuleTypes> rules = ext.namedRegions(AntlrKeys.RULE_BOUNDS);
        NamedSemanticRegion<RuleTypes> region = rules.at(startPositionInExtration);
        if (region != null) {
            return region.end() + 1;
        }
        for (PositionRange pb : ranges) {
            region = rules.at(pb.start());
            if (region != null) {
                return region.end() + 1;
            }
        }
        return ext.source().lookup(Document.class).get().getLength() - 1;
    }

    @Override
    public Consumer<IntBiConsumer> createPostEditPositionFinder(CaretInformation caret, JTextComponent comp, Document doc) {
        return (ibc -> {
            int caretDest = toSelect.start() + 1;
//            comp.getCaret().setDot(caretDest);
            ibc.accept(caretDest, caretDest);
            timer.start();
        });
    }

    @Override
    public void accept(EditBag bag) throws BadLocationException {
        int pos = newRuleInsertPosition();
        String name = newRuleName();
        toSelect = positions.range(pos, Position.Bias.Backward, pos + name.length(), Position.Bias.Forward);
        doc = ext.source().lookup(Document.class).get();
        String newText = newRuleText();
        for (PositionRange rang : ranges) {
            bag.replace(rang, name);
        }
        bag.insert(toSelect.startPosition(), '\n' + (type == RuleTypes.FRAGMENT ? "fragment " : "")
                + name + " : " + newText + (newText.endsWith(";") ? "\n" : ";\n"));
    }

    @Override
    public void run() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        DocumentUtilities.removePriorityDocumentListener(doc, this, DocumentListenerPriority.AFTER_CARET_UPDATE);
        EventQueue.invokeLater(this);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        DocumentUtilities.removePriorityDocumentListener(doc, this, DocumentListenerPriority.AFTER_CARET_UPDATE);
        EventQueue.invokeLater(this);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        DocumentUtilities.removePriorityDocumentListener(doc, this, DocumentListenerPriority.AFTER_CARET_UPDATE);
        EventQueue.invokeLater(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent comp = EditorRegistry.findComponent(doc);
        if (comp != null && toSelect != null) {
            int caretDest = toSelect.start() + 1;
            comp.getCaret().setDot(caretDest);
            String mimeType = NbEditorUtilities.getMimeType(doc);
            if (mimeType != null) {
                Action action = FileUtil.getConfigObject("Editors/" + mimeType + "/Actions/in-place-refactoring.instance", Action.class);
                if (action != null) {
                    ActionEvent ae = new ActionEvent(comp, ActionEvent.ACTION_PERFORMED, "in-place-refactoring");
                    action.actionPerformed(ae);
                }
            }
        }
    }
}
