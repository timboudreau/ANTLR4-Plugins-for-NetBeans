package org.nemesis.antlr.spi.language;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.misc.IntegerStack;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;

/**
 *
 * @author Tim Boudreau
 */
final class GenericAntlrLexer<T extends TokenId, L extends org.antlr.v4.runtime.Lexer> implements Lexer<T> {

    private final LexerRestartInfo<T> info;
    private final L antlrLexer;
    private LexerState lexerState;
    private final NbLexerAdapter<T, L> adapter;

    public GenericAntlrLexer(LexerRestartInfo<T> info, NbLexerAdapter<T, L> adapter) {
        this.info = info;
        this.adapter = adapter;

        // We recover the potential state nedded for initializing the lexer state
        // info.state() represente un objet que l'on a associé au token modifié
        // et qui représente l'état interne du lexer au moment de sa génération
        // Dans notre cas, il s'agit de la liste des modes dans lequel se trouvait
        // le lexer ANTLR
        lexerState = null;
        Object state = info.state();
        if (state instanceof LexerState) {
            lexerState = (LexerState) state;
        }

        // We initialize our lexer
        CharStream charStream = NbAntlrUtils.newCharStream(info.input(),
                info.languagePath().toString());
        this.antlrLexer = adapter.createLexer(charStream);
        // If there is a state we set the internal state of lexer to the right state
        if (lexerState != null) {
            int mode = lexerState.getMode();
            antlrLexer._mode = mode;
//            System.out.println("- current lexer mode=" + ANTLRv4Lexer.modeNames[mode]);
            IntegerStack modeStack = lexerState.getModeStack();
//            System.out.println("- lexer stacked mode number=" + modeStack.size());
            if (modeStack.size() > 0) {
//                System.out.println("  Restored stacked modes:");
                for (int i = 0; i < modeStack.size(); i++) {
                    mode = modeStack.get(i);
//                    System.out.println("  * lexer mode[" + i + "]=" + ANTLRv4Lexer.modeNames[mode]);
                    antlrLexer._modeStack.add(mode);
                }
            }
            adapter.setInitialStackedModeNumber(antlrLexer, lexerState.getInitialStackedModeNumber());
        }
    }

    private int getErroneousTokenId() {
        // We generate one extra token, to handle token ids for erroneous text
        // that the lexer simply gives up on and cannot tokenize
        return antlrLexer.getVocabulary().getMaxTokenType() + 1;
    }

    @Override
    public Token<T> nextToken() {
        Token<T> nbToken;
        org.antlr.v4.runtime.Token antlrToken = antlrLexer.nextToken();
        int tokenType = antlrToken.getType();
        boolean returningNullNoEof = false;
        if (info.input().readLength() < 1) {
            System.out.println("RETURN NULL FOR READLENGTH " + info.input().readLength() + " token " + antlrToken);
            returningNullNoEof = true;
        }
        if (!returningNullNoEof && antlrToken.getType() == -1 && antlrToken.getStopIndex() < antlrToken.getStartIndex()) {
            System.out.println("RETURN NULL FOR " + antlrToken + " " + antlrToken.getStartIndex() + " " + antlrToken.getStopIndex()
                    + " '" + antlrToken.getText() + "'");
            returningNullNoEof = true;
        }
        if (!returningNullNoEof && tokenType != CharStream.EOF) {
            T tokenId = adapter.tokenId(tokenType);
            nbToken = info.tokenFactory().createToken(tokenId);
            // Now we recover lexer state
            // We make a copy of internal lexer state after having recovered
            // next token in order to be able to restore it in case of future
            // differential lexing
            IntegerStack _modeStack = new IntegerStack(antlrLexer._modeStack);
            lexerState = new LexerState(antlrLexer._mode,
                    _modeStack,
                    adapter.getInitialStackedModeNumber(antlrLexer));
            return nbToken;
        } else {
            returningNullNoEof = true;
        }
        if (returningNullNoEof) {
            LexerInput in = info.input();
            int remaining = in.readLength();
            if (remaining > 0) {
                System.out.println("Return a bogus token for '" + in.readText() + "'");
                // The lexer has given up and did not tokenize everything that
                // was read - but we must.  So use the $ERRONEOUS token generated
                // by the annotation processor to capture whatever is left
                return info.tokenFactory().createToken(
                        adapter.tokenId(getErroneousTokenId()), remaining);
            }
        }
        return null;
    }

    @Override
    public LexerState state() {
        return lexerState;
    }

    @Override
    public void release() {
        // antlrLexer = null;
        // lexerState = null;
        // info = null;
    }

    private static final class LexerState {

        protected int _mode;
        protected IntegerStack _modeStack;
        protected int initialStackedModeNumber;

        public IntegerStack getModeStack() {
            return _modeStack;
        }

        public int getMode() {
            return _mode;
        }

        public int getInitialStackedModeNumber() {
            return initialStackedModeNumber;
        }

        public LexerState(int _mode,
                IntegerStack _modeStack,
                int initialStackedModeNumber) {
            this._modeStack = _modeStack;
            this._mode = _mode;
            this.initialStackedModeNumber = initialStackedModeNumber;
        }
    }
}
