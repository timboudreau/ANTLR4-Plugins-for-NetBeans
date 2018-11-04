package org.nemesis.antlr.v4.netbeans.v8.grammar.file;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "File",
        id = "org.nemesis.antlr.v4.netbeans.v8.grammar.file.AssociateFileExtensionAction"
)
@ActionRegistration(
        displayName = "#CTL_AssociateFileExtensionAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 1950, separatorBefore = 1875, separatorAfter = 2025),
    @ActionReference(path = "Loaders/text/x-g4/Actions", position = 1250),
    @ActionReference(path = "Editors/text/x-g4/Popup", position = 990, separatorBefore = 885, separatorAfter = 1095)
})
@Messages("CTL_AssociateFileExtensionAction=Associate File Extension")
public final class AssociateFileExtensionAction implements ActionListener {

    private final G4DataObject context;

    public AssociateFileExtensionAction(G4DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        AssociateFileExtensionDialog.showDialog(context);
    }
}
