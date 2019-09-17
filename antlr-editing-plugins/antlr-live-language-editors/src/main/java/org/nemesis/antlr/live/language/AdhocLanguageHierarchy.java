package org.nemesis.antlr.live.language;

import com.mastfrog.util.collections.CollectionUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParsers;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyTokenType;
import org.nemesis.debug.api.Debug;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.LanguagePath;
import org.netbeans.api.lexer.Token;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.lexer.EmbeddingPresence;
import org.netbeans.spi.lexer.LanguageEmbedding;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
final class AdhocLanguageHierarchy extends LanguageHierarchy<AdhocTokenId> implements Runnable, Supplier<TokensInfo> {

    public static final String DUMMY_TOKEN_ID = "__dummyToken__";
    private final String mimeType;
    private final EmbeddedAntlrParser parser;
    private final AtomicReference<TokensInfo> info = new AtomicReference<>(TokensInfo.EMPTY);
    private static final Logger LOG = Logger.getLogger(AdhocLanguageHierarchy.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }
    private final AtomicBoolean listening = new AtomicBoolean();

    AdhocLanguageHierarchy(String mimeType) {
        this.mimeType = mimeType;
        LOG.log(Level.FINE, "Create lang hier for {0}", mimeType);
        parser = EmbeddedAntlrParsers.forGrammar(
                FileUtil.toFileObject(
                        FileUtil.normalizeFile(
                                AdhocMimeTypes.grammarFilePathForMimeType(mimeType)
                                        .toFile())));
    }

