package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<String, AdhocLanguageHierarchy> cache = new ConcurrentHashMap<>();
    static final Logger LOG = Logger.getLogger(AdhocLanguageFactory.class.getName());

    static AdhocLanguageFactory get() {
        return Lookup.getDefault().lookup(AdhocLanguageFactory.class);
    }

    AdhocLanguageHierarchy hierarchy(String mimeType) {
        return cache.get(mimeType);
    }

    void discard(String mimeType) {
        AdhocLanguageHierarchy hier = cache.remove(mimeType);
        if (hier != null) {
            LOG.log(Level.INFO, "Discard cached language for {0}", AdhocMimeTypes.loggableMimeType(mimeType));
            hier.replaceProxy(AntlrProxies.forUnparsed(Paths.get("/discarded"), mimeType, "(discarded)"));
        }
        super.firePropertyChange(PROP_LANGUAGE);
    }

    void update(AntlrProxies.ParseTreeProxy prox) {
        String mime = prox.mimeType();
        AdhocLanguageHierarchy hier = cache.get(mime);
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
//        AdhocLanguageHierarchy hierarchy = cache.get(mimeType);
        AdhocLanguageHierarchy hierarchy = null;
        if (hierarchy == null) {
            LOG.log(Level.FINER, "Create new Language for {0}", AdhocMimeTypes.loggableMimeType(mimeType));
            if ("text/plain".equals(mimeType)) {
                LOG.log(Level.INFO, "WTF? " + mimeType, new Exception("Trying to create a Language for text/plain!"));
            }
            hierarchy = new AdhocLanguageHierarchy(DynamicLanguageSupport.proxyFor(mimeType));
            cache.put(mimeType, hierarchy);
        }
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
