package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.List;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.Reason.CREATE_LEXER;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyToken;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;

/**
 *
 * @author Tim Boudreau
 */
final class AdhocLexer implements Lexer<AdhocTokenId> {

    private final LexerRestartInfo<AdhocTokenId> info;
    private final List<AdhocTokenId> ids;
    private final List<AdhocTokenId> origIds;
    private final AntlrProxies.ParseTreeProxy proxy;
    private boolean nextIsEof;
    private int pos = 0;
    private int cursor = -1;
    private final int count;

    AdhocLexer(LexerRestartInfo<AdhocTokenId> info, AntlrProxies.ParseTreeProxy proxy, List<AdhocTokenId> ids, AdhocLanguageHierarchy creator) {
        this.info = info;
        this.origIds = ids;
        // We never seem to have a state object passed, but just in case...
        if (info.state() instanceof AntlrProxies.ParseTreeProxy) {
            this.proxy = (AntlrProxies.ParseTreeProxy) info.state();
        } else {
            // Extract the text from the lexer input (something mysterious
            // pre-reads it for us)
            String txt = info.input().readText().toString();
            // Check if it does not match the proxy we were initialized with
            // DynamicLanguageSupport.setTextContext() *tries* to give access
            // to the text being parsed so we can pre-create a parse during
            // language initialization, but that is not the only code path
            if (!txt.equals(proxy.text())) {
                // Sometimes we get empty text.
                // Sometimes we get empty text for a document that is not empty,
                // and the infrastructure will complain that we didn't process
                // characters that weren't there (hence all the unwinding of the
                // LexerInput in nextToken() that shouldn't be necessary but is)
                if (!txt.isEmpty()) {
                    // If we have mismatching text, force antlr generation,
                    // compilation and execution with the text we got RIGHT NOW
                    // so we have the right stuff
                    proxy = DynamicLanguageSupport.parseImmediately(proxy.mimeType(), txt, CREATE_LEXER);
                    // And push it back into the AdhocLanguageHierarchy so it is
                    // available to the next lexer and it won't have to do that
                    // if nothing has changed
                    creator.setProxy(proxy);
                    ids = (List<AdhocTokenId>) creator.createTokenIds();
                } else {
                    proxy = proxy.toEmptyParseTreeProxy(txt);
                }
            }
//            if (proxy.isUnparsed()) {
//                System.err.println("PROXY IS UNPARSED");
//            }
            this.proxy = proxy;
        }
        this.ids = ids;
        this.count = proxy.tokenCount();
    }

    private AdhocTokenId tokenIdForProxyToken(ProxyToken token) {
        int typeOffsetInList = token.getType() + 1; // 0 is EOF, so all id indices are off by one
        if (origIds != ids) {
            // If this lexer was originally created off a ParseTreeProxy created by
            // AntlrProxies.createUnparsed(), then the editor infrastructure's
            // WrapTokenId cache is an array of 2 token types, and all hell will
            // break loose if we return a token with a higher ordinal.  Also
            // an issue on at least the first reparse after a token type has
            // been added via editing the grammar.  This does mean that we will
            // return token IDs that are just plain wrong the first time after
            // a grammar edit or grammar initialization, but there is no path
            // to get the editor infrastructure to discard this cache *while
            // the lexer is being created using it*.
            int type = Math.min(origIds.size() - 1, typeOffsetInList);
            if (type < ids.size()) {
                return ids.get(typeOffsetInList);
            } else {
                return origIds.get(typeOffsetInList);
            }
        } else {
            // Do the sane thing
            return ids.get(typeOffsetInList);
        }
    }

    private Token<AdhocTokenId> dummyToken(int length) {
        // Ensure we don't use a dummy token with a higher ordinal than the
        // lexer infrastructure has cached
        AdhocTokenId id = origIds == ids ? ids.get(ids.size() - 1)
                : origIds.get(origIds.size() - 1);
        return info.tokenFactory().createToken(id, length);
    }

    @Override
    public Token<AdhocTokenId> nextToken() {
        if (cursor == count) {
            // We reached the normal end of the parse
            return null;
        }
        if (nextIsEof) {
            // We got a screwball EOF token that had some text content, and
            // returned it as a dummy token in the previous invocation,
            // OR the lexer did not parse all characters in the file, and we
            // hit EOF but returned a dummy token for the tail of the lexer
            // input
            nextIsEof = false;
            return null;
        }
        ProxyToken tok = proxy.tokens().get(++cursor);
//        System.out.println("TOK " + proxy.tokenTypeForInt(tok.getType()) + " '" + truncated(tok.getText()) + "'");
        if (tok.isEOF()) {
//            System.out.println(cursor + ": EOF WITH TOKEN " + tok + " '" + tok.getText() + "'");
            // Wind the LexerInput all the way down to the end (occasionally we get
            // a screwey LexerInput that will return a few million Character.MAX_VALUEs...sigh)
            StringBuilder sb = new StringBuilder();
            int ch;
            // XXX we do not actually need to capture these characters in a stringbuilder - just
            // useful for logging
            while ((ch = info.input().read()) != -1) {
                sb.append((char) ch);
            }
            if (sb.length() > 0) {
                // Screwball EOF token handling I
                nextIsEof = true;
                return dummyToken(sb.length());
            }
            // Normal EOF token and no extraneous input - we're done
            return null;
        }
        // Now wind the LexerInput forward to (what we hope is) the end of the token in
        // its world as well as ours
        int len = tok.getText().length();
        LexerInput in = info.input();
        // XXX don't need stringbuilder, just a count, once this is fully debugged
        StringBuilder sb = new StringBuilder();
        int c, count = 0;
        while ((c = in.read()) != -1) {
            sb.append((char) c);
            count++;
            if (count == len) {
                break;
            }
        }
        // If the token contains different text than the LexerInput provided,
        // something is wrong - perhaps the grammar skipped some tokens?
        if (!tok.getText().equals(sb.toString())) {
        }
        // If we didn't catch it earlier, we occasionally get Antlr tokens
        // which stop before they start as final tokens - generally this is
        // a trailing newline.  This seems only to happen with the fonts and
        // colors preview
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
                return dummyToken(2);
            } else {
                return null;
            }
        }
        // At last, normal token handling
        AdhocTokenId tid = tokenIdForProxyToken(tok);
        int prevPos = pos; // XXX deleteme - for debug message below in case of an exception
        pos = tok.getStopIndex();
        try {
            // XXX - shouldn't this be (tok.getStopIndex() - tok.getStartIndex()) + 1?  Why does
            // this work?
            return info.tokenFactory().createToken(tid, tok.getStopIndex() - tok.getStartIndex());
        } catch (IndexOutOfBoundsException ex) {
            throw new IllegalStateException("IOOBE with token '" + tok.getText()
                    + "' " + " tokenType=" + tok.getType() + " tokenName="
                    + tid.name() + " cursor=" + cursor + " startIndex "
                    + tok.getStartIndex() + " stopIndex=" + tok.getStopIndex()
                    + " token " + tok.getTokenIndex() + " of "
                    + proxy.tokenCount() + " last pos was " + prevPos, ex);
        }
    }

    @Override
    public Object state() {
//        return proxy;
        return null;
    }

    @Override
    public void release() {
        // do nothing
    }

}
