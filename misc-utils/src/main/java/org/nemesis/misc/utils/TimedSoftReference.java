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
package org.nemesis.misc.utils;

import java.lang.ref.SoftReference;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author Tim Boudreau
 */
public final class TimedSoftReference<T> {

    private static final ScheduledExecutorService DISPATCH = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<T> hardReference;
    private SoftReference<T> softReference;
    private final long delayMillis;
    private Future<?> future;
    private final AtomicInteger seq = new AtomicInteger(Integer.MIN_VALUE);

    TimedSoftReference(T obj, int delay, TimeUnit unit) {
        softReference = obj == null ? null : new SoftReference<>(obj);
        hardReference = obj == null ? new AtomicReference<>() : new AtomicReference<>(obj);
        delayMillis = TimeUnit.MILLISECONDS.convert(delay, unit);
        if (obj != null) {
            startTimer();
        }
    }

    @Override
    public String toString() {
        return "TimedSoftReference{" + getIfPresent() + "}";
    }

    public T getIfPresent() {
        T result = hardReference.get();
        if (result == null) {
            synchronized (hardReference) {
                if (softReference != null) {
                    result = softReference.get();
                }
            }
        }
        return result;
    }

    public void clear() {
        hardReference.set(null);
        synchronized (hardReference) {
            softReference = null;
        }
    }

    public static <T> TimedSoftReference<T> create(int delay, TimeUnit unit) {
        return new TimedSoftReference<>(null, delay, unit);
    }

    public static <T> TimedSoftReference<T> create(T obj) {
        return create(obj, 30, TimeUnit.SECONDS);
    }

    public static <T> TimedSoftReference<T> create(T obj, int delay, TimeUnit unit) {
        return new TimedSoftReference<>(obj, delay, unit);
    }

    public T get() {
        SoftReference<T> ref;
        synchronized (hardReference) {
            ref = softReference;
        }
        T result = hardReference.get();
        if (result == null && ref != null) {
            result = ref.get();
            if (result != null) {
                hardReference.set(result);
                touch();
            }
        }
        return result;
    }

    public void set(T obj) {
        hardReference.set(obj);
        synchronized (hardReference) {
            if (obj != null) {
                softReference = new SoftReference<>(obj);
            }
            touch();
        }
    }

    public boolean isEmpty() {
        T res = hardReference.get();
        boolean result = res == null;
        if (result) {
            System.out.println("  hard was null, try soft");
            SoftReference<T> ref;
            synchronized (hardReference) {
                ref = softReference;
            }
            System.out.println("ref is " + ref);
            if (ref != null) {
                res = ref.get();
                System.out.println("  gets " + res);
            }
        }
        return res == null;
    }

    boolean isHardReferenced() {
        return hardReference.get() != null;
    }

    private synchronized void startTimer() {
        future = DISPATCH.schedule(task(seq.incrementAndGet()),
                delayMillis, TimeUnit.MILLISECONDS);
    }

    private synchronized void touch() {
        System.out.println("touch");
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
        startTimer();
    }

    private void onTimer() {
        hardReference.set(null);
        System.out.println("clear ref for timer soft " + softReference.get());
    }

    private boolean first = true;

    private Runnable task(int seqNum) {
        return () -> {
            if (first) {
                Thread.currentThread().setName("antlr-timed-soft-reference-clear");
                first = false;
            }
            if (seq.compareAndSet(seqNum, seqNum + 1)) {
                onTimer();
            } else {
                System.out.println("bad seqnum " + seqNum + " have " + seq.get());
            }
        };
    }
}
