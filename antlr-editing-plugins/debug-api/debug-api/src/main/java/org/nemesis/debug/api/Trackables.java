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

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.openide.util.Exceptions;
import org.openide.util.Utilities;

/**
 * An API for tracking objects which ought to be disposed at some point,
 * to ensure they aren't proliferating.  Near-zero overhead when nothing
 * is listening, and when something is, can notify listeners about new and
 * cleared items.
 *
 * @author Tim Boudreau
 */
public class Trackables {

    static Map<Class<?>, List<TrackingReferenceImpl<?>>> refs = new HashMap<>();

    static final LinkedBlockingQueue<TrackingReferenceImpl<?>> Q = new LinkedBlockingQueue<>();

    static final List<Consumer<Set<? extends TrackingReference<?>>>> listeners = new CopyOnWriteArrayList<>();

    static Thread pollThread;

    private static void startPollThread() {
        synchronized (Trackables.class) {
            Thread poll = pollThread = new Thread(Trackables::poll, "trackables-poll");
            poll.setPriority(Thread.MAX_PRIORITY - 2);
            poll.setDaemon(true);
            poll.start();
        }
    }

    private static void stopPollThread() {
        Thread t;
        synchronized (Trackables.class) {
            t = pollThread;
            pollThread = null;
        }
        if (t != null) {
            t.interrupt();
        }
    }

    private static void poll() {
        synchronized (Trackables.class) {
            pollThread = Thread.currentThread();
        }
        Set<TrackingReferenceImpl<?>> set = new HashSet<>();
        try {
            for (;;) {
                try {
                    if (!isActive()) {
                        break;
                    }
                    TrackingReferenceImpl<?> first = Q.take();
                    set.add(first);
                    Q.drainTo(set);
                    if (!set.isEmpty()) {
                        Set<TrackingReferenceImpl<?>> copy = new HashSet<>(set);
                        for (Consumer<Set<? extends TrackingReference<?>>> c : listeners) {
                            c.accept(copy);
                        }
                    }
                } catch (InterruptedException ex) {
                    if (!isActive()) {
                        break;
                    }
                    Exceptions.printStackTrace(ex);
                } finally {
                    set.clear();
                }
            }
        } finally {
            set.clear();
            Q.clear();
            refs.clear();
            synchronized (Trackables.class) {
                if (pollThread == Thread.currentThread()) {
                    pollThread = null;
                }
            }
        }
    }

    public static void listen(Consumer<Set<? extends TrackingReference<?>>> listener) {
        boolean wasEmpty = listeners.isEmpty();
        listeners.add(listener);
        if (wasEmpty) {
            startPollThread();
        }
    }

    public static void unlisten(Consumer<Set<? extends TrackingReference<?>>> listener) {
        if (listeners.remove(listener) && listeners.isEmpty()) {
            stopPollThread();
        }
    }

    public static <T> void track(Class<T> type, T obj) {
        track(type, obj, () -> {
            return type.getSimpleName() + "@" + Integer.toString(System.identityHashCode(obj))
                    + " - " + obj.toString();
        });
    }

    public static <T> void track(Class<T> type, T obj, Supplier<String> stringVal) {
        if (isActive()) {
            List<TrackingReferenceImpl<?>> l;
            synchronized (refs) {
                l = refs.get(type);
                if (l == null) {
                    l = new CopyOnWriteArrayList<>();
                    refs.put(type, l);
                }
            }
            TrackingReferenceImpl<T> ref = new TrackingReferenceImpl<>(obj, type, stringVal.get());
            l.add(ref);
            Q.offer(ref);
        }
    }

    static boolean awaitExit() throws InterruptedException { // for tests
        Thread t;
        synchronized (Trackables.class) {
            t = pollThread;
        }
        if (t != null) {
            t.join();
        }
        synchronized (Trackables.class) {
            return pollThread == null;
        }
    }

    static void onDispose(Class<?> type, TrackingReferenceImpl<?> ref) {
        Q.offer(ref);
    }

    public static <T> void discarded(Class<T> type, T obj) {
        List<TrackingReferenceImpl<?>> l;
        synchronized (refs) {
            l = refs.get(type);
        }
        if (l != null) {
            for (TrackingReferenceImpl<?> t : l) {
                if (t.is(obj)) {
                    t.run();
                }
            }
        }
    }

    static boolean isActive() {
        return !listeners.isEmpty();
    }

    public interface TrackingReference<T> extends Comparable<TrackingReference<?>> {

        String stringValue();

        T get();

        Class<T> type();

        long seqNumber();

        int identityHashCode();

        boolean isAlive();
    }

    static final class TrackingReferenceImpl<T> extends WeakReference<T> implements TrackingReference<T>, Runnable {

        static AtomicLong seq = new AtomicLong(Long.MIN_VALUE);
        private final Class<T> type;
        private final int idHash;
        private final String stringValue;
        private long seqNumber = seq.getAndIncrement();
        private volatile boolean disposed;

        public TrackingReferenceImpl(T obj, Class<T> type, String stringValue) {
            super(obj, Utilities.activeReferenceQueue());
            this.type = type;
            this.idHash = System.identityHashCode(obj);
            this.stringValue = stringValue;
        }

        public int identityHashCode() {
            return idHash;
        }

        public long seqNumber() {
            return seqNumber;
        }

        public Class<T> type() {
            return type;
        }

        @Override
        public String stringValue() {
            return stringValue;
        }

        boolean is(Object obj) {
            return System.identityHashCode(obj) == idHash
                    && type.isInstance(obj)
                    && obj == get();
        }

        public boolean isAlive() {
            return !disposed && get() != null;
        }

        @Override
        public String toString() {
            return stringValue + " (" + type.getSimpleName() + "@"
                    + Integer.toString(idHash, 36)
                    + " " + (isAlive() ? "alive" : "dead")
                    + ")";
        }

        @Override
        public int hashCode() {
            return idHash * 37;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TrackingReferenceImpl<?> other = (TrackingReferenceImpl<?>) obj;
            if (this.idHash != other.idHash) {
                return false;
            }
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            return true;
        }

        @Override
        public int compareTo(TrackingReference<?> o) {
            return Long.compare(seqNumber, o.seqNumber());
        }

        @Override
        public void run() {
            if (disposed) {
                return;
            }
            disposed = true;
            onDispose(type, this);
        }
    }
}
