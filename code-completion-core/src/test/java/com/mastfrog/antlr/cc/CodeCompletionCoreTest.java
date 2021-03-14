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
package com.mastfrog.antlr.cc;

import com.mastfrog.predicates.integer.IntPredicates;
import com.mastfrog.util.collections.ArrayUtils;
import org.nemesis.antlr.sample.AntlrSampleFiles;
import com.mastfrog.util.collections.IntList;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.collections.IntSet;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Pair;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.ANTLRv4Parser.*;
import org.nemesis.simple.SampleFile;

public class CodeCompletionCoreTest {

    private static final int ITERATIONS = 5;

    @Test
    public void testSample() throws Exception {
        testSample(AntlrSampleFiles.RUST);
    }

    public void testSample(AntlrSampleFiles sample) throws Exception {
        if (true) {
            // This is a deathly slow many-iterations performance test,
            // but exercises everything
            return;
        }
        CCLog.enable(true);
        boolean foundAnyOpt = false;
        boolean foundAnyOrig = false;

        long origTime = 0;
        long newTime = 0;

        int count = 0;
        ANTLRv4Lexer lex = sample.lexer();
        List<Token> tokens = new ArrayList<>();
        for (;;) {
            Token t = lex.nextToken();
            tokens.add(t);
            count++;
            if (t.getType() == -1) {
                break;
            }
        }
        long[] times = new long[ITERATIONS * 2];

        IntList failing = IntList.create(count);

        Map<String, IntMap<FollowSetsHolder>> m = new HashMap<>();

        for (int j = 0; j < ITERATIONS; j++) {
            long iterNewCumulative = 0;
            long iterOldCumulative = 0;
            ANTLRv4Parser p = parser(sample);
            CodeCompletionCoreOrig orig = new CodeCompletionCoreOrig(p, setOf(PREFERRED_RULE_IDS), setOf(IGNORABLE_TOKEN_IDS));

            CodeCompletionCore core = new CodeCompletionCore(p, ANTLR_PREFERRED_RULES, ANTLR_IGNORE, m);
            IntMap<Pair<CandidatesCollection, CodeCompletionCoreOrig.CandidatesCollection>> failures = IntMap.create(count);
            for (int i = 0; i < count; i++) {
                CCLog.clear();

                long then = System.currentTimeMillis();
                CandidatesCollection coll = core.collectCandidates(i, null);
                long newElapsed = System.currentTimeMillis() - then;
                newTime += newElapsed;
                iterNewCumulative += newElapsed;

                coll = coll.copy();

                if (!coll.isEmpty()) {
                    foundAnyOpt = true;
                }

                then = System.currentTimeMillis();
                CodeCompletionCoreOrig.CandidatesCollection cands
                        = orig.collectCandidates(i, null);
                long origElapsed = System.currentTimeMillis() - then;
                iterOldCumulative += origElapsed;
                origTime += origElapsed;

                if (!isEmpty(cands)) {
                    foundAnyOrig = true;
                }
                boolean ok = assertEq(i + ". ", cands, coll);
                if (!ok) {
                    failing.add(i);
                    failures.put(i, new Pair<>(coll.copy(), cands.copy()));
                    System.out.println((j + 1) + ". Fail on token " + i + " - " + tokens.get(i)
                            + " expected:\n" + cands.toString(ruleNames, VOCABULARY)
                            + "\n\nbut got\n" + coll.toString(ruleNames, VOCABULARY));
                }
                if (!failing.isEmpty()) {
                    StringBuilder sb = new StringBuilder(CCLog.mismatch(orig, core)).append('\n');
                    int iter = j + 1;
                    failures.forEachPair((tok, pair) -> {
                        sb.append(iter).append(". Mismatch on token ").append(VOCABULARY.getSymbolicName(tok))
                                .append("orig ").append(pair.b.toString(ruleNames, VOCABULARY))
                                .append("new ").append(pair.a.toString(ruleNames, VOCABULARY))
                                .append("\n--------------------------------");
                    });
                    fail(sb.toString());
                }
            }
            times[j * 2] = iterNewCumulative;
            times[(j * 2) + 1] = iterOldCumulative;
        }

//        CCLog.print();
//        }
        DecimalFormat fmt = new DecimalFormat("##0.##");

        System.out.println("ORIG ELAPSED: " + origTime);
        System.out.println("NIMP ELAPSED: " + newTime);
        System.out.println("CUMULATIVE SPEEDUP: "
                + fmt.format(
                        ((double) (origTime) / (double) newTime) * 100D)
                + "%");

        for (int i = 0; i < times.length; i += 2) {
            double pct = (double) (times[i+1]) / (double) times[i];
            System.out.println("Iter " + i + ". " + times[i + 1] + "ms / "
                    + times[i] + " speedup " + fmt.format(pct * 100D) + "%");
        }

        System.out.println("FAILING (" + failing.size() + "/" + count + "): " + failing);

        if (!foundAnyOrig) {
            fail("Orig found nothing");
        }
        if (!foundAnyOpt) {
            fail("Found nothing");
        }
    }

