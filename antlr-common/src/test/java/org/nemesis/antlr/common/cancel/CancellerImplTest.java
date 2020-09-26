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

import com.mastfrog.util.preconditions.Exceptions;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import static org.nemesis.antlr.common.cancel.CancelledState.*;

/**
 *
 * @author Tim Boudreau
 */
public class CancellerImplTest {

    @Test
    public void testSimpleCancellationAndReuse() {
        CancelledState outerStatus = CancellerImpl.runInCurrentThread(c -> {
            assertState(c, NOT_CANCELLED, "Initial state should be NOT_CANCELLED");
            CancelledState innerStatus = CancellerImpl.runInCurrentThread(c1 -> {
                assertFalse(c.getAsBoolean(), "Returned true from getAsBoolean() before any call to cancel");
                assertSame(c, c1, "Got different cancellers on the same thread");
                assertSame(CANCELLED, c1.cancel(), "Initial call to cancel() should return CANCELLED");
                assertState(c1, CANCELLED, "After call to cancel(), state should be CANCELLED");
                assertTrue(c.getAsBoolean(), "getAsBoolean() should return true after call to cancel()");
                assertSame(ALREADY_CANCELLED, c1.cancel(), "Subsequent call to cancel() should return ALREADY_CANCELLED");
                assertTrue(c.getAsBoolean(), "getAsBoolean() should return true after call to cancel()");
                assertState(c, CANCELLED_AND_CANCELLATION_DETECTED, "After call to getAsBoolean() in the cancelled state, state should be CANCELLED_AND_CANCELLATION_DETECTED");
                boolean[] wasRun = new boolean[1];
                CancelledState reuseStatus = CancellerImpl.runInCurrentThread(c2 -> {
                    fail("Reentry on a thread with an active cancelled canceller should never be invoked");
                });
                assertSame(CANCELLED_AND_CANCELLATION_DETECTED, reuseStatus);
            });
            assertSame(CANCELLED_AND_CANCELLATION_DETECTED, innerStatus);
        });
        assertSame(CANCELLED_AND_CANCELLATION_DETECTED, outerStatus);
        assertSame(NOTHING_TO_CANCEL, CancellerImpl.cancelActivityOn(Thread.currentThread()));
    }

    @Test
    public void testReentry() {
        CancelledState outerStatus = CancellerImpl.runInCurrentThread(c -> {
            CancelledState innerStatusA = CancellerImpl.runInCurrentThread(c1a -> {
                assertSame(c, c1a, "Got wrong canceller");
            });
            assertSame(NOT_CANCELLED, innerStatusA);
            CancelledState innerStatusB = CancellerImpl.runInCurrentThread(c1b -> {
                assertSame(c, c1b, "Got wrong canceller");
                assertState(c1b, NOT_CANCELLED, "State should return to NOT_CANCELLED on reuse");
            });
            assertSame(NOT_CANCELLED, innerStatusB, "State should be COMPLETED after reuse without cancellation");
            assertSame(CANCELLED, c.cancel(), "Cancelling from the completed state should not change the state");
            CancelledState innerStatusC = CancellerImpl.runInCurrentThread(c1c -> {
                fail("Should not run from the cancelled state");
            });
            assertSame(ALREADY_CANCELLED, c.cancel(), "After inner run with cancellation, state should be cancelled");
        });
        assertSame(CANCELLED, outerStatus, "After inner run with cancellation, outer state should be cancelled");
    }

    @Test
    public void testRemote() throws Exception {
        int threadCount = 9;
        R[] rs = new R[threadCount];
        Thread[] threads = new Thread[threadCount];
        CountDownLatch goLatch = new CountDownLatch(threadCount);
        Phaser blockForCancellation = new Phaser(threadCount + 1);
        Phaser postCancel = new Phaser(threadCount + 1);
        CountDownLatch exitLatch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            R r = rs[i] = new R(i, goLatch, blockForCancellation, exitLatch, i % 2 == 0, postCancel);
            threads[i] = r.createAndStartThread();
        }
        goLatch.await(10, TimeUnit.SECONDS);

        CancelledState[] firstCallStatii = new CancelledState[threadCount];
        for (int i = 0; i < threadCount; i++) {
            Thread t = threads[i];
            if (i != threadCount - 2) {
                firstCallStatii[i] = CancellerImpl.cancelActivityOn(t);
            }
        }
        postCancel.arriveAndDeregister();

