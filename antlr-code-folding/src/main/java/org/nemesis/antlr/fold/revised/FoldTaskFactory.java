package org.nemesis.antlr.fold.revised;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.SchedulerTask;
import org.netbeans.modules.parsing.spi.TaskFactory;

/**
 *
 * @author Tim Boudreau
 */
final class FoldTaskFactory extends TaskFactory {

    static final FoldTaskFactory INSTANCE = new FoldTaskFactory();
    private static final Logger LOG = Logger.getLogger(FoldTaskFactory.class.getName());

    @Override
    public Collection<? extends SchedulerTask> create(Snapshot snapshot) {
        FoldTask task = FoldTasks.getDefault().forSnapshot(snapshot, false);
        if (task != null) {
            LOG.log(Level.FINE, "Found FoldTask for {0}: {1}", new Object[]{snapshot, task});
            return Collections.singleton(task);
        } else {
            LOG.log(Level.FINE, "NO FoldTask for {0}", snapshot);
            return Collections.emptySet();
        }
    }

}
