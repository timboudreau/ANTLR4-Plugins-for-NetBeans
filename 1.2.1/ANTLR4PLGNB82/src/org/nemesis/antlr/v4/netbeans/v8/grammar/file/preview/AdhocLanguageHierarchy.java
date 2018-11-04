package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyTokenType;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;

/**
 *
 * @author Tim Boudreau
 */
class AdhocLanguageHierarchy extends LanguageHierarchy<AdhocTokenId> {

    private final AtomicReference<AntlrProxies.ParseTreeProxy> proxy;

    private String baseMimeType;

    AdhocLanguageHierarchy(AntlrProxies.ParseTreeProxy proxy) {
        this.proxy = new AtomicReference<>(proxy);
        baseMimeType = proxy.mimeType();
    }

    @Override
    protected Map<String, Collection<AdhocTokenId>> createTokenCategories() {
        Map<String, Collection<AdhocTokenId>> m = new HashMap<>();
        for (AdhocTokenId id : createTokenIds()) {
            String cat = id.primaryCategory();
            Collection<AdhocTokenId> c = m.get(cat);
            if (c == null) {
                c = new ArrayList<>(3);
                m.put(cat, c);
            }
            c.add(id);
        }
        return m;
    }

    void replaceProxy(AntlrProxies.ParseTreeProxy proxy) {
        this.proxy.set(proxy);
        ids = null;
        baseMimeType = proxy.mimeType();
    }

    @Override
    protected List<AdhocTokenId> createTokenIds() {
        return tokenIds((ignored, ids) -> {
            return ids;
        });
    }

    private List<AdhocTokenId> ids;

    private <T> T tokenIds(BiFunction<AntlrProxies.ParseTreeProxy, List<AdhocTokenId>, T> func) {
        if (ids != null) {
            return func.apply(proxy.get(), ids);
        }
        // Using the biFunction here ensures that for the lexer, the
        // parse tree proxy in use cannot be a different one than the IDs were
        // generated against, without requiring us to use a lock
        AntlrProxies.ParseTreeProxy proxy = this.proxy.get();
        ids = new ArrayList<>(proxy.tokenTypes().size());
        int maxType = 0;
        for (AntlrProxies.ProxyTokenType id : proxy.tokenTypes()) {
            ids.add(new AdhocTokenId(proxy, id));
            maxType = Math.max(maxType, id.type);
        }
        // We add a catch-all token type for cases where the proxy is out
        // of sync with the text we're parsing, so we have *something* to
        // return that is differentiated from the actual token types
        ProxyTokenType dummyToken = new ProxyTokenType(maxType + 1, null, "__dummyToken", "__dummyToken");
        ids.add(new AdhocTokenId(proxy, dummyToken));
        return func.apply(proxy, ids);
    }

    @Override
    protected Lexer<AdhocTokenId> createLexer(LexerRestartInfo<AdhocTokenId> lri) {
//        String txt = lri.input().readText().toString();
//        ParseTreeProxy pxx = proxy.get();
//        if (pxx != null && !txt.equals(pxx.text())) {
//            System.out.println("PARSE IMMED FOR '" + txt + "'");
//            replaceProxy(DynamicLanguageSupport.parseImmediately(pxx.mimeType(), txt));
//        }
        return tokenIds((px, ids) -> {
            return new AdhocLexer(lri, px, ids);
        });
    }

    @Override
    protected String mimeType() {
        return baseMimeType;
    }

    static final class AdhocLexer implements Lexer<AdhocTokenId> {

        private final LexerRestartInfo<AdhocTokenId> info;
        private final List<AdhocTokenId> ids;
        private final AntlrProxies.ParseTreeProxy proxy;
        private int cursor;
        private final int count;

