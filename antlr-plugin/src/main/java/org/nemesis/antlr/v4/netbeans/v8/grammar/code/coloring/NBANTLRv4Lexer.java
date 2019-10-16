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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring;

import org.antlr.v4.runtime.misc.IntegerStack;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring.impl.ANTLRv4Lexer;

import org.netbeans.api.lexer.Token;

import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerRestartInfo;

/**
 * Emulation of a lexer (from NetBeans framework point of view) based on an
 * ANTLR 4 lexer and an ANTLR 4 parser and an ANTLR 4 listener.
 * 
 * If there is a syntax error
 * 
 * @author Frédéric Yvon Vinet
 */
public class NBANTLRv4Lexer implements Lexer<ANTLRv4TokenId> {
    private final LexerRestartInfo<ANTLRv4TokenId> info;
    private final ANTLRv4Lexer                     antlrLexer;
    private       LexerState                       lexerState;

    public NBANTLRv4Lexer(LexerRestartInfo<ANTLRv4TokenId> info) {
//        System.out.println("NBANTLRv4Lexer.NBANTLRv4Lexer(LexerRestartInfo<ANTLRv4TokenId>) : begin");
        this.info = info;
        
     // We recover the potential state nedded for initializing the lexer state
     // info.state() represente un objet que l'on a associé au token modifié
     // et qui représente l'état interne du lexer au moment de sa génération
     // Dans notre cas, il s'agit de la liste des modes dans lequel se trouvait
     // le lexer ANTLR
        lexerState = null;
        Object state = info.state();
        if (state == null) {
//            System.out.println("- There is no state associated to this lexer");
        } else {
//            System.out.println("- There is a state associated to this lexer");
            if (state instanceof LexerState) {
                lexerState = (LexerState) state;
//                System.out.println("  And this state is a LexerState!");
            }
        }
        
     // We initialize our lexer
        ANTLRv4CharStream charStream = new ANTLRv4CharStream
                                                         (info.input()        ,
                                                          "ANTLRGrammarEditor");
        this.antlrLexer = new ANTLRv4Lexer(charStream);
     // If there is a state we set the internal state of lexer to the right state
        if (lexerState != null) {
            int mode = lexerState.getMode();
            antlrLexer._mode = mode;
//            System.out.println("- current lexer mode=" + ANTLRv4Lexer.modeNames[mode]);
            IntegerStack modeStack = lexerState.getModeStack();
//            System.out.println("- lexer stacked mode number=" + modeStack.size());
            if (modeStack.size() > 0) {
//                System.out.println("  Restored stacked modes:");
                for (int i=0; i < modeStack.size(); i++) {
                    mode = modeStack.get(i);
//                    System.out.println("  * lexer mode[" + i + "]=" + ANTLRv4Lexer.modeNames[mode]);
                    antlrLexer._modeStack.add(mode);
                }
            }
            antlrLexer.setInitialStackedModeNumber(lexerState.getInitialStackedModeNumber());
        }
        
//        System.out.println("NBANTLRv4Lexer.NBANTLRv4Lexer(LexerRestartInfo<ANTLRv4TokenId>) : end");
    }
    

    @Override
    public Token<ANTLRv4TokenId> nextToken() {
//        System.out.println("NBANTLRv4Lexer.nextToken() -> Token : begin");
        Token<ANTLRv4TokenId> nbToken;
        org.antlr.v4.runtime.Token antlrToken = antlrLexer.nextToken();
        int tokenType = antlrToken.getType();
        if (tokenType != ANTLRv4Lexer.EOF) {
//            System.out.print("- token pushed: text='" + antlrToken.getText() + "' ");
//            System.out.print("type=" + ANTLRv4Lexer.VOCABULARY.getSymbolicName(tokenType) + " ");
//            System.out.println("position=" + antlrToken.getStartIndex());
            ANTLRv4TokenId tokenId = ANTLRv4LanguageHierarchy.getToken(tokenType);
            nbToken = info.tokenFactory().createToken(tokenId);
        } else {
            
            nbToken = null;
        }
        
     // Now we recover lexer state
     // We make a copy of internal lexer state after having recovered 
     // next token in order to be able to restore it in case of future
     // differential lexing
        IntegerStack _modeStack = new IntegerStack(antlrLexer._modeStack);
        lexerState = new LexerState(antlrLexer._mode                        ,
                                    _modeStack                              ,
                                    antlrLexer.getInitialStackedModeNumber());
/*
        System.out.println("- lexer state after token emission:");
        System.out.println("  + current lexer mode=" + ANTLRv4Lexer.modeNames[antlrLexer._mode]);
        System.out.println("  + stacked lexer mode number=" + _modeStack.size());
        if (_modeStack.size() > 0) {
            System.out.println("  + stacked lexer modes:");
            for (int i=0; i < _modeStack.size(); i++)
                System.out.println("    * lexer mode[" + i + "]=" + ANTLRv4Lexer.modeNames[_modeStack.get(i)]);
        }

        System.out.println("NBANTLRv4Lexer.nextToken() -> Token : end");
*/
        return nbToken;
    }

    @Override
    public Object state() {
/*
        System.out.println("NBANTLRv4Lexer.state() -> Object : begin");
        if (lexerState != null) {
            System.out.println("- lexer mode=" + ANTLRv4Lexer.modeNames[lexerState.getMode()]);
            IntegerStack modeStack = lexerState.getModeStack();
            System.out.println("- stacked lexer mode number=" + modeStack.size());
            for (int i=0; i < modeStack.size(); i++)
                System.out.println("  + stackedlexer mode[" + i + "]=" + ANTLRv4Lexer.modeNames[modeStack.get(i)]);
            System.out.println("- initial stacked mode number=" + lexerState.getInitialStackedModeNumber());
        }
        System.out.println("NBANTLRv4Lexer.state() -> Object : end");
        System.out.println();
*/
        return lexerState;
    }

    @Override
    public void release() {
//        System.out.println("NBANTLRv4Lexer.release()");
    }
}