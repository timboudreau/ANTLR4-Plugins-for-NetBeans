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

import com.mastfrog.util.collections.AtomicLinkedQueue;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.preconditions.InvalidArgumentException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A weak reference which only becomes weak some time after a post-creation/post-last-use
 * delay expires; calls to <code>get()</code> reset the delay. Note that the timing
 * of when exactly the reference becomes weak is inexact, but will be no <i>less</i> than
 * the requested delay.
 *
 * @author Tim Boudreau
 */
final class TimedWeakReferenceImpl<T> extends TimedWeakReference<T> implements Runnable {

    // Use an atomic, lockless, linked data structure to manage instances
    // that are waiting to have their strong references removed
    private static final AtomicLinkedQueue<TimedWeakReferenceImpl<?>> INSTANCES
            = new AtomicLinkedQueue<>();
    /**
     * The initial delay after startup at which point the first attempt
     * at converting references to weak references should be made.
     */
    private static final int INITIAL_DELAY = 90000;
    /**
     * The time at which this instance becomes expired.
     */
    private volatile long expiryTimeMillis;
    /**
     * The expiry delay.
     */
    private final int delayMillis;
    /**
     * The initial strong reference; note this need not be volatile - the only thing
     * that could be affected by an out-of-order read on it is the garbage
     * collector, and at worst reclamation is delayed by one GC cycle.
     */
    private T strong;

    /**
     * Create a new TimedWeakReference with {@link DEFAULT_DELAY#DEFAULT_DELAY_MILLIS} as its
     * timeout to become a weak reference.
     *
     * @param referent The referenced object
     */
    @SuppressWarnings( "LeakingThisInConstructor" )
    TimedWeakReferenceImpl( T referent ) {
        super( referent );
        strong = referent;
        expiryTimeMillis = System.currentTimeMillis()
                           + DEFAULT_DELAY_MILLIS;
        this.delayMillis = DEFAULT_DELAY_MILLIS;
        enqueue( this );
    }

    /**
     * Create a new TimedWeakReference with the passed delay as its timeout
     * to become a weak reference.
     *
     * @param referent The referenced object
     * @param delay    The delay in <code>unit</code> units
     * @param unit     The time unit
     */
    TimedWeakReferenceImpl( T referent, int delay, TimeUnit unit ) throws InvalidArgumentException {
        this( referent, ( int ) unit.toMillis( Checks.greaterThanZero( "delay", delay ) ) );
    }

    /**
     * Create a new TimedWeakReference with the passed delay as its timeout
     * to become a weak reference. Delays below {@link MIN_DELAY_MILLIS} will
     * be set to {@link MIN_DELAY_MILLIS}.
     *
     * @param referent    The referenced object
     * @param delayMillis The delay in milliseconds
     * @param throws      InvalidArgumentException if the argument is less than or equal to zero
     */
    @SuppressWarnings( "LeakingThisInConstructor" )
    TimedWeakReferenceImpl( T referent, int delayMillis ) throws InvalidArgumentException {
        super( referent );
        strong = referent;
        expiryTimeMillis = System.currentTimeMillis()
                           + Math.max( MIN_DELAY_MILLIS,
                                       Checks.greaterThanZero( "delay", delayMillis ) );
        this.delayMillis = delayMillis;
        enqueue( this );
    }

    /**
     * Get the object if it is non-null and passes the test of the pssed
     * predicate; does not update the expiration delay or return this
     * reference to strong status unless the predicate returns true,
     * so objects which are obsolete for some reason other than age
     * do not get garbage collected even later due to testing for
     * obsolescence.
     *
     * @param pred A predicate
     *
     * @return The referenced object, if it still exists and if it
     *         passes the predicate's test
     */
    @Override
    public T getIf( Predicate<T> pred ) {
        T obj = strong;
        if ( obj == null ) {
            obj = super.get();
        }
        if ( obj != null ) {
            if ( pred.test( obj ) ) {
                strong = obj;
                touch();
                return obj;
            }
        }
        return null;
    }

    private void touch() {
        expiryTimeMillis = System.currentTimeMillis() + delayMillis;
    }

    private boolean isExpired() {
        return System.currentTimeMillis() > expiryTimeMillis;
    }

    /**
     * Force this object to weak reference status and expire it; note that
     * a subsequent call to <code>get()</code> can revive it.
     */
    @Override
    public void discard() {
        // We could remove from INSTANCES, but removals are expensive
        // in AtomicLinkedQueue, and the timer will get to it soon
        // enough
        strong = null;
        expiryTimeMillis = 0;
    }

    @Override
    public T get() {
        T st = strong;
        boolean wasWeak;
        if ( st != null ) {
            touch();
            // another thread may have expired us while we're in here
            strong = st;
            maybeEnqueue( this );
            return st;
        } else {
            wasWeak = true;
        }
        T result = super.get();
        if ( result != null ) {
            touch();
            strong = result;
            if ( wasWeak ) {
                maybeEnqueue( this );
            }
        }
        return result;
    }

    void reallyBecomeWeak() {
        strong = null;
    }

    private boolean isStrong() {
        return strong != null;
    }

    // XXX could do this with a RequestProcessor.Task and avoid a dedicated
    // thread
    private static final Timer timer = new Timer( "timed-weak-refs", true );
    private static final CleanupTask TASK = new CleanupTask();

    static {
        timer.scheduleAtFixedRate( TASK, INITIAL_DELAY, DEFAULT_DELAY_MILLIS + ( DEFAULT_DELAY_MILLIS / 2 ) );
        ShutdownHooks.addRunnable( () -> {
            TASK.run();
        } );
    }

    /**
     * Runs when the reference has been reclaimed by the garbage collector.
     */
    @Override
    public void run() {
        // ensure we're removed - the referent is gone
        INSTANCES.remove( this );
    }

    private static void maybeEnqueue( TimedWeakReferenceImpl ref ) {
        while ( !INSTANCES.contains( ref ) && !TASK.isShutdown && ref.isStrong() ) {
            enqueue( ref );
        }
    }

    private static void enqueue( TimedWeakReferenceImpl ref ) {
        if ( !TASK.isShutdown ) {
            INSTANCES.add( ref );
        }
    }

    private static void makeExpiredReferencesWeak() {
        List<TimedWeakReferenceImpl<?>> items = new ArrayList<>( INSTANCES );
        try {
            for ( Iterator<TimedWeakReferenceImpl<?>> it = items.iterator(); it.hasNext(); ) {
                TimedWeakReferenceImpl<?> t = it.next();
                if ( t.isExpired() ) {
                    t.reallyBecomeWeak();
                } else {
                    it.remove();
                }
            }
            INSTANCES.removeAll( items );
        } catch ( Exception | Error e ) {
            Logger.getLogger( TimedWeakReferenceImpl.class.getName() ).log(
                    Level.SEVERE, "Exception removing " + items, e );
        }
    }

    static final class CleanupTask extends TimerTask {
        boolean isShutdown;

        @Override
        public void run() {
            if ( ShutdownHooks.isShuttingDown() ) {
                isShutdown = true;
                while ( !INSTANCES.isEmpty() ) {
                    INSTANCES.drain( item -> {
                        item.discard();
                    } );
                }
                timer.cancel();
            }
            makeExpiredReferencesWeak();
        }
    }
}
