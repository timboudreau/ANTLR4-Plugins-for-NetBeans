package org.nemesis.antlr.fold;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import org.nemesis.extraction.key.RegionsKey;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.SchedulerTask;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrFoldManagerTaskFactory<T> extends TaskFactory {

    private final String mimeType;
    private final RegionsKey<T> key;
    private final SemanticRegionToFoldConverter<T> converter;

    @SuppressWarnings("LeakingThisInConstructor")
    AntlrFoldManagerTaskFactory(String mimeType, RegionsKey<T> key, SemanticRegionToFoldConverter<T> converter) {
        this.mimeType = mimeType;
        this.key = key;
        this.converter = converter;
        ExtractionElementsFoldManager.LOG.log(Level.FINE, "Create {0}", this);
    }

    @Override
    public String toString() {
        return "AntlrFoldManagerTaskFactory{ " + mimeType + ", " + key + ',' + converter + " }";
    }

    public static <T> TaskFactory create(String mimeType, RegionsKey<T> key, SemanticRegionToFoldConverter<T> converter) {
        return new AntlrFoldManagerTaskFactory<>(mimeType, key, converter);
    }

    @Override
    public final Collection<? extends SchedulerTask> create(Snapshot snapshot) {
        String mime = snapshot.getMimeType();
        if (!mime.equals(this.mimeType)) {
            return null;
        }
        return _createTasks(snapshot);
    }

    /**
     * Creates a set of tasks for a given <code>Snapshot</code>. The
     * <code>language</code> passed in is a registered CSL language relevant for
     * the <code>snapshot</code>'s mimetype.
     *
     * @param language The language appropriate for the <code>snapshot</code>'s
     * mimetype; never <code>null</code>.
     * @param snapshot The snapshot to create tasks for.
     *
     * @return The set of tasks or <code>null</code>.
     */
    private Collection<? extends SchedulerTask> _createTasks(Snapshot snapshot) {
        FileObject file = snapshot.getSource().getFileObject();
        if (file != null) {
            ExtractionFoldTask<T> refreshTask = ExtractionFoldRefresher.getDefault().getTask(file, key, converter);
            if (ExtractionElementsFoldManager.LOG.isLoggable(Level.FINER)) {
                ExtractionElementsFoldManager.LOG.log(Level.FINER, "Scheduling task for file: {0} -> {1}, Thread: {2}", new Object[] { file, refreshTask, Thread.currentThread() });
            }
            return Collections.singleton(refreshTask.toSchedulerTask());
        } else {
            if (ExtractionElementsFoldManager.LOG.isLoggable(Level.FINE)) {
                ExtractionElementsFoldManager.LOG.log(Level.FINE, "FileObject is null: {0}", snapshot.getSource());
            }
            return null;
        }
    }
}