    static String rulesToString(CandidatesCollection coll) {
        StringBuilder sb = new StringBuilder();
        coll.rules.forEach((rule, callStack) -> {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(nameForRuleId(rule));
            if (!callStack.isEmpty()) {
                PrimitiveIterator.OfInt it = callStack.iterator();
                sb.append("(");
                while (it.hasNext()) {
                    sb.append(nameForRuleId(it.nextInt()));
                    if (it.hasNext()) {
                        sb.append("->");
                    }
                }
                sb.append(")");
            }
        });
        return sb.toString();
    }

    static String rulesToString(CodeCompletionCoreOrig.CandidatesCollection coll) {
        StringBuilder sb = new StringBuilder();
        coll.rules.forEach((rule, callStack) -> {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(nameForRuleId(rule));
            if (!callStack.isEmpty()) {
                Iterator<Integer> it = callStack.iterator();
                sb.append("(");
                while (it.hasNext()) {
                    sb.append(nameForRuleId(it.next()));
                    if (it.hasNext()) {
                        sb.append("->");
                    }
                }
                sb.append(")");
            }
        });
        return sb.toString();
    }

    private boolean assertEq(String msg, CodeCompletionCoreOrig.CandidatesCollection orig, CandidatesCollection nue) {
        if (isEmpty(orig) && nue.isEmpty()) {
            System.out.println("both empty for " + msg);
            return true;
        }
        Map<Integer, List<Integer>> rpos = unwrap(nue.rulePositions);
        Map<Integer, List<Integer>> rls = unwrap(nue.rules);
        Map<Integer, Set<Integer>> tks = unwrap(nue.tokens);

        Map<Integer, List<Integer>> erpos = normalizeList(orig.rulePositions);
        Map<Integer, List<Integer>> erls = normalizeList(orig.rules);
        Map<Integer, Set<Integer>> etks = normalize(orig.tokens);

        boolean positionsEqual = erpos.equals(rpos);
        boolean rulesEqual = erls.equals(rls);
        boolean tokensEqual = etks.equals(tks);

        StringBuilder sb = new StringBuilder(msg);
        if (!positionsEqual) {
            sb.append("Different positions exp \n\t").append(erpos).append(" got \n\t").append(rpos).append("\n");
        }
        if (!rulesEqual) {
            sb.append("Different rules exp \n\t").append(erls).append(" got \n\t").append(rls).append("\n");
        }
        if (!tokensEqual) {
            sb.append("Different rules exp \n\t").append(etks).append(" got \n\t").append(tks);
        }
//        assertTrue(positionsEqual && rulesEqual && tokensEqual, sb.toString());
        boolean success = positionsEqual && rulesEqual && tokensEqual;
        if (!success) {
            System.out.println(sb);
        }
        return success;
    }

    private List<Integer> normalize(List<Integer> l) {
        l = new ArrayList<>(new HashSet<>(l));
        Collections.sort(l);
        return l;
    }

    private Map<Integer, Set<Integer>> normalize(Map<Integer, List<Integer>> m) {
        Map<Integer, Set<Integer>> result = new TreeMap<>();
        for (Map.Entry<Integer, List<Integer>> e : m.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            result.put(e.getKey(), new TreeSet<>(e.getValue()));
        }
        return result;
    }

    private Map<Integer, List<Integer>> normalizeList(Map<Integer, List<Integer>> m) {
        Map<Integer, List<Integer>> result = new TreeMap<>();
        for (Map.Entry<Integer, List<Integer>> e : m.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            result.put(e.getKey(), normalize(e.getValue()));
        }
        return result;
    }

