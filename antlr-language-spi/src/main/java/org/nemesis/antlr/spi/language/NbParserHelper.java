package org.nemesis.antlr.spi.language;

import org.nemesis.antlr.spi.language.fix.Fixes;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.extraction.Extraction;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser.Result;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.LazyFixList;

/**
 * Helper class for intercepting parser creation and interacting with the syntax
 * tree after a parse has been completed.
 *
 * @author Tim Boudreau
 */
public abstract class NbParserHelper<P extends Parser, L extends Lexer, R extends Result, T extends ParserRuleContext> {

    private final Logger LOG = Logger.getLogger(getClass().getName());

    protected NbParserHelper() {
    }

    /**
     * Called when the Antlr parser is created.
     *
     * @param lexer
     * @param parser
     * @param snapshot
     * @throws Exception
     */
    public final Supplier<List<? extends SyntaxError>> parserCreated(L lexer, P parser, Snapshot snapshot) throws Exception {
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        GenericAntlrErrorListener listener = new GenericAntlrErrorListener(snapshot);
        onCreateAntlrParser(lexer, parser, snapshot);
        lexer.addErrorListener(listener);
        LOG.log(Level.FINEST, "ADDING ERROR LISTENER TO {0} for {1} as {2} ", new Object[]{lexer, snapshot, listener});
        return listener;
    }

    /**
     * Perform any configuration of the lexer or parser here, before parsing.
     * The default implementation does nothing.
     *
     * @param lexer The lexer
     * @param parser The parser
     * @param snapshot The snapshot
     * @throws Exception if something goes wrong
     */
    protected void onCreateAntlrParser(L lexer, P parser, Snapshot snapshot) throws Exception {

    }

    /**
     * Called when parsing and extraction has been completed - perform any
     * inspection of the results or further analysis here (such as semantic
     * error checking). To make things you compute here available to other code,
     * simply create keys using AntlrParseResult.key(), store those in static
     * fields, and use them to populate data in the ParseResultContents passed
     * in - these objects will be available from AntlrParseResult.get(key) to
     * any code handling the parser result. The default implementation does
     * nothing.
     *
     * @param tree The parse tree for the root object
     * @param extraction The extracted data from the parse
     * @param populate An input object that lets you place objects into the
     * parse result which are available from calls to AntlrParseResult.get() on
     * the instance created from this parse.
     * @throws Exception If something goes wrong
     */
    protected void onParseCompleted(T tree, Extraction extraction, ParseResultContents populate, Fixes fixes, BooleanSupplier cancelled) throws Exception {

    }

    protected final LazyFixList emptyFixList() {
        return Fixes.none();
    }

    /**
     * Convert a SyntaxError into a NetBeans ErrorDescription. Antlr's error
     * messages can be less than user-friendly, so this offers an opportunity to
     * replace them with more human-readable items, include fixes, etc.
     *
     * @param snapshot A snapshot
     * @param error An error
     * @return An error description, or null to use the default conversion
     */
    protected ErrorDescription convertError(Snapshot snapshot, SyntaxError error) {
        return null;
    }

    final void _errorNode(ErrorNode nd, ParseResultContents populate) {
        if (onErrorNode(nd, populate)) {
            return;
        }
        Token tok = nd.getSymbol();
        if (tok != null) {
            SyntaxError se = new SyntaxError(Optional.of(tok), tok.getStartIndex(), tok.getStopIndex() + 1, "Unexpected symbol " + tok.getText(), null);
            populate.addSyntaxError(se);
        }
    }

    /**
     * Convert an error node into a syntax error, or ignore it. If this message
     * returns true, the default handling (creating a generic syntax error) will
     * be bypassed.
     *
     * @param nd An error node
     * @param populate The parser result contents
     * @return true if this message has handled (or ignored) it, false otherwise
     */
    protected boolean onErrorNode(ErrorNode nd, ParseResultContents populate) {
        return false;
    }

    public final void parseCompleted(String mimeType, T tree, Extraction extraction, ParseResultContents populate, BooleanSupplier cancelled, Supplier<List<? extends SyntaxError>> errorSupplier) throws Exception {
        assert errorSupplier != null : "null error supplier";
        assert populate != null : "null populator";
        assert extraction != null : "extraction null";
        assert tree != null : "tree null";
        assert cancelled != null : "cancelled null";
        boolean wasCancelled = cancelled.getAsBoolean();
        if (!wasCancelled) {
            // Ensure the tree gets fully walked and the parse fully run, so
            // all errors are collected
            new ParseTreeWalker().walk(new ErrorNodeCollector(populate), tree);

            List<? extends SyntaxError> errors = errorSupplier.get();
            LOG.log(Level.FINEST, "PARSE GOT {0} errors from {1}", new Object[]{errors.size(), errorSupplier});
            populate.setSyntaxErrors(errors, this);
            Fixes fixes = populate.fixes();
            ParseResultHook.runForMimeType(mimeType, tree, extraction, populate, fixes);
            onParseCompleted(tree, extraction, populate, fixes, cancelled);
        } else {
            LOG.log(Level.FINEST, "Not using parse result {0} due to cancellation", populate);
        }
    }

    final class ErrorNodeCollector implements ParseTreeListener {

        private final ParseResultContents cts;

        public ErrorNodeCollector(ParseResultContents cts) {
            this.cts = cts;
        }

        @Override
        public void visitTerminal(TerminalNode tn) {

        }

        @Override
        public void visitErrorNode(ErrorNode en) {
            _errorNode(en, cts);
            LOG.log(Level.FINE, "  ErrorNode: {0}", en);
        }

        @Override
        public void enterEveryRule(ParserRuleContext prc) {
        }

        @Override
        public void exitEveryRule(ParserRuleContext prc) {
        }
    }
}