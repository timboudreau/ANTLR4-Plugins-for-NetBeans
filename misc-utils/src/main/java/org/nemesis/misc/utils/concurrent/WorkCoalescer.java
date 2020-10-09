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
package org.nemesis.misc.utils.concurrent;

import com.mastfrog.util.collections.AtomicLinkedQueue;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Ensures that when multiple threads are enqueued to do the same work, only one
 * thread actually does it and the rest receive the result. Basically the
 * opposite of a mutex - a mutex sequences work so each thread does it
 * exclusively; this ensures that while only one thread does the work, the
 * computation is done once and all threads trying to do the same work
 * concurrently receive the same result.
 *
 * @author Tim Boudreau
 */
public final class WorkCoalescer<T> {

    final Mutrix mutricia;

    private final int spins;
    private final int spinSleep;
    private volatile long lastUse = System.currentTimeMillis();
    private volatile int calls;
    private volatile int coalesces;
    private static final DecimalFormat FMT = new DecimalFormat("##0.##");

    public WorkCoalescer(String name) {
        this(name, 20, 5);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    public WorkCoalescer(String name, int spins, int spinSleep) {
        this.spins = spins;
        this.spinSleep = spinSleep;
        mutricia = new Mutrix(name);
        CoaWatchdog.register(this);
    }

    public String toString() {
        return "coa(" + mutricia.name
                + " coalescence " + FMT.format(coalescence()) + "% " + " idle "
                + idleMillis() + "ms)";
    }

    /**
     * Get a fraction representing the total number of calls divided by the
     * number of calls which did not lock but used a result being concurrently
     * computed by another thread.
     *
     * @return A fraction, or -1 if this instance has never been used
     */
    public float coalescence() {
        float c = calls;
        return c == 0 ? -1 : (float) coalesces / c;
    }

    /**
     * Get the number of milliseconds this coalescer has been idle;
     *
     * @return A number of milliseconds
     */
    public long idleMillis() {
        return System.currentTimeMillis() - lastUse;
    }

    /**
     * Variant of the three-argument overload of this method which does not take
     * a Consumer.
     *
     * @param resultComputation The computation to run
     * @param ref A reference which will receive the result of the computation,
     * either because it was already concurrently being computed on another
     * thread when this one entered, or by computing it here
     * @return The result
     * @throws InterruptedException
     * @throws
     * org.nemesis.misc.utils.concurrent.WorkCoalescer.ComputationFailedException
     */
    @SuppressWarnings("CallToThreadYield")
    public T coalesceComputation(Supplier<T> resultComputation, AtomicReference<T> ref) throws InterruptedException, ComputationFailedException {
        return coalesceComputation(resultComputation, null, ref);
    }

    /**
     * Enter the computation, passing a supplier that will produce it, an
     * optional consumer that will consume it, and an AtomicReference to store
     * it in. The caller must ensure that the same AtomicReference is passed to
     * every call - only the caller can know when it is safe to <i>clear</i> the
     * reference, and this object is intended to be long-lived - so the only
     * choice this class would have would be to leak the reference to the last
     * result until a new call that creates another one; since this is intended
     * for use with parse trees and wrappers for them, which can be quite large,
     * that is a bad idea.
     *
     * @param resultComputation A thing which computes the result
     * @param c An optional consumer
     * @param ref A reference that is used to share the result with other
     * threads
     * @return The result
     * @throws InterruptedException If the thread is interrupted
     * @throws
     * org.nemesis.misc.utils.concurrent.WorkCoalescer.ComputationFailedException
     * if the work threw an exception; use <code>isOriginatingThread()</code> to
     * know if the failure occurred on the current thread
     */
    @SuppressWarnings("CallToThreadYield")
    public T coalesceComputation(Supplier<T> resultComputation, Consumer<T> c, AtomicReference<T> ref) throws InterruptedException, ComputationFailedException {
        lastUse = System.currentTimeMillis();
        boolean locked = false;
        calls++;
        try {
            // Mmm, mutricious1
            locked = mutricia.lock();
            if (locked) {
                // I know, I know, thread yield, don't rely on the thread scheduler for correctness,
                // yadda yadda.  The POINT here is to allow as many threads as are getting ready to
                // do the work to queue up here, so the work is done once and all get the
                // result
                Thread.yield();
                return mutricia.run(resultComputation, c, ref);
            } else {
                coalesces++;
                T result;
                // A very small busywait here, and I mean micro - we are
                // not waiting for the computation to complete - the call to
                // lock() had us sleep through that.  But we can race with
                // the call to ref.set().
                for (int i = 0; (result = ref.get()) == null && i < spins; i++) {
                    if (spinSleep <= 0) {
                        Thread.yield();
                    } else {
                        LockSupport.park(spinSleep);
                    }
                }
                if (result == null) {
                    result = ref.get();
                }
                if (result == null) {
                    ref.set(result = resultComputation.get());
                }
                if (c != null) {
                    c.accept(result);
                }
                return result;
            }
        } finally {
            if (locked) {
                mutricia.unlock();
            } else {
                mutricia.rethrow();
            }
        }
    }

    public void disable() {
        mutricia.disable();
    }

    public boolean isDisabled() {
        return mutricia.isDisabled();
    }

    public void enable() {
        mutricia.enable();
    }

    public static class ComputationFailedException extends Exception {

        private final long threadId;

        public ComputationFailedException(Thread thread, String message, Throwable cause) {
            super(message + " on " + thread.getName(), cause);
            threadId = thread.getId();
        }

        public ComputationFailedException(String message, Throwable cause) {
            this(Thread.currentThread(), message, cause);
        }

        public boolean isOriginatingThread() {
            return Thread.currentThread().getId() == threadId;
        }
    }

    boolean hasWaiters() {
        return !mutricia.waiters.isEmpty();
    }

    void shake() {
        if (hasWaiters() && idleMillis() > 30000) {
            System.out.println("Release waiters on " + mutricia);
            mutricia.releaseWaiters();
        }
    }

    /**
     * Kind of like a mutex, but not. Allows at most one thread at a time to
     * lock it; a second call to lock from a different thread will block and
     * return false.
     */
    static class Mutrix {

        private volatile int state = UNLOCKED;
        private final AtomicLinkedQueue<Thread> waiters = new AtomicLinkedQueue<>();
        private final AtomicLinkedQueue<Thread> sink = new AtomicLinkedQueue<>();
        private final AtomicIntegerFieldUpdater<Mutrix> up = AtomicIntegerFieldUpdater.newUpdater(
                Mutrix.class, "state");
        private static final int DISABLED = -1;
        private static final int UNLOCKED = 0;
        private static final int PARTIALLY_LOCKED = 1;
        private static final int FULLY_LOCKED = 2;
        private static final int DISPATCHING = 3;
        private volatile ComputationFailedException failure;
        private final String name;

        Mutrix(String name) {
            this.name = name;
        }

        <T> T run(Supplier<T> supp, Consumer<T> cons, AtomicReference<? super T> ref) throws ComputationFailedException {
            failure = null;
            T obj = null;
            try {
                obj = supp.get();
                ref.set(obj);
            } catch (Exception | Error ex) {
                ComputationFailedException f = failure = new ComputationFailedException(name, ex);
                throw f;
            } finally {
                yieldForWaiters();
            }
            if (cons != null) {
                cons.accept(obj);
            }
            return obj;
        }

        void yieldForWaiters() {
            if (state != 0) {
                Thread.yield();;
            }
        }

        void disable() {
            up.set(this, DISABLED);
            releaseWaiters();
        }

        void enable() {
            up.set(this, UNLOCKED);
        }

        boolean isDisabled() {
            return up.get(this) == DISABLED;
        }

        /**
         * If the current thread is the first to enter, returns true
         * immediately; if another thread has already locked, parks the calling
         * thread until that thread unlocks, and returns false.
         *
         * @return True if this thread is the first to enter
         */
        public boolean lock() {
            boolean result = up.compareAndSet(this, UNLOCKED, FULLY_LOCKED);
            if (!result) {
                if (state == DISABLED) {
                    releaseWaiters();
                    return true;
                }
                Thread t = Thread.currentThread();
                waiters.add(t);
                LockSupport.park(this);
            }
            return result;
        }

        /**
         * Unlock; should be called only by the thread that locked this object,
         * when a call to <code>lock()</code> returned true.
         *
         * @return True if unlocking happened
         */
        public boolean unlock() throws ComputationFailedException {
            boolean result = up.compareAndSet(this, FULLY_LOCKED, PARTIALLY_LOCKED);
            if (result) {
                waiters.removeByIdentity(Thread.currentThread());
                waiters.drainTo(sink);
                // A 0.3 millisecond concession to the practicalities of the universe:
                // In the case that the period we are locked is the time it takes to, say,
                // increment one integer, we can acquire a waiter and park it, but
                // unpark will not see the thread as parked and unpark it, so we
                // will leave behind stalled threads.  This 0.3ms delay solves
                // that.

                // XXX mysteriously, this call never returns when on the EDT
//                LockSupport.parkNanos(300000);
                Thread.yield();
                while (!waiters.isEmpty()) {
                    waiters.drainTo(sink);
                }
                if (up.compareAndSet(this, PARTIALLY_LOCKED, UNLOCKED)) {
                    sink.drain(this::unparkThread);
                }
            }
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        public void rethrow() throws ComputationFailedException {
            ComputationFailedException f = failure;
            if (f != null) {
                throw f;
            }
        }

        private void unparkThread(Thread th) {
            Thread.State tstate = th.getState();
            switch (tstate) {
                case BLOCKED:
                case WAITING:
                    LockSupport.unpark(th);
                    break;
            }

        }

        @SuppressWarnings("empty-statement")
        void releaseWaiters() {
            int prevState;
            for (prevState = up.get(this);; prevState = up.get(this)) {
                if (prevState != UNLOCKED) {
                    return;
                } else if (up.compareAndSet(this, prevState, DISPATCHING)) {
                    break;
                } else {
                    return;
                }
            }
            try {
                while (!waiters.isEmpty()) {
                    waiters.drain(this::unparkThread);
                }
            } finally {
                up.set(this, prevState);
            }
        }

        int state() {
            return up.get(this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Mutrix(")
                    .append(name).append(' ');
            int state = this.state;
            switch (state) {
                case DISABLED:
                    sb.append("DISABLED ");
                case UNLOCKED:
                    sb.append("UNLOCKED ");
                    break;
                case PARTIALLY_LOCKED:
                    sb.append("PARTIALLY_LOCKED ");
                    break;
                case FULLY_LOCKED:
                    sb.append("FULLY_LOCKED ");
                    break;
                case DISPATCHING:
                    sb.append("DISPATCHING ");
                    break;
                default:
                    sb.append("UNKNOWN-").append(state);
                    break;
            }
            sb.append("with ").append(waiters.size()).append(" waiters: ");
            waiters.forEach(th -> {
                sb.append(th.getName()).append(' ');
            });
            return sb.append(')').toString();
        }
    }
}
