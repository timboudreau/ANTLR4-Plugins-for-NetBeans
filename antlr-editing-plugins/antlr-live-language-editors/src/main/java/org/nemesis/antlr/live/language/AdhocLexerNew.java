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

import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import javax.swing.text.Document;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocLexerNew implements Lexer<AdhocTokenId> {

    private final LexerRestartInfo<AdhocTokenId> info;
    private final EmbeddedAntlrParser parser;
    private final List<AdhocTokenId> ids;
    private final String mimeType;

    AdhocLexerNew(String mimeType, LexerRestartInfo<AdhocTokenId> info, EmbeddedAntlrParser antlrParser, List<AdhocTokenId> ids) {
        this.mimeType = mimeType;
        this.info = info;
        this.parser = antlrParser;
        this.ids = ids;
    }

    private ParseTreeProxy proxy() {
        int count = 0;
        LexerInput in = info.input();
        while (in.read() != -1) {
            count++;
        }
        String text = in.readText().toString();
        in.backup(count);
        try {
            ParseTreeProxy result = parser.parse(text);
            Document doc = AdhocLanguageHierarchy.document(info);
            if (doc != null) {
                GrammarRunResult<?> gbrg = parser.lastResult();
                AdhocReparseListeners.reparsed(mimeType, doc, gbrg, result);
            }
            return result;
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            return AntlrProxies.forUnparsed(Paths.get(""), "unparsed", text);
        }
    }

    private Iterator<ProxyToken> iter;

    private Iterator<ProxyToken> iterator() {
        if (iter != null) {
            return iter;
        }
        return iter = proxy().tokens().iterator();
    }

    @Override
    public Token<AdhocTokenId> nextToken() {
        LexerInput in = info.input();
        Iterator<ProxyToken> it = iterator();
        if (!it.hasNext()) {
            return null;
        }
        ProxyToken tok = it.next();
        if (tok.isEOF()) {
            return null;
        }
        int length = tok.length();
        for (int i = 0; i < length; i++) {
            in.read();
        }
        AdhocTokenId id = ids.get(tok.getType() + 1);
        return info.tokenFactory().createToken(id, length);
    }

    @Override
    public Object state() {
        return null;
    }

    @Override
    public void release() {
        iter = null;
    }
}
