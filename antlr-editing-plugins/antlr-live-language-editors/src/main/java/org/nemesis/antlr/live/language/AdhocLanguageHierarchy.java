package org.nemesis.antlr.live.language;

import com.mastfrog.util.collections.CollectionUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParsers;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyTokenType;
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
final class AdhocLanguageHierarchy extends LanguageHierarchy<AdhocTokenId> implements Runnable {

    public static final String DUMMY_TOKEN_ID = "__dummyToken__";
    private final String mimeType;
    private final EmbeddedAntlrParser parser;
    private final Map<String, Collection<AdhocTokenId>> categories = new HashMap<>();
    private final List<AdhocTokenId> tokenIds = new CopyOnWriteArrayList<>();
    private static final Logger LOG = Logger.getLogger(AdhocLanguageHierarchy.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    AdhocLanguageHierarchy(String mimeType) {
        this.mimeType = mimeType;
        LOG.log(Level.FINE, "Create lang hier for {0}", mimeType);
        parser = EmbeddedAntlrParsers.forGrammar(FileUtil.toFileObject(AdhocMimeTypes.grammarFilePathForMimeType(mimeType).toFile()));
        updateTokensListAndCategories();
        parser.listen(this);
    }

    @Override
    public void run() {
        LOG.log(Level.FINE, "Hier for {0} notified of grammar change from {1}",
                new Object[]{mimeType, parser});
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

    List<AdhocTokenId> tokens() {
        maybeUpdate();
        return tokenIds;
    }

    Collection<AdhocTokenId> tokensForCategory(String category) {
        maybeUpdate();
        return categories.get(category);
    }

    Set<String> categories() {
        maybeUpdate();
        return categories.keySet();
    }

    private void maybeUpdate() {
        if (tokenIds.isEmpty()) {
            updateTokensListAndCategories();
        }
    }

    @Override
    protected Collection<AdhocTokenId> createTokenIds() {
        maybeUpdate();
        return tokenIds;
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
        return new AdhocLexerNew(mimeType, info, parser, tokenIds);
    }

    @Override
    protected Map<String, Collection<AdhocTokenId>> createTokenCategories() {
        maybeUpdate();
        return categories;
    }

    void languageUpdated() {
        LOG.log(Level.FINEST, "Language updated for {0}",
                AdhocMimeTypes.loggableMimeType(mimeType));
        // Live lexers are keeping copies of the tokens list, and we
        // *want* them updated on the fly;  a lexer in progress
        // definitely shouldn't suddenly find itself with no
        // contents
        updateTokensListAndCategories();
//        AdhocMimeDataProvider.getDefault().gooseLanguage(mimeType);
        wipeLanguage();
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

    private synchronized void updateTokensListAndCategories() {
        Map<String, Set<AdhocTokenId>> cats = CollectionUtils.supplierMap(TreeSet::new);
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
        LOG.log(Level.FINEST, "Update tokens for {0} to {1}", new Object[]{mimeType, newIds});
        Collections.sort(newIds);
        includeDummyToken(newIds);
        tokenIds.clear();
        categories.clear();
        categories.putAll(cats);
        tokenIds.addAll(newIds);
    }
}
