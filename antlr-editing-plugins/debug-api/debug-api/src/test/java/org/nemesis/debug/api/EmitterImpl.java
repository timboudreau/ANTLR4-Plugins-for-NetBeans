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
package org.nemesis.debug.api;

import com.mastfrog.util.thread.OneThreadLatch;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.nemesis.debug.spi.Emitter;

/**
 *
 * @author Tim Boudreau
 */
class EmitterImpl implements Emitter {

    private final OneThreadLatch latch = new OneThreadLatch();
    final long start = System.currentTimeMillis();
    final List<EmitItem> received = new CopyOnWriteArrayList<>();
    int d = -1;
    private final AtomicInteger rev = new AtomicInteger();

    Waiter waiter() {
        return new Waiter();
    }

    class Waiter {

        int lastWait = rev.get();

        void await() throws InterruptedException {
            int ct = rev.get();
            if (ct > lastWait) {
                lastWait = ct;
                return;
            }
            EmitterImpl.this.await();
            lastWait = rev.get();
        }

        void await(int count) throws InterruptedException {
            int ct = rev.get();
            if (ct >= lastWait + count) {
                lastWait = ct;
                System.out.println("wait exit " + ct);
                return;
            }
            int curr;
            long then = System.currentTimeMillis();
            while ((curr = rev.get()) < lastWait + count) {
                EmitterImpl.this.await();
                if (System.currentTimeMillis() - then > 5000) {
                    break;
                }
            }
            lastWait = curr;
            System.out.println("wait exit b " + lastWait);
        }
    }

    void await() throws InterruptedException {
        latch.await(5, TimeUnit.SECONDS);
    }

    @Override
    public void enterContext(int depth, long globalOrder, long timeStamp, String threadName, long threadId, boolean isReentry, String ownerType, String ownerToString, String action, Supplier<String> details, long creationThreadId, String creationThreadString) {
        d = depth;
        received.add(new EmitItem(d, EmitItemType.ENTER, ownerToString + ":" + action, threadName));
    }

    @Override
    public void exitContext(int depth) {
        received.add(new EmitItem(depth, EmitItemType.EXIT, "" + depth));
        d = depth;
        if (depth == 0) {
            int newRev = rev.incrementAndGet();
            System.out.println("rev " + newRev);
            latch.releaseAll();
        }
    }

    @Override
    public void thrown(int depth, long globalOrder, long timestamp, String threadName, long threadId, Throwable thrown, Supplier<String> stackTrace) {
        received.add(new EmitItem(d, EmitItemType.THROWN, thrown.getClass().getSimpleName(), threadName));
    }

    @Override
    public void message(int depth, long globalOrder, long timestamp, String threadName, long threadId, String heading, Supplier<String> msg) {
        received.add(new EmitItem(d, EmitItemType.MESSAGE, msg.get(), threadName));
    }

    @Override
    public void successOrFailure(int depth, long globalOrder, long timestamp, String threadName, long threadId, String heading, boolean success, Supplier<String> msg) {
        if (success) {
            received.add(new EmitItem(d, EmitItemType.SUCCESS, msg.get(), threadName));
        } else {
            received.add(new EmitItem(d, EmitItemType.FAILURE, msg.get(), threadName));
        }
    }

}
