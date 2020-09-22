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
package org.nemesis.antlr.live.language;

import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.preconditions.Exceptions;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
final class DocumentDeadlockBreaker {

    private static final long DELAY = 20000;

    private static final DelayQueue<Entry> DQ = new DelayQueue<>();
    private static final Logger LOG = Logger.getLogger(DocumentDeadlockBreaker.class.getName());

    static {
        Thread killThread = new Thread(DocumentDeadlockBreaker::loop, "adhoc-doc-deadlock-breaker");
        killThread.setPriority(Thread.NORM_PRIORITY - 1);
        killThread.setDaemon(true);
        killThread.setUncaughtExceptionHandler((Thread t, Throwable e) -> {
            Exceptions.printStackTrace("Uncaught exception on " + t, e);
        });
        killThread.start();
    }

    public static Runnable enqueue() {
        Entry e = new Entry();
        DQ.offer(e);
        return e;
    }

    static void loop() {
        for (;;) {
            String last = null;
            try {
                Entry e = DQ.take();
                last = e.toString();
                if (!e.isDone()) {
                    LOG.log(Level.WARNING,
                            "Deadlock breaker interrupting {0}", e);
                    e.kill();
                }
            } catch (Exception ex) {
                LOG.log(Level.INFO,
                        "Exception in deadlock breaker; last: " + last, ex);
            }
        }
    }

    private static final class Entry implements Delayed, Runnable {

        private final AtomicBoolean done = new AtomicBoolean();
        private final long expires = System.currentTimeMillis() + DELAY;
        private final Thread thread;

        Entry() {
            this.thread = Thread.currentThread();
        }

        public String toString() {
            return thread.toString();
        }

        boolean kill() {
            if (setDone()) {
                try {
                    StringBuilder stacks = null;
                    try {
                        stacks = collectStackTraces();
                    } catch (Exception ex) {
                        LOG.log(Level.INFO, null, ex);
                    }
                    thread.interrupt();
                    Thread.yield();
                    if (stacks != null) {
                        LOG.log(Level.INFO, "Attempted to break deadlock on {0}. Contention info: {1}", stacks);
                    }
                    if (thread.getState() == Thread.State.BLOCKED) {
                        LOG.log(Level.WARNING, "Attempted "
                                        + "to break deadlock in {0} "
                                        + "but it still appears to be blocked.\n{1}",
                                        new Object[]{thread, collectStackTraces()});
                        thread.interrupt();
                    }
                    return true;
                } catch (Exception ex) {
                    LOG.log(Level.INFO,
                            "Exception killing late render task on " + thread, ex);
                }
            }
            return false;
        }

        private static boolean skipThread(String name) {
            switch(name) {
                // skip some irrelevant threads that could not be participating
                case "JGit-Workqueue":
                case "AWT-Shutdown":
                case "Event Dispatch Thread": // preferences
                case "Java2D Disposer":
                case "Finalizer":
                case "Framework Event Dispatcher":
                case "Bundle File Closer":
                case "Batik Cleaner Thread":
                case "Common-Cleaner":
                case "VM Thread":
                case "VM Periodic Task Thread":
                case "StrDedup":
                case "DestroyJavaVM":
                case "File Watcher":
                case "FileSystemWatchService":
                case "Framework Active Thread":
                case "AWT-XAWT":
                case "CLI Requests Server":
                case "Notification Thread":
                case "Signal Dispatcher":
                case "Service Thread":
                case "Reference Handler":
                    return true;
            }
            if (name.startsWith("GC") || name.startsWith("G1") || name.startsWith("C1") || name.startsWith("C2")) {
                return true;
            }
            return false;
        }

        private StringBuilder collectStackTraces() {
            StringBuilder sb = new StringBuilder();
            Map<Object, Set<Thread>> sharedLocks = CollectionUtils.supplierMap(HashSet::new);
            Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
            for (Map.Entry<Thread, StackTraceElement[]> e : allStackTraces.entrySet()) {
                Thread t = e.getKey();
                if (t == Thread.currentThread() || skipThread(t.getName())) {
                    continue;
                }
                Thread.State state = t.getState();
                switch (state) {
                    case BLOCKED:
                    case TIMED_WAITING:
                    case WAITING:
                    case RUNNABLE :
                        Object blocker = LockSupport.getBlocker(t);
                        StackTraceElement[] els = e.getValue();
                        if (blocker != null) {
                            sharedLocks.get(blocker).add(t);
                        }
                        sb.append('\n').append(t.getName()).append(' ').append(state);
                        if (blocker != null) {
                            sb.append(" blocked on ").append(blocker)
                                    .append(" (").append(blocker.getClass().getSimpleName()).append(")\n");
                        }
                        for (int i = 0; i < els.length; i++) {
                            sb.append('\t').append(els[i]).append('\n');
                        }
                }
            }
            return sb;
        }

        boolean isDone() {
            return done.get();
        }

        boolean setDone() {
            return done.compareAndSet(false, true);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(expires - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
        }

        @Override
        public void run() {
            setDone();
        }
    }
}
