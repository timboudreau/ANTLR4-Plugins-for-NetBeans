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

import org.nemesis.antlr.refactoring.GenericRefactoringContextAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.Position;
import static org.nemesis.antlr.ANTLRv4Lexer.FRAGDEC_ID;
import static org.nemesis.antlr.ANTLRv4Lexer.ID;
import static org.nemesis.antlr.ANTLRv4Lexer.PARSER_RULE_ID;
import static org.nemesis.antlr.ANTLRv4Lexer.TOKEN_ID;
import org.nemesis.antlr.file.AntlrHierarchy;
import org.nemesis.antlr.file.AntlrToken;
import org.nemesis.antlr.file.antlrrefactoring.InlineRuleRefactoring.InlineRule;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.refactoring.spi.ui.UI;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionState;
import org.openide.loaders.DataObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.PositionBounds;
import org.openide.text.PositionRef;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "Refactoring",
        id = "org.nemesis.antlr.file.antlrrefactoring.InlineRuleAction"
)
@ActionRegistration(
        displayName = "#CTL_InlineRuleAction",
        enabledOn = @ActionState(useActionInstance = true)
)
@ActionReferences({
    @ActionReference(path = "Shortcuts", name = "OS-N"),
    @ActionReference(path = "Editors/text/x-g4/RefactoringActions", name = "InlineAction", position = 421),
    @ActionReference(path = "Editors/text/x-g4/Popup", name = "InlineAction", position = 421)
})
@Messages("CTL_InlineRuleAction=Inline Rule")
public final class InlineRuleAction extends GenericRefactoringContextAction<AntlrToken> {

    private static final Logger LOG = Logger.getLogger(InlineRuleAction.class.getName());

    public InlineRuleAction() {
        super(AntlrHierarchy::antlrLanguage);
        putValue(NAME, Bundle.CTL_InlineRuleAction());
    }

    @Override
    protected boolean isEnabled(CloneableEditorSupport doc, Caret caret, TokenSequence<AntlrToken> seq, int caretPosition, Token<AntlrToken> tok) {
        switch (tok.id().ordinal()) {
            case PARSER_RULE_ID:
            case TOKEN_ID:
            case FRAGDEC_ID:
            case ID:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void perform(CloneableEditorSupport context, Caret caret, TokenSequence<AntlrToken> seq, int caretPosition, Token<AntlrToken> caretToken) {
        Document doc = context.getDocument();
        int dot = caret.getDot();
        int mark = caret.getMark();
        PositionRef start = context.createPositionRef(Math.min(dot, mark), Position.Bias.Forward);
        PositionRef end = context.createPositionRef(Math.max(dot, mark), Position.Bias.Backward);
        PositionBounds bds = new PositionBounds(start, end);
        DataObject dob = NbEditorUtilities.getDataObject(doc);
        LOG.log(Level.FINER, "Apply inline refactoring on {0}:{1} in {2}",
                new Object[]{bds.getBegin(), bds.getEnd(), dob});
        InlineRule inlineRule = new InlineRule(dob.getLookup(), dob.getPrimaryFile(), context, bds);
        InlineRefactoringUI ui = new InlineRefactoringUI(inlineRule);
        UI.openRefactoringUI(ui, NbEditorUtilities.getOuterTopComponent(EditorRegistry.findComponent(doc)));
    }

//    @interface CustomRefactoringRegistration {
//        int[] enabledOnTokens();
//        String name();
//        Class<? extends AbstractRefactoring> refactoring() default AbstractRefactoring.class;
//        Class<? extends RefactoringPlugin> plugin();
//    }
}