    private Map<Integer, Set<Integer>> unwrap(IntSetMapping am) {
        Map<Integer, Set<Integer>> result = new TreeMap<>();
        am.forEach((int k, IntSet v) -> {
            if (v.isEmpty()) {
                return;
            }
            Set<Integer> vals = new TreeSet<>(v);
            result.put(k, vals);
        });
        return result;
    }

    private Map<Integer, List<Integer>> unwrap(IntArrayMapping am) {
        Map<Integer, List<Integer>> result = new TreeMap<>();
        am.forEach((int k, IntList v) -> {
            if (v.isEmpty()) {
                return;
            }
            List<Integer> vals = new ArrayList<>(new HashSet<>(v));
            Collections.sort(vals);
            result.put(k, vals);
        });
        return result;
    }

    private boolean isEmpty(CodeCompletionCoreOrig.CandidatesCollection cands) {
        return cands.rulePositions.isEmpty() && cands.rules.isEmpty()
                && cands.tokens.isEmpty();
    }

    private String toS(CodeCompletionCoreOrig.CandidatesCollection cands) {
        return "Candidates(rulePositions=" + cands.rulePositions
                + ", rules=" + cands.rules + ", tokens=" + cands.tokens;
    }

    private static Set<Integer> setOf(int[] ints) {
        Set<Integer> result = new HashSet<>(ints.length);
        for (int i = 0; i < ints.length; i++) {
            result.add(ints[i]);
        }
        return result;
    }

    static ANTLRv4Parser parser(SampleFile file) throws IOException {
        CharStream stream = CharStreams.fromString(file.text());
        ANTLRv4Lexer lexer = new ANTLRv4Lexer(stream);
        lexer.removeErrorListeners();
        CommonTokenStream ts = new CommonTokenStream(lexer);
        ANTLRv4Parser parser = new ANTLRv4Parser(ts);
        parser.removeErrorListeners();
        return parser;
    }

    private static final int[] WHITESPACE_TOKEN_IDS = {PARDEC_WS, ID_WS, IMPORT_WS, CHN_WS, FRAGDEC_WS,
        HDR_IMPRT_WS, HDR_PCKG_WS, HEADER_P_WS, HEADER_WS, LEXCOM_WS,
        OPT_WS, PARDEC_OPT_WS, TOK_WS, TOKDEC_WS, TYPE_WS, WS};
    private static final int[] COMMENT_TOKEN_IDS = {
        LINE_COMMENT, BLOCK_COMMENT, CHN_BLOCK_COMMENT,
        FRAGDEC_LINE_COMMENT, CHN_LINE_COMMENT, DOC_COMMENT,
        HDR_IMPRT_LINE_COMMENT, HDR_PCKG_LINE_COMMENT,
        HEADER_BLOCK_COMMENT, HEADER_LINE_COMMENT, HEADER_P_BLOCK_COMMENT,
        HEADER_P_LINE_COMMENT, ID_BLOCK_COMMENT, ID_LINE_COMMENT,
        IMPORT_BLOCK_COMMENT, IMPORT_LINE_COMMENT, LEXCOM_BLOCK_COMMENT,
        LEXCOM_LINE_COMMENT, OPT_BLOCK_COMMENT, OPT_LINE_COMMENT, PARDEC_LINE_COMMENT,
        PARDEC_BLOCK_COMMENT, PARDEC_OPT_LINE_COMMENT, PARDEC_OPT_BLOCK_COMMENT,
        TOK_BLOCK_COMMENT, TOK_LINE_COMMENT,
        TYPE_LINE_COMMENT};

    private static final int[] PREFERRED_RULE_IDS = {
        ANTLRv4Parser.RULE_ebnfSuffix,
        ANTLRv4Parser.RULE_parserRuleIdentifier,
        ANTLRv4Parser.RULE_fragmentRuleIdentifier,
        ANTLRv4Parser.RULE_tokenRuleIdentifier,
        ANTLRv4Parser.RULE_ruleElementIdentifier,};

    private static final int[] IGNORABLE_TOKEN_IDS;

    static {
        IGNORABLE_TOKEN_IDS = ArrayUtils.concatenate(WHITESPACE_TOKEN_IDS, COMMENT_TOKEN_IDS);
    }

    private static final IntPredicate ANTLR_IGNORE
            = IntPredicates.anyOf(IGNORABLE_TOKEN_IDS);

