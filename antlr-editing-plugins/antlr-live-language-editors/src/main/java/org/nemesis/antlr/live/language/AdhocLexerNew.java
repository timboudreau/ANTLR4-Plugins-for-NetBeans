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
package org.nemesis.antlr.live.language;

import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.nemesis.debug.api.Debug;
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
    private final EmbeddedAntlrParser parser;
    private final String mimeType;
    private final Supplier<? extends TokensInfo> supp;
    private List<ProxyToken> tokens;
    private int cursor;

    AdhocLexerNew(String mimeType, LexerRestartInfo<AdhocTokenId> info, EmbeddedAntlrParser antlrParser, Supplier<? extends TokensInfo> supp) {
        this.mimeType = mimeType;
        this.info = info;
        this.parser = antlrParser;
        this.supp = supp;
        if (info.state() instanceof Integer) {
            System.out.println("Create lexer with state " + info.state());
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

    private ParseTreeProxy proxy() {
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
                        ParseTreeProxy result = parser.parse(text);
                        Document doc = AdhocLanguageHierarchy.document(info);
                        if (doc != null) {
                            Debug.message("document", doc::toString);
                            GrammarRunResult<?> gbrg = parser.lastResult();
                            AdhocReparseListeners.reparsed(mimeType, doc, gbrg, result);
                        }
                        if (result.isUnparsed()) {
                            Debug.failure("unparsed", () -> result.text().toString());
                            LOG.log(Level.FINE, "Unparsed result for {0}", currentLexedName());
                        }
                        return result;
                    });
        } catch (Exception ex) {
            String nm = currentLexedName();
            LOG.log(Level.WARNING, "Thrown in embedded parser for " + nm, ex);
            return AntlrProxies.forUnparsed(Paths.get(""), nm, text);
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
        return token(id, length);
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
        AdhocTokenId dummy = ids.get(ids.size() - 1);
        if (count > 0) {
            LOG.log(Level.WARNING, "Lexer returned insufficient tokens "
                    + "for {0}; synthesizing a dummy token for {1}",
                    new Object[]{
                        currentLexedName(),
                        Strings.escape(sb, Escaper.NEWLINES_AND_OTHER_WHITESPACE)
                    });
            return token(dummy, count);
        }
        if (in.readLength() > 0) {
            int len = in.readLength();
            LOG.log(Level.WARNING, "Lexer read length inconsistent "
                    + "for {0} - read {1}",
                    new Object[]{
                        currentLexedName(),
                        len
                    });
            return token(dummy, len);
        }
        return null;
    }

    private Token<AdhocTokenId> token(AdhocTokenId targetId, int length) {
        boolean broken = false;
        try {
            if (targetId.canBeFlyweight()) {
                return info.tokenFactory().getFlyweightToken(targetId, targetId.literalName());
            }
            return info.tokenFactory().createToken(targetId, length);
        } catch (ArrayIndexOutOfBoundsException ex) {
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
