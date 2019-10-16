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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.actions;

import java.awt.event.ActionEvent;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.editor.ext.ExtKit.ToggleCommentAction;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
@Messages("antlr-toggle-comment=Toggle Comment")
@EditorActionRegistration(
    name = "antlr-toggle-comment",
    weight=Integer.MAX_VALUE,
    category = "Editing",
    popupText = "Comment/Uncomment",
    menuPath = "Source",
    menuText = "Comment/Uncomment",
    mimeType = "text/g-4")
public class AntlrToggleCommentAction extends ToggleCommentAction {
    public AntlrToggleCommentAction() {
        super("//");
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        System.out.println("toggle comment");
        super.actionPerformed(evt, target); //To change body of generated methods, choose Tools | Templates.
    }


}
