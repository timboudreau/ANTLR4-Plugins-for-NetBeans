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

import com.mastfrog.util.collections.CollectionUtils;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParsers;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyTokenType;
import org.nemesis.debug.api.Debug;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.LanguagePath;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.lexer.EmbeddingPresence;
import org.netbeans.spi.lexer.LanguageEmbedding;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.WeakSet;

/**
 *
 * @author Tim Boudreau
 */
final class AdhocLanguageHierarchy extends LanguageHierarchy<AdhocTokenId> implements Supplier<TokensInfo> {

    public static final String DUMMY_TOKEN_ID = "__dummyToken__";
    private final String mimeType;
    private static final Logger LOG = Logger.getLogger(AdhocLanguageHierarchy.class.getName());

    private final HierarchyInfo hinfo;
    static AtomicInteger CREATED = new AtomicInteger();
    private int id = CREATED.incrementAndGet();

    AdhocLanguageHierarchy(String mimeType) {
        this.mimeType = mimeType;
        LOG.log(Level.FINE, "Create lang hier for {0}", mimeType);
        hinfo = hierarchyInfo(mimeType);
    }

    int id() {
        return id;
    }

    static int hierarchiesCreated() { // Used to know when a Language instance may be stale
        return CREATED.get();
    }

    @Override
    public String toString() {
        return "AdhocLanguageHierarchy(" + Integer.toString(System.identityHashCode(this), 36)
                + ")";
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

    @Override
    protected Collection<AdhocTokenId> createTokenIds() {
        return hinfo.info().tokens();
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
        return new AdhocLexerNew(mimeType, info, this);
    }

    @Override
    protected Map<String, Collection<AdhocTokenId>> createTokenCategories() {
        return hinfo.info().categories();
    }

    boolean languageUpdated() {
        LOG.log(Level.FINEST, "Language updated for {0}",
                AdhocMimeTypes.loggableMimeType(mimeType));
        // Live lexers are keeping copies of the tokens list, and we
        // *want* them updated on the fly;  a lexer in progress
        // definitely shouldn't suddenly find itself with no
        // contents
//        AdhocMimeDataProvider.getDefault().gooseLanguage(mimeType);
        if (hinfo.initialized()) {
            // If we are inside a call to super.language(), we shouldn't
            // clear the language field, and the synchronization of that
            // method makes it trivial to deadlock
            wipeLanguage();
        }
        return false;
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

    Language<AdhocTokenId> languageUnsafe;

    Language<AdhocTokenId> languageUnsafe() {
        // XXX Evil deadlock work around
        if (languageUnsafe != null) {
            return languageUnsafe;
        }
        Field f = languageField();
        if (f != null) {
            try {
                languageUnsafe = (Language<AdhocTokenId>) f.get(this);
                if (languageUnsafe != null) {
                    return languageUnsafe;
                }
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                LOG.log(Level.INFO, null, ex);
            }
        }
        return languageUnsafe = language();
    }

    /**
     * Either we nuke the language field, or we have to reinitialize the entire
     * language infrastructure, which is too expensive to be practical a lot of
     * the time.
     */
    void wipeLanguage() {
        Field f = languageField();
        if (f != null) {
            try {
                Object o = f.get(this);
                if (o != null) {
                    synchronized (this) {
                        if (f.get(this) == o) {
                            f.set(this, null);
                            languageUnsafe = null;
                        }
                    }
                }
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                LOG.log(Level.INFO, null, ex);
            }
        }
    }

    @Override
    public TokensInfo get() {
        TokensInfo result = hinfo.info();
        return result;
    }

    private static final Map<String, HierarchyInfo> H_INFOS = new HashMap<>();

    static synchronized HierarchyInfo hierarchyInfo(String mime) {
        HierarchyInfo info = H_INFOS.get(mime);
        if (info == null) {
            info = new HierarchyInfo(mime);
            H_INFOS.put(mime, info);
        }
        return info;
    }

    static EmbeddedAntlrParser parserFor(String mime) {
        // XXX need to track the lifecycle of the parser and dispose?
        HierarchyInfo info = hierarchyInfo(mime);
        return info.parser();
    }

    public static void onNewEnvironment(String mime, BiConsumer<Extraction, GrammarRunResult<?>> run) {
        HierarchyInfo info = hierarchyInfo(mime);
        info.onReplace(run);
        // ensure init
        info.parser();
    }

    /**
     * It is possible to have multiple live AdhocLanguageHierarchy instances for
     * the same mime type (language being updated while lexing is running), so
     * we centralize the shared information.
     */
    static final class HierarchyInfo implements BiConsumer<Extraction, GrammarRunResult<?>> {

        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false);
        private TokensInfo info = TokensInfo.EMPTY;
        private EmbeddedAntlrParser parser;
        private final String mimeType;
        private final ReentrantReadWriteLock.ReadLock readLock;
        private final ReentrantReadWriteLock.WriteLock writeLock;
        private final Path grammarFilePath;
        private long expectedGrammarLastModified = Long.MIN_VALUE;
        private volatile boolean initialized;
        private final Set<BiConsumer<Extraction, GrammarRunResult<?>>> onReplaces = new WeakSet<>();
        private FileObject grammarFo;
        private Project project;
        public HierarchyInfo(String mimeType) {
            this.mimeType = mimeType;
            readLock = lock.readLock();
            writeLock = lock.writeLock();
            grammarFilePath = AdhocMimeTypes.grammarFilePathForMimeType(mimeType);
            // Caching these - looking up last modified is expensive, and this
            // makes it a bit cheaper
            grammarFo = FileUtil.toFileObject(grammarFilePath.toFile());
            project = FileOwnerQuery.getOwner(grammarFo);
        }

        void onReplace(BiConsumer<Extraction, GrammarRunResult<?>> r) {
            onReplaces.add(r);
        }

        boolean initialized() {
            return initialized;
        }

        public long grammarLastModified() throws IOException {
            if (project != null) {
                long result = RebuildSubscriptions.mostRecentGrammarLastModifiedInProject(project);
                if (result != Long.MIN_VALUE) {
                    return result;
                }
            }
            FileObject fo = grammarFo == null ?  FileUtil.toFileObject(grammarFilePath.toFile()) : grammarFo;
            if (fo != null) {
                return RebuildSubscriptions.mostRecentGrammarLastModifiedInProjectOf(fo);
            }
            return -1;
        }

        long isGrammarInfoUpToDate() throws IOException {
            if (Long.MIN_VALUE == expectedGrammarLastModified) {
                return -1;
            }
            long val = grammarLastModified();
            if (val > expectedGrammarLastModified) {
                return val;
            }
            return -1;
        }

        private void includeDummyToken(List<AdhocTokenId> ids) {
            // subtract one because ordinal() will add it
            AdhocTokenId nue = new AdhocTokenId(DUMMY_TOKEN_ID, ids.size() - 1);
            ids.add(nue);
        }

        public void accept(Extraction ext, GrammarRunResult<?> res) {
            LOG.log(Level.FINE, "{0} notified new env - notifying {1} listeners {2}",
                    new Object[]{this, onReplaces.size(), onReplaces});
            Debug.run(this, "New extraction " + ext.source() + " notifying " + onReplaces.size() + " listeners", () -> {
                StringBuilder sb = new StringBuilder("RunResult ").append(res)
                        .append(" Usable? ").append(res.isUsable()).append('\n');
                sb.append("Extraction - placeholder?").append(ext.isPlaceholder())
                        .append('\n')
                        .append(ext.logString().get());
                return sb.toString();
            }, () -> {
                for (BiConsumer<Extraction, GrammarRunResult<?>> r : onReplaces) {
                    try {
                        Debug.message("notify", r::toString);
                        r.accept(ext, res);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            });
        }

        private EmbeddedAntlrParser parser() {
            if (parser == null) {
                parser = EmbeddedAntlrParsers.forGrammar(
                        "hierarchyInfo:" + AdhocMimeTypes.loggableMimeType(mimeType),
                        FileUtil.toFileObject(
                                FileUtil.normalizeFile(
                                        AdhocMimeTypes.grammarFilePathForMimeType(mimeType)
                                                .toFile())));
                parser.listen(this);
            } else {
                if (parser.isDisposed()) {
                    Exception ex = new Exception("Parser was unexpectedly disposed");
                    LOG.log(Level.SEVERE, "Disposed " + this, ex);
                    parser = null;
                }
            }
            return parser;
        }

        private TokensInfo recreateInfo() {
            try {
                assert writeLock.isHeldByCurrentThread();
                EmbeddedAntlrParser p = parser();
                ParseTreeProxy proxy = p.parse(null).proxy();
                TokensInfo result = buildTokensInfo(proxy);
                expectedGrammarLastModified = grammarLastModified();
                return result;
            } catch (Exception ex) {
                Logger.getLogger(AdhocLanguageHierarchy.class.getName()).log(Level.SEVERE, "Exception getting token categories for "
                        + AdhocMimeTypes.loggableMimeType(mimeType), ex);
                return TokensInfo.EMPTY;
            }
        }

        private TokensInfo buildTokensInfo(ParseTreeProxy prox) {
            Map<String, Collection<AdhocTokenId>> cats = CollectionUtils.supplierMap(HashSet::new);
            List<AdhocTokenId> newIds = new ArrayList<>();
            List<ProxyTokenType> allTypes = prox.tokenTypes();
            Set<String> names = new HashSet<>(allTypes.size());
            for (ProxyTokenType ptt : allTypes) {
                AdhocTokenId id = new AdhocTokenId(ptt, names);
                newIds.add(id);
                cats.get(id.primaryCategory()).add(id);
            }
            LOG.log(Level.FINEST, "Update tokens for {0} with {1} for {2} on {3}",
                    new Object[]{AdhocMimeTypes.loggableMimeType(mimeType),
                        newIds.size(), this, Thread.currentThread()});
            Collections.sort(newIds);
            includeDummyToken(newIds);
            TokensInfo newInfo = new TokensInfo(newIds, cats);
            TokensInfo old = info;
            if (!newInfo.equals(old)) {
                info = newInfo;
            }
            return newInfo;
        }

        public TokensInfo info() {
            TokensInfo result = null;
            try {
                readLock.lock();
                try {
                    result = info;
                } finally {
                    readLock.unlock();
                }
                long id = result.id();
                if (result.isEmpty() || isGrammarInfoUpToDate() != -1) {
                    result = info = inWriteLockMaybeRebuild(id);
                }
                return result;
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, mimeType, ex);
                return result != null ? result : TokensInfo.EMPTY;
            }
        }

        private TokensInfo inWriteLockMaybeRebuild(long oldId) {
            TokensInfo result;
            writeLock.lock();
            try {
                result = info;
                if (result.id() == oldId) {
                    // if not, another thread did the update while we were
                    // waiting for the lock, so we don't need to
                    result = info = recreateInfo();
                }
            } finally {
                initialized = true;
                writeLock.unlock();
            }
            return result;
        }
    }
}
