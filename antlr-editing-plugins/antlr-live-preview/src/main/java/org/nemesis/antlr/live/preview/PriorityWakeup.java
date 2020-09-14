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
package org.nemesis.antlr.live.preview;

import java.util.concurrent.SynchronousQueue;
import org.nemesis.misc.utils.ActivityPriority;
import org.openide.util.Exceptions;

/**
 * Used to force reinitialization of plumbing that gets deinitialized
 * after som idle time.  While I don't like using thread priorities to
 * solve this sort of thing, dang if it didn't make a ton of UI hangs
 * where 15 things go to war with each other over the (real bug which is the)
 * ProcessManager mutex vanish.  So, until there's a better solution,
 * Thread.MAX_PRIORITY is our friend.
 *
 * @author Tim Boudreau
 */
final class PriorityWakeup implements Runnable {

    // We WANT a queue that will block until the job is on its
    // way here, so other events in the EDT don't take precedence
    // and start a parse going
    private final SynchronousQueue<Runnable> q = new SynchronousQueue<>();

    static PriorityWakeup INSTANCE = new PriorityWakeup();
    private final Thread thread;

    PriorityWakeup() {
        assert INSTANCE == null;
        thread = new Thread(this, "priority-wakeup");
        // Yup, starve the universe and get it done
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.setDaemon(true);
        thread.start();
    }

    static void runImmediately(Runnable r) {
        System.out.println("PRIORITY WAKEUP.");
        INSTANCE.q.offer(r);
    }

    @Override
    public void run() {
        for (;;) {
            Runnable r = null;
            try {
                r = q.take();
            } catch (Exception | Error ex) {
                if (ex instanceof OutOfMemoryError) {
                    return;
                }
                Exceptions.printStackTrace(ex);
            }
            if (r != null) {
                ActivityPriority.REALTIME.wrap(r);
            }
        }
    }

}
