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
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.extraction.Extraction;

/**
 *
 * @author Tim Boudreau
 */
public class BrokenSourceThrottle {

    private final TimedCache<String, RebuildAttempts, RuntimeException> cache;
    private Map<String, ?> map;

    public BrokenSourceThrottle() {
        this.cache = TimedCache.create(60000, (String request) -> new RebuildAttempts(),
                new MapSupplier<String>() {
            @Override
            public <V> Map<String, V> get() {
                Map<String, V> backingStore = new ConcurrentHashMap<>();
                map = backingStore;
                return backingStore;
            }
        });
    }

    public void reset(Path path) {
        String str = path.toString();
        if (map != null) {
            for (Map.Entry<String, ?> e : map.entrySet()) {
                String key = e.getKey();
                int ix = key.indexOf('|');
                if (ix > 0 && ix < key.length() - 1) {
                    key = key.substring(0, ix);
                    if (key.equals(str)) {
                        Optional<RebuildAttempts> att = cache.cachedValue(key);
                        if (att.isPresent()) {
                            System.out.println("  reset " + key);
                            att.get().reset();
                        }
                    }
                }
            }
        }
    }

    public boolean isThrottled(Path path, String tokensHash) {
        RebuildAttempts attempts = cache.get(key(path, tokensHash));
        return attempts == null ? false : attempts.isBlocked();
    }

    public boolean maybeThrottle(Path path, String tokensHash) {
        RebuildAttempts attempts = cache.get(key(path, tokensHash));
        return attempts.check();
    }

    public boolean incrementThrottleIfBad(Extraction extraction, AntlrGenerationResult result) {
        if (!result.isUsable()) {
            String key = key(extraction);
            RebuildAttempts attempts = cache.get(key);
            boolean res = attempts.check();
            System.out.println((res ? "ithrottled: " : "inon-throttled: ") + key + " - " + attempts.count.get());
            return res;
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
        boolean result = attempts.isBlocked();
        System.out.println((result ? "throttled: " : "non-throttled: ") + key + " - " + attempts.count.get());
        return result;
    }

    private static final class RebuildAttempts {

        private final AtomicInteger count = new AtomicInteger();
        private static final int MAX = 4;

        int increment() {
            return count.incrementAndGet();
        }

        boolean isBlocked() {
            return count.get() > MAX;
        }

        boolean check() {
            return increment() > MAX;
        }

        void reset() {
            count.lazySet(0);
        }
    }
}
