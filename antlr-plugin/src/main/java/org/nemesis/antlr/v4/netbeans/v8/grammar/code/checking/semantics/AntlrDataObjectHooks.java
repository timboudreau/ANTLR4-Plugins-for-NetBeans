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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.nemesis.antlr.spi.language.DataObjectHooks;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes;
import com.mastfrog.function.throwing.io.IORunnable;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle;
import org.openide.util.UserQuestionException;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({
    "# {0} - the file name",
    "# {1} - list of registered file extensions",
    "hasRegisteredExtensions=Grammar {0} has the file extension(s) {1} registered to it. "
    + "Unregister those extensions and really delete it?"})
public class AntlrDataObjectHooks implements DataObjectHooks {

    @Override
    public void handleDelete(DataObject on, IORunnable superHandleDelete) throws IOException {
        Set<String> registeredExtensions = registeredExtensions(on);
        if (!registeredExtensions.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String e : registeredExtensions) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(e);
            }
            String msg = Bundle.hasRegisteredExtensions(on.getPrimaryFile().getName(), sb);
            throw new UserQuestionException(msg) {
                @Override
                public void confirmed() throws IOException {
                    AdhocMimeTypes.unregisterMimeType(adhocMimeType(on));
                    superHandleDelete.run();
                }
            };
        } else {
            superHandleDelete.run();
        }
    }

    private String adhocMimeType(DataObject on) {
        File file = FileUtil.toFile(on.getPrimaryFile());
        if (file != null) {
            return AdhocMimeTypes.mimeTypeForPath(file.toPath());
        }
        return null;
    }

    private Set<String> registeredExtensions(DataObject on) {
        String mimeType = adhocMimeType(on);
        if (mimeType != null) {
            return AdhocMimeTypes.registeredExtensionsFor(mimeType);
        }
        return Collections.emptySet();
    }
}
