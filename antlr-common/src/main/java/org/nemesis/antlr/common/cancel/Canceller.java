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

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * General-purpose cancellation support, with the ability to abort activity from
 * another thread.
 *
 * @author Tim Boudreau
 */
public interface Canceller extends BooleanSupplier, Supplier<CancelledState> {

    /**
     * Cancel the work.
     *
     * @return The state immediately after performing cancellation
     */
    CancelledState cancel();

    /**
     * Run something
     *
     * @param supplier
     * @return
     */
    static CancelledState runInCurrentThread(Consumer<? super BooleanSupplier> supplier) {
        return CancellerImpl.runInCurrentThread(supplier::accept);
    }

    static CancelledState runWithState(Consumer<? super Supplier<CancelledState>> supplier) {
        return CancellerImpl.runInCurrentThread(supplier::accept);
    }

    static Canceller create() {
        return CancellerImpl.create();
    }

    static Canceller getOrCreate() {
        Canceller result = CancellerImpl.current();
        if (result == null) {
            return create();
        }
        return result;
    }

    /**
     * Create a Canceller, and invoke the passed consumer with it in the
     * execution context of the passed executor service; cancelling will also
     * cancel the future returned by submitting the job.
     *
     * @param exe A thread pool
     * @param c The code that will run, periodically checking the canceller
     * @return A canceller
     */
    static Canceller wrap(ExecutorService exe, Consumer<? super BooleanSupplier> c) {
        CancellerImpl result = CancellerImpl.create();
        Future<?> fut = exe.submit(() -> {
            c.accept(result);
        });
        return new FutureWrapCanceller(result, fut);
    }

    /**
     * Cancel all work on all threads; should only be used in a shutdown hook.
     *
     * @return A map of all threads where a canceller was found, and the result
     * of calling cancel() on it
     */
    static Map<Thread, CancelledState> cancelAll() {
        return CancellerImpl.cancelAll();
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
        return CancellerImpl.cancelActivityOn(thread);
    }

    /**
     * Test if a canceller is present on the current thread and was cancelled;
     * wherever possible, prefer to pass an instance of CancellerImpl, as this
     * call involves a lookup in a ConcurrentHashMap which will be considerably
     * slower than testing an int.
     *
     * @return True if cancelled
     */
    static boolean isCancelled() {
        return CancellerImpl.isCancelled();
    }
}
