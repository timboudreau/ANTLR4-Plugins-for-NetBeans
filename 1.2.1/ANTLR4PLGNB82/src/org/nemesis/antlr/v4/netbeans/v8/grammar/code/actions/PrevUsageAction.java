/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.actions;

import org.netbeans.api.editor.EditorActionRegistration;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({"prev-usage=Next Usage"})
@EditorActionRegistration(
        name = "prev-usage",
        weight = Integer.MAX_VALUE,
        category = "Editing",
        popupText = "Prev Usage",
        menuPath = "Source",
        menuText = "Prev Usage",
        mimeType = "text/g-4")
public class PrevUsageAction extends NextUsageAction {

    public PrevUsageAction() {
        super(false);
    }
}
