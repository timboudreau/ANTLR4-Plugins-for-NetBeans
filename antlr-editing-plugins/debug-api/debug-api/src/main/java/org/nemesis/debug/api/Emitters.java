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
