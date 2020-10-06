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

import com.mastfrog.graph.ObjectGraph;
import com.mastfrog.util.path.UnixPath;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.LexerInterpreter;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.GrammarParserInterpreter;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.parsing.extract.ParserExtractor.CharSequenceCharStream;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
public class AmbiguityAnalyzer {

    private final FileObject grammar;
    private final STVProvider prov;

    AmbiguityAnalyzer(FileObject grammar, STVProvider prov) {
        this.grammar = grammar;
        this.prov = prov;
    }

    public static AmbiguityAnalyzer create(FileObject grammar) {
        AmbiguityUI ui = new AmbiguityUI();
        return new AmbiguityAnalyzer(grammar, ui);
    }

    @Messages({
        "analyzing=Analyzing ambiguity...",
        "preparing=Preparing to reparse...",
        "reparsing=Reparsing for ambiguity"
    })
    public void analyze(CharSequence seq, int start, int stop, BitSet conflictingAlternatives,
            int decision, int ruleIndex, int targetState) {
        ProgressHandle progress = ProgressHandle.createHandle(Bundle.analyzing());
        try {
            progress.start(4);
            AntlrGenerationResult res = RebuildSubscriptions.recentGenerationResult(grammar);
            if (res == null) {
                System.out.println("no generation result");
                return;
            }
            progress.progress(1);
            Grammar g = res.mainGrammar;
            if (g == null) {
                System.out.println("no grammar");
                return;
            }
            System.out.println("  have gen result and grammar ");
            CharSequenceCharStream chars = new CharSequenceCharStream(seq);
            progress.setDisplayName(Bundle.preparing());
            LexerInterpreter lexer;
            if (g.isCombined()) {
                lexer = g.implicitLexer.createLexerInterpreter(chars);
            } else {
                ObjectGraph<UnixPath> deps = res.dependencyGraph();
                Set<UnixPath> directDeps = deps.children(res.grammarFile.path());
                System.out.println("direct deps: " + directDeps);
                Set<Grammar> all = res.allGrammars;
                Grammar lexerGrammar = null;
                for (Grammar gg : all) {
                    String fn = gg.fileName;
                    System.out.println("  gg filename " + fn);
                    for (UnixPath path : directDeps) {
                        if (path.toString().equals(gg.fileName) || path.rawName().equals(gg.name)) {
                            lexerGrammar = gg;
                            break;
                        }
                    }
                }
                if (lexerGrammar == null) {
                    System.out.println("  did not find lexer grammar in " + all);
                    return;
                }
                lexer = lexerGrammar.createLexerInterpreter(chars);
                System.out.println("HAVE LEXER " + lexer.getGrammarFileName());
            }
            progress.progress(2);

            lexer.removeErrorListeners();;
            CommonTokenStream cts = new CommonTokenStream(lexer);
            GrammarParserInterpreter gpi = g.createGrammarParserInterpreter(cts);
            gpi.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
            gpi.setBuildParseTree(true);
            System.out.println(" parser configured");
            AE ae = new AE(g, gpi, this, seq, start, stop, conflictingAlternatives,
                    decision, ruleIndex, targetState);
            gpi.removeErrorListeners();
            gpi.addErrorListener(ae);
            progress.setDisplayName(Bundle.reparsing());
            progress.progress(3);
            ParserRuleContext ctx = gpi.parse(0);

            if (ctx == null) {
                System.out.println("GOT NULL RULE CONTEXT");
                return;
            }
            System.out.println("  rule context " + g.getRuleNames()[ctx.getRuleIndex()]);
            ctx.accept(ae);
        } catch (Exception | Error ex) {
            ex.printStackTrace();
        } finally {
            progress.finish();
        }
    }

    private void handleAmbiguity(GrammarParserInterpreter gpi, Grammar grammar, Parser recognizer,
            int stopIndex, BitSet ambigAlts, int startIndex, DFA dfa, ATNConfigSet configs,
            CharSequence parsing) throws RecognitionException {

        String[] rules = recognizer.getRuleNames();
        Vocabulary vocab = recognizer.getVocabulary();

        String ruleName = rules[dfa.atnStartState.ruleIndex];

        CharSequence offendingText = recognizer.getTokenStream().getText(Interval.of(startIndex, stopIndex));

        STV stv = prov.stv(this.grammar, grammar, startIndex, stopIndex, ambigAlts, stopIndex, ruleName, offendingText);
        if (stv == null) {
            return;
        }

        try {
            stv.status("Analyzing possible parse trees", -1, -1);

            List<List<List<PT>>> allPaths = new ArrayList<>();
            try {
                List<ParserRuleContext> all = GrammarParserInterpreter.getAllPossibleParseTrees(
                        grammar, recognizer, recognizer.getTokenStream(),
                        dfa.decision, ambigAlts, startIndex, stopIndex, 0);
                System.out.println("GOT " + all.size() + " possible parse trees");
                stv.status("Have " + all.size() + " parse trees", -1, -1);
                int of = all.size() + 2;
                for (int i = 0; i < all.size(); i++) {
                    ParserRuleContext ctx = all.get(i);
                    stv.status("Analyzing " + (i + 1) + "/" + of, i + 1, of);
                    allPaths.add(PT.allPaths(ctx, rules, vocab, startIndex, stopIndex));
                    int alt = ctx.getAltNumber();
                    boolean altPresent = ambigAlts.get(alt);
//                    if (altPresent) {
                    Interval ival = grammar.getStateToGrammarRegion(ctx.invokingState);
                    visit(gpi, grammar, rules, 0, 0, ctx, stv, ival);
//                    }
                }
                stv.status("Computing diffs", of - 1, of);
            } catch (ParseCancellationException ex) {
                ex.printStackTrace();
            }

            System.out.println("ALLPATHS: " + allPaths.size());
            if (!allPaths.isEmpty()) {
                Map<PT, List<List<PT>>> diff = PT.diffPaths(allPaths, stv);
                System.out.println("DIFF SIZE " + diff.size());
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
                String ruleName, CharSequence offendingText);
    }

    interface STV {

        void status(String msg, int step, int of);

        boolean visit(GrammarParserInterpreter gpi, Grammar grammar, int depth, int ruleIndex, String ruleName, int childIndex, Interval invokingStateGrammarRegions, ParseTree tree);

        void visitPaths(GrammarParserInterpreter gpi, Grammar grammar, Map<PT, List<List<PT>>> pathsByTail, Vocabulary vocab, String[] ruleNames);

        void done();
    }

    static final class AE extends ANTLRv4BaseVisitor<Void> implements ANTLRErrorListener {

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

        public AE(Grammar grammar, GrammarParserInterpreter gpi, AmbiguityAnalyzer aa, CharSequence parsing,
                int targetStart, int targetStop, BitSet expectedConflictingAlternatives, int expectedDecision,
                int ruleIndex, int targetState) {
            this.grammar = grammar;
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
                System.out.println("AMBIGUITY " + recognizer.getRuleNames()[dfa.atnStartState.ruleIndex]);
                aa.handleAmbiguity(gpi, grammar, recognizer, stopIndex, ambigAlts, startIndex, dfa, configs, parsing);
            }
        }

        @Override
        public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {

        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {

        }

        @Override
        public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
        }

        @Override
        public Void visitChildren(RuleNode node) {
//            if (hitTarget) {
//                return null;
//            }
            return super.visitChildren(node);
        }
    }

}
