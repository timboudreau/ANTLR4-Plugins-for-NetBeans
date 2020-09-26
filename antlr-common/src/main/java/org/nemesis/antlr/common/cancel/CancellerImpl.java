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
package org.nemesis.antlr.common.cancel;

import static org.nemesis.antlr.common.cancel.CancelledState.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * General-purpose cancellation support, with the ability to abort activity from
 * another thread.
 *
 * @author Tim Boudreau
 */
final class CancellerImpl implements BooleanSupplier, Supplier<CancelledState>, Canceller {

    private static final ThreadLocal<CancellerImpl> current = new ThreadLocal<>();
    private static final AtomicIntegerFieldUpdater<CancellerImpl> updater
            = AtomicIntegerFieldUpdater.newUpdater(CancellerImpl.class, "state");
    static final Map<Thread, CancellerImpl> byThread = new ConcurrentHashMap<>(16, 0.125F);
    private volatile int state;
    private volatile boolean cancellationDetected;

    private CancellerImpl() {

    }

    static CancellerImpl current() {
        return current.get();
    }

    static CancellerImpl create() {
        return new CancellerImpl();
    }

    static CancelledState runInCurrentThread(Consumer<? super CancellerImpl> c) {
        CancellerImpl curr = current.get();
        if (curr == null) {
            Thread t = Thread.currentThread();
            curr = new CancellerImpl();
            return curr.run(c);
        } else {
            return curr.reuse(c);
        }
    }

    public CancelledState run(Consumer<? super CancellerImpl> c) {
        Thread t = Thread.currentThread();
        CancellerImpl old = byThread.put(t, this);
        if (old != null && old != this) {
            CancelledState oldState = old.get();
            if (!oldState.isCancelledState()) {
                return old.reuse(c);
            }
        }
        current.set(this);
        try {
            c.accept(this);
        } finally {
            onFinished();
            current.remove();
            byThread.remove(t);
        }
        return get();
    }

    /**
     * Get the internal state (see int constants on CancelledState)
     *
     * @return The current state
     */
    private int state() {
        return updater.get(this);
    }

    /**
     * Determine if this canceller was cancelled <i>and if something called
     * getAsBoolean() after it was cancelled and detected the cancelled
     * state</i>.
     *
     * @return whether or not cancellation was detected
     */
    public boolean wasCancellationDetected() {
        return cancellationDetected;
    }

    /**
     * Returns true if cancelled.
     *
     * @return true if the activity was cancelled
     */
    @Override
    public boolean getAsBoolean() {
        int st = state();
        switch (st) {
            case STATE_CANCELLED:
                updater.lazySet(this, STATE_CANCELLED_AND_DETECTED);
            // fallthrough
            case STATE_CANCELLED_AND_DETECTED:
            case STATE_ALREADY_CANCELLED:
                return true;
            default:
                return false;
        }
    }

    private CancelledState onFinished() {
        int newState = updater.updateAndGet(this, oldState -> {
            switch (oldState) {
                case STATE_NOT_CANCELLED:
                    return STATE_COMPLETED;
                default:
                    return oldState;
            }
        });
        return CancelledState.forState(newState);
    }

    /**
     * Cancel the activity.
     *
     * @return The result of attempting to cancel
     */
    public CancelledState cancel() {
        int prevState = updater.getAndUpdate(this, oldState -> {
            switch (oldState) {
                case 0:
                    return 1;
                default:
                    return oldState;
            }
        });
        switch (prevState) {
            case STATE_NOT_CANCELLED:
                return CancelledState.CANCELLED;
            case STATE_CANCELLED:
            case STATE_CANCELLED_AND_DETECTED:
                return CancelledState.ALREADY_CANCELLED;
            default:
                return CancelledState.forState(prevState);
        }
    }

    /**
     * Get the current state of this canceller.
     *
     * @return The cancelled state
     */
    @Override
    public CancelledState get() {
        return CancelledState.forState(state());
    }

    /**
     * Test if a canceller is present on the current thread and was cancelled;
     * wherever possible, prefer to pass an instance of CancellerImpl, as this
     * call involves a lookup in a ConcurrentHashMap which will be considerably
     * slower than testing an int.
     *
     * @return True if cancelled
     */
    public static boolean isCancelled() {
        CancellerImpl ownedByCurrentThread = byThread.get(Thread.currentThread());
        return ownedByCurrentThread == null ? false : ownedByCurrentThread.getAsBoolean();
    }

    @Override
    public String toString() {
        return get().name();
    }

    /**
     * Cancel any running activity on the passed thread - this is intended for
     * use by watchdog timers and similar that may need to cancel activity
     * anywhere in the VM if something goes amok.
     *
     * @param thread The thread to cancel activity on
     * @return The result of cancelling on that thread
     */
    static CancelledState cancelActivityOn(Thread thread) {
        CancellerImpl canc = byThread.get(thread);
        if (canc != null) {
            return canc.cancel();
        }
        return CancelledState.NOTHING_TO_CANCEL;
    }

    /**
     * Cancel all work on all threads; should only be used in a shutdown hook.
     *
     * @return A map of all threads where a canceller was found, and the result
     * of calling cancel() on it
     */
    static Map<Thread, CancelledState> cancelAll() {
        Map<Thread, CancelledState> result = new HashMap<>();
        for (Map.Entry<Thread, CancellerImpl> e : byThread.entrySet()) {
            result.put(e.getKey(), e.getValue().cancel());
        }
        return result;
    }

    private CancelledState reuse(Consumer<? super CancellerImpl> c) {
        int newState = updater.updateAndGet(this, old -> {
            switch (old) {
                case STATE_NOT_CANCELLED:
                case STATE_COMPLETED:
                    return 0;
                default:
                    return old;
            }
        });
        switch (newState) {
            case STATE_NOT_CANCELLED:
                Thread t = Thread.currentThread();
                try {
                    byThread.put(t, this);
                    c.accept(this);
                } finally {
                    byThread.remove(t);
                }
                return get();
            default:
                return CancelledState.forState(newState);
        }
    }
}
