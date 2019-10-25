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

import com.mastfrog.function.IntBiConsumer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.netbeans.modules.parsing.api.Snapshot;
import org.openide.text.NbDocument;

/**
 *
 * @author Tim Boudreau
 */
final class GenericAntlrErrorListener implements ANTLRErrorListener, Supplier<List<? extends SyntaxError>> {

    private final Snapshot snapshot;
    private final List<SyntaxError> syntaxErrors = new ArrayList<>( 30 );

    GenericAntlrErrorListener( Snapshot snapshot ) {
        this.snapshot = snapshot;
    }

    Snapshot snapshot() {
        return snapshot;
    }

    static boolean offsetsOf( Document doc, Token tok, IntBiConsumer startEnd ) {
        if ( doc instanceof StyledDocument ) { // always will be outside some tests
            StyledDocument sdoc = ( StyledDocument ) doc;
            Element el = NbDocument.findLineRootElement( sdoc );
            int lineNumber = tok.getLine() >= el.getElementCount()
                                     ? el.getElementCount() - 1 : tok.getLine();
            int lineOffsetInDocument = NbDocument.findLineOffset( ( StyledDocument ) doc, lineNumber );
            int errorStartOffset = Math.max( 0, lineOffsetInDocument + tok.getLine() );
            int errorEndOffset = Math.min( doc.getLength() - 1,
                                           errorStartOffset + ( tok.getStopIndex() - tok
                                           .getStartIndex() ) + 1 );
            startEnd.accept( errorStartOffset, errorEndOffset );
            return true;
        }
        return false;
    }

    @Override
    public void syntaxError( Recognizer<?, ?> rcgnzr, Object offendingSymbol, int startIndex, int stopIndex,
            String message, RecognitionException re ) {
        int endOffset = stopIndex + 1;
        if ( offendingSymbol instanceof Token ) {
            Token tok = ( Token ) offendingSymbol;
            if ( !offsetsOf( snapshot.getSource().getDocument( false ), tok, ( start, end ) -> {
                         syntaxErrors.add( new SyntaxError( Optional.of( tok ), start, end, message, re ) );
                     } ) ) {
                syntaxErrors.add( new SyntaxError( Optional.of( tok ), startIndex, endOffset, message, re ) );
            }
        } else {
            syntaxErrors.add( new SyntaxError( Optional.empty(), startIndex, endOffset, message, re ) );
        }
    }

    @Override
    public void reportAmbiguity( Parser parser, DFA dfa, int i, int i1, boolean bln, BitSet bitset, ATNConfigSet atncs ) {
        // do nothing
    }

    @Override
    public void reportAttemptingFullContext( Parser parser, DFA dfa, int i, int i1, BitSet bitset, ATNConfigSet atncs ) {
        // do nothing
    }

    @Override
    public void reportContextSensitivity( Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atncs ) {
        // do nothing
    }

    @Override
    public List<? extends SyntaxError> get() {
        return Collections.unmodifiableList( syntaxErrors );
    }
}
