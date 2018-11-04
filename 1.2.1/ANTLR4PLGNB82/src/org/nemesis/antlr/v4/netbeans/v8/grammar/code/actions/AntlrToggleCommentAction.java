/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
