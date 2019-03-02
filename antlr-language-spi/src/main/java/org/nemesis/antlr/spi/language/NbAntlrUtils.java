package org.nemesis.antlr.spi.language;

import org.antlr.v4.runtime.CharStream;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;

/**
 *
 * @author Tim Boudreau
 */
public final class NbAntlrUtils {

    public static CharStream newCharStream(LexerInput input, String name) {
        return new AntlrStreamAdapter(input, name);
    }

    public static <T extends TokenId> Lexer<T> createLexer(LexerRestartInfo<T> info, NbLexerAdapter<T, ?> adapter) {
        return new GenericAntlrLexer<>(info, adapter);
    }

    private NbAntlrUtils() {
        throw new AssertionError();
    }

    public static TaskFactory createErrorHighlightingTaskFactory(String mimeType) {
        return AntlrInvocationErrorHighlighter.factory(mimeType);
    }
}
