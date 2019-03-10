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
