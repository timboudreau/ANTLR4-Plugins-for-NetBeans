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
package org.nemesis.antlr.file.antlrrefactoring;

import java.awt.Component;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.spi.ui.CustomRefactoringPanel;
import org.netbeans.modules.refactoring.spi.ui.RefactoringUI;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
@Messages({"uiInlineRule=Inline Rule",
    "descInlineRule=Remove the rule and replace all usages of it with its contents"})
public final class InlineRefactoringUI implements RefactoringUI {

    private final AbstractRefactoring inlineRefactoring;

    public InlineRefactoringUI(AbstractRefactoring inlineRefactoring) {
        this.inlineRefactoring = inlineRefactoring;
    }

    @Override
    public String getName() {
        return Bundle.uiInlineRule();
    }

    @Override
    public String getDescription() {
        return Bundle.descInlineRule();
    }

    @Override
    public boolean isQuery() {
        return false;
    }

    @Override
    public CustomRefactoringPanel getPanel(ChangeListener cl) {
        // The panel will never be shown, but if we don't return a useless
        // panel, the user will never be shown any problems or warnings -
        // the UI will go straight to the refactoring preview.  Silly.
        return new CRP(cl);
//        return null;
    }

    @Override
    public Problem setParameters() {
//        return inlineRefactoring.checkParameters();
        return null;
    }

    @Override
    public Problem checkParameters() {
//        return inlineRefactoring.checkParameters();
        return null;
    }

    @Override
    public boolean hasParameters() {
        return false;
    }

    @Override
    public AbstractRefactoring getRefactoring() {
        return inlineRefactoring;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    class CRP implements CustomRefactoringPanel {

        private final ChangeListener cl;

        CRP(ChangeListener cl) {
            this.cl = cl;
        }

        @Override
        public void initialize() {
        }

        @Override
        public Component getComponent() {
            return new JPanel();
        }
    }
}
