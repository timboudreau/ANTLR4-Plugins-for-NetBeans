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
package org.nemesis.antlr.live.language;

import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.debug.api.Debug;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.LanguagePath;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.LanguageEmbedding;
import org.netbeans.spi.lexer.LanguageProvider;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = LanguageProvider.class, position = 20000)
public final class AdhocLanguageFactory extends LanguageProvider {

    static final Logger LOG = Logger.getLogger(AdhocLanguageFactory.class.getName());
    private AtomicInteger count = new AtomicInteger();
    private static final RequestProcessor LANGUAGE_UPDATES
            = new RequestProcessor("AdhocLanguageFactory-force-regenerate", 1);
    private final RequestProcessor.Task refresh = LANGUAGE_UPDATES.create(this::reallyFire);
    private final Map<String, AdhocLanguageHierarchy> cache
            = Collections.synchronizedMap(new WeakHashMap<>());

    static AdhocLanguageFactory get() {
        return Lookup.getDefault().lookup(AdhocLanguageFactory.class);
    }

    void kill(String mimeType) {
        LOG.log(Level.FINE, "Discard cached language {0} was {1}", new Object[]{
            AdhocMimeTypes.loggableMimeType(mimeType)//, CACHE.get(mimeType)
        });
        AdhocLanguageHierarchy hier = cache.get(mimeType);
        if (hier != null) {
            hier.languageUpdated();
        }
        if (count.get() > 0) {
            fire();
        }
    }

    AdhocLanguageHierarchy getOrCreate(String mimeType, boolean create) {
        if (create) {
            count.incrementAndGet();
            AdhocLanguageHierarchy hier = new AdhocLanguageHierarchy(mimeType);
            cache.put(mimeType, hier);
        }
        return cache.get(mimeType);
    }

    AdhocLanguageHierarchy hierarchy(String mimeType) {
        return getOrCreate(mimeType, true);
    }

    long countAtLastFire = -1;
    private volatile boolean pendingFire;

    void reallyFire() {
        LOG.log(Level.FINEST, "Really fire property change to force LanguageManager to refresh");
//        Thread.dumpStack();
        long ct = count.get();
        if (ct != countAtLastFire) {
            countAtLastFire = ct;
            super.firePropertyChange(null);
            pendingFire = false;
        }
    }

    void fire() {
        if (pendingFire) {
            return;
        }
        pendingFire = true;
        Debug.message("AdhocLanguageFactory schedule fire languages change");
        LOG.log(Level.FINEST, "Schedule property change to force LanguageManager to refresh");
        refresh.schedule(100);
    }

    void discard(String mimeType) {
        fire();
    }

    boolean update(String mime) {
        AdhocLanguageHierarchy hier = getOrCreate(mime, false);
        if (hier != null) {
            boolean reentry = hier.languageUpdated();
            if (!reentry) {
                LOG.log(Level.FINEST, "Update language {0}", AdhocMimeTypes.loggableMimeType(mime));
                synchronized (cache) {
                    AdhocLanguageHierarchy h = cache.get(mime);
                    if (h == hier) {
                        cache.remove(mime);
                    }
                }
                fire();
            }
            return reentry;
        } else {
            LOG.log(Level.FINE, "No hierarchies to update for {0}", AdhocMimeTypes.loggableMimeType(mime));
//            reallyFire();
            return false;
        }
    }

    public Language<?> language(String mimeType) {
        if (!AdhocMimeTypes.isAdhocMimeType(mimeType)) {
            return null;
        }
        AdhocLanguageHierarchy hierarchy = getOrCreate(mimeType, true);
        return hierarchy.languageUnsafe();
    }

    @Override
    public Language<?> findLanguage(String string) {
        return language(string);
    }

    @Override
    public LanguageEmbedding<?> findLanguageEmbedding(Token<?> token, LanguagePath lp, InputAttributes ia) {
        return null;
    }

    static boolean awaitFire(long millis) throws InterruptedException { // for tests
        CountDownLatch latch = new CountDownLatch(1);
        AdhocLanguageFactory factory = get();
        boolean[] fired = new boolean[1];
        PropertyChangeListener l = pcl -> {
            fired[0] = true;
            latch.countDown();
        };
        factory.addPropertyChangeListener(l);
        try {
            latch.await(millis, TimeUnit.MILLISECONDS);
        } finally {
            factory.removePropertyChangeListener(l);
        }
        return fired[0];
    }
}
