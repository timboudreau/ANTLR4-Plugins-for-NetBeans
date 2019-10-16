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
package org.nemesis.antlr.language.formatting;

import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.RuleNode;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.ANTLRv4Lexer.VOCABULARY;
import static org.nemesis.antlr.ANTLRv4Lexer.modeNames;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.language.formatting.G4FormatterStubTest.CombinatoricConfigurer.BooleanSetter;
import org.nemesis.antlr.language.formatting.G4FormatterStubTest.CombinatoricConfigurer.IntSetter;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.ColonHandling;
import org.nemesis.antlrformatting.api.Criterion;
import org.nemesis.antlrformatting.api.FormattingResult;
import org.nemesis.antlrformatting.spi.AntlrFormatterProvider;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class G4FormatterStubTest {

    private String tokensLexer;
    private String tokensParser;
    private String antlrParser;
    private G4FormatterStub stub;
    private AntlrFormatterProvider<Preferences, AntlrCounters> prov;
    private String sensorsParser;
    private String rustGrammar;

    @Test
    public void testDev() {
        String toTest = rustGrammar;
        MockPreferences p = new MockPreferences();
        p.putBoolean(AntlrFormatterConfig.KEY_FLOATING_INDENT, true);
        p.putInt(AntlrFormatterConfig.KEY_INDENT, 4);
        p.putInt(AntlrFormatterConfig.KEY_MAX_LINE, 80);
        p.putBoolean(AntlrFormatterConfig.KEY_WRAP, true);
        p.putBoolean(AntlrFormatterConfig.KEY_SPACES_INSIDE_PARENS, false);
        p.putBoolean(AntlrFormatterConfig.KEY_BLANK_LINE_BEFORE_RULES, true);
        p.putBoolean(AntlrFormatterConfig.KEY_REFLOW_LINE_COMMENTS, false);
        p.putBoolean(AntlrFormatterConfig.KEY_SEMICOLON_ON_NEW_LINE, false);
        p.putInt(AntlrFormatterConfig.KEY_COLON_HANDLING, ColonHandling.NEWLINE_AFTER.ordinal());
        testOne(toTest, true, p);
    }

    private Thread collectStacks(BooleanSupplier done) {
        // poor man's profiling
        Thread devThread = Thread.currentThread();
        Thread t = new Thread(() -> {
            Map<StackTraceElement, Integer> counts = new HashMap<>();
            try {
                Thread.sleep(20);
                int samples = 0;
                while (!done.getAsBoolean()) {
                    samples++;
                    StackTraceElement[] el = devThread.getStackTrace();
                    for (StackTraceElement ste : el) {
                        String fn = ste.toString();
                        if (fn != null && (fn.contains("nemesis") || fn.contains("openide") || fn.contains("netbeans") || fn.contains("java.util"))) {
                            Integer ct = counts.get(ste);
                            if (ct == null) {
                                ct = 0;
                            } else {
                                ct++;
                            }
                            counts.put(ste, ct);
                        }
                    }
                    Thread.sleep(1);
                }
                System.out.println("Sampled " + samples + " times");
                List<StackTraceElement> sorted = new ArrayList<>(counts.keySet());
                Collections.sort(sorted, (a, b) -> {
                    return counts.get(b).compareTo(counts.get(a));
                });
                for (StackTraceElement ste : sorted) {
                    System.out.println(counts.get(ste) + ". " + ste);
                }
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        });
        t.setName("stack-collector-thread");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
        return t;
    }

    boolean profile = false;

    @Test
    public void testAllPossibleSettingsCombinationsResultInValidGrammars() throws InterruptedException {
        if (true) {
            // This test formats several files with all 125 possible combinations
            // of settings and tests the results, so it is slooooowww
            return;
        }
        boolean[] done = new boolean[1];
        Thread collector = profile ? collectStacks(() -> done[0]) : null;
        try {
            testGrammarText("rustGrammar", rustGrammar, true);
            testGrammarText("sensorParser", sensorsParser, true);
            testGrammarText("tokensLexer", tokensLexer, false);
            testGrammarText("tokensParser", tokensParser, false);
            testGrammarText("antlrParser", antlrParser, true);
        } finally {
            done[0] = true;
        }
        if (collector != null) {
            collector.join();
        }
    }

    private void testOne(String toReformat, boolean parse, MockPreferences prefs) {
        FormattingResult result = prov.reformat(toReformat, 0, toReformat.length(), prefs);
        String reformatted = result.text();
        System.out.println("\n-----------------------------------------------------------------------------------");
        System.out.println(reformatted);
        System.out.println("-----------------------------------------------------------------------------------\n");

        LexingAndParsingResult base = lex(toReformat, true);
        LexingAndParsingResult nue = lex(reformatted, true);

        String tokDiff = base.isSameTokenSequence(nue);
//        System.out.println("TOK DIFF " + tokDiff);
//
//        System.out.println("ORIG ERRS " + base.errors);
//        System.out.println("ORIG PARSE ERRS " + base.parseErrors);
//
//        System.out.println("FMT ERRS " + nue.errors);
//        System.out.println("FMT PARSE ERRS " + nue.parseErrors);

        assertFalse(nue.hasErrors());
        assertNull(tokDiff, "Token seq differs: " + tokDiff);
    }

    private void testGrammarText(String name, String text, boolean parse) {
//        System.out.println("TESTING " + name);
        char[] chars = text.toCharArray();
        LexingAndParsingResult base = lex(chars, parse);
        CombinatoricConfigurer cfig = cfig();
        List<ResultEntry> failures = new ArrayList<>();
        int count = 0;
        while (!cfig.done()) {
            long then = System.currentTimeMillis();
            Preferences prefs = new MockPreferences();
            cfig.accept(prefs);
            AntlrFormatterProvider formatter = stub.toFormatterProvider("text/x-g4", AntlrCounters.class,
                    VOCABULARY, modeNames, G4FormatterStubTest::lexerFor,
                    AntlrCriteria.ALL_WHITESPACE, ANTLRv4Parser.ruleNames, G4FormatterStubTest::rn);

            FormattingResult res = formatter.reformat(new ANTLRInputStream(chars, chars.length), 0, text.length(), prefs);
            String txt = res.text();
            LexingAndParsingResult nue = lex(txt.toCharArray(), parse);
            if (nue.hasErrors() || base.isSameTokenSequence(nue) != null) {
                System.out.println("  bad " + name + " " + prefs + " elapsed ms " + (System.currentTimeMillis() - then));
                failures.add(new ResultEntry(prefs, nue, txt));
                System.out.println(txt);
            } else {
                System.out.println("  ok " + name + " " + prefs + " elapsed ms " + (System.currentTimeMillis() - then));
            }
            count++;
        }
        System.out.println("cfig done " + count);
        assertNotEquals(0, count, "No iterations");
        assertTrue(count > 1, "" + count);
        StringBuilder msg = new StringBuilder();
        for (ResultEntry e : failures) {
            String s = base.isSameTokenSequence(e.res);
            if (s != null) {
                msg.append(name).append(" Token sequence altered by ").append(e.prefs).append(":\n").append(s);
            }
            if (e.res.hasErrors()) {
                if (!e.res.errors.isEmpty()) {
                    msg.append(name).append(" Syntax errors after formatting with ").append(e.prefs).append("\n");
                    for (String err : e.res.errors) {
                        msg.append("  ").append(err).append("\n");
                    }
                }
                if (!e.res.parseErrors.isEmpty()) {
                    msg.append(name).append(" Parser errors after formatting with ").append(e.prefs).append("\n");
                    for (String err : e.res.parseErrors) {
                        msg.append("  ").append(err).append("\n");
                    }
                }
            }
        }
        if (msg.length() > 0) {
            fail(msg.toString());
        }
    }

    static class ResultEntry {

        private final Preferences prefs;
        private final LexingAndParsingResult res;

        public ResultEntry(Preferences prefs, LexingAndParsingResult res, String txt) {
            this.prefs = prefs;
            this.res = res;
        }

    }

    @BeforeEach
    public void setup() throws URISyntaxException, IOException {
        ProjectTestHelper helper = ProjectTestHelper.relativeTo(G4FormatterStubTest.class);
        Path dir = helper.findChildProjectWithChangedAntlrDirAndEncoding();
        Path tokensLexer = dir.resolve("src/main/antlr/source/org/nemesis/tokens/TokensLexer.g4");
        Path tokensParser = dir.resolve("src/main/antlr/source/org/nemesis/tokens/Tokens.g4");
        Path antlrParser = helper.findAntlrGrammarProjectDir().resolve("src/main/antlr4/org/nemesis/antlr/ANTLRv4.g4");
        Path formattingSample = helper.projectBaseDir().resolve("../antlr-language-formatting-ui/src/main/resources/org/nemesis/antlr/language/formatting/ui/Sensors-g4.txt");
        this.tokensLexer = FileUtils.readString(tokensLexer, UTF_8);
        this.tokensParser = FileUtils.readString(tokensParser, UTF_8);
        this.antlrParser = FileUtils.readString(antlrParser, UTF_8);
        this.sensorsParser = FileUtils.readString(formattingSample, UTF_8);
        this.stub = new G4FormatterStub();
        this.prov = stub.toFormatterProvider("text/x-g4", AntlrCounters.class,
                VOCABULARY, modeNames, G4FormatterStubTest::lexerFor,
                AntlrCriteria.ALL_WHITESPACE, ANTLRv4Parser.ruleNames, G4FormatterStubTest::rn);
        try (InputStream in = G4FormatterStub.class.getResourceAsStream("Rust.g4")) {
            if (in == null) {
                throw new IOException("Rust.g4 missing");
            }
            this.rustGrammar = Streams.readUTF8String(in);
        }
    }

    static ANTLRv4Lexer lexerFor(CharStream stream) {
        return new ANTLRv4Lexer(stream);
    }

    static RuleNode rn(Lexer lexer) {
        CommonTokenStream str = new CommonTokenStream(lexer);
        ANTLRv4Parser parser = new ANTLRv4Parser(str);
        return parser.grammarFile();
    }

    public List<Consumer<Preferences>> adjusters(boolean val) {
        List<Consumer<Preferences>> all = new ArrayList<>(AntlrFormatterConfig.BOOLEAN_KEYS.length);
        for (String k : AntlrFormatterConfig.BOOLEAN_KEYS) {
            BooleanSetter s = new BooleanSetter(val, k);
            all.add(s);
        }
        return all;
    }

    public List<Consumer<Preferences>> lineLengthAdjusters() {
        List<Consumer<Preferences>> all = new ArrayList<>(AntlrFormatterConfig.BOOLEAN_KEYS.length);
        all.add(new IntSetter(AntlrFormatterConfig.KEY_MAX_LINE, 80));
        all.add(new IntSetter(AntlrFormatterConfig.KEY_MAX_LINE, 30));
        all.add(new IntSetter(AntlrFormatterConfig.KEY_MAX_LINE, Integer.MAX_VALUE));
        return all;
    }

    public List<Consumer<Preferences>> colonHandlingAdjusters() {
        List<Consumer<Preferences>> all = new ArrayList<>(AntlrFormatterConfig.BOOLEAN_KEYS.length);
        for (ColonHandling ch : ColonHandling.values()) {
            all.add(new IntSetter(AntlrFormatterConfig.KEY_COLON_HANDLING, ch.ordinal()));
        }
        return all;
    }

    public List<Consumer<Preferences>> indentAdjusters() {
        List<Consumer<Preferences>> all = new ArrayList<>(AntlrFormatterConfig.BOOLEAN_KEYS.length);
//        all.add(new IntSetter(AntlrFormatterConfig.KEY_INDENT, 2));
        all.add(new IntSetter(AntlrFormatterConfig.KEY_INDENT, 4));
        return all;
    }

    CombinatoricConfigurer cfig() {
        return new CombinatoricConfigurer(adjusters(true), adjusters(false), colonHandlingAdjusters());
    }

    /**
     * Iterably provides a Preferences for every possible combination of
     * settings in AntlrFormatterConfig.
     */
    static class CombinatoricConfigurer implements Consumer<Preferences> {

        private final List<Consumer<Preferences>> colonHandling;
        private final ListPairCombinatorics comb;
        private Iterator<Consumer<Preferences>> iter;
        private int runs;

        public CombinatoricConfigurer(List<Consumer<Preferences>> booleanSetters, List<Consumer<Preferences>> booleanClearers, List<Consumer<Preferences>> colonHandling) {
            comb = new ListPairCombinatorics(booleanClearers, booleanSetters);
            this.colonHandling = colonHandling;
            iter = colonHandling.iterator();
        }

        public boolean done() {
            return runs >= ((colonHandling.size() + 1) * comb.total());
        }

        @Override
        public void accept(Preferences t) {
            if (!iter.hasNext()) {
                iter = colonHandling.iterator();
            }
            Consumer<Preferences> colons = iter.next();
            List<Consumer<Preferences>> booleans = comb.get();
            colons.accept(t);
            for (Consumer<Preferences> p : booleans) {
                p.accept(t);
            }
            runs++;
        }

        static class ListPairCombinatorics implements Supplier<List<Consumer<Preferences>>> {

            private final List<Consumer<Preferences>> booleanSetters;
            private final List<Consumer<Preferences>> booleanClearers;
            private final int sz;
            private int index;
            private int loopAroundCount;

            public ListPairCombinatorics(List<Consumer<Preferences>> booleanSetters, List<Consumer<Preferences>> booleanClearers) {
                this.booleanSetters = booleanSetters;
                this.booleanClearers = booleanClearers;
                assert booleanClearers.size() == booleanSetters.size();
                sz = booleanSetters.size();
            }

            int total() {
                return (sz * sz);
            }

            public int loopAroundCount() {
                return loopAroundCount;
            }

            @Override
            public List<Consumer<Preferences>> get() {
                int oldIndex = index;
                if (index >= sz * sz) {
                    loopAroundCount++;
                    index = 0;
                }
                if (oldIndex > 0 && oldIndex % (sz * sz) == 0) {
                    index++;
                    return new ArrayList<>(booleanSetters);
                }
                List<Consumer<Preferences>> result = new ArrayList<>(booleanClearers);
                for (int i = 0; i <= sz; i++) {
                    if ((index & (1 << i)) != 0) {
                        result.set(i, booleanSetters.get(i));
                    }
                }
                index++;
                return result;
            }
        }

        static class BooleanSetter implements Consumer<Preferences> {

            private final boolean value;
            private final String key;

            public BooleanSetter(boolean value, String key) {
                this.value = value;
                this.key = key;
            }

            @Override
            public void accept(Preferences t) {
                t.putBoolean(key, value);
            }

            @Override
            public String toString() {
                return value ? key : "!" + key;
            }
        }

        static class IntSetter implements Consumer<Preferences> {

            private final String key;
            private final int value;

            public IntSetter(String key, int value) {
                this.key = key;
                this.value = value;
            }

            @Override
            public void accept(Preferences t) {
                t.putInt(key, value);
            }

            @Override
            public String toString() {
                return key + "=" + value;
            }
        }
    }

    public LexingAndParsingResult lex(String what, boolean alsoParse) {
        return lex(what.toCharArray(), alsoParse);
    }

    public LexingAndParsingResult lex(char[] what, boolean alsoParse) {
        CharStream stream = new ANTLRInputStream(what, what.length); // faster
        ANTLRv4Lexer lex = new ANTLRv4Lexer(stream);
        lex.removeErrorListeners();
        ErrL errL = new ErrL();
        List<String> txt = new ArrayList<>();
        List<String> info = tokenInfo(lex, txt);
        List<String> parseErrors = Collections.emptyList();
        if (alsoParse) {
            stream = new ANTLRInputStream(what, what.length);
            lex = new ANTLRv4Lexer(stream);
            CommonTokenStream cts = new CommonTokenStream(lex, 0);
            ANTLRv4Parser p = new ANTLRv4Parser(cts);
            p.removeErrorListeners();
            V v = new V();
            p.grammarFile().accept(v);
            parseErrors = v.errors;
        }
        LexingAndParsingResult res = new LexingAndParsingResult(info, errL.errors, parseErrors, txt);
        return res;
    }

    static class LexingAndParsingResult {

        public final List<String> tokenTypes;
        public final List<String> errors;
        public final List<String> parseErrors;
        private final List<String> tokenText;

        public LexingAndParsingResult(List<String> tokenTypes, List<String> errors, List<String> parseErrors, List<String> tokenText) {
            this.tokenTypes = tokenTypes;
            this.errors = errors;
            this.parseErrors = parseErrors;
            this.tokenText = tokenText;
        }

        boolean hasErrors() {
            return !errors.isEmpty() || !parseErrors.isEmpty();
        }

        void maybeThrow(Preferences prefs) {
            if (hasErrors()) {
                String le = Strings.join('\n', errors);
                String se = Strings.join('\n', parseErrors);
                throw new AssertionError("Parsed with errors: " + prefs + "\n" + le + "\n" + se);
            }
        }

        public String isSameTokenSequence(LexingAndParsingResult other) {
            if (tokenTypes.equals(other.tokenTypes)) {
                return null;
            }
            return diff(tokenTypes, other.tokenTypes, other.tokenText);
        }

        private String diff(List<String> expected, List<String> got, List<String> gotText) {
            int max = Math.min(expected.size(), got.size());
            StringBuilder sb = new StringBuilder();
            int consecuitive = 0;
            for (int i = 0; i < max; i++) {
                String a = expected.get(i);
                String b = got.get(i);
                if (!a.equals(b)) {
                    if (consecuitive++ > 10) {
                        continue;
                    }
                    sb.append("  ").append(i).append(". ").append(a).append(" != ").append(b).append('\n');
                    sb.append("     text-diff: '").append(tokenText.get(i))
                            .append("' != '").append(gotText.get(i)).append("'\n");
                } else {
                    consecuitive = 0;
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
            return null;
        }
    }

    static List<String> tokenInfo(ANTLRv4Lexer lexer, List<String> tokenText) {
        List<String> result = new ArrayList<>();
        Criterion whitespace = AntlrCriteria.whitespace();
        Token tok;
        for (;;) {
            tok = lexer.nextToken();
            int type = tok.getType();
            if (type == -1) {
                break;
            }
            if (whitespace.test(type)) {
                continue;
            }
            String typeName = ANTLRv4Lexer.VOCABULARY.getSymbolicName(type);
            if (typeName == null) {
                typeName = ANTLRv4Lexer.VOCABULARY.getLiteralName(type);
            }
            if (typeName == null) {
                typeName = ANTLRv4Lexer.VOCABULARY.getDisplayName(type);
            }
            result.add(typeName);
            tokenText.add(tok.getText());
        }
        return result;
    }

    static class ErrL implements ANTLRErrorListener {

        List<String> errors = new ArrayList<>();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            errors.add("Syntax Error: " + line + ":" + charPositionInLine + " sym " + offendingSymbol + " " + msg);
            if (e != null) {
                e.printStackTrace();
            }
        }

        @Override
        public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
        }

        @Override
        public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
        }

        @Override
        public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {
        }
    }

    static class V extends ANTLRv4BaseVisitor<Void> {

        List<String> errors = new ArrayList<>();

        @Override
        public Void visitErrorNode(ErrorNode node) {
            errors.add("Error Node: " + node.toString());
            return super.visitErrorNode(node);
        }
    }
}
