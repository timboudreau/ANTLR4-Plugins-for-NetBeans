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
package org.nemesis.antlr.live.parsing.extract.ambig;

import com.mastfrog.antlr.cc.CodeCompletionCore;
import com.mastfrog.antlr.cc.FollowSetsHolder;
import com.mastfrog.util.collections.IntMap;
import ignoreme.placeholder.DummyLanguageLexer;
import ignoreme.placeholder.DummyLanguageParser;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 *
 * @author Tim Boudreau
 */
public class AmbiguityExtractor {

    public AmbiguitiesInfo extract(CharSequence seq, BooleanSupplier cancelled) throws Exception {
        return extract(seq, 0, cancelled);
    }

    public AmbiguitiesInfo extract(CharSequence seq, int ruleIndex, BooleanSupplier cancelled) throws Exception {
        CharStream str = CharStreams.fromString(seq.toString());
        DummyLanguageLexer lex = new DummyLanguageLexer(str);
        lex.removeErrorListeners();
        TokenStream tokenStream = new CommonTokenStream(lex);
        DummyLanguageParser parser = new DummyLanguageParser(tokenStream);
        parser.removeErrorListeners();
        parser.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
        AmbiguityFinder finder = new AmbiguityFinder(cancelled);
        parser.addErrorListener(finder);
        String startRuleMethodName = DummyLanguageParser.ruleNames[ruleIndex].replace('-', '_');
        Method method = DummyLanguageParser.class.getMethod(startRuleMethodName);
        ParseTree pt = (ParseTree) method.invoke(parser);
        return pt.accept(finder);
    }

    static final class CoreHolder {

        private final CodeCompletionCore core;
        private final Map<String, IntMap<FollowSetsHolder>> followSets;

        CoreHolder(DummyLanguageParser parser) {
            followSets = new HashMap<>(parser.getRuleNames().length);
            core = new CodeCompletionCore(parser, rule -> true, tok -> false, followSets);
        }
    }

    private static ThreadLocal<CoreHolder> core;

    private static CoreHolder core(DummyLanguageParser parser) {
        CoreHolder c = core.get();
        if (c != null) {
            return c;
        }
        c = new CoreHolder(parser);
        core.set(c);
        return c;
    }

//    private static int[] ruleNameToRuleIndex;
//    private static String[] namesSorted;
//    private static int[] ruleNameToRuleIndex() {
//        if (ruleNameToRuleIndex == null) {
//            int[] result
//                    = ruleNameToRuleIndex = new int[DummyLanguageParser.ruleNames.length];
//            List<String> namesInOrder = Arrays.asList(DummyLanguageParser.ruleNames);
//            String[] names = namesSorted = Arrays.copyOf(DummyLanguageParser.ruleNames, result.length);
//            Arrays.sort(names);
//            for (int i = 0; i < result.length; i++) {
//                String rule = namesIn.get(i);
//                int index = namesInOrder.indexOf(rule);
//                result[i] = index;
//            }
//        }
//        return ruleNameToRuleIndex;
//    }
//
//    private static int ruleNumberForRuleName(String ruleName) {
//        int[] rntri = ruleNameToRuleIndex();
//        int index = Arrays.binarySearch(namesSorted, ruleName);
//        return index < 0 ? -1 : rntri[index];
//    }

    private static void analyze(DummyLanguageParser recognizer, DFA dfa, int startIndex, int stopIndex,
            boolean exact, BitSet ambigAlts, ATNConfigSet configs, AmbiguitiesInfo into) {
//        CoreHolder core = core(recognizer);
//        CandidatesCollection candidates = core.core.collectCandidates(startIndex, recognizer.getContext());
//        int[] nameToIndex = ruleNameToRuleIndex();
//        IntMap<Set<IntList>> pathsForRule = IntMap.create(nameToIndex.length / 2, true, HashSet::new);
//        candidates.rules.forEach((int rule, IntList path) -> {
//
//        });
    }

    public static final class AmbiguitiesInfo {

    }

    private static final class AmbiguityFinder extends AbstractParseTreeVisitor<AmbiguitiesInfo> implements ANTLRErrorListener {

        private final BooleanSupplier cancelled;
        private final AmbiguitiesInfo info = new AmbiguitiesInfo();

        public AmbiguityFinder(BooleanSupplier cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex,
                boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
            analyze((DummyLanguageParser) recognizer, dfa, startIndex, stopIndex, exact, ambigAlts, configs, info);
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                int line, int charPositionInLine, String msg, RecognitionException e) {
        }

        @Override
        public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex,
                int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
        }

        @Override
        public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex,
                int stopIndex, int prediction, ATNConfigSet configs) {
        }

        @Override
        protected AmbiguitiesInfo defaultResult() {
            return info;
        }
    }
}
