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

import com.mastfrog.util.strings.Escaper;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.openide.util.Exceptions;
import org.openide.util.Utilities;

/**
 * An API for tracking objects which ought to be disposed at some point, to
 * ensure they aren't proliferating. Near-zero overhead when nothing is
 * listening, and when something is, can notify listeners about new and cleared
 * items.
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
            if (pollThread != null) {
                pollThread.interrupt();
            }
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
                        refs.clear();
                        Q.clear();
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
                        refs.clear();
                        Q.clear();
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
        track(type, obj, () -> obj.toString());
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

        String htmlStringValue();

        String htmlValue();

        default int compareTo(TrackingReference<?> o) {
            return Long.compare(seqNumber(), o.seqNumber());
        }
    }

    static final class TrackingReferenceImpl<T> extends WeakReference<T> implements TrackingReference<T>, Runnable {

        static AtomicLong seq = new AtomicLong(Long.MIN_VALUE);
        private final Class<T> type;
        private final int idHash;
        private final String stringValue;
        private final long seqNumber = seq.getAndIncrement();
        private volatile boolean disposed;
        private final long created = System.currentTimeMillis();

        public TrackingReferenceImpl(T obj, Class<T> type, String stringValue) {
            super(obj, Utilities.activeReferenceQueue());
            this.type = type;
            this.idHash = System.identityHashCode(obj);
            this.stringValue = stringValue;
        }

        public long age() {
            return System.currentTimeMillis() - created;
        }

        @Override
        public int identityHashCode() {
            return idHash;
        }

        @Override
        public long seqNumber() {
            return seqNumber;
        }

        @Override
        public Class<T> type() {
            return type;
        }

        static int TRUNCATE_LENGTH = 80;
        static int DANGEROUSLY_OLD = 90000;

        private static String truncate(String s) {
            s = Escaper.BASIC_HTML.escape(s);
            if (s.length() > TRUNCATE_LENGTH) {
                s = s.substring(0, TRUNCATE_LENGTH) + "\u2026";
            }
            return s;
        }

        @Override
        public String htmlValue() {
            StringBuilder sb = new StringBuilder();
            long age = age();
            boolean alive = isAlive();
            if (alive && age > DANGEROUSLY_OLD) {
                sb.append("<font color='#ED0000'>");
            }
            sb.append(formattedAge(age)).append(' ');
            if (alive) {
                sb.append("<b>");
            } else {
                sb.append("<s>");
            }
            sb.append(type.getSimpleName());
            if (alive) {
                sb.append("</b>");
            } else {
                sb.append("</s>");
            }
            sb.append(" <i>").append(idHash).append("</i>: ");
            sb.append(truncate(stringValue));
            if (alive && age > DANGEROUSLY_OLD) {
                sb.append("</font>");
            }
            return sb.toString();
        }

        String formattedAge(long age) {
            if (age < 1000) {
                return Long.toString(age) + "ms ";
            }
            StringBuilder sb = new StringBuilder();
            long seconds = (age / 1000) % 60;
            long minutes = ((age / 1000) / 60) % 60;
            long hours = ((age / 1000) / 60) / 60;
            prependLong(sb, seconds);
            prependLong(sb, minutes);
            prependLong(sb, hours);
            return sb.toString();
        }

        private void prependLong(StringBuilder to, long val) {
            if (to.length() > 0) {
                to.insert(0, ':');
            }
            to.insert(0, Long.toString(val));
            if (val < 10) {
                to.insert(0, '0');
            }
        }

        @Override
        public String htmlStringValue() {
            StringBuilder sb = new StringBuilder("<html>");
            sb.append("Age: ").append("<b>").append(formattedAge(age()))
                    .append("</b><br>");
            boolean alive = isAlive();
            if (alive) {
                sb.append("<b>");
            }
            sb.append(type.getSimpleName());
            if (alive) {
                sb.append("</b>");
            }
            sb.append("<i>").append(idHash).append("</i>:<p>\n");
            String body = Escaper.BASIC_HTML.escape(stringValue);
            String[] parts = body.split("\\s+");
            int lineLength = 80;
            int currLineLength = 0;
            for (String part : parts) {
                if (currLineLength + part.length() > lineLength) {
                    sb.append("\n<br>");
                    currLineLength = 0;
                }
                sb.append(part).append(' ');
                currLineLength += part.length() + 1;
            }
            return sb.toString();
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

        @Override
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
            } else if (obj == null || obj.getClass() != TrackingReferenceImpl.class) {
                return false;
            }
            final TrackingReferenceImpl<?> other = (TrackingReferenceImpl<?>) obj;
            return idHash == other.idHash && type == other.type;
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
