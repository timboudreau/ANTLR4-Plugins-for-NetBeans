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
 *
 * @author Tim Boudreau
 */
public interface DataObjectHooks {

    default void notifyCreated(DataObject dob) {
        // do nothing
    }

    default void decorateLookup(DataObject on, InstanceContent content, Supplier<Lookup> superGetLookup) {
        // do nothing
    }

    default Node createNodeDelegate(DataObject on, Supplier<Node> superDelegate) {
        return superDelegate.get();
    }

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

    default DataObject handleCopyRename(DataObject on, DataFolder df, String name, String ext, IOTriFunction<DataFolder, String,String, DataObject> superHandleRenameCopy) throws IOException {
        return superHandleRenameCopy.apply(df, name, ext);
    }
}