        AdhocLexer(LexerRestartInfo<AdhocTokenId> info, AntlrProxies.ParseTreeProxy proxy, List<AdhocTokenId> ids) {
            this.info = info;
            this.ids = ids;
            if (info.state() instanceof AntlrProxies.ParseTreeProxy) {
                this.proxy = (AntlrProxies.ParseTreeProxy) info.state();
//                System.out.println("USING EXISTING STATE " + this.proxy.text());
            } else {
//                System.out.println("INFO IS " + info);
//                System.out.println("INPUT IS " + info.input());
                String txt = info.input().readText().toString();
                if (!txt.equals(proxy.text())) {
//                    System.out.println("REPARSE '" + truncated(txt) + "'");
                    proxy = DynamicLanguageSupport.parseImmediately(proxy.mimeType(), txt);
//                    System.out.println("PROXY TEXT '" + truncated(proxy.text()) + "'");
                } else {
//                    System.out.println("proxy text matches lexer input, no reparse");
                }
                this.proxy = proxy;
            }
            this.count = proxy.tokenCount();
//            System.out.println("HAVE " + proxy.tokenCount() + " tokens");
        }

        @Override
        public Token<AdhocTokenId> nextToken() {
            if (cursor == count) {
                return null;
            }
            if (nextIsEof) {
                nextIsEof = false;
                return null;
            }
            ProxyToken tok = proxy.tokens().get(cursor++);
            if (tok.isEOF()) {
//                System.out.println(cursor + ": EOF WITH TOKEN " + tok + " '" + tok.getText() + "'");
                StringBuilder sb = new StringBuilder();
                int ch;
                while ((ch = info.input().read()) != -1) {
                    sb.append((char) ch);
                }
                if (sb.length() > 0) {
//                    System.out.println(cursor + ": FINALE DUMMY TOKEN TEXT '" + sb + "'");
                    nextIsEof = true;
                    AdhocTokenId dummy = ids.get(ids.size() - 1);
                    return info.tokenFactory().createToken(dummy, sb.length());
                }
//                System.out.println(cursor + ": GOT EOF TOKEN WITH TEXT '" + tok.getText() + "'");
                return null;
            }
            int len = tok.getText().length();
            LexerInput in = info.input();
            StringBuilder sb = new StringBuilder();
            int c;
            int count = 0;
            while ((c = in.read()) != -1) {
                sb.append((char) c);
                count++;
                if (count == len) {
                    break;
                }
            }
            if (!tok.getText().equals(sb.toString())) {
//                System.out.println(cursor + ": TOKEN AND READ TEXT MISMATCH - TOKEN: '"
//                        + tok.getText() + "' but read '" + sb);
            }
            if (tok.getType() == -1 && tok.getStopIndex() < tok.getStartIndex()) {
                // Return EOF token
                String txt = info.input().readText().toString();
                if (txt.length() > 0 && txt.trim().isEmpty()) {
                    // Under some bizarre set of circumstances peculiar to the font coloring
                    // preview window, Antlr discards trailing whitespace, returns a token with
                    // a negative span and a type of EOF.  For that case, we manually return a
                    // whitespace token for the last text read from the input, and ensure that
                    // the *next* call to nextToken() returns null
                    nextIsEof = true;
                    return info.tokenFactory().createToken(this.ids.get(ids.size() - 1), 2);
                } else {
                    return null;
                }
            }

            AdhocTokenId tid = ids.get(tok.getType() + 1);
            int prevPos = pos;
            pos = tok.getStopIndex();
            try {
                return info.tokenFactory().createToken(tid, (tok.getStopIndex() - tok.getStartIndex()));
            } catch (IndexOutOfBoundsException ex) {
                throw new IllegalStateException("IOOBE with token '" + tok.getText() + "' "
                        + " tokenType=" + tok.getType()
                        + " tokenName=" + tid.name()
                        + " cursor=" + cursor
                        + " startIndex " + tok.getStartIndex()
                        + " stopIndex=" + tok.getStopIndex()
                        + " token " + tok.getTokenIndex() + " of " + proxy.tokenCount()
                        + " last pos was " + prevPos,
                        ex);
            }
        }
        boolean nextIsEof;
        int pos = 0;

        @Override
        public Object state() {
            return proxy;
        }

        @Override
        public void release() {
            // do nothing
        }
    }
}
