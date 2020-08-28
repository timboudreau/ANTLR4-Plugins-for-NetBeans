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
package org.nemesis.antlr.live.parsing.extract;

import ignoreme.placeholder.DummyLanguageLexer;
import ignoreme.placeholder.DummyLanguageParser; //parser
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.BitSet;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * This class is not called directly by the module; rather, it is used as a
 * template (the copy in the resources folder named ParserExtractor.template
 * will be included in the JAR as source). ExtractionCodeGenerator will modify
 * its contents for the right class and package names, substitute the right
 * values into static fields, and remove parser class references in the case
 * that only a lexer was generated, and copy it next to the generated Antlr
 * lexer and parser before compiling. This class is then called reflectively in
 * an isolating classloader that is discarded afterwards, to extract information
 * from the grammar. Lines prefixed with //parser are omitted when handling a
 * lexer grammar for which no parser class will exist. No Antlr types may leak
 * out of the isolating classloader, so all objects (including exception types)
 * representing the grammar and parse are copied into proxy objects defined in
 * AntlrProxies, which is loaded via the module's, not the isolation
 * environment's classloader. Must not reference any non-JDK, non-ANTLR classes
 * other than those explicitly included in the classloader, and any lines which
 * could reference a parser that does not exist for lexer-only grammars should
 * be postfixed with a comment so they can be automatically removed during
 * generation. Classes from the same package as this class must be referenced by
 * FQN or they will not be resolvable when repackaged during generation. If
 * classes from the module are needed, they must be added to the classloader in
 * ProxiesInvocationRunner. DummyLanguageLexer and DummyLanguageParser exist
 * only to provide placeholders for substitution (and so this code is compilable
 * and easily edited during module development).
 *
 * @author Tim Boudreau
 */
public class ParserExtractor {

    // These field values are replaced during code generation
    private static final String GRAMMAR_NAME = "DummyLanguage";
    private static final Path GRAMMAR_PATH = Paths.get("/replace/with/path");
    private static final String GRAMMAR_TOKENS_HASH = "--tokensHash--";

    public static org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy //parser
            extract(String text, String ruleName) { //parser
        int index = Arrays.asList(DummyLanguageParser.ruleNames).indexOf(ruleName); //parser
        if (index < 0) { //parser
            throw new IllegalArgumentException("No such rule: " + ruleName); //parser
        } //parser
        return extract(text, index); //parser
    } //parser

    public static org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy extract(CharSequence text) {
        return extract(text, 0);
    }

