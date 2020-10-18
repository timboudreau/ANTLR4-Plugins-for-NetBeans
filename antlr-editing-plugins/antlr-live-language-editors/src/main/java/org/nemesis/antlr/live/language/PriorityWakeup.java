/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nemesis.antlr.common.ShutdownHooks;
import org.nemesis.misc.utils.ActivityPriority;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * Used to force reinitialization of plumbing that gets deinitialized after som
 * idle time. While I don't like using thread priorities to solve this sort of
 * thing, dang if it didn't make a ton of UI hangs where 15 things go to war
 * with each other over the (real bug which is the) ProcessManager mutex vanish.
 * So, until there's a better solution, Thread.MAX_PRIORITY is our friend.
 *
 * @author Tim Boudreau
 */
public final class PriorityWakeup {

    // We WANT a queue that will block until the job is on its
    // way here, so other events in the EDT don't take precedence
    // and start a parse going
    private final SynchronousQueue<Runnable> q = new SynchronousQueue<>();

    private static final PriorityWakeup INSTANCE = new PriorityWakeup();
    private final Thread thread;
    private final R r = new R();
    private final AtomicBoolean started = new AtomicBoolean();

    private PriorityWakeup() {
        assert INSTANCE == null;
        thread = new Thread(r, "priority-wakeup");
        // Yup, starve the universe and get it done
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.setDaemon(true);
    }

    public static void runImmediately(Runnable r) {
        if (ShutdownHooks.isShuttingDown()) {
            return;
        }
        if (INSTANCE.started.compareAndSet(false, true)) {
            INSTANCE.thread.start();
            try {
                INSTANCE.r.latch.await(10, TimeUnit.SECONDS);
                // Give it a chance to start slurping on the queue
                Thread.sleep(20);

            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        if (!INSTANCE.thread.isAlive()) {
            r.run();
            return;
        }
        Job job = new Job(r, Lookup.getDefault());
        boolean accepted = INSTANCE.q.offer(job);
        if (!accepted) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            }
            accepted = INSTANCE.q.offer(job);
            if (!accepted) {
                r.run();
            }
        }
    }

    private static final class Job implements Runnable {

        private final Runnable run;
        private final Lookup defLookup;

        public Job(Runnable run, Lookup defLookup) {
            this.run = run;
            this.defLookup = defLookup;
        }

        @Override
        public void run() {
            Lookups.executeWith(defLookup, run);
        }

        public String toString() {
            return "Job(" + run + ")";
        }
    }

    final class R implements Runnable {

        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void run() {
            List<Runnable> toRun = new ArrayList<>(3);
            latch.countDown();
            Runnable r = null;
            for (;;) {
                try {
                    r = q.take();
                    if (r != null) {
                        ActivityPriority.REALTIME.wrap(r);
                    }
                    r = null;
                    q.drainTo(toRun);
                    if (!toRun.isEmpty()) {
                        try {
                            for (Runnable r1 : toRun) {
                                try {
                                    ActivityPriority.REALTIME.wrap(r1);;
                                } catch (Error | Exception e1) {
                                    if (e1 instanceof OutOfMemoryError) {
                                        e1.printStackTrace();
                                        return;
                                    }
                                    Exceptions.printStackTrace(e1);
                                }
                            }
                        } finally {
                            toRun.clear();
                        }
                    }
                } catch (Exception | Error ex) {
                    if (ex instanceof OutOfMemoryError) {
                        ex.printStackTrace();
                        return;
                    }
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }
}
