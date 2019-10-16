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
package org.nemesis.antlr.spi.language.fix;

import java.util.Optional;
import org.nemesis.extraction.Extraction;
import org.netbeans.editor.BaseDocument;
import org.openide.filesystems.FileObject;

/**
 * Code which is called to implement a fix to a document.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface FixImplementation {

    /**
     * Alter the document to conform with the proposed fix.
     *
     * @param document The document
     * @param file The file, if any
     * @param extraction The extraction derived from the file
     * @param edits A collection of edits to the document that can be
     * added to.  In the case of multiple, discontiguous edits to the
     * same document, either add them in reverse order, or use the
     * <code>multiple()</code> method to ensure edit positions are
     * preserved.
     * @throws Exception If something goes wrong
     */
    void implement(BaseDocument document, Optional<FileObject> file, Extraction extraction, DocumentEditBag edits) throws Exception;

}
