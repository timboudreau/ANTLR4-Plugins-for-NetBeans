package org.nemesis.antlr.live.parsing.extract;

import ignoreme.placeholder.DummyLanguageLexer;
import ignoreme.placeholder.DummyLanguageParser; //parser
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.BitSet;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * This class is not called directly by the module; rather, it is used as a
 * template (the symlink named ParserExtractor.template will be included in the
 * JAR as source). CompileAntlrSources will modify its contents for the right
 * class and package names, and remove parser class references in the case that
 * only a lexer was generated, and copy it next to the generated Antlr lexer and
 * parser before compiling. This class is then called reflectively in an
 * isolating classloader that is discarded afterwards, to extract information
 * from the grammar. Lines prefixed with //parser are omitted when handling a
 * lexer grammar for which no parser class will exist. No Antlr types may leak
 * out of the isolating classloader, so all objects (including exception types)
 * representing the grammar and parse are copied into proxy objects defined in
 * AntlrProxies, which is loaded via the module's, not the isolation
 * environment's classloader.
 *
 * @author Tim Boudreau
 */
public class ParserExtractor {

    // These two field values are replaced during generation
    private static final String GRAMMAR_NAME = "DummyLanguage";
    private static final Path GRAMMAR_PATH = Paths.get("/replace/with/path");

    public static org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy //parser
            extract(String text, String ruleName) { //parser
        int index = Arrays.asList(DummyLanguageParser.ruleNames).indexOf(ruleName); //parser
        if (index < 0) { //parser
            throw new IllegalArgumentException("No such rule: " + ruleName); //parser
        } //parser
        return extract(text, index); //parser
    } //parser

    public static AntlrProxies.ParseTreeProxy extract(String text) {
        return extract(text, 0);
    }

    @SuppressWarnings("deprecation")
    public static org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy extract(String text, int ruleIndex) {
        org.nemesis.antlr.live.parsing.extract.AntlrProxies proxies
                = new org.nemesis.antlr.live.parsing.extract.AntlrProxies(GRAMMAR_NAME, GRAMMAR_PATH, text);
        try {
            int max = DummyLanguageLexer.VOCABULARY.getMaxTokenType() + 1;
            // Iterate all the token types and report them, so we can
            // pass back the token types without reference to antlr classes
            for (int tokenType = 0; tokenType < max; tokenType++) {
                String dn = DummyLanguageLexer.VOCABULARY.getDisplayName(tokenType);
                String sn = DummyLanguageLexer.VOCABULARY.getSymbolicName(tokenType);
                String ln = DummyLanguageLexer.VOCABULARY.getLiteralName(tokenType);
                proxies.addTokenType(tokenType, dn, sn, ln);
            }
            // The channel names are simply a string array - safe enough
            proxies.channelNames(DummyLanguageLexer.channelNames);
            // Same for the rule names
            proxies.setParserRuleNames(DummyLanguageLexer.ruleNames); //parser
            // We may have been called just to build a lexer vocabulary w/o text
            // to parse
            if (text != null) {
                // Use deprecated ANTLRInputStream rather than 4.7's CharStreams,
                // since we may be running against an older version of Antlr where
                // that call would fail
                DummyLanguageLexer lex = new DummyLanguageLexer(new org.antlr.v4.runtime.ANTLRInputStream(text.toCharArray(), text.length()));
                lex.removeErrorListeners();
                // Collect all of the tokens
                ErrL errorListener = new ErrL(proxies);
                lex.addErrorListener(errorListener);
                Token tok;
                int tokenIndex = 0;
                do {
                    tok = lex.nextToken();
                    int type = tok.getType();
                    int start = tok.getStartIndex();
                    int stop = tok.getStopIndex();
                    String txt = tok.getText();
                    if (type == -1) {
                        // EOF has peculiar behavior in Antlr - the start
                        // offset is less than the end offset
                        start = Math.max(start, stop);
                        stop = start;
                        // And the text may be "<EOF>" which we don't want
                        // to accidentally return as a netbeans token
                        txt = "";
                    }
                    proxies.onToken(txt, type,
                            tok.getLine(), tok.getCharPositionInLine(),
                            tok.getChannel(), tokenIndex++,
                            start, stop);
                } while (tok.getType() != DummyLanguageLexer.EOF);
                lex.reset();
                // Now lex again to run the parser
                DummyLanguageParser parser = new DummyLanguageParser(new CommonTokenStream(lex, 0)); //parser
                parser.removeErrorListeners(); //parser
                parser.addErrorListener(errorListener); //parser
                org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeBuilder //parser
                        bldr = proxies.treeBuilder(); //parser
                RuleTreeVisitor v = new RuleTreeVisitor(bldr); //parser
                String startRuleMethodName = DummyLanguageParser.ruleNames[ruleIndex].replace("-", "_"); //parser
                Method method = DummyLanguageParser.class.getMethod(startRuleMethodName); //parser
                ParseTree pt = (ParseTree) method.invoke(parser); //parser
                pt.accept(v); //parser
                bldr.build(); //parser
            }
        } catch (Exception | Error ex) {
            proxies.onThrown(ex);
        }
        return proxies.result();
    }

