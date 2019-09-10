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
                    listener.accept(g, proxy);
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, "Passing new parse of "
                            + proxy.grammarPath() + " to " + listener, ex);;
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