    private static final IntPredicate ANTLR_PREFERRED_RULES
            = IntPredicates.anyOf(PREFERRED_RULE_IDS);

    private static final IntPredicate ANTLR_COMMENTS
            = IntPredicates.anyOf(COMMENT_TOKEN_IDS);

    private static final IntPredicate ANTLR_WHITESPACE
            = IntPredicates.anyOf(WHITESPACE_TOKEN_IDS);

    public static final String nameForRuleId(int ruleId) {
        switch (ruleId) {
            case 0:
                return "grammarFile";
            case 1:
                return "grammarSpec";
            case 2:
                return "grammarType";
            case 3:
                return "analyzerDirectiveSpec";
            case 4:
                return "optionsSpec";
            case 5:
                return "optionSpec";
            case 6:
                return "superClassSpec";
            case 7:
                return "languageSpec";
            case 8:
                return "tokenVocabSpec";
            case 9:
                return "tokenLabelTypeSpec";
            case 10:
                return "delegateGrammars";
            case 11:
                return "delegateGrammarList";
            case 12:
                return "delegateGrammar";
            case 13:
                return "grammarIdentifier";
            case 14:
                return "tokensSpec";
            case 15:
                return "tokenList";
            case 16:
                return "channelsSpec";
            case 17:
                return "idList";
            case 18:
                return "action";
            case 19:
                return "headerAction";
            case 20:
                return "memberAction";
            case 21:
                return "actionDestination";
            case 22:
                return "headerActionBlock";
            case 23:
                return "headerActionContent";
            case 24:
                return "packageDeclaration";
            case 25:
                return "importDeclaration";
            case 26:
                return "singleTypeImportDeclaration";
            case 27:
                return "actionBlock";
            case 28:
                return "modeSpec";
            case 29:
                return "modeDec";
            case 30:
                return "ruleSpec";
            case 31:
                return "parserRuleSpec";
            case 32:
                return "parserRuleDeclaration";
            case 33:
                return "exceptionGroup";
            case 34:
                return "exceptionHandler";
            case 35:
                return "finallyClause";
            case 36:
                return "parserRulePrequel";
            case 37:
                return "parserRuleReturns";
            case 38:
                return "throwsSpec";
            case 39:
                return "localsSpec";
            case 40:
                return "ruleAction";
            case 41:
                return "parserRuleDefinition";
            case 42:
                return "parserRuleLabeledAlternative";
            case 43:
                return "altList";
            case 44:
                return "parserRuleAlternative";
            case 45:
                return "parserRuleElement";
            case 46:
                return "labeledParserRuleElement";
            case 47:
                return "parserRuleAtom";
            case 48:
                return "parserRuleReference";
            case 49:
                return "actionBlockArguments";
            case 50:
                return "tokenRuleSpec";
            case 51:
                return "fragmentRuleSpec";
            case 52:
                return "tokenRuleDeclaration";
            case 53:
                return "tokenRuleDefinition";
            case 54:
                return "fragmentRuleDeclaration";
            case 55:
                return "fragmentRuleDefinition";
            case 56:
                return "lexerRuleBlock";
            case 57:
                return "lexerRuleAlt";
            case 58:
                return "lexerRuleElements";
            case 59:
                return "lexerRuleElement";
            case 60:
                return "lexerRuleElementBlock";
            case 61:
                return "lexerCommands";
            case 62:
                return "lexerCommand";
            case 63:
                return "lexComChannel";
            case 64:
                return "lexComMode";
            case 65:
                return "lexComPushMode";
            case 66:
                return "lexerRuleAtom";
            case 67:
                return "ebnf";
            case 68:
                return "ebnfSuffix";
            case 69:
                return "notSet";
            case 70:
                return "blockSet";
            case 71:
                return "setElement";
            case 72:
                return "block";
            case 73:
                return "characterRange";
            case 74:
                return "terminal";
            case 75:
                return "elementOptions";
            case 76:
                return "elementOption";
            case 77:
                return "tokenOption";
            case 78:
                return "identifier";
            case 79:
                return "ruleElementIdentifier";
            case 80:
                return "classIdentifier";
            case 81:
                return "genericClassIdentifier";
            case 82:
                return "packageIdentifier";
            case 83:
                return "parserRuleIdentifier";
            case 84:
                return "tokenRuleIdentifier";
            case 85:
                return "fragmentRuleIdentifier";
            default:
                return null;
        }
    }
}
