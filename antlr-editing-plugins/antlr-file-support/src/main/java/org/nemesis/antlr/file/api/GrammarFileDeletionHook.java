package org.nemesis.antlr.file.api;

import com.mastfrog.function.throwing.io.IORunnable;
import java.io.IOException;
import org.openide.loaders.DataObject;

/**
 * Called when a grammar file is about to be deleted, and again once it has been
 * - used to deregister synthetic mime types that allow files to be edited with
 * syntax highlighting based on live parsing and runninng of Antlr grammars.
 *
 * @author Tim Boudreau
 */
public abstract class GrammarFileDeletionHook {

    /**
     * Called when the user has requested to delete a grammar file.
     *
     * @param file The grammar file
     * @param ifOk Callback which will allow deletion to continue (perhaps
     * a UserQuestionException is thrown and this called from its callback
     * method)
     * @throws IOException If something goes wrong
     */
    public abstract void onBeforeDeleteGrammar(DataObject file, IORunnable ifOk) throws IOException;

    /**
     * Caled when the user has deleted a grammar file.
     *
     * @param file A grammar file
     * @throws IOException If something goes wrong
     */
    public abstract void onAfterDeleteGrammar(DataObject file) throws IOException;

}
