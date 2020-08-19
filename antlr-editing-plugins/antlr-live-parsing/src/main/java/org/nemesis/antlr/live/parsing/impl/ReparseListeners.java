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
package org.nemesis.antlr.live.parsing.impl;

import com.mastfrog.util.collections.CollectionUtils;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.openide.util.WeakSet;

/**
 *
 * @author Tim Boudreau
 */
public final class ReparseListeners {

    private static volatile ReparseListeners INSTANCE;
    private static final Logger LOG = Logger.getLogger(ReparseListeners.class.getName());
    private final Map<Path, Set<BiConsumer<? super GrammarRunResult<EmbeddedParser>, ? super AntlrProxies.ParseTreeProxy>>> listeners
            = CollectionUtils.concurrentSupplierMap(WeakSet::new);

    static ReparseListeners instance() {
        ReparseListeners instance = INSTANCE;
        if (instance == null) {
            synchronized (ReparseListeners.class) {
                if ((instance = INSTANCE) == null) {
                    instance = INSTANCE = new ReparseListeners();
                }
            }
        }
        return instance;
    }

    public static void listen(Path path, BiConsumer<? super GrammarRunResult<EmbeddedParser>, ? super AntlrProxies.ParseTreeProxy> listener) {
        instance()._listen(path, listener);
    }

    public static void unlisten(Path path, BiConsumer<? super GrammarRunResult<EmbeddedParser>, ? super AntlrProxies.ParseTreeProxy> listener) {
        instance()._unlisten(path, listener);
    }

    public static void fire(GrammarRunResult<EmbeddedParser> g, AntlrProxies.ParseTreeProxy proxy) {
        ReparseListeners instance = INSTANCE;
        if (instance == null) {
            synchronized (ReparseListeners.class) {
                instance = INSTANCE;
            }
        }
        if (instance != null) {
            instance._fire(g, proxy);
        }
    }

    private void _fire(GrammarRunResult<EmbeddedParser> g, AntlrProxies.ParseTreeProxy proxy) {
        if (listeners.containsKey(proxy.grammarPath())) {
            Set<BiConsumer<? super GrammarRunResult<EmbeddedParser>, ? super AntlrProxies.ParseTreeProxy>> s
                    = listeners.get(proxy.grammarPath());
            for (BiConsumer<? super GrammarRunResult<EmbeddedParser>, ? super AntlrProxies.ParseTreeProxy> listener : s) {
                try {
                    LOG.log(Level.FINEST, "Fire reparse of {0} to {1} with {2}", new Object[] {proxy.grammarPath(), s, g} );
                    listener.accept(g, proxy);
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, "Passing new parse of "
                            + proxy.grammarPath() + " to " + listener, ex);
                }
            }
        }
        gc();
    }

    private void _listen(Path path, BiConsumer<? super GrammarRunResult<EmbeddedParser>, ? super AntlrProxies.ParseTreeProxy> listener) {
        listeners.get(path).add(listener);
        gc();
    }

    private void _unlisten(Path path, BiConsumer<? super GrammarRunResult<EmbeddedParser>, ? super AntlrProxies.ParseTreeProxy> listener) {
        if (listeners.containsKey(path)) {
            listeners.get(path).remove(listener);
        }
        gc();
    }

    void gc() {
        Set<Path> paths = new HashSet<>(3);
        for (Map.Entry<Path, Set<BiConsumer<? super GrammarRunResult<EmbeddedParser>, ? super AntlrProxies.ParseTreeProxy>>> e : listeners.entrySet()) {
            if (e.getValue().isEmpty()) {
                paths.add(e.getKey());
            }
        }
        for (Path p : paths) {
            listeners.remove(p);
        }
    }
}
