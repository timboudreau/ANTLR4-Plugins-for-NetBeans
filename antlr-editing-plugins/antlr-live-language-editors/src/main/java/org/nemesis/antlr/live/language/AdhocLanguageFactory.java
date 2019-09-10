package org.nemesis.antlr.live.language;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
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

    static final Logger LOG = Logger.getLogger(AdhocLanguageFactory.class.getName());
    private AtomicInteger count = new AtomicInteger();

    static {
        LOG.setLevel(Level.ALL);
    }

    static AdhocLanguageFactory get() {
        return Lookup.getDefault().lookup(AdhocLanguageFactory.class);
    }

    void kill(String mimeType) {
        LOG.log(Level.FINE, "Discard cached language {0} was {1}", new Object[]{
            AdhocMimeTypes.loggableMimeType(mimeType)//, CACHE.get(mimeType)
        });
        if (count.get() > 0) {
            fire();
        }
    }

    AdhocLanguageHierarchy getOrCreate(String mimeType, boolean create) {
        if (create) {
            count.incrementAndGet();
            return new AdhocLanguageHierarchy(mimeType);
        }
        return null;
    }

    AdhocLanguageHierarchy hierarchy(String mimeType) {
        return getOrCreate(mimeType, true);
    }

    void fire() {
        LOG.log(Level.FINEST, "Fire property change to force LanguageManager to refresh");
        super.firePropertyChange(null);
    }

    void discard(String mimeType) {
        fire();
    }

    void update(String mime) {
//        AdhocLanguageHierarchy hier = getOrCreate(mime, false);
//        if (hier != null) {
//            LOG.log(Level.FINEST, "Update language {0}", new Object[]{AdhocMimeTypes.loggableMimeType(mime)});
//            hier.languageUpdated();
//        }
    }

    public Language<?> language(String mimeType) {
        if (!AdhocMimeTypes.isAdhocMimeType(mimeType)) {
            return null;
        }
//        mimeType = DynamicLanguageSupport.currentMimeType(mimeType);
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
