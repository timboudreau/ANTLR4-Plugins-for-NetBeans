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
package org.nemesis.antlr.file.antlrrefactoring;

import com.mastfrog.util.collections.CollectionUtils;
import java.util.Collections;
import java.util.function.BooleanSupplier;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.refactoring.usages.ImportersFinder;
import org.nemesis.antlr.refactoring.usages.SimpleImportersFinder;
import org.nemesis.antlr.project.Folders;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
@MimeRegistration(mimeType = ANTLR_MIME_TYPE, position = 100, service = ImportersFinder.class)
public class AntlrLanguageImportersFinder extends SimpleImportersFinder {

    @Override
    protected Iterable<FileObject> possibleImportersOf(BooleanSupplier cancelled, FileObject file) {
        if (cancelled.getAsBoolean()) {
            return Collections.emptySet();
        }
        return CollectionUtils.concatenate(Folders.ANTLR_GRAMMAR_SOURCES.allFiles(file),
                Folders.ANTLR_IMPORTS.allFiles(file));
    }
}