    @SuppressWarnings("deprecation")
    public static org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy extract(CharSequence text, int ruleIndex) {
        org.nemesis.antlr.live.parsing.extract.AntlrProxies proxies
                = new org.nemesis.antlr.live.parsing.extract.AntlrProxies(GRAMMAR_NAME, GRAMMAR_PATH, text);
        proxies.setGrammarTokensHash(GRAMMAR_TOKENS_HASH);
        try {
            int max = checksumTokenAndRuleNamesAndAddErrorToken(proxies);
            collectChannelNames(proxies);
            // Same for the rule names
            proxies.setParserRuleNames(DummyLanguageParser.ruleNames); //parser
            proxies.setLexerRuleNames(DummyLanguageLexer.ruleNames);
            int defaultMode = DummyLanguageLexer.DEFAULT_MODE;
            String[] modeNames = DummyLanguageLexer.modeNames;
            proxies.setModeInfo(defaultMode, modeNames);
            int[] lineEnds = null;
            int lineCount = -1;
            // We may have been called just to build a lexer vocabulary w/o text
            // to parse
            if (text != null) {
                // Use deprecated ANTLRInputStream rather than 4.7's CharStreams,
                // since we may be running against an older version of Antlr where
                // that call would fail
                CharSequenceCharStream charStream = new CharSequenceCharStream(text);
                DummyLanguageLexer lex = new DummyLanguageLexer(charStream);
                lex.removeErrorListeners();
                // Collect all of the tokens
                ErrL errorListener = new ErrL(proxies, charStream);
                lex.addErrorListener(errorListener);
                Token tok;
                int tokenIndex = 0;
                org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeBuilder //lexerOnly
                        lexerTreeBuilder = proxies.treeBuilder(); //lexerOnly
//                System.out.println("\nUsing lexer code for " + GRAMMAR_PATH + "\n"); //lexerOnly
//                System.out.println("\nUsing parser code for " + GRAMMAR_PATH + "\n"); //parser
                int prevStop = -1;
                do {
                    tok = lex.nextToken();
                    int type = tok.getType();
                    int start = tok.getStartIndex();
                    int stop = tok.getStopIndex();
                    // If there are syntax errors, there will be gaps in the text
                    // that's tokenized.  NetBeans lexers REALLY don't like that.
                    // While we have some magic in AdhocLexer to deal with this,
                    // it is best handled here
                    if (type != DummyLanguageLexer.EOF && start > prevStop + 1) {
                        // Synthesize a dummy token here
                        int erroneousType = max;
                        if (lineEnds == null) {
                            lineEnds = new int[Math.max(100, text.length() / 45)];
                            int cursor = 0;
                            lineCount = 0;
                            for (int i = 0; i < text.length(); i++) {
                                if (text.charAt(i) == '\n') {
                                    // It would be nice to use IntList but that
                                    // would drag in a bunch of dependencies we have
                                    // to load in our lockless isolating classloader,
                                    // so do it the hard way
                                    if (cursor > lineEnds.length) {
                                        lineEnds = Arrays.copyOf(lineEnds, lineEnds.length * 2);
                                    }
                                    lineEnds[cursor++] = i;
                                    lineCount++;
                                }
                            }
                        }
                        int errorBeginLine = 0;
                        int errorCharPosition = 0;
                        for (int i = 0; i < lineCount; i++) {
                            int prevLineEnd = i == 0 ? 0 : lineEnds[i - 1];
                            int currLineEnd = lineEnds[i];
                            if (prevStop == prevLineEnd) {
                                errorBeginLine = i + 1;
                                break;
                            } else if (prevStop > prevLineEnd && prevStop < currLineEnd) {
                                errorBeginLine = i;
                                errorCharPosition = prevStop - prevLineEnd;
                                break;
                            }
                        }
                        proxies.onToken(erroneousType, errorBeginLine,
                                errorCharPosition, 0, tokenIndex++,
                                prevStop + 1, start - 1, 0, 0);
                    }
                    prevStop = stop;
                    errorListener.updateTokenIndex(tokenIndex, type);
                    int trim = 0;
                    if (type == DummyLanguageLexer.EOF) {
                        // EOF has peculiar behavior in Antlr - the start
                        // offset is less than the end offset
                        start = Math.max(start, stop);
                        stop = start;
                        // We may be better off not returning EOF,but it
                        // will require fixes elsewhere
//                        break;
                    } else {
                        for (int i = stop; i >= start; i--) {
                            if (Character.isWhitespace(text.charAt(i))) {
                                trim++;
                            } else {
                                break;
                            }
                        }
                    }
                    proxies.onToken(type,
                            tok.getLine(), tok.getCharPositionInLine(),
                            tok.getChannel(), tokenIndex++,
                            start, stop, trim, lex._mode);
                    if (type != DummyLanguageLexer.EOF) { //lexerOnly
                        lexerTreeBuilder.addTerminalNode(tokenIndex - 1, tok.getText(), 1); //lexerOnly
                    } //lexerOnly
                } while (tok.getType() != DummyLanguageLexer.EOF);
                lexerTreeBuilder.build(); //lexerOnly
                lex.reset(); //parser
                errorListener.updateTokenIndex(0, -1);
                // Now lex again to run the parser
                CommonTokenStream cts = new CommonTokenStream(lex, 0); // parser
                errorListener.cts = cts;
                DummyLanguageParser parser = new DummyLanguageParser(cts); //parser
                parser.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION); //parser
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
            ex.printStackTrace();
            proxies.onThrown(ex);
        }
        return proxies.result();
    }

