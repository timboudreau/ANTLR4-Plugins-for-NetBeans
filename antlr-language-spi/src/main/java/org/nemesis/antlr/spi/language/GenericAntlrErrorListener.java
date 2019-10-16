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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.netbeans.modules.parsing.api.Snapshot;

/**
 *
 * @author Tim Boudreau
 */
final class GenericAntlrErrorListener implements ANTLRErrorListener, Supplier<List<? extends SyntaxError>> {

    private final Snapshot snapshot;
    private final List<SyntaxError> syntaxErrors = new ArrayList<>(30);

    GenericAntlrErrorListener(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    Snapshot snapshot() {
        return snapshot;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> rcgnzr, Object offendingSymbol, int startIndex, int stopIndex, String message, RecognitionException re) {
        int endOffset = stopIndex + 1;
        if (re != null) {
            re.printStackTrace(System.out);
        }
        if (offendingSymbol instanceof Token) {
            Token tok = (Token) offendingSymbol;
            syntaxErrors.add(new SyntaxError(Optional.of(tok), startIndex, endOffset, message, re));
        } else {
            syntaxErrors.add(new SyntaxError(Optional.empty(), startIndex, endOffset, message, re));
        }
    }

    @Override
    public void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean bln, BitSet bitset, ATNConfigSet atncs) {
        // do nothing
    }

    @Override
    public void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitset, ATNConfigSet atncs) {
        // do nothing
    }

    @Override
    public void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atncs) {
        // do nothing
    }

    @Override
    public List<? extends SyntaxError> get() {
        return Collections.unmodifiableList(syntaxErrors);
    }
}
