package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.List;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
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
    private final AntlrProxies.ParseTreeProxy proxy;
    private int cursor;
    private final int count;

    AdhocLexer(LexerRestartInfo<AdhocTokenId> info, AntlrProxies.ParseTreeProxy proxy, List<AdhocTokenId> ids) {
        this.info = info;
        this.ids = ids;
        if (info.state() instanceof AntlrProxies.ParseTreeProxy) {
            System.out.println("GOT A RESTART INFO WITH STATE " + info.state());
            this.proxy = (AntlrProxies.ParseTreeProxy) info.state();
            //                System.out.println("USING EXISTING STATE " + this.proxy.text());
        } else {
            //                System.out.println("INFO IS " + info);
            //                System.out.println("INPUT IS " + info.input());
            String txt = info.input().readText().toString();
            if (!txt.equals(proxy.text())) {
                if (!txt.isEmpty()) {
                    proxy = DynamicLanguageSupport.parseImmediately(proxy.mimeType(), txt);
                } else {
                    proxy = proxy.toEmptyParseTreeProxy(txt);
                }
//                //                    System.out.println("PROXY TEXT '" + truncated(proxy.text()) + "'");
            } else {
                //                    System.out.println("proxy text matches lexer input, no reparse");
            }
            this.proxy = proxy;
        }
        this.count = proxy.tokenCount();
        //            System.out.println("HAVE " + proxy.tokenCount() + " tokens");
    } //                System.out.println("USING EXISTING STATE " + this.proxy.text());
    //                System.out.println("INFO IS " + info);
    //                System.out.println("INPUT IS " + info.input());
    //                    System.out.println("REPARSE '" + truncated(txt) + "'");
    //                    System.out.println("PROXY TEXT '" + truncated(proxy.text()) + "'");
    //                    System.out.println("proxy text matches lexer input, no reparse");
    //            System.out.println("HAVE " + proxy.tokenCount() + " tokens");

    private void rewind(LexerInput in) {
        for (int i = 0; i < 10;) {
            in.backup(1);

        }
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
        AntlrProxies.ProxyToken tok = proxy.tokens().get(cursor++);
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
            return info.tokenFactory().createToken(tid, tok.getStopIndex() - tok.getStartIndex());
        } catch (IndexOutOfBoundsException ex) {
            throw new IllegalStateException("IOOBE with token '" + tok.getText() + "' " + " tokenType=" + tok.getType() + " tokenName=" + tid.name() + " cursor=" + cursor + " startIndex " + tok.getStartIndex() + " stopIndex=" + tok.getStopIndex() + " token " + tok.getTokenIndex() + " of " + proxy.tokenCount() + " last pos was " + prevPos, ex);
        }
    }
    boolean nextIsEof;
    int pos = 0;

    @Override
    public Object state() {
        return proxy;
//        return null;
    }

    @Override
    public void release() {
        // do nothing
    }

}
