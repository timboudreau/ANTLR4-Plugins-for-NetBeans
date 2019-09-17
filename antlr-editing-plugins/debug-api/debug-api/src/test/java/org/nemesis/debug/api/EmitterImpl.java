/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
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
