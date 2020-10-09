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
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.live.language.AdhocErrorHighlighter;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.Utilities;
import org.netbeans.editor.ext.ExtKit;
import org.openide.awt.Mnemonics;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
public final class AdhocEditorPopupAction extends AbstractAction {

    private final ExtKit kit;

    public AdhocEditorPopupAction(ExtKit kit) {
        this.kit = kit;
        putValue(NAME, ExtKit.buildPopupMenuAction);
    }

    @NbBundle.Messages(value = {"copy=&Copy", "cut=Cu&t", "paste=&Paste", "importMenu=Import"})
    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent target = (JTextComponent) e.getSource();
        EditorUI ui = Utilities.getEditorUI(target);
        PopupPositionExposingPopupMenu menu = new PopupPositionExposingPopupMenu();
        try {
            menu.add(ImportIntoSampleAction.submenu(target));
            menu.add(new GoToOriginatingStateRegionInGrammarAction(target, menu, kit.getContentType()));
            menu.add(new CopySyntaxTreePathAction(target));
            menu.add(new CopyTokenSequenceAction(target));
            menu.add(new JSeparator());
            JMenuItem cutItem = new JMenuItem(kit.getActionByName(DefaultEditorKit.cutAction));
            Mnemonics.setLocalizedText(cutItem, Bundle.cut());
            menu.add(cutItem);
            JMenuItem copyItem = new JMenuItem(kit.getActionByName(DefaultEditorKit.copyAction));
            Mnemonics.setLocalizedText(copyItem, Bundle.copy());
            menu.add(copyItem);
            JMenuItem pasteItem = new JMenuItem(kit.getActionByName(DefaultEditorKit.pasteAction));
            Mnemonics.setLocalizedText(pasteItem, Bundle.paste());
            menu.add(pasteItem);
            menu.add(new JSeparator());
            menu.add(AdhocErrorHighlighter.toggleHighlightParserErrorsAction(false).getPopupPresenter());
            menu.add(AdhocErrorHighlighter.toggleHighlightLexerErrorsAction(false).getPopupPresenter());
            menu.add(AdhocErrorHighlighter.toggleHighlightAmbiguitiesAction(false).getPopupPresenter());
            menu.add(SetPredictionModeAction.createMenuItem());
            menu.add(new JSeparator());
            menu.add(createLazyGotoSubmenu(target));
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        ui.setPopupMenu(menu);
    }

    @NbBundle.Messages(value = {"goto=Go To", "waitFor=Please wait...", "# {0} - tokenName", "tokenDefinition=Token Definition for ''{0}''", "noNav=Could not find items to navigate to"})
    JMenuItem createLazyGotoSubmenu(JTextComponent target) {
        JMenu sub = new JMenu(Bundle._goto());
        JMenuItem waitItem = new JMenuItem(Bundle.waitFor());
        waitItem.setEnabled(false);
        sub.add(waitItem);
        GotoSubmenuLazyPopulatorListener pl = new GotoSubmenuLazyPopulatorListener(sub, waitItem, target, kit.getContentType());
        sub.addItemListener(pl);
        return sub;
    }
}