    private static class ErrL implements ANTLRErrorListener {

        private final org.nemesis.antlr.live.parsing.extract.AntlrProxies proxies;

        ErrL(org.nemesis.antlr.live.parsing.extract.AntlrProxies proxies) {
            this.proxies = proxies;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> rcgnzr, Object offendingSymbol, int line,
                int charPositionInLine, String message, RecognitionException re) {
            if (offendingSymbol instanceof Token) {
                Token t = (Token) offendingSymbol;
                proxies.onSyntaxError(message, line, charPositionInLine, t.getTokenIndex(), t.getType(), t.getStartIndex(), t.getStopIndex());
            } else {
                proxies.onSyntaxError(message, line, charPositionInLine);
            }
        }

        @Override
        public void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean bln, BitSet bitset, ATNConfigSet atncs) {
//            System.out.println("AMBIGUITY at " + i + ":" + i1 + " " + dfa.toLexerString() + " cs " + atncs.toString());
        }

        @Override
        public void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitset, ATNConfigSet atncs) {
//            System.out.println("ATTEMPT FULL at " + i + ":" + i1 + " " + dfa.toLexerString() + " cs " + atncs.toString());
        }

        @Override
        public void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atncs) {
        }
    }

    private static class RuleTreeVisitor implements ParseTreeVisitor<Void> { //parser

        private final org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeBuilder builder; //parser

        public RuleTreeVisitor(org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeBuilder builder) { //parser
            this.builder = builder; //parser
        } //parser

        @Override //parser
        public Void visit(ParseTree tree) { //parser
            tree.accept(this); //parser
            return null; //parser
        } //parser

        @Override //parser
        public Void visitChildren(RuleNode node) { //parser
            String ruleName = DummyLanguageParser.ruleNames[node.getRuleContext().getRuleIndex()]; //parser
            int alt = node.getRuleContext().getAltNumber(); //parser
            Interval ival = node.getSourceInterval(); //parser
            builder.addRuleNode(ruleName, alt, ival.a, ival.b, () -> { //parser
                int n = node.getChildCount(); //parser
                for (int i = 0; i < n; i++) { //parser
                    ParseTree c = node.getChild(i); //parser
                    c.accept(this); //parser
                } //parser
            }); //parser
            return null; //parser
        } //parser

        @Override //parser
        public Void visitTerminal(TerminalNode node) { //parser
            builder.addTerminalNode(node.getSymbol().getTokenIndex(), node.getText()); //parser
            return null; //parser
        } //parser

        @Override //parser
        public Void visitErrorNode(ErrorNode node) { //parser
            builder.addErrorNode(node.getSourceInterval().a, node.getSourceInterval().b); //parser
            return null; //parser
        } //parser
    } //parser
}
