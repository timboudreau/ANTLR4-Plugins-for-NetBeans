package org.nemesis.antlr.v4.netbeans.v8.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.Exceptions;
import org.openide.util.Parameters;

/**
 * A straightforward cache with expiring entries, capable of conversion into a
 * bi-directional cache. Highly concurrent, but with perfect timing it is
 * possible for a value to be computed simultaneously by two threads. It is
 * assumed that such computation is idempotent. For when you can't have a
 * dependency on Guava.
 *
 * @author Tim Boudreau
 */
public class TimedCache<T, R, E extends Exception> {

    private final long timeToLive;
    private final Answerer<T, R, E> answerer;
    private final Map<T, CacheEntry> cache;
    private BiConsumer<T, R> onExpire;

    TimedCache(long ttl, Answerer<T, R, E> answerer) {
        this.timeToLive = ttl;
        this.answerer = answerer;
        cache = new ConcurrentHashMap<>();
    }

    private TimedCache(TimedCache<T, R, E> other) {
        this.timeToLive = other.timeToLive;
        this.answerer = other.answerer;
        this.cache = other.cache;
        this.onExpire = other.onExpire;
    }

    public boolean remove(T key) {
        return cache.remove(key) != null;
    }

    public String toString() {
        return toString(new StringBuilder(getClass().getSimpleName())
                .append('{')).append('}').toString();
    }

    StringBuilder toString(StringBuilder sb) {
        sb.append("entries=[");
        for (Iterator<Map.Entry<T, CacheEntry>> it = cache.entrySet().iterator(); it.hasNext();) {
            Map.Entry<T, CacheEntry> e = it.next();
            sb.append(e.getKey()).append('=').append(e.getValue());
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append(']');
        return sb;
    }

    /**
     * Clear this cache, removing expiring all entries immediately.
     * If an OnExpire handler has been set, it will not be called for
     * entries that are being removed (use close() if you need that).
     *
     * @return this
     */
    public TimedCache<T, R, E> clear() {
        cache.clear();
        caches().removeAll(cache.values());
        return this;
    }

    boolean containsKey(T key) {
        return cache.containsKey(key);
    }

    /**
     * Create a cache that throws a specific exception type if lookup fails.
     *
     * @param <T> The key type
     * @param <R> The value type
     * @param <E> The exception type
     * @param ttl The time after creation or last query it satisfied
     * that a value should live for, in milliseconds
     * @param answerer A function which computes the value if it is not cached
     * @return A cache
     */
    public static <T, R, E extends Exception> TimedCache<T, R, E> createThrowing(long ttl, Answerer<T, R, E> answerer) {
        return new TimedCache<>(ttl, answerer);
    }

    /**
     * Create a cache which does not throw a checked exception on lookup.
     *
     * @param <T> The key type
     * @param <R> The value type
     * @param <E> The exception type
     * @param ttl The time after creation or last query it satisfied
     * that a value should live for, in milliseconds
     * @param answerer A function which computes the value if it is not cached
     * @return A cache
     */
    public static <T, R> TimedCache<T, R, RuntimeException> create(long ttl, Answerer<T, R, RuntimeException> answerer) {
        return new TimedCache<>(ttl, answerer);
    }

    /**
     * Get a value which may be null.
     *
     * @param key The key
     * @return The value
     * @throws E If something goes wrong
     */
    public Optional<R> getOptional(T key) throws E {
        return Optional.ofNullable(get(key));
    }

    /**
     * Shut down this cache, immediately expiring any entries which have
     * not been evicted.
     */
    public void close() {
        for (CacheEntry e : cache.values()) {
            e.close();
        }
    }

    /**
     * Add a consumer which is called after a value has been expired
     * from the cache - this can be used to perform any cleanup work
     * necessary.
     *
     * @param onExpire A biconsumer to call that receives they key
     * and value which have expired.
     *
     * @return this
     */
    public TimedCache<T, R, E> onExpire(BiConsumer<T, R> onExpire) {
        if (this.onExpire != null) {
            throw new IllegalStateException("OnExpire is already "
                    + this.onExpire);
        }
        this.onExpire = onExpire;
        return this;
    }

    /**
     * Get a value from the cache, computing it if necessary.
     *
     * @param key The key to look up.  May not be null.
     * @return A value or null if the answerer returned null
     * @throws E If something goes wrong
     */
    public R get(T key) throws E {
        Parameters.notNull("key", key);
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            R result = answerer.answer(key);
            if (result != null) {
                entry = createEntry(key, result);
            }
        } else {
            entry.touch();
        }
        return entry == null ? null : entry.value;
    }

    CacheEntry createEntry(T key, R val) {
        CacheEntry result = new CacheEntry(key, val);
        cache.put(key, result);
        caches().offer(result);
        return result;
    }

    /**
     * Convert this cache to a bi-directional cache, providing an answerer
     * for reverse queries.  The returned cache will initially share its
     * contents with this cache and show changes from the original.
     *
     * @param reverseAnswerer An answerer
     * @return A bidirectional cache with the same contents as this one
     */
    public BidiCache<T, R, E> toBidiCache(Answerer<R, T, E> reverseAnswerer) {
        return new BidiCache<>(this, reverseAnswerer);
    }

    void expireEntry(CacheEntry ce) {
        cache.remove(ce.key, ce);
        if (onExpire != null) {
            try {
                onExpire.accept(ce.key, ce.value);
            } catch (Exception e) {
                Logger.getLogger(TimedCache.class.getName())
                        .log(Level.SEVERE, "Failure in onExpire", e);
            }
        }
    }

    /**
     * A bi-directional variant of TimedCache.
     *
     * @param <T> The key type
     * @param <R> The value type
     * @param <E> The exception that may be thrown by the function which looks
     * things up
     */
    public static final class BidiCache<T, R, E extends Exception> extends TimedCache<T, R, E> {

