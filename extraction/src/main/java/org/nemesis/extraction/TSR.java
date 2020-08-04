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
package org.nemesis.extraction;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A stuttery but simple and low-resource consumption variant on the timed soft
 * reference pattern - simply uses single global java.util.Timer rather than a
 * dedicated thread which cleans up strong references, at which point the
 * garbage collector does the rest.
 *
 * @author Tim Boudreau
 */
final class TSR<T> extends SoftReference<T> {

    static final DelayQueue<TimedStrongReference<?>> Q = new DelayQueue<>();
    private final TimedStrongReference<T> strong;

    public TSR(T referent, long delayMs) {
        super(referent);
        strong = new TimedStrongReference<>(referent, delayMs);
        TSRCleanup.INSTANCE.ensureStarted();
    }

    public TSR(T referent, ReferenceQueue<? super T> q, long delayMs) {
        super(referent, q);
        strong = new TimedStrongReference<>(referent, delayMs);
        Q.offer(strong);
        TSRCleanup.INSTANCE.ensureStarted();
    }

    public static <T> TSR<T> create(T obj) {
        return create(obj, 30, TimeUnit.SECONDS);
    }

    public static <T> TSR<T> create(T obj, int delay, TimeUnit unit) {
        return new TSR<>(obj, unit.convert(delay, MILLISECONDS));
    }

    void dispose() {
        strong.clear();
    }

    @Override
    public T get() {
        // ensures the timestamp is touched
        T obj = strong.getValue();
        if (obj == null) {
            obj = super.get();
        }
        if (obj == null) {
            Q.remove(strong);
        }
        return obj;
    }

    @Override
    public String toString() {
        return "TSR(" + super.get() + ")";
    }

    static class TSRCleanup extends TimerTask {

        private static final long INTERVAL = 30;
        private static final AtomicBoolean started = new AtomicBoolean();
        private final Timer timer = new Timer("tsr-cleanup", true);
        static final TSRCleanup INSTANCE = new TSRCleanup();

        void ensureStarted() {
            if (started.compareAndSet(false, true)) {
                timer.schedule(this, INTERVAL);
            }
        }

        @Override
        public void run() {
            TimedStrongReference<?> ref;
            while ((ref = Q.poll()) != null) {
                if (ref.isExpired()) {
                    ref.clear();
                }
            }
        }
    }

    static class TimedStrongReference<T> extends AtomicReference<T> implements Delayed {

        private final long delayMs;
        private volatile long expires;

        public TimedStrongReference(T initialValue, long delayMs) {
            super(initialValue);
            this.delayMs = delayMs;
            touch();
        }

        T getValue() {
            T result = get();
            if (result != null) {
                touch();
            }
            return result;
        }

        void clear() {
            super.set(null);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expires;
        }

        void touch() {
            expires = System.currentTimeMillis() + delayMs;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(expires - System.currentTimeMillis(), unit);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(getDelay(MILLISECONDS), o.getDelay(MILLISECONDS));
        }
    }
}
