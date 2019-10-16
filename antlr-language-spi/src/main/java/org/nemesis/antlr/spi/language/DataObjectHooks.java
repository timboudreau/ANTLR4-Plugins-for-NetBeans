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
package org.nemesis.antlr.spi.language;

import java.io.IOException;
import java.util.function.Supplier;
import com.mastfrog.function.throwing.io.IOBiFunction;
import com.mastfrog.function.throwing.io.IOFunction;
import com.mastfrog.function.throwing.io.IORunnable;
import com.mastfrog.function.throwing.io.IOTriFunction;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.lookup.InstanceContent;

/**
 * Can be specified in the <code>&#064;FileType</code> field of your
 * <a href="AntlrLanguageRegistration.html"><code>&#064;AntlrLanguageRegistration</code></a>
 * annotation, to be notified about creation and other events relating to an
 * Antlr file type. All methods on this class allow you to intercept lifecycle
 * methods of {@link DataObject} and either invoke the default behavior or
 * provide your own.
 *
 * @author Tim Boudreau
 */
public interface DataObjectHooks {

    /**
     * Called when a DataObject of your type is created.
     *
     * @param dob A DataObject
     */
    default void notifyCreated(DataObject dob) {
        // do nothing
    }

    /**
     * Called post-creation, allowing you to add or monitor contents in the
     * DataObject's lookup.
     *
     * @param on The data object
     * @param content The content, which you can contribute to
     * @param superGetLookup Returns the super lookup created by default for the
     * data object, which is also merged into the lookup returned by the data
     * object's <code>getLookup()</code> method
     */
    default void decorateLookup(DataObject on, InstanceContent content, Supplier<Lookup> superGetLookup) {
        // do nothing
    }

    /**
     * Allows for overriding creation of the data object's node.
     *
     * @param on The data object in question
     * @param superDelegate A supplier that returns the original node
     * @return A node
     */
    default Node createNodeDelegate(DataObject on, Supplier<Node> superDelegate) {
        return superDelegate.get();
    }

    /**
     * Handle deletion (hint: if you want to delay / override it, throw a
     * <code>UserQuestionException</code>).
     *
     * @param obj The data object in question
     * @param superHandleDelete Call this to invoke default delete handling
     * @throws IOException If something goes wrong
     */
    default void handleDelete(DataObject obj, IORunnable superHandleDelete) throws IOException {
        superHandleDelete.run();
    }

    default FileObject handleRename(DataObject on, String name, IOFunction<String, FileObject> superHandleRename) throws IOException {
        return superHandleRename.apply(name);
    }

    default DataObject handleCopy(DataObject on, DataFolder target, IOFunction<DataFolder, DataObject> superHandleCopy) throws IOException {
        return superHandleCopy.apply(target);
    }

    default FileObject handleMove(DataObject on, DataFolder target, IOFunction<DataFolder, FileObject> superHandleMove) throws IOException {
        return superHandleMove.apply(target);
    }

    default DataObject handleCreateFromTemplate(DataObject on, DataFolder df, String name, IOBiFunction<DataFolder, String, DataObject> superHandleCreateFromTemplate) throws IOException {
        return superHandleCreateFromTemplate.apply(df, name);
    }

    default DataObject handleCopyRename(DataObject on, DataFolder df, String name, String ext, IOTriFunction<DataFolder, String, String, DataObject> superHandleRenameCopy) throws IOException {
        return superHandleRenameCopy.apply(df, name, ext);
    }
}
