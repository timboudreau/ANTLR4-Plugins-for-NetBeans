package org.nemesis.antlr.live;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.nemesis.jfs.JFS;
import org.netbeans.api.project.Project;
import org.openide.util.Exceptions;
import org.openide.util.Utilities;

/**
 *
 * @author Tim Boudreau
 */
class JFSMapping {

    // also may need to kill it if low memory
    private final Map<Project, ProjectReference> refs
            = new WeakHashMap<>();

    synchronized JFS forProject(Project project) throws IOException {
        ProjectReference ref = refs.get(project);
        if (ref == null) {
            ref = new ProjectReference(createJFS(), project);
            refs.put(project, ref);
        }
        return ref.jfs;
    }

    synchronized JFS getIfPresent(Project project) {
        ProjectReference ref = refs.get(project);
        if (ref != null && !ref.disposed) {
            return ref.jfs;
        }
        return null;
    }

    JFS createJFS() throws IOException {
        return JFS.builder().build();
    }

    <T> T whileLocked(JFS jfs, ThrowingSupplier<T> run) throws Exception {
        ProjectReference target = null;
        synchronized (this) {
            for (ProjectReference ref : refs.values()) {
                if (ref.jfs == jfs) {
                    target = ref;
                    break;
                }
            }
        }
        return target == null ? run.get() : target.whileLocked(run);
    }

    static class ProjectReference extends WeakReference<Project> implements Runnable {

        private final JFS jfs;
        volatile boolean disposed;
        private final ReentrantLock lock = new ReentrantLock(true);

        public ProjectReference(JFS jfs, Project project) {
            super(project, Utilities.activeReferenceQueue());
            this.jfs = jfs;
        }

        Project whileStronglyReferenced(ThrowingRunnable r) throws Exception {
            Project p = get();
            r.run();
            return p;
        }

        Project whileLocked(ThrowingRunnable run) throws Exception {
            // Ensure the referent can't be garbage collected
            // while running - unlikely but would be a potent
            // heisenbug
            Project p = get();
            ThrowingSupplier<Void> x = () -> {
                run.run();
                return null;
            };
            whileLocked(x);
            return p;
        }

        <T> T whileLocked(ThrowingSupplier<T> run) throws Exception {
            return whileLocked(run, new Project[1]);
        }

        private <T> T whileLocked(ThrowingSupplier<T> run, Project[] p) throws Exception {
            p[0] = get();
            lock.lock();
            try {
                return run.get();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void run() {
            disposed = true;
            try {
                jfs.close();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }
}
