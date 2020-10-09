/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.live.language.editoractions;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.nemesis.antlr.live.language.AdhocErrorHighlighter;
import org.nemesis.antlr.live.parsing.EmbeddedParserFeatures;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
@Messages({
    "setPredictionMode=Set the Prediction Mode",
    "LL=Use Parser Context (LL prediction mode - the Antlr default)",
    "SLL=Ignore Parser Context (SLL - fast, sometimes wrong)",
    "LL_EXACT_AMBIG_DETECTION=Exact Ambiguity Detection (slower but provides correct details)"
})
public final class SetPredictionModeAction extends AbstractAction {

    private final PredictionMode mode;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public SetPredictionModeAction(PredictionMode mode) {
        this.mode = mode;
        putValue(NAME, NbBundle.getMessage(SetPredictionModeAction.class, mode.name()));
        putValue(SELECTED_KEY, "_sel");
        putValue("_sel", mode == EmbeddedParserFeatures.getInstance(null).currentPredictionMode());
    }

    boolean isSelected() {
        return mode == EmbeddedParserFeatures.getInstance(null).currentPredictionMode();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (mode != PredictionMode.LL_EXACT_AMBIG_DETECTION) {
            AdhocErrorHighlighter.highlightAmbiguities(false);
        }
        EmbeddedParserFeatures.getInstance(null).setPredictionMode(mode);
    }

    public static JMenuItem createMenuItem() {
        JMenu sub = new JMenu(Bundle.setPredictionMode());
        for (PredictionMode p : PredictionMode.values()) {
            SetPredictionModeAction a = new SetPredictionModeAction(p);
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(a);
            item.setSelected(a.isSelected());
            sub.add(item);
        }
        return sub;
    }

    public JMenuItem[] createMenuItems() {
        PredictionMode[] modes = PredictionMode.values();
        JCheckBoxMenuItem[] result = new JCheckBoxMenuItem[modes.length];
        for (int i = 0; i < result.length; i++) {
            PredictionMode p = modes[i];
            SetPredictionModeAction a = new SetPredictionModeAction(p);
            result[i] = new JCheckBoxMenuItem(a);
            result[i].setSelected(a.isSelected());
        }
        return result;
    }
}
