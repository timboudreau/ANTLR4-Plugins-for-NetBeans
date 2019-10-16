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

import java.lang.ref.SoftReference;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.LanguagePath;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.LanguageEmbedding;
import org.netbeans.spi.lexer.LanguageProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = LanguageProvider.class, position = 20000)
public final class AdhocLanguageFactory extends LanguageProvider {

//    private final Map<String, AdhocLanguageHierarchy> cache = new ConcurrentHashMap<>();
    private static final Map<String, ThreadLocal<SoftReference<AdhocLanguageHierarchy>>> cache = new HashMap<>();
    static final Logger LOG = Logger.getLogger(AdhocLanguageFactory.class.getName());

//    static {
//        LOG.setLevel(Level.ALL);
//    }

    static AdhocLanguageFactory get() {
        return Lookup.getDefault().lookup(AdhocLanguageFactory.class);
    }

    static synchronized AdhocLanguageHierarchy getOrCreate(String mimeType, boolean create) {
        AdhocLanguageHierarchy result = null;
        ThreadLocal<SoftReference<AdhocLanguageHierarchy>> loc = cache.get(mimeType);
        if (loc == null && create) {
            loc = new ThreadLocal<>();
//            cache.put(mimeType, loc);
        } else if (loc != null) {
            SoftReference<AdhocLanguageHierarchy> ref = loc.get();
            if (ref != null) {
                result = ref.get();
            }
        }

        if (result == null && create) {
            LOG.log(Level.FINER, "Create new Language for {0}", AdhocMimeTypes.loggableMimeType(mimeType));
            if ("text/plain".equals(mimeType)) {
                LOG.log(Level.INFO, "WTF? " + mimeType, new Exception("Trying to create a Language for text/plain!"));
            }
            result = new AdhocLanguageHierarchy(mimeType);
            loc.set(new SoftReference<>(result));
        }
        return result;
    }

    AdhocLanguageHierarchy hierarchy(String mimeType) {
        return getOrCreate(mimeType, true);
    }

    void fire() {
        super.firePropertyChange(PROP_LANGUAGE);
    }

    void discard(String mimeType) {
        ThreadLocal<SoftReference<AdhocLanguageHierarchy>> localRef = cache.remove(mimeType);
        if (localRef != null) {
            SoftReference<AdhocLanguageHierarchy> ref = localRef.get();
            if (ref != null) {
                AdhocLanguageHierarchy hier = ref.get();
                if (hier != null) {
                    LOG.log(Level.INFO, "Discard cached language for {0}", AdhocMimeTypes.loggableMimeType(mimeType));
                    hier.replaceProxy(AntlrProxies.forUnparsed(Paths.get("/discarded"), mimeType, "(discarded)"));
                }
            }
        }
        fire();
    }

    void update(AntlrProxies.ParseTreeProxy prox) {
        String mime = prox.mimeType();
        AdhocLanguageHierarchy hier = getOrCreate(mime, false);
        if (hier != null) {
            LOG.log(Level.FINEST, "Update language {0} with new proxy {1}", new Object[]{AdhocMimeTypes.loggableMimeType(mime), prox.summary()});
            hier.replaceProxy(prox);
        }
    }

    public Language<?> language(String mimeType) {
        if (!AdhocMimeTypes.isAdhocMimeType(mimeType)) {
            return null;
        }
        mimeType = DynamicLanguageSupport.currentMimeType(mimeType);
        AdhocLanguageHierarchy hierarchy = getOrCreate(mimeType, true);
        return hierarchy.language();
    }

    @Override
    public Language<?> findLanguage(String string) {
        return language(string);
    }

    @Override
    public LanguageEmbedding<?> findLanguageEmbedding(Token<?> token, LanguagePath lp, InputAttributes ia) {
        return null;
    }
}
