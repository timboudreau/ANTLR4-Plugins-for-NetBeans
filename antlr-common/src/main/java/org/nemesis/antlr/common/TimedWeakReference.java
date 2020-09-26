/*
 * Copyright 2020 Mastfrog Technologies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.common;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.openide.util.Utilities;

/**
 * A weak reference which only becomes weak some time after a
 * post-creation/post-last-use delay expires; calls to <code>get()</code> reset
 * the delay. Note that the timing of when exactly the reference becomes weak is
 * inexact, but will be no <i>less</i> than the requested delay subsequent
 * to creation or the most recent call to <code>get()</code>
 *
 * @author Tim Boudreau
 */
public abstract class TimedWeakReference<T> extends WeakReference<T> {

    /**
     * The default delay used by the single-argument constructor.
     */
    public static final int DEFAULT_DELAY_MILLIS = 20000;
    /**
     * The minimum delay allowed to avoid thrashing the queue.
     */
    public static final int MIN_DELAY_MILLIS = 125;

    /**
     * Get the object if it is non-null and passes the test of the pssed
     * predicate; does not update the expiration delay or return this reference
     * to strong status unless the predicate returns true, so objects which are
     * obsolete for some reason other than age do not get garbage collected even
     * later due to testing for obsolescence.
     *
     * @param pred A predicate
     *
     * @return The referenced object, if it still exists and if it passes the
     * predicate's test
     */
    public abstract T getIf(Predicate<T> pred);

    /**
     * Force this object to weak reference status and expire it (note that a
     * subsequent call to <code>get()</code> can revive it and restore it to
     * strong reference status).
     */
    public abstract void discard();

    /**
     * Create a new TimedWeakReference with
     * {@link DEFAULT_DELAY#DEFAULT_DELAY_MILLIS} as its timeout to become a
     * weak reference.
     *
     * @param referent The referenced object
     */
    public static <T> TimedWeakReference<T> create(T referent) {
        return new TimedWeakReferenceImpl<>(notNull("referent", referent));
    }

    /**
     * Create a new TimedWeakReference with the passed delay as its timeout to
     * become a weak reference.
     *
     * @param referent The referenced object
     * @param delay The delay in <code>unit</code> units
     * @param unit The time unit
     */
    public static <T> TimedWeakReference<T> create(T referent, int delayMillis) {
        return new TimedWeakReferenceImpl<>(notNull("referent", referent), delayMillis);
    }

    /**
     * Create a new TimedWeakReference with the passed delay as its timeout to
     * become a weak reference. Delays below {@link MIN_DELAY_MILLIS} will be
     * set to {@link MIN_DELAY_MILLIS}.
     *
     * @param referent The referenced object
     * @param delayMillis The delay in milliseconds
     * @param throws InvalidArgumentException if the argument is less than or
     * equal to zero
     */
    public static <T> TimedWeakReference<T> create(T referent, int delayMillis, TimeUnit unit) {
        return new TimedWeakReferenceImpl<>(notNull("referent", referent), delayMillis, unit);
    }

    /**
     * Create a new TimedWeakReference.
     *
     * @param referent The referenced object
     */
    TimedWeakReference(T referent) {
        super(referent, Utilities.activeReferenceQueue());
    }

}
