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

import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.Document;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.nemesis.debug.api.Debug;
import org.nemesis.misc.utils.ActivityPriority;
import org.netbeans.api.lexer.Token;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocLexerNew implements Lexer<AdhocTokenId> {

    private static final Logger LOG = Logger.getLogger(AdhocLexerNew.class.getName());
    private final LexerRestartInfo<AdhocTokenId> info;
    private final String mimeType;
    private final Supplier<? extends TokensInfo> supp;
    private List<ProxyToken> tokens;
    private int cursor;

    AdhocLexerNew(String mimeType, LexerRestartInfo<AdhocTokenId> info, Supplier<? extends TokensInfo> supp) {
        this.mimeType = mimeType;
        this.info = info;
        this.supp = supp;
        if (info.state() instanceof Integer) {
            cursor = (Integer) info.state();
        }
    }

    List<AdhocTokenId> ids() {
        return supp.get().tokens();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "("
                + Integer.toString(System.identityHashCode(this), 36)
                + AdhocMimeTypes.loggableMimeType(mimeType)
                + " over " + currentLexedName()
                + " has iter " + (tokens != null)
                + ")";
    }

    private String currentLexedName() {
        FileObject result = AdhocLanguageHierarchy.file(info);
        if (result != null) {
            return result.getName();
        }
        Document doc = AdhocLanguageHierarchy.document(info);
        if (doc != null) {
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            if (fo != null) {
                return fo.getName();
            }
            return doc.toString();
        }
        return info.input().toString();
    }

    private ParseTreeProxy proxy;

    private ParseTreeProxy proxy() {
        if (proxy != null) {
            return proxy;
        }
        int count = 0;
        LexerInput in = info.input();
        while (in.read() != -1) {
            count++;
        }
        CharSequence text = in.readText();
        in.backup(count);
        try {
            return Debug.runObjectThrowing("Lex " + AdhocMimeTypes.loggableMimeType(mimeType) + " "
                    + currentLexedName(), "", () -> {
                        return ActivityPriority.REALTIME.wrapThrowing(() -> {
                            EmbeddedAntlrParser parser = AdhocLanguageHierarchy.parserFor(mimeType);
                            EmbeddedAntlrParserResult pres = parser.parse(text);
                            ParseTreeProxy result = pres.proxy();
                            LOG.log(Level.FINE, "Lexer gets new mime {0} parser "
                                    + "result tokens hash {1} hier tokens hash {2}",
                                    new Object[]{result.loggingInfo(), pres.grammarTokensHash(),
                                        AdhocLanguageHierarchy.hierarchyInfo(mimeType).grammarTokensHash()});

                            AdhocLanguageHierarchy.maybeUpdateTokenInfo(mimeType, pres);
                            Document doc = AdhocLanguageHierarchy.document(info);
                            if (doc != null) {
                                Debug.message("document", doc::toString);
                                AdhocReparseListeners.reparsed(mimeType, doc, pres);
                            }
                            if (result.isUnparsed()) {
                                Debug.failure("unparsed", parser::toString);
                                LOG.log(Level.FINE, "Unparsed result for {0}", currentLexedName());
                            }
                            return proxy = result;
                        });
                    });
        } catch (Exception ex) {
            String nm = currentLexedName();
            LOG.log(Level.WARNING, "Thrown in embedded parser for " + nm, ex);
            return proxy = AntlrProxies.forUnparsed(Paths.get(""), nm, text);
        }
    }

    private List<ProxyToken> iterator() {
        if (tokens != null) {
            return tokens;
        }
        return tokens = proxy().tokens();
    }

    private AdhocTokenId idFor(ProxyToken tok) {
        int index = tok.getType() + 1;
        List<AdhocTokenId> ids = ids();
        if (index < ids.size()) {
            return ids.get(index);
        }
        return ids.get(ids.size() - 1);
    }

    @Override
    @SuppressWarnings("empty-statement")
    public Token<AdhocTokenId> nextToken() {
        LexerInput in = info.input();
        List<ProxyToken> tokenList = iterator();
        if (cursor >= tokenList.size()) {
            return trailingJunkToken(in);
        }
        ProxyToken tok = tokenList.get(cursor++);
        if (tok.isEOF()) {
            return trailingJunkToken(in);
        }
        int length = tok.length();
        for (int i = 0; i < length; i++) {
            in.read();
        }
        AdhocTokenId id = idFor(tok);
        int actualLength = Math.min(proxy.text().length() - tok.getStartIndex(), length);
        return token(id, actualLength);
    }

    private static AdhocTokenId fabricateDummyToken() {
        AdhocTokenId id = new AdhocTokenId(AntlrProxies.ERRONEOUS_TOKEN_NAME, 0);
        return id;
    }

    private Token<AdhocTokenId> trailingJunkToken(LexerInput in) {
        // In the case the grammar did not include EOF, and so the
        // parser decided it was done ahead of the end of the text,
        // NetBeans lexer infrastructure does not tolerate any un-lexed
        // tokens, which is why we add one "dummy" token to the token
        // list
        int count = 0;
        StringBuilder sb = null;
        for (int c = in.read(); c != -1; c = in.read(), count++) {
            if (sb == null) {
                sb = new StringBuilder();
            }
            sb.append((char) c);
        }
        List<AdhocTokenId> ids = ids();
        AdhocTokenId dummy = ids.isEmpty() ? fabricateDummyToken() : ids.get(ids.size() - 1);
        if (count > 0) {
            LOG.log(Level.FINE, "Lexer returned insufficient tokens "
                    + "for {0}; synthesizing a dummy token for {1}",
                    new Object[]{
                        currentLexedName(),
                        Strings.escape(sb, Escaper.NEWLINES_AND_OTHER_WHITESPACE)
                    });
            return token(dummy, count);
        }
        if (in.readLength() > 0) {
            int len = in.readLength();
            LOG.log(Level.FINE, "Lexer read length inconsistent "
                    + "for {0} - read {1}",
                    new Object[]{
                        currentLexedName(),
                        len
                    });
            return token(dummy, len);
        }
        return null;
    }

    static Pattern ERRMP = Pattern.compile("tokenLength=(\\d+)\\s>\\s(\\d+).*");

    private Token<AdhocTokenId> token(AdhocTokenId targetId, int length) {
        boolean broken = false;
        try {
            if (targetId.canBeFlyweight()) {
                return info.tokenFactory().getFlyweightToken(targetId, targetId.literalName());
            }
            return info.tokenFactory().createToken(targetId, length);
        } catch (IndexOutOfBoundsException ex) {
            if (ex.getMessage() != null) {
                Matcher m = ERRMP.matcher(ex.getMessage());
                if (m.find()) {
                    int realLength = Integer.parseInt(m.group(2));
                    return info.tokenFactory().createToken(targetId, realLength);
                }
            }

            List<AdhocTokenId> ids = ids();
            broken = true;
            int ix = ids.indexOf(targetId);
            if (ix < 0) {
                ix = ids.size();
            }
            for (int i = ix - 1; i >= 0; i--) {
                targetId = ids.get(i);
                try {
                    return info.tokenFactory().createToken(targetId, length);
                } catch (ArrayIndexOutOfBoundsException ex2) {

                }
            }
            throw ex;
        } finally {
            if (broken) {
                // Force the data provider to nuke the current LanguageHierarchy
                // so it gets rebuilt - we are running off a stale token
//              // list
                LOG.log(Level.FINE, "Force replacement of language "
                        + "hierarchy - token list is broken for {0}", this);
                AdhocMimeDataProvider.getDefault().gooseLanguage(mimeType);
            }
        }
    }

    @Override
    public Object state() {
        return cursor;
    }

    @Override
    public void release() {
        tokens = null;
    }
}
