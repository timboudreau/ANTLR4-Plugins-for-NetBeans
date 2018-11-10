package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyTokenType;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
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
}
