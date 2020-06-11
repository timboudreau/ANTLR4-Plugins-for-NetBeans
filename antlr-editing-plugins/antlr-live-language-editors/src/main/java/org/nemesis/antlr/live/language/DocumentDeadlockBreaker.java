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

import com.mastfrog.util.preconditions.Exceptions;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
final class DocumentDeadlockBreaker {

    private static final long DELAY = 20000;

    private static final DelayQueue<Entry> DQ = new DelayQueue<>();

    static {
        Thread killThread = new Thread("adhoc-doc-deadlock-breaker");
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

    static void loop() throws InterruptedException {
        for (;;) {
            Entry e = DQ.take();
            if (!e.isDone()) {
                System.out.println("deadlock breaker interrupting " + e);
                e.kill();
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
                    thread.interrupt();
                    return true;
                } catch (Exception ex) {
                    Logger.getLogger(Entry.class.getName()).log(Level.INFO, 
                            "Exception killing late render task on " + thread, ex);
                }
            }
            return false;
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