        private final Answerer<R, T, E> reverseAnswerer;
        private final Map<R, TimedCache<T, R, E>.CacheEntry> reverseEntries = new ConcurrentHashMap<>();

        BidiCache(TimedCache<T, R, E> orig, Answerer<R, T, E> reverseAnswerer) {
            super(orig);
            this.reverseAnswerer = reverseAnswerer;
        }

        boolean removeValue(R value) {
            TimedCache<T,R,E>.CacheEntry entry = reverseEntries.get(value);
            if (entry != null) {
                return super.remove(entry.key);
            }
            return false;
        }

        StringBuilder toString(StringBuilder sb) {
            sb.append(" reverse-entries=[");
            for (Iterator<Map.Entry<R, TimedCache<T, R, E>.CacheEntry>> it = reverseEntries.entrySet().iterator(); it.hasNext();) {
                Map.Entry<R, TimedCache<T, R, E>.CacheEntry> e = it.next();
                sb.append(e.getKey()).append('=').append(e.getValue());
                if (it.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append(']');
            return sb;
        }

        public BidiCache<T, R, E> clear() {
            reverseEntries.clear();
            super.clear();
            return this;
        }

        /**
         * On BidiCache, toBidiCache returns this
         *
         * @param reverseAnswerer ignored
         * @return this
         */
        public BidiCache<T, R, E> toBidiCache(Answerer<R, T, E> reverseAnswerer) {
            return this;
        }

        /**
         * Get the key for a value, wrapped in an Optional.
         *
         * @param value The value
         * @return The key, which will be present if contained in the cache
         * already or if the reverse anwerer returned non-null
         * @throws E
         */
        public Optional<T> getKeyOptional(R value) throws E {
            return Optional.ofNullable(getKey(value));
        }

        boolean containsValue(R value) {
            return reverseEntries.containsKey(value);
        }

        public String toString() {
            return reverseEntries.toString();
        }

        public BidiCache<T, R, E> onExpire(BiConsumer<T, R> onExpire) {
            super.onExpire(onExpire);
            return this;
        }

        /**
         * Get the key for a value.
         *
         * @param value The value
         * @return The key, or null
         * @throws E If something goes wrong
         */
        @SuppressWarnings("unchecked")
        public T getKey(R value) throws E {
            TimedCache<T, R, E>.CacheEntry entry = reverseEntries.get(value);
            if (entry == null) {
                T result = reverseAnswerer.answer(value);
                if (result != null) {
                    entry = createEntry(result, value);
                }
            } else {
                entry.touch();
            }
            return entry == null ? null : entry.key;
        }

        @Override
        void expireEntry(TimedCache<T, R, E>.CacheEntry ce) {
            reverseEntries.remove(ce.value, ce);
            super.expireEntry(ce);
        }

        @Override
        TimedCache<T, R, E>.CacheEntry createEntry(T key, R val) {
            TimedCache<T, R, E>.CacheEntry result = super.createEntry(key, val);
            reverseEntries.put(val, result);
            return result;
        }
    }

    /**
     * Answers queries for a cache if no value is already present to answer
     * the query.
     * 
     * @param <T> The key type
     * @param <R> The value type
     * @param <E> An exception to throw on failure
     */
    @FunctionalInterface
    public interface Answerer<T, R, E extends Exception> {

        R answer(T request) throws E;
    }

    private final class CacheEntry implements Expirable {

        volatile long touched = System.currentTimeMillis();
        private final T key;
        private final R value;

        public CacheEntry(T key, R value) {
            this.key = key;
            this.value = value;
        }

        public String toString() {
            return key + ":" + value;
        }

        void touch() {
            touched = System.currentTimeMillis();
        }

        void close() {
            touched = 0;
        }

        public void expire() {
            expireEntry(this);
        }

        public boolean isExpired() {
            return remaining() <= 0;
        }

        private long remaining() {
            long rem = Math.max(0, (System.currentTimeMillis() - touched) - timeToLive);
            return rem;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(remaining(), MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            long a = getDelay(MILLISECONDS);
            long b = o.getDelay(MILLISECONDS);
            return a > b ? 1 : a == b ? 0 : -1;
        }
    }

    interface Expirable extends Delayed {

        void expire();

        boolean isExpired();
    }

    static Expirer caches() {
        if (EXPIRER == null) {
            EXPIRER = expirerFactory.get();
        }
        return EXPIRER;
    }

    static Supplier<Expirer> expirerFactory = Expirer::new;
    private static Expirer EXPIRER;

    static class Expirer implements Runnable {

        private final DelayQueue<Expirable> queue = new DelayQueue<>();
        private volatile boolean started;
        private final int prio;

        Expirer(int prio) {
            this.prio = prio;
        }

        Expirer() {
            this(Thread.MIN_PRIORITY);
        }

        void removeAll(Collection<? extends Expirable> dead) {
            queue.removeAll(dead);
        }

        void offer(Expirable expirable) {
            queue.offer(expirable);
            checkStarted();
        }

        private void checkStarted() {
            if (!started) {
                started = true;
            }
            Thread expireThread = new Thread(this);
            expireThread.setName("antlr-cache-expire");
            expireThread.setPriority(prio);
            expireThread.setDaemon(true);
            expireThread.start();
        }

        void expireOne(Expirable toExpire) {
//            if (toExpire.isExpired()) {
            toExpire.expire();
//            } else {
//                queue.offer(toExpire);
//            }
        }

        @Override
        public void run() {
            for (;;) {
                try {
                    Expirable toExpire = queue.take();
                    expireOne(toExpire);
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }
}
