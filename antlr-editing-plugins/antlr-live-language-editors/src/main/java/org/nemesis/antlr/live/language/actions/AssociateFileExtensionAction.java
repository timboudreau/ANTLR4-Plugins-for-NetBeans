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
package org.nemesis.antlr.live.language.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle;

/*

For mysterious reasons, the continuous build claims this class does not implement
ActionListener, so manually adding the generated entries to the layer file
and removing the annotation.

@ActionID(
        category = "File",
        id = "org.nemesis.antlr.live.language.actions.AssociateFileExtensionAction"
)
@ActionRegistration(
        displayName = "#CTL_AssociateFileExtensionAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 1950, separatorBefore = 1875, separatorAfter = 2025),
    @ActionReference(path = "Loaders/text/x-g4/Actions", position = 1250),
    @ActionReference(path = "Editors/text/x-g4/Popup", position = 990, separatorBefore = 885, separatorAfter = 1095)
})
*/
@NbBundle.Messages({"associationsDialog=Add File Extension",
    "CTL_AssociateFileExtensionAction=Associate File Extension",
    "fileExtension=&File Name Extension",
    "existingAssociations=E&xisting Extensions",
    "noAssociations=<no extensions yet>",
    "extensionTip=Choose a file extension such as 'foo' and the IDE will recognize files"
    + " whose names end with that extension as belonging to your grammar.  You"
    + " can set up syntax coloring on the preview tab of the editor for your grammar.",
    "add=&Add",})
public final class AssociateFileExtensionAction implements ActionListener {

    private final DataObject context;

    public AssociateFileExtensionAction(DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        AssociateFileExtensionDialog.showDialog(context);
    }
}
