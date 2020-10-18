/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.live.language.ambig;

import com.mastfrog.util.collections.IntMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.TokenStream;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.GrammarParserInterpreter;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.editor.ops.DocumentOperator;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
public class AmbiguityAnalyzer {

    private final FileObject grammar;
    private final STVProvider prov;
    private final ParseTreeProxy proxy;

    AmbiguityAnalyzer(FileObject grammar, STVProvider prov, ParseTreeProxy proxy) {
        this.grammar = grammar;
        this.prov = prov;
        this.proxy = proxy;
    }

    public static AmbiguityAnalyzer create(FileObject grammar, ParseTreeProxy proxy) {
        AmbiguityUI ui = new AmbiguityUI();
        return new AmbiguityAnalyzer(grammar, ui, proxy);
    }

    private IntMap<PositionRange> rangesForGrammarStates(Grammar grammar, IntMap<PositionRange> result) {
        try {
            if (grammar.stateToGrammarRegionMap != null && !grammar.stateToGrammarRegionMap.isEmpty()) {
                DataObject dob = DataObject.find(this.grammar);
                EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                if (ck != null) {
                    StyledDocument doc = ck.openDocument();
                    if (doc != null) {
                        return DocumentOperator.render(doc, () -> {
                            TokenStream str = grammar.originalTokenStream;
                            int len = doc.getLength();
                            PositionFactory pf = PositionFactory.forDocument(doc);
                            for (Map.Entry<Integer, Interval> e : grammar.stateToGrammarRegionMap.entrySet()) {
                                int state = e.getKey();
                                int firstTokenOffset = e.getValue().a;
                                int lastTokenOffset = e.getValue().b;
                                if (firstTokenOffset >= 0) {
                                    int start, end;
                                    if (firstTokenOffset == lastTokenOffset || lastTokenOffset < 0) {
                                        CommonToken onlyToken = (CommonToken) str.get(firstTokenOffset);
                                        start = onlyToken.getStartIndex();
                                        end = onlyToken.getStopIndex() + 1;
                                    } else {
                                        CommonToken firstToken = (CommonToken) str.get(firstTokenOffset);
                                        CommonToken lastToken = (CommonToken) str.get(lastTokenOffset);
                                        start = firstToken.getStartIndex();
                                        end = lastToken.getStopIndex() + 1;
                                    }
                                    if (end > start && end <= len) {
                                        result.put(state, pf.range(start, Position.Bias.Forward, end, Position.Bias.Forward));
                                    }
                                }
                            }
                            return result;
                        });
                    }
                }
            }
        } catch (IOException | BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        return result;
    }

    @Messages({
        "preparing=Preparing to reparse...",
        "reparsing=Reparsing for ambiguity",
        "noGenerationResult=Generation from grammar failed",
        "noGrammar=Generation did not produce a Grammar to interrorgate",
        "noParse=Parsing grammar failed",
        "ambiguityNotDetected=Ambiguity not detected"
    })
    public void analyze(ProgressHandle progress, CharSequence seq, int start, int stop, BitSet conflictingAlternatives,
            int decision, int ruleIndex, int targetState) {
        AE ae = null;
        try {
            progress.switchToDeterminate(4);
            AntlrGenerationResult res = RebuildSubscriptions.recentGenerationResult(grammar);
            if (res == null) {
                StatusDisplayer.getDefault().setStatusText(Bundle.noGenerationResult(), StatusDisplayer.IMPORTANCE_ERROR_HIGHLIGHT);
                return;
            }
            progress.progress(1);
            Grammar g = res.mainGrammar;
            if (g == null) {
                StatusDisplayer.getDefault().setStatusText(Bundle.noGrammar(), StatusDisplayer.IMPORTANCE_ERROR_HIGHLIGHT);
                return;
            }
            progress.setDisplayName(Bundle.preparing());
            TokenSource lexer = new ProxyTokenSource(proxy);
            /*
            if (g.isCombined()) {
                lexer = g.implicitLexer.createLexerInterpreter(chars);
            } else {
                ObjectGraph<UnixPath> deps = res.dependencyGraph();
                Set<UnixPath> directDeps = deps.children(res.grammarFile.path());
                System.out.println("direct deps: " + directDeps);
                Set<Grammar> all = res.allGrammars;
                Grammar lexerGrammar = null;
                for (Grammar gg : all) {
                    if (gg == g) {
                        continue;
                    }
                    String fn = gg.fileName;
                    System.out.println("  dep filename " + fn);
                    for (UnixPath path : directDeps) {
                        String ext = path.extension();
                        if ("g4".equals(ext) || "g".equals(ext)) {
                            if (path.toString().equals(gg.fileName) || path.rawName().equals(gg.name)) {
                                lexerGrammar = gg;
                                break;
                            }
                        }
                    }
                }
                if (lexerGrammar == null) {
                    System.out.println("  did not find lexer grammar in " + all);
                    return;
                }
                System.out.println("LEXER GRAMMAR " + lexerGrammar.name + " " + lexerGrammar.fileName);
                lexer = lexerGrammar.createLexerInterpreter(chars);
                System.out.println("HAVE LEXER " + lexer);
            }
             */
            progress.progress(2);

            IntMap<PositionRange> editSafeRegions = IntMap.create(64);
//            lexer.reset();
//            lexer.removeErrorListeners();
            CommonTokenStream cts = new CommonTokenStream(lexer, 0);
            cts.seek(0);
            GrammarParserInterpreter gpi = g.createGrammarParserInterpreter(cts);
            gpi.reset();
            gpi.setErrorHandler(new DefaultErrorStrategy());
            gpi.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
            gpi.setBuildParseTree(true);
            gpi.setProfile(true);
//            System.out.println(" parser configured");
            ae = new AE(g, gpi, this, seq, start, stop, conflictingAlternatives,
                    decision, ruleIndex, targetState, editSafeRegions);
            gpi.removeErrorListeners();

            gpi.addErrorListener(ae);
            progress.setDisplayName(Bundle.reparsing());
            progress.progress(3);
            ParserRuleContext ctx = gpi.parse(0);

            if (ctx == null) {
//                System.out.println("GOT NULL RULE CONTEXT");
                StatusDisplayer.getDefault().setStatusText(Bundle.noParse(), StatusDisplayer.IMPORTANCE_ERROR_HIGHLIGHT);
                return;
            }
            ctx.accept(ae);
        } catch (Exception | Error ex) {
            ex.printStackTrace();
        } finally {
            progress.finish();
            if (ae != null && !ae.hitTarget) {
                StatusDisplayer.getDefault().setStatusText(Bundle.ambiguityNotDetected(), StatusDisplayer.IMPORTANCE_ERROR_HIGHLIGHT);
            }
//            System.out.println("exit - hit target ? " + (ae == null ? "null" : ae.hitTarget));
        }
    }

    private void handleAmbiguity(GrammarParserInterpreter gpi, Grammar grammar, Parser recognizer,
            int stopIndex, BitSet ambigAlts, int startIndex, DFA dfa, ATNConfigSet configs,
            CharSequence parsing, IntMap<PositionRange> editSafeRegions) throws RecognitionException {

        String[] rules = recognizer.getRuleNames();
        Vocabulary vocab = recognizer.getVocabulary();

        String ruleName = rules[dfa.atnStartState.ruleIndex];

        CharSequence offendingText = recognizer.getTokenStream().getText(Interval.of(startIndex, stopIndex));

        STV stv = prov.stv(this.grammar, grammar, startIndex, stopIndex, ambigAlts, stopIndex, ruleName, offendingText, editSafeRegions);
        if (stv == null) {
            return;
        }
        // Populate the map here, so we can be sure it is populated
        // with at least everything we have seen thus far - if we do
        // this before we have started parsing it can be incomplete
        rangesForGrammarStates(grammar, editSafeRegions);

        try {
            stv.status("Analyzing possible parse trees", -1, -1);

            List<List<List<PT>>> allPaths = new ArrayList<>();
            boolean bailed = false;
            try {
                List<ParserRuleContext> all = GrammarParserInterpreter.getAllPossibleParseTrees(
                        grammar, recognizer, recognizer.getTokenStream(),
                        dfa.decision, ambigAlts, startIndex, stopIndex, 0);
//                System.out.println("GOT " + all.size() + " possible parse trees");
                stv.status("Have " + all.size() + " parse trees", -1, -1);
                int of = all.size() + 2;
                for (int i = 0; i < all.size(); i++) {
                    ParserRuleContext ctx = all.get(i);
                    stv.status("Analyzing " + (i + 1) + "/" + of, i + 1, of);
                    allPaths.add(PT.allPaths(ctx, rules, vocab, startIndex, stopIndex));
                    int alt = ctx.getAltNumber();
                    boolean altPresent = ambigAlts.get(alt);
                    Interval ival = grammar.getStateToGrammarRegion(ctx.invokingState);
                    visit(gpi, grammar, rules, 0, 0, ctx, stv, ival);
                }
                stv.status("Computing diffs", of - 1, of);
            } catch (ParseCancellationException ex) {
                stv.status("Parse failed, try alternate method", -1, -1);
                ex.printStackTrace();
                bailed = true;
            }
            if (bailed && allPaths.isEmpty()) {
                List<ParserRuleContext> all = GrammarParserInterpreter.getLookaheadParseTrees(grammar, gpi, recognizer.getTokenStream(),
                        0, dfa.decision, startIndex, stopIndex);
                stv.status("Have " + all.size() + " parse trees", -1, -1);
                int of = all.size() + 2;
                for (int i = 0; i < all.size(); i++) {
                    ParserRuleContext ctx = all.get(i);
                    stv.status("Analyzing " + (i + 1) + "/" + of, i + 1, of);
                    allPaths.add(PT.allPaths(ctx, rules, vocab, startIndex, stopIndex));
                    int alt = ctx.getAltNumber();
                    boolean altPresent = ambigAlts.get(alt);
                    Interval ival = grammar.getStateToGrammarRegion(ctx.invokingState);
                    visit(gpi, grammar, rules, 0, 0, ctx, stv, ival);
                }
                stv.status("Computing diffs", of - 1, of);
            }

//            System.out.println("ALLPATHS: " + allPaths.size());
            if (!allPaths.isEmpty()) {
                Map<PT, List<List<PT>>> diff = PT.diffPaths(allPaths, stv);
//                System.out.println("DIFF SIZE " + diff.size());
                stv.visitPaths(gpi, grammar, diff, vocab, rules);
            }
        } finally {
            stv.status("Finishing", 100, 100);
            stv.done();
        }
    }

    private void visit(GrammarParserInterpreter gpi, Grammar grammar, String[] ruleNames,
            int depth, int childIndex, ParseTree ctx, STV stv, Interval ival) {

        String name;
        int ix = -1;
        if (ctx instanceof ParserRuleContext) {
            ix = ((ParserRuleContext) ctx).getRuleIndex();
            name = ruleNames[ix];
        } else {
            name = ctx.getClass().getName();
        }
        stv.visit(gpi, grammar, depth, ix, name, childIndex, ival, ctx);
        if (ctx.getChildCount() > 0) {
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree childTree = ctx.getChild(i);
                Interval childIval = null;
                if (childTree instanceof ParserRuleContext) {
                    childIval = grammar.getStateToGrammarRegion(((ParserRuleContext) childTree).invokingState);
                }
                visit(gpi, grammar, ruleNames, depth + 1, i, childTree, stv, childIval);
            }
        }
    }

