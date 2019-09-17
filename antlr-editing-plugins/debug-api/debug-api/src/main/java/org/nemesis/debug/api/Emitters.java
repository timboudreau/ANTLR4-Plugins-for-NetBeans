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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.nemesis.debug.spi.Emitter;

/**
 *
 * @author Tim Boudreau
 */
public class Emitters {

    private static final List<Emitter> subscribers = new CopyOnWriteArrayList<>();

    static void clear() {
        subscribers.clear();
    }

    public static boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    public static void subscribe(Emitter emitter) {
        if (!subscribers.contains(emitter)) {
            subscribers.add(emitter);
        }
    }

    public static boolean unsubscribe(Emitter emitter) {
        return subscribers.remove(emitter);
    }

    static Emitter emitter() {
        if (subscribers.isEmpty()) {
            return null;
        }
        if (subscribers.size() == 1) {
            try {
                return subscribers.get(0);
            } catch (IndexOutOfBoundsException e) {
                // race - changed since call to size()
                return null;
            }
        }
        return wrapped();
    }

    static Emitter wrapped() {
        List<Emitter> all = new ArrayList<>(subscribers);
        if (all.isEmpty()) { // test another race
            return null;
        }
        return new ListEmitter(all);
    }

    static final class ListEmitter implements Emitter {

        private final List<Emitter> delegates;

        public ListEmitter(List<Emitter> delegates) {
            this.delegates = delegates;
        }

        @Override
        public void exitContext(int depth) {
            for (Emitter e : delegates) {
                e.exitContext(depth);
            }
        }

        @Override
        public void thrown(int depth, long globalOrder, long timestamp, String threadName, long threadId, Throwable thrown, Supplier<String> stackTrace) {
            for (Emitter e : delegates) {
                e.thrown(depth, globalOrder, timestamp, threadName, threadId, thrown, stackTrace);
            }
        }

        @Override
        public void message(int depth, long globalOrder, long timestamp, String threadName, long threadId, String heading, Supplier<String> msg) {
            for (Emitter e : delegates) {
                e.message(depth, globalOrder, timestamp, threadName, threadId, heading, msg);
            }
        }

        @Override
        public void successOrFailure(int depth, long globalOrder, long timestamp, String threadName, long threadId,String heading, boolean success, Supplier<String> msg) {
            for (Emitter e : delegates) {
                e.successOrFailure(depth, globalOrder, timestamp, threadName, threadId, heading, success, msg);
            }
        }

        @Override
        public void enterContext(int depth, long globalOrder, long timeStamp, String threadName, long threadId, boolean isReentry, String ownerType, String ownerToString, String action, Supplier<String> details, long creationThreadId, String creationThreadString) {
            for (Emitter e : delegates) {
                e.enterContext(depth, globalOrder, timeStamp, threadName, threadId, isReentry, ownerType, ownerToString, action, details, creationThreadId, creationThreadString);
            }
        }
    }
}
