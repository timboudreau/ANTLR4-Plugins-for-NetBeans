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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.openide.util.Mutex;
import org.openide.util.RequestProcessor;

/**
 * Cache for parse trees associated with a file and the text to parse. Antlr
 * reparses are very expensive, since we may be generating sources, compiling
 * generated sources and then running them in an isolated classloader (meaning
 * classes can't be cached, so class loading is half the performance hit). This
 * cache ensures that, per grammar file revision, parsing is done for a given
 * piece of text exactly once.
 * <p>
 * The key thing is to always return a ParseTreeProxy for the text we are
 * passed, since the lexer is very unhappy if the tokens don't match what is
 * really there.
 * </p>
 *
 * @author Tim Boudreau
 */
final class ParseTreeCache implements Runnable {

    private final Path grammarFile;
    private final int expireObsoleteAfter;
    private final int expireLiveAfter;

    private final BiFunction<String, Consumer<String>, ParseTreeProxy> parser;
    private final Map<CacheKey, CacheEntry> cache = new HashMap<>();
    private final RequestProcessor.Task task;

    /**
     * Create a new cache.
     *
     * @param grammarFile The grammar file
     * @param expireObsoleteMillis How long a parse result should be allowed to
     * stay in memory once the grammar file has changed. This can happen while a
     * parse is in progress, so it is important to keep such results - the text
     * a call thinks it sent to parse, and the tokens output must match.
     * However, these do not need to last terribly long.
     * @param expireLiveMillis How long should parse results be cached when the
     * file *is* current - i.e. the user simply stopped using functionality of
     * the module and may come back to it.
     * @param threadPool A request processor to schedule the cleanup task in
     * @param parser Af cuntion which will parse
     */
    ParseTreeCache(Path grammarFile,
            int expireObsoleteMillis,
            int expireLiveMillis,
            RequestProcessor threadPool,
            BiFunction<String, Consumer<String>, ParseTreeProxy> parser) {
        this.grammarFile = grammarFile;
        this.expireObsoleteAfter = expireObsoleteMillis;
        this.expireLiveAfter = expireLiveMillis;
        this.parser = parser;
        task = threadPool.create(this);
    }

    ParseTreeCache(ParseTreeProxy initial, Path grammarFile, int expireObsoleteMillis,
            int expireLiveMillis, RequestProcessor threadPool, BiFunction<String, Consumer<String>, ParseTreeProxy> parser) {
        this(grammarFile, expireObsoleteMillis, expireLiveMillis, threadPool, parser);
        long lm = initial.isUnparsed() ? 0 : lastModified();
        CacheKey key = new CacheKey(lm, initial.text());
        CacheEntry entry = new CacheEntry(initial.text(), initial);
        cache.put(key, entry);
    }

    public ParseTreeProxy get(String text, Consumer<String> statusMonitor) {
        CacheEntry en = cacheEntryFor(text);
        return en.get(parser, statusMonitor);
    }

    public ParseTreeProxy getIfPresent(String text) {
        CacheEntry en = cacheEntryFor(text);
        return en.getIfPresent();
    }