    static int depth(ParseTree ctx) {
        int count = 0;
        ctx = ctx.getParent();
        while (ctx != null) {
            count++;
            ctx = ctx.getParent();
        }
        return count;
    }

    interface STVProvider {

        STV stv(FileObject grammarFile, Grammar grammar, int startIndex, int stopIndex, BitSet bits, int ruleIndex,
                String ruleName, CharSequence offendingText, IntMap<PositionRange> editSafeRegions);
    }

    interface STV {

        void status(String msg, int step, int of);

        boolean visit(GrammarParserInterpreter gpi, Grammar grammar, int depth, int ruleIndex, String ruleName, int childIndex, Interval invokingStateGrammarRegions, ParseTree tree);

        void visitPaths(GrammarParserInterpreter gpi, Grammar grammar, Map<PT, List<List<PT>>> pathsByTail, Vocabulary vocab, String[] ruleNames);

        void done();
    }

    static final class AE extends AbstractParseTreeVisitor<Void> implements ANTLRErrorListener {

        private final Grammar grammar;
        private final GrammarParserInterpreter gpi;
        private final AmbiguityAnalyzer aa;
        private final CharSequence parsing;
        private final int targetStart;
        private final int targetStop;
        private final BitSet expectedConflictingAlternatives;
        private final int expectedDecision;
        private boolean hitTarget;
        private final int ruleIndex;
        private final int targetState;
        private final IntMap<PositionRange> editSafeRegions;