    boolean initIfNecessary() {
        boolean result;
        if (result = listening.compareAndSet(false, true)) {

            LOG.log(Level.FINE, "Hierarchy for {0} begins listening on {1}",
                    new Object[]{AdhocMimeTypes.loggableMimeType(mimeType),
                        parser});
            try {
                initializing = true;
                updateTokensListAndCategories();
                parser.listen(this);
            } finally {
                initializing = false;
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "AdhocLanguageHierarchy(" + Integer.toString(System.identityHashCode(this), 36)
                + " listening=" + listening.get() + " tokens " + info.get() + ")";
    }

    @Override
    public void run() {
        LOG.log(Level.FINE, "Hier for {0} notified of grammar change from {1}",
                new Object[]{AdhocMimeTypes.loggableMimeType(mimeType), parser});
        languageUpdated();
    }

    @Override
    protected String mimeType() {
        return mimeType;
    }

    static String truncated(String txt) {
        txt = txt.trim().replace('\n', ' ');
        int len = txt.length();
        if (len > 20) {
            txt = txt.substring(0, 20) + "...";
        }
        return txt;
    }

    @Override
    protected boolean isRetainTokenText(AdhocTokenId tokenId) {
        return false;
    }

    @Override
    protected EmbeddingPresence embeddingPresence(AdhocTokenId id) {
        return EmbeddingPresence.NONE;
    }

    @Override
    protected LanguageEmbedding<?> embedding(Token<AdhocTokenId> token, LanguagePath languagePath, InputAttributes inputAttributes) {
        return null;
    }

    private boolean initializing;

    private void maybeUpdate() {
        boolean wasInitialCall = initIfNecessary();
        if (!wasInitialCall && info.get().isEmpty()) {
            updateTokensListAndCategories();
        }
    }

    @Override
    protected Collection<AdhocTokenId> createTokenIds() {
        maybeUpdate();
        return info.get().tokens();
    }

    public static Document document(LexerRestartInfo<AdhocTokenId> info) {
        Document d = (Document) info.getAttributeValue("doc");
        if (d == null) {
            d = (Document) info.getAttributeValue("currentDocument");
            if (d == null) {
                return AdhocEditorKit.currentDocument();
            }
            return d;
        }
        return null;
    }

    public static FileObject file(LexerRestartInfo<AdhocTokenId> info) {
        Document doc = document(info);
        if (doc != null) {
            return NbEditorUtilities.getFileObject(doc);
        }
        return null;
    }

    @Override
    protected Lexer<AdhocTokenId> createLexer(LexerRestartInfo<AdhocTokenId> info) {
        maybeUpdate();
        InputAttributes attrs = info.inputAttributes();
        if (attrs != null) {
            LanguagePath pth = LanguagePath.get(language());
            FileObject fo = (FileObject) attrs.getValue(pth, "currentFile");
            if (fo == null) {
                fo = AdhocEditorKit.currentFileObject();
                if (fo != null) {
                    attrs.setValue(pth, "currentFile", fo, false);
                }
            }
            Document d = (Document) attrs.getValue(pth, "currentDocument");
            if (d == null) {
                Document doc = (Document) attrs.getValue(pth, "doc");
                if (doc == null) {
                    doc = AdhocEditorKit.currentDocument();
                }
                if (doc != null) {
                    attrs.setValue(pth, "currentDocument", doc, false);
                }
            }
        }
        return new AdhocLexerNew(mimeType, info, parser, this);
    }

    @Override
    protected Map<String, Collection<AdhocTokenId>> createTokenCategories() {
        maybeUpdate();
        return info.get().categories();
    }

    boolean languageUpdated() {
        LOG.log(Level.FINEST, "Language updated for {0}",
                AdhocMimeTypes.loggableMimeType(mimeType));
        // Live lexers are keeping copies of the tokens list, and we
        // *want* them updated on the fly;  a lexer in progress
        // definitely shouldn't suddenly find itself with no
        // contents
        boolean isReentry = updateTokensListAndCategories();
//        AdhocMimeDataProvider.getDefault().gooseLanguage(mimeType);
        if (!initializing && !isReentry) {
            // If we are inside a call to super.language(), we shouldn't
            // clear the language field, and the synchronization of that
            // method makes it trivial to deadlock
            wipeLanguage();
        }
        return initializing || isReentry;
    }

    static Field languageField;
    static boolean logged;

    static Field languageField() {
        if (languageField != null) {
            return languageField;
        } else if (logged) {
            return null;
        }
        try {
            Field result = languageField
                    = LanguageHierarchy.class.getDeclaredField("language");
            result.setAccessible(true);
            return result;
        } catch (NoSuchFieldException | SecurityException ex) {
            if (!logged) {
                logged = true;
                LOG.log(Level.INFO, null, ex);
            }
            return null;
        }
    }

    synchronized void wipeLanguage() {
        Field f = languageField();
        if (f != null) {
            try {
                f.set(this, null);
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                LOG.log(Level.INFO, null, ex);
            }
        }
    }

    private void includeDummyToken(List<AdhocTokenId> ids) {
        AdhocTokenId nue = new AdhocTokenId(DUMMY_TOKEN_ID, AdhocMimeTypes.grammarFilePathForMimeType(mimeType), ids.size());
        ids.add(nue);
    }

    private int entryCount;

    private boolean updateTokensListAndCategories() {
        if (entryCount > 0) {
            return true;
        }
        entryCount++;
        try {
            String msg = "hierarchy-tokens-update " + AdhocMimeTypes.loggableMimeType(mimeType)
                    + " on " + Integer.toString(System.identityHashCode(this), 36);
            Debug.run(this, msg, () -> {
                Map<String, Collection<AdhocTokenId>> cats = CollectionUtils.supplierMap(HashSet::new);
                List<AdhocTokenId> newIds = new ArrayList<>();
                ParseTreeProxy prox;
                try {
                    prox = parser.parse(null);
                } catch (Exception ex) {
                    Logger.getLogger(AdhocLanguageHierarchy.class.getName()).log(Level.SEVERE, "Exception getting token categories for "
                            + AdhocMimeTypes.loggableMimeType(mimeType), ex);
                    prox = AntlrProxies.forUnparsed(AdhocMimeTypes.grammarFilePathForMimeType(mimeType), "x", "abc");
                }
                List<ProxyTokenType> allTypes = prox.tokenTypes();
                Set<String> names = new HashSet<>(allTypes.size());
                for (ProxyTokenType ptt : allTypes) {
                    AdhocTokenId id = new AdhocTokenId(prox, ptt, names);
                    newIds.add(id);
                    cats.get(id.primaryCategory()).add(id);
                }
                LOG.log(Level.FINEST, "Update tokens for {0} with {1} for {2} on {3}",
                        new Object[]{AdhocMimeTypes.loggableMimeType(mimeType),
                            newIds.size(), this, Thread.currentThread()});
                Collections.sort(newIds);
                includeDummyToken(newIds);
                TokensInfo newInfo = new TokensInfo(newIds, cats);
                TokensInfo old = info.get();
                if (!newInfo.equals(old)) {
                    info.set(newInfo);
                }
            });
        } finally {
            entryCount--;
        }
        return false;
    }

    @Override
    public TokensInfo get() {
        TokensInfo result = info.get();
        if (result.isEmpty()) {
            maybeUpdate();
            result = info.get();
        }
        return result;
    }

    static final class HierarchyInfo implements Runnable {

        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false);
        private TokensInfo info = TokensInfo.EMPTY;
        private EmbeddedAntlrParser parser;
        private final String mimeType;
        private final ReentrantReadWriteLock.ReadLock readLock;
        private final ReentrantReadWriteLock.WriteLock writeLock;

        public HierarchyInfo(String mimeType) {
            this.mimeType = mimeType;
            readLock = lock.readLock();
            writeLock = lock.writeLock();
        }

        private TokensInfo recreateInfo() {
            return null;
        }

        public TokensInfo info() {
            TokensInfo result;
            readLock.lock();
            try {
                result = info;
            } finally {
                readLock.unlock();
            }
            long id = result.id();
            if (result.isEmpty()) {
                writeLock.lock();
                try {
                    result = recreateInfo();
                } finally {
                    writeLock.unlock();
                }
            }
            return result;
        }

        @Override
        public void run() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }
}
