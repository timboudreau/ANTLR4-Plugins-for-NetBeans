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
package org.nemesis.antlr.spi.language;

import java.util.logging.Level;
import java.util.logging.Logger;
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
    private static final Logger LOG = Logger.getLogger(GenericAntlrLexer.class.getName());

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
            returningNullNoEof = true;
        }
        if (!returningNullNoEof && antlrToken.getType() == -1 && antlrToken.getStopIndex() < antlrToken.getStartIndex()) {
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
            // Devour any remaining input
            LexerInput in = info.input();
            int remaining = in.readLength();
            if (remaining > 0) {
                LOG.log(Level.WARNING, "Antlr lexer {0} did not consume "
                        + "{1} characters at end of input. Returning error token",
                        new Object[] {antlrLexer.getClass().getSimpleName(), remaining});
                // The lexer has given up and did not tokenize everything that
                // was read - but we must.  So use the $ERRONEOUS token generated
                // by the annotation processor to capture whatever is left
                Token<T> result = info.tokenFactory().createToken(
                        adapter.tokenId(getErroneousTokenId()), remaining);
                LOG.log(Level.FINEST, "Returning bogus token {0}", result);
                return result;
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