        public AE(Grammar grammar, GrammarParserInterpreter gpi, AmbiguityAnalyzer aa, CharSequence parsing,
                int targetStart, int targetStop, BitSet expectedConflictingAlternatives, int expectedDecision,
                int ruleIndex, int targetState, IntMap<PositionRange> editSafeRegions) {
            this.grammar = grammar;
            this.editSafeRegions = editSafeRegions;
            this.gpi = gpi;
            this.aa = aa;
            this.parsing = parsing;
            this.targetStart = targetStart;
            this.targetStop = targetStop;
            this.expectedConflictingAlternatives = expectedConflictingAlternatives;
            this.expectedDecision = expectedDecision;
            this.ruleIndex = ruleIndex;
            this.targetState = targetState;
        }

        private boolean fuzzy(int expect, int val) {
            return expect == val || (expect - 1) == val || (expect + 1) == val;
        }

        @Override
        public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact,
                BitSet ambigAlts, ATNConfigSet configs) {
            boolean isTarget
                    = //                    fuzzy(targetStart, startIndex) && fuzzy(targetStop, stopIndex)
                    targetStart == startIndex && targetStop == stopIndex
                    && dfa.decision == expectedDecision
                    && dfa.atnStartState.ruleIndex == ruleIndex
                    && dfa.atnStartState.stateNumber == targetState;
            ;
            if (isTarget) {
                hitTarget = true;
//                System.out.println("AMBIGUITY " + recognizer.getRuleNames()[dfa.atnStartState.ruleIndex]);
                aa.handleAmbiguity(gpi, grammar, recognizer, stopIndex, ambigAlts, startIndex, dfa, configs, parsing,
                        editSafeRegions);
//            } else {
//                System.out.println("wrong ambig " + startIndex + " / " + stopIndex + " dec " + dfa.decision
//                        + " state " + dfa.atnStartState.stateNumber);
//                System.out.println("looking for " + targetStart + " / " + targetStop + " dec " + expectedDecision
//                        + " state " + targetState);
            }
        }

        @Override
        public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {

        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
//            System.out.println("Syntax error " + msg);
        }

        @Override
        public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
        }

        @Override
        public Void visitChildren(RuleNode node) {
            if (hitTarget) {
                return null;
            }
            return super.visitChildren(node);
        }
    }
}