        CancelledState[] secondCallStatii = new CancelledState[threadCount];
        for (int i = 0; i < threadCount; i++) {
            Thread t = threads[i];
            if (i != threadCount - 3 && i != threadCount - 2) {
                secondCallStatii[i] = CancellerImpl.cancelActivityOn(t);
            }
        }
        blockForCancellation.arriveAndDeregister();
        exitLatch.await(10, TimeUnit.SECONDS);
        for (int i = 0; i < threadCount; i++) {
            threads[i].join(10000);
            rs[i].rethrow();;
        }
        for (int i = 0; i < threadCount; i++) {
            assertSame(NOTHING_TO_CANCEL, CancellerImpl.cancelActivityOn(threads[i]), "With all threads exited, "
                    + "some cancellers were not removed from the thread map: " + CancellerImpl.byThread);
            if (i != threadCount - 2) {
                assertSame(CANCELLED, firstCallStatii[i], "Unexpected first cancel status");
                if (i == threadCount - 3) {
                    assertNull(secondCallStatii[i], "Should not have attempted a second cancel on " + i);
                } else {
                    assertSame(ALREADY_CANCELLED, secondCallStatii[i], "Unexpected second cancel status for " + i);
                }
            } else {
                assertNull(firstCallStatii[i], "Should not have even tried to cancel " + i + " - test bug.");
            }
        }
        for (int i = 0; i < threadCount; i++) {
            R r = rs[i];
            if (i != threadCount - 2) {
                r.assertCancelled();
                assertTrue(r.endState.isCancelledState(), "Expected a cancelled state for " + i + " but got " + r.endState);
            } else {
                assertFalse(r.cancelledStateIfChecked, "Thread " + i + " should not have been cancelled");
                assertEquals(NOT_CANCELLED, r.status, "Status for thread " + i + " should have been NOT_CANCELLED");
                assertSame(COMPLETED, r.endState, "End state should be completed for " + i);
            }
        }
    }

    static class R implements Runnable, Thread.UncaughtExceptionHandler {

        private final int ix;

        private final CountDownLatch mainThreadLatch;

        private final Phaser initialPause;
        private final CountDownLatch exitLatch;
        private volatile boolean cancelledStateIfChecked;
        private volatile CancelledState status;
        private final boolean checkState;
        private volatile Throwable thrown;
        private final Random RND = new Random(3101390);
        private final Phaser postCancel;
        private volatile CancelledState endState;

        public R(int ix, CountDownLatch mainThreadLatch, Phaser initialPause, CountDownLatch exitLatch, boolean checkState, Phaser postCancel) {
            this.ix = ix;
            this.mainThreadLatch = mainThreadLatch;
            this.initialPause = initialPause;
            this.exitLatch = exitLatch;
            this.checkState = checkState;
            this.postCancel = postCancel;
        }

        void assertEndState(CancelledState state) {
            assertSame(state, endState, "Wrong end state " + state + " for "
                    + ix + " - intermediate state was " + status);
        }

        void assertCancelled() {
            if (checkState) {
                assertTrue(cancelledStateIfChecked, ix + " did not get true returned from Cancelled.getAsBoolean()");
                assertSame(CANCELLED_AND_CANCELLATION_DETECTED, status);
            } else {
                assertSame(CANCELLED, status);
            }
        }

        @Override
        public void run() {
            Thread.currentThread().setUncaughtExceptionHandler(this);
            try {
                // Randomly jitter entry
                Thread.sleep(RND.nextInt(120) + (ix * 2));
                 endState = CancellerImpl.runInCurrentThread(c -> {
                    try {
                        mainThreadLatch.countDown();
                        Thread.sleep(20);
                        initialPause.arriveAndAwaitAdvance();
                        Thread.sleep(20);
                        if (checkState) {
                            boolean canc = c.getAsBoolean();
                            cancelledStateIfChecked = canc;
                        }
                        postCancel.arriveAndAwaitAdvance();
                        Thread.sleep(RND.nextInt(120) + (ix * 2));
                        status = c.get();
                    } catch (InterruptedException ex) {
                        uncaughtException(Thread.currentThread(), thrown);
                    }
                });
            } catch (InterruptedException ex) {
                uncaughtException(Thread.currentThread(), ex);
            } finally {
                exitLatch.countDown();
            }
        }

        Thread createAndStartThread() {
            Thread t = new Thread(this, "t-" + ix);
            t.setDaemon(true);
            t.start();
            return t;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            thrown = e;
        }

        void rethrow() {
            Throwable t = thrown;
            if (t != null) {
                Exceptions.chuck(t);
            }
        }

    }

    private static void assertState(CancellerImpl c, CancelledState status, String msg) {
        assertEquals(status, c.get(), msg);
    }

}
