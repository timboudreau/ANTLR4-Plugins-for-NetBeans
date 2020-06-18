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
package org.nemesis.antlr.live.parsing;

import java.util.prefs.Preferences;
import javax.swing.text.Document;
import org.netbeans.modules.editor.impl.DocumentFactoryImpl;
import org.netbeans.spi.editor.document.DocumentFactory;
import org.openide.filesystems.FileObject;
import org.openide.util.NbPreferences;

/**
 * We have a problem of some tests triggering a background thread that, via
 * CodeStylePreferences, initializes the entire module system. This class
 * asstempts to block that pathway by preemptively putting a Perferences object
 * as a property.
 *
 * @author Tim Boudreau
 */
public class WrapDocumentFactory implements DocumentFactory {

    private final DocumentFactory delegate = new DocumentFactoryImpl();

    @Override
    public Document createDocument(String string) {
        Document result = delegate.createDocument(string);
        if (result != null) {
            // Try to avoid initializing the module system by accident, via CodeStylePreferences
            // initialized by a background thread deep in the editor api's plumbing
            Preferences prefs = NbPreferences.forModule(EmbeddedAntlrParsersTest.class);
            result.putProperty("Tools-Options->Editor->Formatting->Preview - Preferences", prefs);
        }
        return result;
    }

    @Override
    public Document getDocument(FileObject fo) {
        Document result = delegate.getDocument(fo);
        // Try to avoid initializing the module system by accident, via CodeStylePreferences
        // initialized by a background thread deep in the editor api's plumbing
        Preferences prefs = NbPreferences.forModule(EmbeddedAntlrParsersTest.class);
        result.putProperty("Tools-Options->Editor->Formatting->Preview - Preferences", prefs);
        return result;
    }

    @Override
    public FileObject getFileObject(Document dcmnt) {
        return delegate.getFileObject(dcmnt);
    }

}