    static void collectChannelNames(AntlrProxies proxies) {
        // The channel names are simply a string array - safe enough
        try {
            // Turns out, if a grammar doesn't use channels, this field
            // does not exist.  So, find it reflectively.
            Field f = DummyLanguageLexer.class.getField("channelNames");
            Object o = f.get(null);
            if (o instanceof String[]) {
                proxies.channelNames((String[]) o);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            proxies.channelNames(new String[]{"default"});
        }
    }

    static int checksumTokenAndRuleNamesAndAddErrorToken(AntlrProxies proxies) {
        long namesChecksum = 0;
        int max = DummyLanguageLexer.VOCABULARY.getMaxTokenType() + 1;
        // Iterate all the token types and report them, so we can
        // pass back the token types without reference to antlr classes
        for (int tokenType = 0; tokenType < max; tokenType++) {
            String dn = DummyLanguageLexer.VOCABULARY.getDisplayName(tokenType);
            String sn = DummyLanguageLexer.VOCABULARY.getSymbolicName(tokenType);
            String ln = DummyLanguageLexer.VOCABULARY.getLiteralName(tokenType);
            proxies.addTokenType(tokenType, dn, sn, ln);
            String nameToHash = sn != null ? sn : ln != null ? ln : dn;
            namesChecksum += 727 * (nameToHash.hashCode() * (tokenType + 1));
        }
        for (int i = 0; i < DummyLanguageParser.ruleNames.length; i++) { //parser
            namesChecksum += 197 * (DummyLanguageParser.ruleNames[i].hashCode() * (i + 1)); //parser
        } //parser
        proxies.setTokenNamesChecksum(namesChecksum);
        String errName = org.nemesis.antlr.live.parsing.extract.AntlrProxies.ERRONEOUS_TOKEN_NAME;
        proxies.addTokenType(max, errName, errName, errName);
        return max;
    }

    private static class ErrL implements ANTLRErrorListener {

        private final org.nemesis.antlr.live.parsing.extract.AntlrProxies proxies;
        private final CharSequenceCharStream stream;
        private int tokenIndex = 0;
        private int lastTokenType = -1;
        private CommonTokenStream cts;

        ErrL(org.nemesis.antlr.live.parsing.extract.AntlrProxies proxies, CharSequenceCharStream stream) {
            this.proxies = proxies;
            this.stream = stream;
        }

        void reset() {
            tokenIndex = 0;
        }

        void updateTokenIndex(int index, int type) {
            tokenIndex++;
            lastTokenType = type;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> rcgnzr, Object offendingSymbol, int line,
                int charPositionInLine, String message, RecognitionException re) {
            Token t = null;
            if (offendingSymbol instanceof Token) {
                t = (Token) offendingSymbol;
            } else {
                if (cts != null && cts.index() >= 0) {
                    t = cts.get(cts.index());
                }
            }
            if (t != null) {
                int start = t.getStartIndex();
                int stop = t.getStopIndex();
                if (start > stop || start < 0 || stop < 0) {
                    if (t.getType() == DummyLanguageLexer.EOF) {
                        start = stream.index() - 1;
                        stop = start + 1;
                    } else {
                        start = stream.index();
                        stop = start + 1;
                    }
                }
                proxies.onSyntaxError(message, line, charPositionInLine, t.getTokenIndex(),
                        t.getType(), start, stop);
            } else {
                boolean atEnd = stream.index() >= stream.size() - 1;
                int start = atEnd ? stream.size() - 1 : stream.index();
                int end = start + 1;
//                proxies.onSyntaxError(message, line, charPositionInLine, stream.index());
                proxies.onSyntaxError(message, line, charPositionInLine, tokenIndex, lastTokenType, start, end);
            }
        }

        @Override
        public void reportAmbiguity(Parser parser, DFA dfa, int startIndex, int stopIndex,
                boolean exact, BitSet conflictingAlternatives, ATNConfigSet atncs) {
            int decision = dfa.decision;
            int ruleIndex = dfa.atnStartState.ruleIndex;
            String[] ruleNames = parser.getRuleNames();
            String ruleName = null;
            if (ruleIndex < 0 || ruleIndex >= ruleNames.length) {
                ruleName = String.valueOf(decision);
            } else {
                ruleName = ruleNames[ruleIndex];
                if (ruleName == null || ruleName.isEmpty()) {
                    ruleName = String.valueOf(decision);
                }
            }
            if (conflictingAlternatives == null) {
                conflictingAlternatives = atncs.getAlts();
            }
            org.nemesis.antlr.live.parsing.extract.AntlrProxies.Ambiguity ambig
                    = new org.nemesis.antlr.live.parsing.extract.AntlrProxies.Ambiguity(
                            decision, ruleIndex, ruleName, conflictingAlternatives,
                            startIndex, stopIndex);
            proxies.onAmbiguity(ambig);
        }

        @Override
        public void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitset, ATNConfigSet atncs) {
        }

        @Override
        public void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atncs) {
        }

