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
