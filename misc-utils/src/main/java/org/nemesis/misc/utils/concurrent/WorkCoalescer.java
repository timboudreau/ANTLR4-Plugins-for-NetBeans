/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.misc.utils.concurrent;

import com.mastfrog.util.collections.AtomicLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Ensures that when multiple threads are enqueued to do the same work, only
 * one thread actually does it and the rest receive the result.
 *
 * @author Tim Boudreau
 */
public class WorkCoalescer<T> {

    final Mutrix mutricia = new Mutrix();

    private final int spins;
    private final int spinSleep;

    public WorkCoalescer() {
        this(20, 5);
    }

    public WorkCoalescer(int spins, int spinSleep) {
        this.spins = spins;
        this.spinSleep = spinSleep;
    }

    @SuppressWarnings("CallToThreadYield")
    public T coalesceComputation(Supplier<T> resultComputation, Consumer<T> c, AtomicReference<T> ref) throws InterruptedException {
        boolean locked = false;
        try {
            // Mmm, mutricious1
            locked = mutricia.lock();
            T result;
            if (locked) {
                result = resultComputation.get();
                ref.set(result);
                mutricia.yieldForWaiters();
                result = ref.get();
                c.accept(result);
                return result;
            } else {
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
                result = ref.get();
                if (result == null) {
                    ref.set(result = resultComputation.get());
                }
                c.accept(result);
                return result;
            }
        } finally {
            if (locked) {
                mutricia.unlock();
            }
        }
    }

    public void disable() {
        mutricia.disable();;
    }

    public boolean isDisabled() {
        return mutricia.isDisabled();
    }

    public void enable() {
        mutricia.enable();
    }

    /**
     * Kind of like a mutex, but not.  Allows at most one thread at a time to lock it;
     * a second call to lock from a different thread  will block and return false.
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
//            assert !waiters.fi
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
        public boolean unlock() {
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
                LockSupport.parkNanos(300000);
                while (!waiters.isEmpty()) {
                    waiters.drainTo(sink);
                }
                if (up.compareAndSet(this, PARTIALLY_LOCKED, UNLOCKED)) {
                    sink.drain(this::unparkThread);
                }
            }
            return result;
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
                    System.out.println("rdrain " + waiters.size());
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
            StringBuilder sb = new StringBuilder("Mutrix(");
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
