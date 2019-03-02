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
        System.out.println("CREATE GENERIC LISTENER FOR " + snapshot);
    }

    Snapshot snapshot() {
        return snapshot;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> rcgnzr, Object offendingSymbol, int startIndex, int stopIndex, String message, RecognitionException re) {
        System.out.println("SYNTAX ERROR " + message + " " + offendingSymbol + " " + startIndex + " " + stopIndex);
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
        System.out.println("  GET SYNTAX ERRORS WITH " + syntaxErrors);
        return Collections.unmodifiableList(syntaxErrors);
    }
}