    private synchronized Map<CacheKey, CacheEntry> entries() {
        return new HashMap<>(cache);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append("for ")
                .append(grammarFile).append('{');
        List<Map.Entry<CacheKey, CacheEntry>> all = new ArrayList<>();
        synchronized (this) {
            all.addAll(cache.entrySet());
        }
        Collections.sort(all, (a, b) -> {
            int result = a.getKey().compareTo(b.getKey());
            if (result == 0) {
                result = a.getValue().compareTo(b.getValue());
            }
            return result;
        });
        for (Map.Entry<CacheKey, CacheEntry> e : all) {
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private void gc() {
        // Do as little under a lock as possible; get a copy of
        // the cache to find items to remove in
        Set<Map.Entry<CacheKey, CacheEntry>> toRemove = new HashSet<>();
        long lm = lastModified();
        // Get a copy in a synchronized block
        Map<CacheKey, CacheEntry> copy = entries();
        // Iterate the entries
        for (Map.Entry<CacheKey, CacheEntry> e : copy.entrySet()) {
            // Determine if
            //  - The cache entry is for an old version of the grammar file
            //    and it is passed its expiration, OR
            //  - The cache entry is for the current version of the grammar
            //    file, but nothing has asked to use it in a long time and
            //    it's worth it to reclaim the memory and parse again on demand
            boolean isDefunct
                    = (!e.getKey().isCurrent(lm) && e.getValue().isExpired(expireObsoleteAfter))
                    || (e.getKey().isCurrent(lm) && e.getValue().isExpired(expireLiveAfter));
            if (isDefunct) {
                toRemove.add(e);
            }
        }
        if (!toRemove.isEmpty()) {
            boolean cacheHasGrown;
            boolean nowEmpty;
            // Now we actually update the cache
            synchronized (this) {
                // Determine if the cache has had members added since
                // we copied it - if so we will want to reschedule gc
                // on the short obsolete item delay
                cacheHasGrown = copy.size() < cache.size();
                for (Map.Entry<CacheKey, CacheEntry> e : copy.entrySet()) {
                    cache.remove(e.getKey());
                }
                nowEmpty = cache.isEmpty();
            }
            // If the cache still has contents, schedule another gc
            // for later
            if (!nowEmpty) {
                if (cacheHasGrown) {
                    // do it sooner, new items were added
                    task.schedule(expireObsoleteAfter + 1);
                } else {
                    // do it later
                    task.schedule(expireLiveAfter + 1);
                }
            }
        } else {
            boolean cacheIsEmpty;
            synchronized (this) {
                cacheIsEmpty = cache.isEmpty();
            }
            // If the cache is empty, gc will be scheduled the next
            // time an item is added
            if (!cacheIsEmpty) {
                task.schedule(expireLiveAfter + 1);
            }
        }
    }

    private String grammarName() {
        return grammarName(grammarFile);
    }

    public static String grammarName(Path grammarFile) {
        String nm = grammarFile.getFileName().toString();
        int ix = nm.lastIndexOf('.');
        if (ix > 0) {
            nm = nm.substring(0, ix);
        }
        return nm;
    }

    private synchronized CacheEntry cacheEntryFor(String text) {
        long lm = lastModified();
        if (lm == -1) { // file deleted
            // The original grammar file has been deleted - return a
            // dummy parse result that embeds all the text in a single
            // token - there is nothing else to do here
            return new CacheEntry(text, AntlrProxies.forUnparsed(grammarFile,
                    grammarName(), text));
        }
        CacheKey key = new CacheKey(lm, text);
        CacheEntry result = cache.get(key);
        if (result == null) {
            result = new CacheEntry(text);
            cache.put(key, result);
        }
        task.schedule(expireObsoleteAfter + 1);
        return result;
    }

    private long lastModified() {
        try {
            FileTime ft = Files.getLastModifiedTime(grammarFile);
            return ft.toMillis();
        } catch (IOException ioe) {
            return -1;
        }
    }

    @Override
    public void run() {
        gc();
    }

    private static final class CacheKey implements Comparable<CacheKey> {

        private final long lastModified;
        private final String text;

        CacheKey(long lastModified, String text) {
            this.lastModified = lastModified;
            this.text = text;
        }

        boolean isCurrent(long lastModifiedNow) {
            return lastModifiedNow == lastModified;
        }

        public boolean isSameLastModified(CacheKey other) {
            return lastModified == other.lastModified;
        }

        public boolean isSameText(CacheKey other) {
            return Objects.equals(text, other.text);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + (int) (this.lastModified ^ (this.lastModified >>> 32));
            hash = 67 * hash + Objects.hashCode(this.text);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (obj instanceof CacheKey) {
                CacheKey k = (CacheKey) obj;
                return k.isSameLastModified(this)
                        && k.isSameText(this);
            }
            return false;
        }

        @Override
        public int compareTo(CacheKey o) {
            // We do not need lexical comparison here - we are just
            // trying to group items with the same text for debugging
            // purposes
            int aHc = text.hashCode();
            int bHc = o.text.hashCode();
            if (aHc != bHc) {
                return aHc > bHc ? 1 : aHc == bHc ? 0 : -1;
            }
            long aLm = lastModified;
            long bLm = o.lastModified;
            return aLm > bLm ? -1 : aLm < bLm ? 1 : 0;
        }

        private String truncatedText() {
            if (text.length() > 100) {
                return text.substring(0, 100);
            }
            return text;
        }

        public String toString() {
            return Long.toString(lastModified) + ":" + truncatedText();
        }
    }

    private static final class CacheEntry implements Comparable<CacheEntry> {

        private final long created = System.currentTimeMillis();
        private volatile long touched = created;
        private final String text;
        private ParseTreeProxy content;
        private final Mutex mutex = new Mutex();

        CacheEntry(String text) {
            this.text = text;
        }

        /**
         * Constructor for the case that the grammar file has been deleted.
         *
         * @param text
         * @param unparsedResult
         */
        CacheEntry(String text, ParseTreeProxy unparsedResult) {
            this.text = text;
            this.content = unparsedResult;
        }

        public int compareTo(CacheEntry other) {
            return created > other.created ? -1
                    : created == other.created ? 1 : 0;
        }

        private void touch() {
            touched = System.currentTimeMillis();
        }

        public Duration age() {
            return Duration.ofMillis(touched - created);
        }

        public boolean isExpired(long maxAge) {
            return System.currentTimeMillis() - touched > maxAge;
        }

        ParseTreeProxy getIfPresent() {
            touch();
            return mutex.readAccess(new Mutex.Action<ParseTreeProxy>() {
                @Override
                public ParseTreeProxy run() {
                    return content;
                }
            });
        }

        ParseTreeProxy get(BiFunction<String, Consumer<String>, ParseTreeProxy> func, Consumer<String> statusMonitor) {
            touch();
            return mutex.writeAccess(new Mutex.Action<ParseTreeProxy>() {
                @Override
                public ParseTreeProxy run() {
                    if (content != null && !content.isUnparsed()) {
                        return content;
                    }
                    content = func.apply(text, statusMonitor);
                    return content;
                }
            });
        }
    }
}
