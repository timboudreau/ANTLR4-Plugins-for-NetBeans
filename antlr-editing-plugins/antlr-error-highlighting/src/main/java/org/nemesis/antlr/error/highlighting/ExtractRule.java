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

package org.nemesis.antlr.error.highlighting;

import com.mastfrog.util.strings.Strings;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.spi.language.fix.DocumentEditBag;
import org.nemesis.antlr.spi.language.fix.FixImplementation;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.utils.DocumentOperator;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.text.PositionBounds;

/**
 *
 * @author Tim Boudreau
 */
final class ExtractRule implements FixImplementation, Runnable, DocumentListener, ActionListener {

    private final List<PositionBoundsRange> ranges;
    private final Extraction ext;
    private final PositionBoundsRange preferred;
    private final String text;
    private final Timer timer;
    private PositionBounds toSelect;
    private BaseDocument doc;
    private final String newRuleName;

    ExtractRule(List<PositionBoundsRange> ranges, Extraction ext, PositionBoundsRange preferred, String text) {
        this(ranges, ext, preferred, text, null);
    }

    ExtractRule(List<PositionBoundsRange> ranges, Extraction ext, PositionBoundsRange preferred, String text, String newRuleName) {
        this.ranges = ranges;
        this.ext = ext;
        this.preferred = preferred;
        this.text = text;
        timer = new Timer(75, this);
        timer.setRepeats(false);
        this.newRuleName = newRuleName;
    }

    private String newRuleText() {
        return Strings.escape(text, (ch) -> {
            switch (ch) {
                case '\n':
                case '\t':
                case 0:
                    return "";
                case ' ':
                    return "";
            }
            return Strings.singleChar(ch);
        }).replaceAll("\\s+", " ");
    }

    private String newRuleName() {
        // XXX scan the references inside the bounds, and figure out if any
        // are parser rules, if so, lower case, if not upper
        return newRuleName == null ?  "new_rule" : newRuleName;
    }

    private int newRuleInsertPosition() {
        // XXX get the current caret position and find the rule nearest
        NamedSemanticRegions<RuleTypes> rules = ext.namedRegions(AntlrKeys.RULE_BOUNDS);
        NamedSemanticRegion<RuleTypes> region = rules.at(preferred.original().start());
        if (region != null) {
            return region.end() + 1;
        }
        for (PositionBoundsRange pb : ranges) {
            region = rules.at(pb.start());
            if (region != null) {
                return region.end() + 1;
            }
        }
        return ext.source().lookup(Document.class).get().getLength() - 1;
    }

    @Override
    public void implement(BaseDocument document, Optional<FileObject> file, Extraction extraction, DocumentEditBag edits) throws Exception {
        doc = document;
        String name = newRuleName();
        int pos = newRuleInsertPosition();
        String newText = newRuleText();
        toSelect = PositionBoundsRange.createBounds(extraction.source(), pos, pos + name.length());
        DocumentOperator.builder().disableTokenHierarchyUpdates().readLock().writeLock().singleUndoTransaction().blockIntermediateRepaints().acquireAWTTreeLock().build().operateOn((StyledDocument) document).operate(() -> {
            for (PositionBoundsRange bds : ranges) {
                edits.replace(document, bds.start(), bds.end(), name);
            }
            int newTextStart = toSelect.getBegin().getOffset();
            document.addPostModificationDocumentListener(this);
            edits.insert(document, newTextStart, '\n' + name + " : " + newText + (newText.endsWith(";") ? "\n": ";\n"));
            return null;
        });
    }

    @Override
    public void run() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        doc.removePostModificationDocumentListener(this);
        EventQueue.invokeLater(this);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        doc.removePostModificationDocumentListener(this);
        EventQueue.invokeLater(this);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        doc.removePostModificationDocumentListener(this);
        EventQueue.invokeLater(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent comp = EditorRegistry.findComponent(doc);
        if (comp != null && toSelect != null) {
            int caretDest = toSelect.getBegin().getOffset() + 1;
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
