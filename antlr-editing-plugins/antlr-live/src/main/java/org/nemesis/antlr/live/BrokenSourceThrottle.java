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
package org.nemesis.antlr.live;

import com.mastfrog.util.cache.MapSupplier;
import com.mastfrog.util.cache.TimedCache;
import com.mastfrog.util.collections.IntSet;
import static com.mastfrog.util.preconditions.Checks.greaterThanZero;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.extraction.Extraction;

/**
 * Allows for throttling calls to regenerate sources when the that path with
 * that tokens hash has repeatedly produced unusable results and there is no
 * point in doing the same work that will end in failure over and over.  Uses
 * a timed cache where the timeout is reset when a key is fetched, and an IntSet
 * of hash codes to perform a fast negative test when resetting the throttled
 * state, which should be done when a dependency of the failed file is rebuilt.
 *
 * @author Tim Boudreau
 */
public class BrokenSourceThrottle {

    private static final int DEFAULT_MAX_BAD = 8;
    private static final long CACHE_EXPIRE_MS = 60000;
    private final TimedCache<String, RebuildAttempts, RuntimeException> cache;
    private final IntSet throttledPathHashes = IntSet.arrayBased(16);
    private Map<String, ?> cacheStore;
    private int maxBad;

    public BrokenSourceThrottle() {
        this(DEFAULT_MAX_BAD);
    }

    public BrokenSourceThrottle(int maxBad) {
        this.maxBad = greaterThanZero("maxBad", maxBad);
        this.cache = TimedCache.create(CACHE_EXPIRE_MS, (String request) -> new RebuildAttempts(),
                new MapSupplier<String>() {
            @Override
            public <V> Map<String, V> get() {
                Map<String, V> backingStore = new ConcurrentHashMap<>();
                cacheStore = backingStore;
                return backingStore;
            }
        });
        cache.onExpire((key, attempt) -> {
            Path path = pathFromKey(key);
            boolean hasOther = false;;
            String pathPortion = path.toString() + "|";
            for (Map.Entry<String, ?> e : cacheStore.entrySet()) {
                if (!key.equals(e.getKey()) && e.getKey().startsWith(pathPortion)) {
                    hasOther = true;
                    break;
                }
            }
            if (!hasOther) {
                synchronized (throttledPathHashes) {
                    throttledPathHashes.remove(path.hashCode());
                }
            }
        });
    }

    public void clearThrottle(Path path) {
        // Fast test if we should look at the map
        synchronized (throttledPathHashes) {
            if (!throttledPathHashes.contains(path.hashCode())) {
                return;
            }
        }
        String str = path.toString();
        if (cacheStore != null) {
            for (Map.Entry<String, ?> e : cacheStore.entrySet()) {
                String key = e.getKey();
                int ix = key.indexOf('|');
                if (ix > 0 && ix < key.length() - 1) {
                    key = key.substring(0, ix);
                    if (key.equals(str)) {
                        Optional<RebuildAttempts> att = cache.cachedValue(key);
                        if (att.isPresent()) {
                            att.get().reset();
                        }
                    }
                }
            }
        }
    }

    private static Path pathFromKey(String key) {
        int ix = key.indexOf('|');
        if (ix > 0 && ix < key.length() - 1) {
            key = key.substring(0, ix);
            return Paths.get(key);
        }
        return null;
    }

    public boolean isThrottled(Path path, String tokensHash) {
        RebuildAttempts attempts = cache.get(key(path, tokensHash));
        return attempts == null ? false : attempts.isThrottled(maxBad);
    }

    public boolean maybeThrottle(Path path, String tokensHash) {
        RebuildAttempts attempts = cache.get(key(path, tokensHash));
        boolean wasThrottled = attempts.isThrottled(maxBad);
        boolean result = attempts.check(maxBad);
        if (!wasThrottled && result) {
            synchronized (throttledPathHashes) {
                throttledPathHashes.add(path.hashCode());
            }
        }
        return result;
    }

    public boolean incrementThrottleIfBad(Extraction extraction, AntlrGenerationResult result) {
        if (!result.isUsable()) {
            String key = key(extraction);
            RebuildAttempts attempts = cache.get(key);
            boolean wasThrottled = attempts.isThrottled(maxBad);
            boolean res = attempts.check(maxBad);
            if (!wasThrottled && res) {
                extraction.source().lookup(Path.class, pth -> {
                    synchronized (throttledPathHashes) {
                        throttledPathHashes.add(pth.hashCode());
                    }
                });
            }
            return res;
        } else {
            String key = key(extraction);
            RebuildAttempts attempts = cache.get(key);
            attempts.reset();
        }
        return false;
    }

    private String key(Extraction extraction) {
        Optional<Path> pth = extraction.source().lookup(Path.class);
        if (!pth.isPresent()) {
            return extraction.source().id() + "|" + extraction.tokensHash();
        }
        return key(pth.get(), extraction.tokensHash());

    }

    private String key(Path path, String tokensHash) {
        return path + "|" + tokensHash;
    }

    public boolean isThrottled(Extraction extraction) {
        String key = key(extraction);
        RebuildAttempts attempts = cache.get(key(extraction));
        boolean result = attempts.isThrottled(maxBad);
//        System.out.println((result ? "throttled: " : "non-throttled: ") + key + " - " + attempts.count.get());
        return result;
    }

    private static final class RebuildAttempts {

        private final AtomicInteger count = new AtomicInteger();

        int increment() {
            return count.incrementAndGet();
        }

        boolean isThrottled(int max) {
            return count.get() > max;
        }

        boolean check(int max) {
            return increment() > max;
        }

        void reset() {
            count.lazySet(0);
        }
    }
}
