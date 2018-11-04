/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.file;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.text.MultiViewEditorElement;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.UserQuestionException;
import org.openide.windows.TopComponent;

@Messages({
    "LBL_G4_LOADER=ANTLR v4 grammar files"
})
@MIMEResolver.ExtensionRegistration(
        displayName = "#LBL_G4_LOADER",
        mimeType = "text/x-g4",
        extension = {"g4"}
)
@DataObject.Registration(
        mimeType = "text/x-g4",
        iconBase = "org/nemesis/antlr/v4/netbeans/v8/grammar/file/antlr-g4-file-type.png",
        displayName = "#LBL_G4_LOADER",
        position = 300
)
@ActionReferences({
    @ActionReference(
            path = "Loaders/text/x-g4/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.OpenAction"),
            position = 100,
            separatorAfter = 200
    ),
    @ActionReference(
            path = "Loaders/text/x-g4/Actions",
            id = @ActionID(category = "Edit", id = "org.openide.actions.CutAction"),
            position = 300
    ),
    @ActionReference(
            path = "Loaders/text/x-g4/Actions",
            id = @ActionID(category = "Edit", id = "org.openide.actions.CopyAction"),
            position = 400,
            separatorAfter = 500
    ),
    @ActionReference(
            path = "Loaders/text/x-g4/Actions",
            id = @ActionID(category = "Edit", id = "org.openide.actions.DeleteAction"),
            position = 600
    ),
    @ActionReference(
            path = "Loaders/text/x-g4/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.RenameAction"),
            position = 700,
            separatorAfter = 800
    ),
    @ActionReference(
            path = "Loaders/text/x-g4/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.SaveAsTemplateAction"),
            position = 900,
            separatorAfter = 1000
    ),
    @ActionReference(
            path = "Loaders/text/x-g4/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.FileSystemAction"),
            position = 1100,
            separatorAfter = 1200
    ),
    @ActionReference(
            path = "Loaders/text/x-g4/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.ToolsAction"),
            position = 1300
    ),
    @ActionReference(
            path = "Loaders/text/x-g4/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.PropertiesAction"),
            position = 1400
    )
})
public class G4DataObject extends MultiDataObject {

    public G4DataObject(FileObject pf,
            MultiFileLoader loader)
            throws DataObjectExistsException {
        super(pf, loader);
        registerEditor("text/x-g4", true);
    }

    @Override
    protected int associateLookup() {
        return 1;
    }

    @MultiViewElement.Registration(
            displayName = "#LBL_G4_EDITOR",
            iconBase = "org/nemesis/antlr/v4/netbeans/v8/grammar/file/antlr-g4-file-type.png",
            mimeType = "text/x-g4",
            persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED,
            preferredID = "G4",
            position = 1000
    )
    @Messages("LBL_G4_EDITOR=Source")
    public static MultiViewEditorElement createEditor(Lookup lkp) {
        return new MultiViewEditorElement(lkp);
    }

    private String adhocMimeType() {
        File file = FileUtil.toFile(getPrimaryFile());
        if (file != null) {
            return AdhocMimeTypes.mimeTypeForPath(file.toPath());
        }
        return null;
    }

    private Set<String> registeredExtensions() {
        String mimeType = adhocMimeType();
        if (mimeType != null) {
            return AdhocMimeTypes.registeredExtensionsFor(mimeType);
        }
        return Collections.emptySet();
    }

    @Override
    @Messages({
        "# {0} - the file name",
        "# {1} - list of registered file extensions",
        "hasRegisteredExtensions=Grammar {0} has the file extension(s) {1} registered to it. "
        + "Unregister those extensions and really delete it?"})
    protected void handleDelete() throws IOException {
        Set<String> registeredExtensions = registeredExtensions();
        if (!registeredExtensions.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String e : registeredExtensions) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(e);
            }
            String msg = Bundle.hasRegisteredExtensions(getPrimaryFile().getName(), sb);
            throw new UserQuestionException(msg) {
                @Override
                public void confirmed() throws IOException {
                    AdhocMimeTypes.unregisterMimeType(adhocMimeType());
                    G4DataObject.super.handleDelete();
                }
            };
        } else {
            super.handleDelete();
        }
    }
}