        protected String getDecisionDescription(Parser recognizer, DFA dfa) {
            int decision = dfa.decision;
            int ruleIndex = dfa.atnStartState.ruleIndex;
            String[] ruleNames = recognizer.getRuleNames();
            if (ruleIndex < 0 || ruleIndex >= ruleNames.length) {
                return String.valueOf(decision);
            }
            String ruleName = ruleNames[ruleIndex];
            if (ruleName == null || ruleName.isEmpty()) {
                return String.valueOf(decision);
            }
            return String.format("rule '%s' (%d)", ruleName, decision);
        }
    }

    private static class RuleTreeVisitor implements ParseTreeVisitor<Void> { //parser

        private final org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeBuilder builder; //parser
        private int currentDepth; //parser

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
            builder.addRuleNode(ruleName, alt, ival.a, ival.b, currentDepth, () -> { //parser
                int n = node.getChildCount(); //parser
                for (int i = 0; i < n; i++) { //parser
                    ParseTree c = node.getChild(i); //parser
                    currentDepth++; //parser
                    c.accept(this); //parser
                    currentDepth--; //parser
                } //parser
            }); //parser
            return null; //parser
        } //parser

        @Override //parser
        public Void visitTerminal(TerminalNode node) { //parser
            builder.addTerminalNode(node.getSymbol().getTokenIndex(), node.getText(), currentDepth + 1); //parser
            return null; //parser
        } //parser

        @Override //parser
        public Void visitErrorNode(ErrorNode node) { //parser
            builder.addErrorNode(node.getSourceInterval().a, node.getSourceInterval().b, currentDepth, node.getSymbol() //parser
                .getStartIndex(), node.getSymbol().getStopIndex(), node.getSymbol().getText(), node.getSymbol().getType()); //parser
            return null; //parser
        } //parser
    } //parser

    public static final class CharSequenceCharStream implements CharStream {

        private CharSequence data;
        private int n;
        private int p;

        public CharSequenceCharStream(String input) {
            this.data = input;
            this.n = input.length();
        }

        public CharSequenceCharStream(CharSequence seq) {
            this.data = seq;
            this.n = seq.length();
        }

        int data(int ix) {
            return data.charAt(ix);
        }

        public void reset() {
            p = 0;
        }

        @Override
        public void consume() {
            if (p >= n) {
                assert LA(1) == IntStream.EOF;
                throw new IllegalStateException("cannot consume EOF");
            }
            if (p < n) {
                p++;
            }
        }

        @Override
        public int LA(int i) {
            if (i == 0) {
                return 0;
            }
            if (i < 0) {
                i++;
                if ((p + i - 1) < 0) {
                    return IntStream.EOF;
                }
            }

            if ((p + i - 1) >= n) {
                return IntStream.EOF;
            }
            return data(p + i - 1);
        }

        public int LT(int i) {
            return LA(i);
        }

        @Override
        public int index() {
            return p;
        }

        @Override
        public int size() {
            return n;
        }

        @Override
        public int mark() {
            return -1;
        }

        @Override
        public void release(int marker) {
        }

        @Override
        public void seek(int index) {
            if (index <= p) {
                p = index;
                return;
            }
            index = Math.min(index, n);
            while (p < index) {
                consume();
            }
        }

        @Override
        public String getText(Interval interval) {
            int start = interval.a;
            int stop = interval.b;
            if (stop >= n) {
                stop = n - 1;
            }
            int count = stop - start + 1;
            if (start >= n) {
                return "";
            }
            return data.subSequence(start, start + count).toString();
        }

        @Override
        public String getSourceName() {
            return UNKNOWN_SOURCE_NAME;
        }

        @Override
        public String toString() {
            return data.toString();
        }
    }
}
