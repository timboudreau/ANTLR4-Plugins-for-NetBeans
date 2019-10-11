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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class TrackablesTest {

    @Test
    public void testTracking() throws InterruptedException {
        int max = 15;
        Cons c = new Cons();

        Trackables.listen(c);

        List<Thing> things = new ArrayList<>(max);
        List<Integer> idHashes = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            Thing t = new Thing("thing-" + i);
            idHashes.add(System.identityHashCode(t));
            Trackables.track(Thing.class, t);
            things.add(t);
        }

        c.assertAlive(new HashSet<>(idHashes));

        List<Integer> replacementCodes = new ArrayList<>((max / 3) + 1);
        Set<Integer> killed = new HashSet<>();

        for (int i = 0; i < max; i += 3) {
            Thing nue = new Thing("thing-new-" + i);
            Trackables.track(Thing.class, nue);
            Thing old = things.set(i, nue);
            killed.add(System.identityHashCode(old));
            replacementCodes.add(System.identityHashCode(nue));
        }

        for (int i = 0; i < 5; i++) {
            System.gc();
            System.runFinalization();
        }
        c.assertAlive(new HashSet<>(replacementCodes));
        c.assertDead(killed);

        killed.clear();
        for (int i = 0; i < max; i += 2) {
            Thing t = things.get(i);
            killed.add(System.identityHashCode(t));
            t.dispose();
        }
        c.assertDead(killed);

        Trackables.unlisten(c);
        boolean xit = Trackables.awaitExit();
        Trackables.track(Thing.class, new Thing("ignoreMe"));
        Trackables.track(Thing.class, new Thing("ignoreMe2"));
        Trackables.track(Thing.class, new Thing("ignoreMe3"));
        c.assertNotNotified();
    }

    static final class Cons implements Consumer<Set<? extends Trackables.TrackingReference<?>>> {

        Set<Integer> aliveHashes = new HashSet<>();
        Set<Integer> deadHashes = new HashSet<>();
        AtomicInteger notifyCount = new AtomicInteger();

        OneThreadLatch latch = new OneThreadLatch();

        void assertAlive(List<Integer> all) throws InterruptedException {
            assertAlive(new HashSet<>(all));
        }

        void assertAlive(Set<Integer> all) throws InterruptedException {
            assertSetHas(false, aliveHashes, all);
        }

        void assertDead(List<Integer> all) throws InterruptedException {
            assertDead(new HashSet<>(all));
        }

        void assertDead(Set<Integer> all) throws InterruptedException {
            assertSetHas(true, deadHashes, all);
        }

        void assertNotNotified() throws InterruptedException {
            int nc = notifyCount.get();
            Thread.sleep(250);
            assertEquals(nc, notifyCount.get());
        }

        void assertSetHas(boolean dead, Set<Integer> target, Set<Integer> all) throws InterruptedException {
            Set<Integer> checkFor = new HashSet<>(all);
            for (int i = 0; i < 200; i++) {
                Set<Integer> copy;
                synchronized (this) {
                    copy = new HashSet<>(target);
                }
                checkFor.removeAll(copy);
                if (checkFor.isEmpty()) {
                    break;
                } else {
                    latch.await(300, TimeUnit.MILLISECONDS);
                }
            }
            assertTrue(checkFor.isEmpty(), "Missing notification that these items are "
                    + (dead ? "dead" : "alive") + checkFor);
        }

        @Override
        public synchronized void accept(Set<? extends Trackables.TrackingReference<?>> t) {
            notifyCount.getAndAdd(t.size() + 1);
            for (Trackables.TrackingReference<?> ref : t) {
                if (ref.isAlive()) {
                    aliveHashes.add(ref.identityHashCode());
                } else {
                    aliveHashes.remove(ref.identityHashCode());
                    deadHashes.add(ref.identityHashCode());
                }
            }
            latch.releaseOne();
        }
    }

    static class Thing {

        private final String sv;

        public Thing(String sv) {
            this.sv = sv;
        }

        @Override
        public String toString() {
            return sv;
        }

        public void dispose() {
            Trackables.discarded(Thing.class, this);
        }
    }

}
