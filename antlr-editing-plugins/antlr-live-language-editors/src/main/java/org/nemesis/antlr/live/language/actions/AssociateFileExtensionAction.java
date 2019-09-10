package org.nemesis.antlr.live.language.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle;

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
