package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.DynamicLanguageSupport.truncated;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.Reason.CREATE_LEXER;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyTokenType;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.LanguagePath;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.EmbeddingPresence;
import org.netbeans.spi.lexer.LanguageEmbedding;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.netbeans.spi.lexer.TokenFactory;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
final class AdhocLanguageHierarchy extends LanguageHierarchy<AdhocTokenId> {

    private ParseTreeProxy proxy;
    private List<ProxyTokenType> lastTypes;
    private List<AdhocTokenId> ids;
    private Map<String, Collection<AdhocTokenId>> tokenCategories;
    public static final String DUMMY_TOKEN_ID = "__dummyToken__";
    private final String mimeType;

    AdhocLanguageHierarchy(String mimeType) {
        this.mimeType = mimeType;
        debugLog("Created new hierarchy for " + mimeType);
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
    protected Lexer<AdhocTokenId> createLexer(LexerRestartInfo<AdhocTokenId> lri) {
        String text = drain(lri.input());
        synchronized (this) {
            if (this.proxy == null || !Objects.equals(text, this.proxy.text())) {
                setProxy(DynamicLanguageSupport.parseImmediately(mimeType, text, Reason.CREATE_LEXER));
            }
            return new AdhocLexer(lri, proxy, ids, this);
        }
    }

//    @Override
    protected Lexer<AdhocTokenId> ycreateLexer(LexerRestartInfo<AdhocTokenId> lri) {
//        debugLog("CREATE LEXER FOR lri " + System.identityHashCode(lri));
        new Exception("CREATE LEXER FOR lri " + System.identityHashCode(lri));
        return new AdhocLexer3(cursorSupplier(lri), mimeType);
    }

    protected Lexer<AdhocTokenId> xcreateLexer(LexerRestartInfo<AdhocTokenId> lri) {
//        debugLog("CREATE LEXER FOR lri " + System.identityHashCode(lri));
        new Exception("CREATE LEXER FOR lri " + System.identityHashCode(lri)).printStackTrace(System.out);
        return new AdhocLexer2(lazySupplier(lri));
    }

    private Supplier<List<Token<AdhocTokenId>>> lazySupplier(LexerRestartInfo<AdhocTokenId> lri) {
        return () -> {
            return tokensSupplier(lri, drainLexerInput(lri)).get();
        };
    }

    private void debugLog(String txt) {
        String toLog = "[" + Thread.currentThread().getId() + ":"
                + Thread.currentThread().getName()
                + ":"
                + System.identityHashCode(this) + "]" + txt;
        System.out.println(toLog);
    }

    private String drainLexerInput(LexerRestartInfo<AdhocTokenId> lri) {
        StringBuilder sb = new StringBuilder();
        LexerInput in = lri.input();
        debugLog("DRAIN LEXER INPUT - LRI STATE " + lri.state() + " readLength " + lri.input().readLength()
                + " READ TEXT '" + lri.input().readText() + "'");
        // Sometimes we get a LexerInput that has been chewed on already
        if (in.readLengthEOF() > 0) {
            debugLog("BACKING UP LRI " + System.identityHashCode(lri) + " BY " + in.readLengthEOF());
            in.backup(in.readLengthEOF());
        }
        int count = 0;
        try {
            for (;; ++count) {
                int ch = in.read();
                if (ch == -1) {
                    debugLog("READ " + sb.length() + " input chars. Read length: " + in.readLength()
                            + " with eof " + in.readLengthEOF());
                    // We cannot create the tokens ahead of time, before the
                    // lexer is instantiated and returned, or we trigger an NPE
                    // in LexerInputOperation referencing its lexer field which
                    // has not been assigned yet - so the token factory in the
                    // restart info cannot yet be used.  This way we do it lazily,
                    // so the lexer will construct the token list the first time
                    // it is asked for a token
                    return sb.toString();
                } else {
                    sb.append((char) ch);
                }
            }
        } finally {
            System.out.println("READ " + count + " CHARS FROM lri " + System.identityHashCode(lri));
//            debugLog("Back up input by " + count);
//            in.backup(count);
        }
    }

    private synchronized Supplier<List<Token<AdhocTokenId>>> tokensSupplier(LexerRestartInfo<AdhocTokenId> lri, String text) {
        ParseTreeProxy currProxy;
        List<AdhocTokenId> currIds;
        synchronized (this) {
            currProxy = this.proxy;
            if (currProxy == null || currProxy.isUnparsed() || !Objects.equals(text, currProxy.text())) {
                if (text.isEmpty() && currProxy != null) {
                    debugLog("USE EMPTY PROXY");
                    currProxy = setProxy(currProxy.toEmptyParseTreeProxy(""));
                } else {
                    debugLog("REBOOT PROXY");
                    currProxy = setProxy(DynamicLanguageSupport.parseImmediately(
                            mimeType(), text, Reason.CREATE_LEXER));
                }
                currIds = this.ids;
            } else {
                currIds = this.ids;
            }
        }
        final ParseTreeProxy px = currProxy;
        final List<AdhocTokenId> cids = currIds;
        return () -> {
            return readTokensList(lri, text, px, cids);
        };
    }

    private String drain(LexerInput input) {
        if (input.readLengthEOF() > 0) {
            System.out.println("LexerInput arrived in strange state, backing up " + input.readLengthEOF()
                    + " EXISTING TEXT: '" + input.readText() + "'");
            input.backup(input.readLengthEOF());
        }
        for (;;) {
            if (input.read() == -1) {
                String result = input.readText().toString();
                int backupBy = input.readLengthEOF();
                System.out.println("READ " + result.length() + " chars, backup by " + backupBy);
                input.backup(backupBy);
                return result;
            }
        }
    }

//    static {
//        try {
//            Field f = LexerInput.class.getDeclaredField("LOG");
//            f.setAccessible(true);
//            Logger logger = (Logger) f.get(null);
//            logger.setLevel(Level.ALL);
//        } catch (NoSuchFieldException ex) {
//            Exceptions.printStackTrace(ex);
//        } catch (SecurityException ex) {
//            Exceptions.printStackTrace(ex);
//        } catch (IllegalArgumentException ex) {
//            Exceptions.printStackTrace(ex);
//        } catch (IllegalAccessException ex) {
//            Exceptions.printStackTrace(ex);
//        }
//    }
    static void inspect(LexerInput input) {
        try {
            Field f = LexerInput.class.getDeclaredField("operation");
            f.setAccessible(true);
            Object o = f.get(input);
            Class<?> c = o.getClass();
            System.out.println("OPERATION CLASS " + c.getName());
            for (Field ff : c.getDeclaredFields()) {
                ff.setAccessible(true);
                Object o1 = ff.get(o);
                if (o1 != null) {
                    System.out.println(ff.getName() + " = " + o1 + "(" + o1.getClass().getName() + ")");
                } else {
                    System.out.println(ff.getName() + " = " + null);
                }
            }
        } catch (NoSuchFieldException ex) {
            Exceptions.printStackTrace(ex);
        } catch (SecurityException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IllegalArgumentException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IllegalAccessException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private Supplier<Iterator<Token<AdhocTokenId>>> cursorSupplier(LexerRestartInfo<AdhocTokenId> info) {
        System.out.println("STATE: " + info.state());
        if (info.state() instanceof LexerInputCursor) {
            return () -> {
                System.out.println("USE EXISTING LEXER STATE " + info.state());
                Iterator<Token<AdhocTokenId>> cur = (Iterator<Token<AdhocTokenId>>) info.state();
                return cur;
            };
        }
        ParseTreeProxy currProxy;
        LexerInput input = info.input();
        inspect(input);
        int length;
        synchronized (this) {
            final List<AdhocTokenId> origIds = this.ids;
            String txt = info.input().readText().toString();
            length = txt.length();
            debugLog("READ " + txt.length() + " chars from " + System.identityHashCode(info) + ": '" + truncated(txt) + "' rle " + input.readLengthEOF());
            if (!txt.equals(this.proxy.text())) {
                if (!txt.isEmpty()) {
                    currProxy = DynamicLanguageSupport.parseImmediately(mimeType, txt, CREATE_LEXER);
                    System.err.println("REBOOT PROXY '" + truncated(currProxy.text()) + "'");
                    setProxy(currProxy);
                } else {
                    currProxy = setProxy(this.proxy.toEmptyParseTreeProxy(txt));
                    new Exception("CREATE EMPTY TREE FOR TEXT '" + txt + "' FROM INPUT").printStackTrace(System.err);
                }
            } else {
                System.err.println("proxy text matches lexer input, no reparse");
                currProxy = this.proxy;
            }
            if (currProxy.isUnparsed()) {
                System.err.println("PROXY IS UNPARSED");
            }
            List<AdhocTokenId> currIds = createTokenIds();
            final ParseTreeProxy px = currProxy;
            List<ProxyToken> tokens = px.tokens();
            return () -> {
                return new LexerInputCursor(tokens, info, length, info.tokenFactory(), currIds, origIds);
            };
//            if (length == 0) {
//                if (!trailingNewline) {
//                    return Collections::emptyIterator;
//                } else {
//                    return () -> {
//                        Token<AdhocTokenId> tok = info.tokenFactory().createToken(ids.get(ids.size() - 1), Math.max(1, input.readLengthEOF()));
//                        return Collections.<Token<AdhocTokenId>>singletonList(tok).iterator();
//                    };
//                }
//            }
//            currProxy = this.proxy;
//            if (currProxy == null || currProxy.isUnparsed() || !Objects.equals(text, currProxy.text())) {
//                if (text.isEmpty() && currProxy != null) {
//                    debugLog("USE EMPTY PROXY");
//                    currProxy = currProxy.toEmptyParseTreeProxy("");
//                } else {
//                    debugLog("REBOOT PROXY");
//                    currProxy = setProxy(DynamicLanguageSupport.parseImmediately(
//                            mimeType(), text, Reason.CREATE_LEXER));
//                }
//                currIds = this.ids;
//            } else {
//                currIds = this.ids;
//            }
        }
    }

    static final class LexerInputCursor implements Iterator<Token<AdhocTokenId>> {

        private final List<ProxyToken> tokens;
        private final LexerRestartInfo<AdhocTokenId> info;
        private final int inputLength;
        private final TokenFactory<AdhocTokenId> fac;
        private int cursor = -1;
        private final AdhocTokenId dummyId;
        private final List<AdhocTokenId> tokenIds;
        private Token<AdhocTokenId> nextToken;
        private boolean nextIsEof;
        private int lriPosition;
        private final List<AdhocTokenId> origIds;

        public LexerInputCursor(List<ProxyToken> tokens, LexerRestartInfo<AdhocTokenId> info, int inputLength, TokenFactory<AdhocTokenId> fac, List<AdhocTokenId> tokenIds, List<AdhocTokenId> origIds) {
            this.tokens = tokens;
            this.info = info;
            this.inputLength = inputLength;
            this.fac = fac;
            this.dummyId = tokenIds.get(tokenIds.size() - 1);
            this.tokenIds = tokenIds;
            this.origIds = origIds;
        }

        @Override
        public String toString() {
            return "LexerInputCursor{" + "inputLength=" + inputLength + ", cursor=" + cursor
                    + ", lriPosition=" + lriPosition
                    + " with " + tokens.size() + " tokens of " + tokenIds.size() + " types" + '}';
        }

        @Override
        public boolean hasNext() {
            if (nextToken == null) {
                nextToken = findNextToken();
            }
            return nextToken != null;
        }

        @Override
        public Token<AdhocTokenId> next() {
            if (nextToken == null) {
                throw new IndexOutOfBoundsException("No next token in " + this);
            }
            return nextToken;
        }

        private AdhocTokenId tokenIdForProxyToken(ProxyToken token) {
            int typeOffsetInList = token.getType() + 1; // 0 is EOF, so all id indices are off by one
            if (origIds != tokenIds) {
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
                if (type < tokenIds.size()) {
                    return tokenIds.get(typeOffsetInList);
                } else {
                    return origIds.get(typeOffsetInList);
                }
            } else {
                // Do the sane thing
                return tokenIds.get(typeOffsetInList);
            }
        }

        private Token<AdhocTokenId> createToken(AdhocTokenId id, int length) {
            System.out.println("CREATE TOKEN " + cursor + " " + id.name() + " of " + length
                    + " for " + info.input().readLength());
            return info.tokenFactory().createToken(id, length);
        }

        private Token<AdhocTokenId> createDummyToken(int length) {
            // Ensure we get the right ordinal
            AdhocTokenId dummy = tokenIds == origIds ? tokenIds.get(tokenIds.size() - 1) : origIds.get(origIds.size() - 1);
            return createToken(dummy, length);
        }

        private Token<AdhocTokenId> findNextToken() {
            if (cursor >= tokens.size()) {
                // We reached the normal end of the parse
                System.out.println("CURSOR IS COUNT - RETURN NULL");
                return null;
            }
            if (nextIsEof) {
                // We got a screwball EOF token that had some text content, and
                // returned it as a dummy token in the previous pass,
                // OR the lexer did not parse all characters in the file, and we
                // hit EOF but returned a dummy token for the tail of it
                System.out.println("NEXT IS EOF AT " + (cursor + 1) + " - RETURN NULL");
                nextIsEof = false;
                return null;
            }
            AntlrProxies.ProxyToken tok = tokens.get(++cursor);
            System.out.println("TOK " + tokenIds.get(tok.getType() + 1) + " '" + truncated(tok.getText()) + "'");
            if (tok.isEOF()) {
//            System.out.println(cursor + ": EOF WITH TOKEN " + tok + " '" + tok.getText() + "'");
                // Wind the LexerInput all the way down to the end (occasionally we get
                // a screwey LexerInput that will return a few million Character.MAX_VALUEs...sigh)
                System.out.println(cursor + ": EOF WITH TOKEN " + tok + " '" + tok.getText() + "'");
                StringBuilder sb = new StringBuilder();
                // XXX we do not actually need to capture these characters in a stringbuilder - just
                // useful for logging
                int ch;
                while ((ch = info.input().read()) != -1) {
                    sb.append((char) ch);
                }
                System.out.println(cursor + ": GOT EOF TOKEN WITH TEXT '" + tok.getText() + "'");
                if (sb.length() > 0) {
                    // Screwball EOF token handling I
                    System.out.println(cursor + ": FINALE DUMMY TOKEN TEXT '" + truncated(sb.toString()) + "' with length " + sb.length());
                    nextIsEof = true;
                    return createDummyToken(sb.length());
                }
                System.out.println("DONE with EOF TOKEN at " + cursor + " / " + tokens.size());
                // Normal EOF token and no extraneous input - we're done
                return null;
            }
            // Now wind the LexerInput forward to (what we hope is) the end of the token in
            // its world as well as ours
            int len = tok.getText().length();
            LexerInput in = info.input();
            // XXX don't need stringbuilder, just a count, once this is fully debugged
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
            if (count > 0) {
                System.out.println("FAST FORWARDED " + count);
            }
            // If the token contains different text than the LexerInput provided,
            // something is wrong - perhaps the grammar skipped some tokens?
            if (!tok.getText().equals(sb.toString())) {
                System.out.println(cursor + ": TOKEN AND READ TEXT MISMATCH - TOKEN: '"
                        + tok.getText() + "' but read '" + sb);
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
                    // the *next* call to nextToken() returns null - length is 2 to
                    // accomodate the EOF "character"
                    nextIsEof = true;
                    return createToken(dummyId, 2);
                } else {
                    return null;
                }
            }
            AdhocTokenId tid = tokenIdForProxyToken(tok);
            int prevPos = lriPosition;
            lriPosition = tok.getStopIndex();
            try {
                // XXX - shouldn't this be (tok.getStopIndex() - tok.getStartIndex()) + 1?  Why does
                // this work?
                return createToken(tid, tok.getStopIndex() - tok.getStartIndex());
            } catch (IndexOutOfBoundsException ex) {
                throw new IllegalStateException("IOOBE with token '"
                        + tok.getText() + "' " + " tokenType="
                        + tok.getType() + " tokenName=" + tid.name()
                        + " cursor=" + cursor + " startIndex "
                        + tok.getStartIndex() + " stopIndex="
                        + tok.getStopIndex() + " token "
                        + tok.getTokenIndex()
                        + " of " + tokens.size() + " last pos was " + prevPos, ex);
            }
        }
    }

    static final class XLexerInputCursor implements Iterator<Token<AdhocTokenId>> {

        private final List<ProxyToken> tokens;
        private final LexerRestartInfo<AdhocTokenId> info;
        private final int inputLength;
        private final TokenFactory<AdhocTokenId> fac;
        private int cursor = -1;
        private int textPosition;
        private int lriPosition;
        private final AdhocTokenId dummyId;
        private final List<AdhocTokenId> tokenIds;

        public XLexerInputCursor(List<ProxyToken> tokens, LexerRestartInfo<AdhocTokenId> info, int inputLength, TokenFactory<AdhocTokenId> fac, List<AdhocTokenId> tokenIds) {
            this.tokens = tokens;
            this.info = info;
            this.inputLength = inputLength;
            this.fac = fac;
            this.dummyId = tokenIds.get(tokenIds.size() - 1);
            this.tokenIds = tokenIds;
        }

        @Override
        public String toString() {
            return "LexerInputCursor{" + "inputLength=" + inputLength + ", cursor=" + cursor
                    + ", textPosition=" + textPosition + ", lriPosition=" + lriPosition
                    + " with " + tokens.size() + " tokens of " + tokenIds.size() + " types" + '}';
        }

        @Override
        public boolean hasNext() {
            if (cursor == -1) {
//                input.backup(input.readLengthEOF());
            }
            if (cursor == tokens.size() - 1) {
                ProxyToken tok = tokens.get(cursor);
                if (tok.isEOF()) {
                    if (tok.getText().length() > 0) {
                        System.out.println("ANOTHER TOKEN AT EOF BECAUSE TEXT '" + tok.getText() + "'");
                        return true;
                    }
                    if (textPosition < inputLength) {
                        System.out.println("ANOTHER TOKEN AT EOF BECAUSE TEXT POSITION " + textPosition + " AND INPUT LENGTH " + inputLength);
                        return true;
                    }
                    return false;
                }
            }
            return cursor < tokens.size();
        }

        private void readOneCharFromLRI() {
            lriPosition++;
            char c = (char) info.input().read();
            System.out.println("  ONE CHAR: '" + c + "' (" + (int) c + ") for " + lriPosition);
        }

        @Override
        public Token<AdhocTokenId> next() {
            try {
                ProxyToken tok = tokens.get(++cursor);
                int startOffset = tok.getStartIndex();
                if (startOffset > textPosition) {
                    cursor--;
                    Token<AdhocTokenId> leadingDummy = fac.createToken(dummyId, startOffset - textPosition);
                    textPosition = tok.getStartIndex();
                    return leadingDummy;
                }
                if (tok.isEOF()) {
//                    cursor--;
                    // We only get here if there *is* a length discrepancy, assuming
                    // hasNext() was called
//                    Token<AdhocTokenId> trailingDummy = fac.createToken(dummyId, Math.max(1, inputLength - textPosition));
                    Token<AdhocTokenId> trailingDummy = fac.createToken(dummyId, info.input().readLength());
                    textPosition = inputLength;
                    return trailingDummy;
                }
                lriPosition = tok.getStopIndex() + 1;
                textPosition += tok.length();
                AdhocTokenId id = tokenIds.get(tok.getType() + 1);
                System.out.println("TOK-" + tok.getTokenIndex() + ": " + id.name() + "@" + tok.getStartIndex() + ":" + tok.getStopIndex());
                return fac.createToken(id, tok.length());
            } finally {
                while (lriPosition < textPosition) {
                    readOneCharFromLRI();
                    System.out.println("AFTER TOKEN " + cursor + " READ LENGTH IS " + info.input().readLength());
                }
            }
        }
    }

    private List<Token<AdhocTokenId>> readTokensList(LexerRestartInfo<AdhocTokenId> lri, String text, ParseTreeProxy currProxy, List<AdhocTokenId> currIds) {
        TokenFactory<AdhocTokenId> factory = lri.tokenFactory();
        debugLog("READ LENGTH IS " + lri.input().readLength());

        if (text.length() == 0) {
            // If the first character in your input is EOF, the NetBeans
            // lexer infrastructure will complain that you didn't consume a
            // non-existent newline
            if (!lri.input().consumeNewline()) {
                char c = (char) lri.input().read();
                debugLog("DID NOT CONSUME NEWLINE but read '" + c + "'");
                debugLog("READ LENGTH NOW " + lri.input().readLength());
                if (c != '\n') {
                    debugLog("SUPPOSED TO BE \\n BUT ACTUALLY '" + c + "'");
                    return Collections.singletonList(lri.tokenFactory().createToken(currIds.get(currIds.size() - 1), 1));
                }
            }
            debugLog("CREATE AN EMPTY LEXER");
            return Collections.emptyList();
        }
        List<ProxyToken> tokens = new ArrayList<>(currProxy.tokens());
        List<Token<AdhocTokenId>> nbTokens = new ArrayList<>(tokens.size() + 2);
        int charsHandledByTokens = 0;

        debugLog("PARSE PROXY WITH " + tokens.size() + " tokens");
        int ix = 0;
        int expectedTextPosition = 0;
        AdhocTokenId dummyTokenID = currIds.get(currIds.size() - 1);
        for (ProxyToken tok : tokens) {
            debugLog(" TOK " + ix++ + tok + " type " + currProxy.tokenTypeForInt(tok.getType()));

            if (expectedTextPosition < tok.getStartIndex()) {
                int skipLength = tok.getStartIndex() - expectedTextPosition;
                debugLog(" SKIPPED SOME TEXT: '" + truncated(text.substring(expectedTextPosition, tok.getStartIndex())) + "' - add dummy token at " + (ix - 1));
                Token<AdhocTokenId> skippedTextToken
                        = factory.createToken(dummyTokenID, skipLength);
                nbTokens.add(skippedTextToken);
                charsHandledByTokens += skipLength;
                expectedTextPosition += skipLength;
            }

            charsHandledByTokens += tok.getText().length();
            expectedTextPosition += tok.getText().length();
            int id = tok.getType();
            // ANTLR EOF tokens may contain text - we need to do *something*
            // with it, so the chars in matches the chars consumed by the lexer
            String txt = tok.getText();
            // Special case 1:  Antlr hands us an EOF token with content:
            if (id == -1 && txt != null && !txt.isEmpty()) {
                debugLog("DUMMY TOKEN FOR EOF CONTENT '" + truncated(txt) + "' tok " + tok + "'" + txt + "'");
                Token<AdhocTokenId> tailToken = factory.createToken(dummyTokenID, tok.getText().length());
                nbTokens.add(tailToken);
                continue;
            }
            // Special case 2:  We have an empty EOF token but we are not actually
            // at the end of the text
            if (id == -1 && expectedTextPosition < text.length()) {
                txt = text.substring(expectedTextPosition, text.length());
                debugLog("TRAILING DUMMY TOKEN FOR EOF CONTENT '" + truncated(txt) + "' tok " + tok + "'" + txt + "'");
                Token<AdhocTokenId> tailToken = factory.createToken(dummyTokenID, text.length() - expectedTextPosition);
                nbTokens.add(tailToken);
                break;
            }

            if (txt == null || txt.isEmpty()) {
                debugLog("EMPTY or null text '" + txt + "' for token "
                        + proxy.tokenTypeForInt(tok.getType()) + " tokenText length " + tok.getText().length()
                        + " token start/stop " + tok.getStartIndex() + "/" + tok.getEndIndex());
                System.out.println("EXPECTED TEXT POSITION " + expectedTextPosition + " charsHandledByTokens " + charsHandledByTokens
                        + " text length " + text.length());
                continue;
            }

            if (id != -1) {
                // XXX check that there are not gaps between tokens
                AdhocTokenId tokId = currIds.get(id + 1); // 0 = type -1 - EOF
                debugLog("CREATE TOKEN " + tokId.name() + " for '" + truncated(txt) + "' id " + tokId);
                Token<AdhocTokenId> currToken = factory.createToken(tokId, tok.getText().length());
                nbTokens.add(currToken);
            }
        }
        debugLog("CHARS HANDLED BY TOKENS " + charsHandledByTokens);
        debugLog("TEXT LENGTH " + text.length());
        // A grammar in which the top level rule does not consume EOF may
        // not parse all the way to the end of the input, but just stop at
        // some point, leaving dangling characters.  This can also happen with
        // unhandled trailing whitespace.  Since the netbeans lexing infrastructure
        // will explode horrifically leaving the IDE nearly unusable if all tokens
        // don't match, we add a dummy token to handle that case here:
        if (charsHandledByTokens < text.length()) {
            String tail = text.substring(charsHandledByTokens);
            AdhocTokenId dummy = currIds.get(currIds.size() - 1);
            debugLog("CREATE TRAILING DUMMY TOKEN FOR " + tail.length() + " chars: '" + truncated(tail) + "'");
            Token<AdhocTokenId> currToken = factory.createToken(dummy, tail.length());
            nbTokens.add(currToken);
        }
        return nbTokens;
    }

    void replaceProxy(ParseTreeProxy proxy) { // called by infrastructure
        setProxy(proxy);
    }
//
//    synchronized void setProxyAndIds(ParseTreeProxy proxy, List<AdhocTokenId> ids) {
//        this.proxy = proxy;
//        this.ids = Collections.unmodifiableList(ids);
//        if (!Objects.equals(proxy.tokenTypes(), lastTypes)) {
//            rebuildTokenCategories();
//        }
//    }

    synchronized ParseTreeProxy setProxy(ParseTreeProxy proxy) {
        this.proxy = proxy;
        if (!Objects.equals(proxy.tokenTypes(), lastTypes)) {
            debugLog("rebuilding token categories and ids for " + proxy.mimeType());
            lastTypes = proxy.tokenTypes();
            this.ids = Collections.unmodifiableList(rebuildTokenIds(proxy));
            rebuildTokenCategories();
            AdhocLanguageFactory factory = Lookup.getDefault().lookup(AdhocLanguageFactory.class);
//            factory.fire();
            factory.discard(mimeType);
        }
        return proxy;
    }

    static List<AdhocTokenId> rebuildTokenIds(ParseTreeProxy proxy) {
        List<AdhocTokenId> newIds = new ArrayList<>(proxy.tokenTypes().size());
        int maxType = 0;
        for (ProxyTokenType id : proxy.tokenTypes()) {
            newIds.add(new AdhocTokenId(proxy, id));
            maxType = Math.max(maxType, id.type);
        }
        // We add a catch-all token type for cases where the proxy is out
        // of sync with the text we're parsing, so we have *something* to
        // return that is differentiated from the actual token types
        ProxyTokenType dummyToken = new ProxyTokenType(maxType + 1, DUMMY_TOKEN_ID, DUMMY_TOKEN_ID, DUMMY_TOKEN_ID);
        newIds.add(new AdhocTokenId(proxy, dummyToken));
        return newIds;
    }

    private void rebuildTokenCategories() {
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
        this.tokenCategories = Collections.unmodifiableMap(m);
    }

    @Override
    protected Map<String, Collection<AdhocTokenId>> createTokenCategories() {
        if (ids == null) {
            setProxy(DynamicLanguageSupport.parseImmediately(mimeType, null, Reason.CREATE_LEXER));
        }
        return tokenCategories;
    }

    @Override
    protected List<AdhocTokenId> createTokenIds() {
        if (ids == null) {
            setProxy(DynamicLanguageSupport.parseImmediately(mimeType, null, Reason.CREATE_LEXER));
        }
        return ids;
    }

    @Override
    protected synchronized String mimeType() {
        return mimeType;
    }
    /*
    static final class LexerCursor {
        private final LexerRestartInfo<AdhocTokenId> info;
        private final ParseTreeProxy orig;
        private final String mimeType;
        private String text;
        private ParseTreeProxy proxy;

        public LexerCursor(LexerRestartInfo<AdhocTokenId> info, String mimeType, ParseTreeProxy orig) {
            this.info = info;
            this.mimeType = mimeType;
            this.orig = orig;
        }

        private static class Iter implements Iterator<Token<AdhocTokenId>> {
            int textPosition = 0;
            int tokenPosition = 0;
            private final LexerInput input;
            private final List<AdhocTokenId> ids;
            private final TokenFactory<AdhocTokenId> factory;
            private final ParseTreeProxy proxy;

            public Iter(LexerInput input, List<AdhocTokenId> ids, TokenFactory<AdhocTokenId> factory, ParseTreeProxy proxy) {
                this.input = input;
                this.ids = ids;
                this.factory = factory;
                this.proxy = proxy;
            }

            @Override
            public boolean hasNext() {
                return tokenPosition < proxy.tokenCount();
            }

            @Override
            public Token<AdhocTokenId> next() {
                ProxyToken next = proxy.tokens().get(tokenPosition);
                AdhocTokenId id = ids.get(next.getType() + 1);
                int count = 0;
                while (textPosition <= next.getStopIndex()) {
                    input.read();
                    count++;
                }
                return factory.createToken(id, count);
            }
        }

        ParseTreeProxy proxy() {
            if (proxy != null) {
                return proxy;
            }
            proxy = loadProxy();
            
            return proxy;
        }

        private ParseTreeProxy loadProxy() {
            String txt = text();
            if (!orig.isUnparsed() && Objects.equals(txt, orig.text())) {
                return orig;
            }
            if (txt.length() == 0) {
                return orig.toEmptyParseTreeProxy("");
            }
            return DynamicLanguageSupport.parseImmediately(
                            mimeType, txt, Reason.CREATE_LEXER);
        }

        private synchronized String text() {
            if (text == null) {
                text = loadText();
            }
            return text;
        }

        private String loadText() {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            LexerInput input = info.input();
            for (;;count++) {
                int i = input.read();
                if (i == -1) {
                    input.backup(count);
                    return sb.toString();
                }
            }
        }
    }
     */

 /*
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

    void setProxy(AntlrProxies.ParseTreeProxy proxy) {
        this.proxy.set(proxy);
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
            return new AdhocLexer(lri, px, ids, this);
        });
    }

    @Override
    protected String mimeType() {
        return baseMimeType;
    }
     */
}
