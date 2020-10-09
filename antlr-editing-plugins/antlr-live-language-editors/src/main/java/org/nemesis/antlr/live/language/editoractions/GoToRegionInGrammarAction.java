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
import java.io.IOException;
import javax.swing.AbstractAction;
import javax.swing.text.BadLocationException;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.util.EditorSelectionUtils;
import org.nemesis.localizers.api.Localizers;
import org.openide.text.PositionBounds;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {
    "# {0} - definitionOfWhat", "# {1} - ruleType",
    "# {2} - fileName", "gotoDefinitionOf=Go To ''{0}'' ({1}) in {2}",
    "# {0} - whatFailed", "failed=Failed: {0}"})
public final class GoToRegionInGrammarAction extends AbstractAction {

    private final PositionBounds bounds;

    public GoToRegionInGrammarAction(String ruleName, RuleTypes type, String sourceName, PositionBounds bounds) throws IOException {
        String typeName = Localizers.displayName(type);
        putValue(NAME, Bundle.gotoDefinitionOf(ruleName, typeName, sourceName));
        this.bounds = bounds;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            EditorSelectionUtils.openAndSelectRange(bounds.getBegin().getCloneableEditorSupport().getDocument(),
                    PositionFactory.toPositionRange(bounds));
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
