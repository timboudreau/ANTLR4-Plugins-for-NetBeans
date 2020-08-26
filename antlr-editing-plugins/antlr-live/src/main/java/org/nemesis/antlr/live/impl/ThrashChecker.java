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
package org.nemesis.antlr.live.impl;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * A generic checking mechanism for the phenomenon of thrashing in an
 * asynchronous concurrent system - too many calls to do the same thing within a
 * given time interval.  Millisecond resolution.
 *
 * @param <T> The key type (make it something like a string that can be leaked
 * without a large penalty, or call <code>cleanup()</code> periodically)
 */
final class ThrashChecker<T> {

    private final Map<T, ThrashEntry> activityTimestamps = new ConcurrentHashMap<>();
    private static final int DEFAULT_THRASH_THRESHOLD_COUNT = 5;
    private static final long THRASH_INTERVAL = 30000;
    private final int thrashThreshold;
    private final long perMilliseconds;

    /**
     * Create a ThrashChecker with the defaults of five calls per key every 30
     * seconds.
     */
    public ThrashChecker() {
        this(DEFAULT_THRASH_THRESHOLD_COUNT, THRASH_INTERVAL);
    }

    /**
     * Create a ThrashChecker with the passed thrash threshold and interval
     * milliseconds.
     *
     * @param thrashThreshold The number of calls with the same key that need to
     * be made within <code>perMilliseconds</code> milliseconds for that key to
     * be considered in the "thrashing" state
     * @param perMilliseconds The interval of milliseconds within which
     * <code>thrashThreshold</code> calls to <code>isThrashing()</code> must
     * occur for the passed object to be considered in the "thrashing" state
     */
    public ThrashChecker(int thrashThreshold, long perMilliseconds) {
        this.thrashThreshold = thrashThreshold;
        this.perMilliseconds = perMilliseconds;
    }

    /**
     * Create a ThrashChecker with the passed thrash threshold and interval
     * milliseconds.
     *
     * @param thrashThreshold The number of calls with the same key that need to
     * be made within <code>perMilliseconds</code> milliseconds for that key to
     * be considered in the "thrashing" state
     * @param perUnit The interval of milliseconds within which
     * <code>thrashThreshold</code> calls to <code>isThrashing()</code> must
     * occur for the passed object to be considered in the "thrashing" state
     * @param the passed time unit (will be stored internally in milllisecond
     * resolution).
     */
    public ThrashChecker(int thrashThreshold, long perUnit, TimeUnit unit) {
        this.thrashThreshold = thrashThreshold;
        this.perMilliseconds = unit.convert(perUnit, TimeUnit.MILLISECONDS);
    }


    public void clear() {
        activityTimestamps.clear();
    }

    /**
     * Delete entries which have been quiet for more than 1.5 x the thrash
     * interval.
     *
     * @return the number of entries deleted
     */
    public int cleanup() {
        long now = System.currentTimeMillis();
        return cleanup(now);
    }

    /**
     * For testability without needing the test to actually sleep for some
     * interval to test correctness, a package private version of
     * <code>cleanup()</code> which takes a timestamp.
     *
     * @param now A time stamp
     * @return The number of entries deleted
     */
    int cleanup(long now) {
        Set<T> toRemove = new HashSet<>(activityTimestamps.keySet());
        int count = 0;
        for (Iterator<T> it = toRemove.iterator(); it.hasNext();) {
            T key = it.next();
            ThrashEntry th = activityTimestamps.get(key);
            if (th != null && th.isQuiet(now)) {
                activityTimestamps.remove(key);
                count++;
            }
        }
        return count;
    }

    /**
     * Update the timestamps for an item and return true if it has entered the
     * "thrashing" state.
     *
     * @param item An item
     * @return True if more than the threshold number of calls to this method
     * with the passed key have been made in the last
     * <code>perMilliseconds</code> milliseconds.
     */
    public boolean isThrashing(T item) {
        return isThrashing(item, System.currentTimeMillis());
    }

    /**
     * For testability without needing the test to actually sleep for some
     * interval to test correctness, a package private version of
     * <code>isThrashing()</code> which takes a timestamp.
     *
     * @param item
     * @param timestamp
     * @return
     */
    boolean isThrashing(T item, long timestamp) {
        return activityTimestamps.computeIfAbsent(item,
                ignored -> new ThrashEntry()).update(timestamp);
    }

    /**
     * Convenience method to call <code>isThrashing()</code> and print a stack
     * trace if so.
     *
     * @param item An item to update the state for
     * @return true if it is thrashing
     */
    public boolean logStackIfThrashing(T item) {
        if (isThrashing(item)) {
            new Exception("Thrashing: " + item).printStackTrace();
            return true;
        }
        return false;
    }

    private final class ThrashEntry {

        AtomicLongArray arr = new AtomicLongArray(thrashThreshold + 1);
        final AtomicInteger cursor = new AtomicInteger();

        boolean isQuiet(long now) {
            for (int i = 0; i < thrashThreshold; i++) {
                long value = arr.get(i);
                if (now - value < perMilliseconds + (perMilliseconds / 2)) {
                    return false;
                }
            }
            return true;
        }

        boolean update(long now) {
            long newerThan = now - perMilliseconds;
            int buildsInPrecedingInterval = 0;
            for (int i = 0; i < thrashThreshold; i++) {
                long when = arr.get(i);
                if (when > newerThan) {
                    buildsInPrecedingInterval++;
                }
            }
            arr.set(cursor.getAndUpdate(oldValue -> {
                if (oldValue + 1 > thrashThreshold) {
                    oldValue = 0;
                } else {
                    return oldValue + 1;
                }
                return oldValue;
            }), now);
            return buildsInPrecedingInterval >= perMilliseconds;
        }
    }
}
