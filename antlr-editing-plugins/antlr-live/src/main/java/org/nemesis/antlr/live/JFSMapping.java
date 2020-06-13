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
package org.nemesis.antlr.live;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import java.io.IOException;
import java.lang.ref.WeakReference;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.debug.api.Debug;
import org.nemesis.debug.api.Trackables;
import org.nemesis.jfs.JFS;
import org.netbeans.api.project.Project;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

/**
 *
 * @author Tim Boudreau
 */
class JFSMapping {

    // also may need to kill it if low memory
    private final Map<Project, ProjectReference> refs
            = new WeakHashMap<>();

    private static final Logger LOG = Logger.getLogger(JFSMapping.class.getName());
    private final RequestProcessor.Task killer = RequestProcessor.getDefault().create(() -> {
        Map<Project, ProjectReference> map = new HashMap<>(refs);
        try {
            synchronized (this) {
                for (Map.Entry<Project, ProjectReference> e : map.entrySet()) {
                    if (e.getValue().isExpired()) {
                        boolean killed = kill(e.getKey());
                        if (killed) {
                            LOG.log(Level.INFO, "Killed abandoned JFS {0} for {1}", new Object[]{e.getValue().jfs.id(), e.getKey()});
                        }
                    }
                }
            }
        } finally {
            this.killer.schedule((int) (ProjectReference.EXPIRATION / 2));
        }
    });

    JFSMapping() {
        killer.schedule((int) (ProjectReference.EXPIRATION / 2));
    }

    synchronized boolean kill(Project p) {
        ProjectReference ref = refs.get(p);
        if (ref == null) {
            return false;
        }
        if (RebuildSubscriptions.instance().kill(p, ref.jfs)) {
            refs.remove(p);
            ref.run();
            return true;
        }
        return false;
    }

    synchronized JFS forProject(Project project) throws IOException {
        ProjectReference ref = refs.get(project);
        if (ref == null) {
            JFS jfs = createJFS();
            ref = new ProjectReference(jfs, project);
            String id = jfs.id();
            Trackables.track(JFS.class, jfs, () -> {
                return "JFS-" + id + project.getProjectDirectory().getName();
            });
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
        return JFS.builder().withCharset(UTF_8).build();
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

    static class ProjectReference extends WeakReference<Project> implements Runnable, Delayed {

        private final JFS jfs;
        volatile boolean disposed;
        private final ReentrantLock lock = new ReentrantLock(true);
        volatile long lastTouch = System.currentTimeMillis();
        private static final long EXPIRATION = 1000 * 60 * 10; // 10 min

        public ProjectReference(JFS jfs, Project project) {
            super(project, Utilities.activeReferenceQueue());
            this.jfs = jfs;
        }

        private void touch() {
            lastTouch = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return getDelay(TimeUnit.MILLISECONDS) <= 0;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long target = lastTouch + EXPIRATION;
            return unit.convert(target - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
        }

        Project whileStronglyReferenced(ThrowingRunnable r) throws Exception {
            touch();
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
            touch();
            p[0] = get();
            LOG.log(Level.FINEST, "Lock {0} for {1}", new Object[]{jfs, run});
            return Debug.runObjectThrowing(jfs, "lock-jfs", () -> {
                StringBuilder sb = new StringBuilder("JFS-").append(jfs.id());
                if (p[0] != null) {
                    sb.append(" for ").append(p[0].getProjectDirectory().getName());
                }
                sb.append('\n');
                jfs.listAll((loc, fo) -> {
                    sb.append(loc).append(": ").append(fo);
                });
                return sb.toString();
            }, () -> {
                lock.lock();
                try {
                    return run.get();
                } finally {
                    lock.unlock();
                    LOG.log(Level.FINEST, "Unlocked {0} for {1}", new Object[]{jfs, run});
                }
            });
        }

        @Override
        public void run() {
            if (disposed) {
                LOG.log(Level.FINE, "JFS already disposed: {0} for {1}", new Object[]{jfs.id(), get()});
                return;
            }
            disposed = true;
            try {
                LOG.log(Level.FINER, "Project disappeared - close {0}", jfs);
                jfs.close();
                Trackables.discarded(JFS.class, jfs);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Closing jfs", ex);
            }
        }
    }
}
