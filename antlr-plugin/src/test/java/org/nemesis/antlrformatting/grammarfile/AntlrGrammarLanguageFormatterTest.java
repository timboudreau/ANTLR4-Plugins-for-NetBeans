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
package org.nemesis.antlrformatting.grammarfile;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.algorithm.myers.MyersDiff;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.Patch;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ErrorNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4BaseVisitor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlrformatting.grammarfile.AntlrFormatterSettings.NewlineStyle;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.ErrorHandlingTest;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir.projectBaseDir;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrGrammarLanguageFormatterTest {

    private String nestedMapGrammar;
    private String rustMinimal;
    private String antlrLexer;
    private String antlrGrammar;
    private String java;
    private String testOne;
    private String testTwo;
    private String testThree;
    private String testFour;
    private String testFive;

    @Test
    public void testGrammarHasNotChangedDrastically() {
        AntlrGrammarLanguageFormatter.checkExpectedModeNames((missing, added) -> {
            // The lambda will only be called if they list is wrong
            fail("Grammar's mode name list does not match the set of mode names "
                    + "the formatter was written to.  \nMissing: " + missing +
                    "\nAdded: " + added);
        });
    }

    private final AntlrFormatterSettings[] allSettings = new AntlrFormatterSettings[]{
        new AntlrFormatterSettings()
        .setNewlineAfterColon(false)
        .setSpacesInsideParentheses(false)
        .setBlankLineAfterRule(NewlineStyle.ALWAYS)
        .setWrapPoint(80)
        .setReflowBlockComments(true)
        .setWrapLines(false),
        new AntlrFormatterSettings()
        .setNewlineAfterColon(true)
        .setSpacesInsideParentheses(true)
        .setBlankLineAfterRule(NewlineStyle.NEVER)
        .setWrapPoint(80)
        .setReflowBlockComments(true)
        .setWrapLines(false),
        new AntlrFormatterSettings()
        .setNewlineAfterColon(false)
        .setSpacesInsideParentheses(true)
        .setReflowBlockComments(true)
        .setBlankLineAfterRule(NewlineStyle.IF_COMPLEX)
        .setWrapPoint(32)
        .setWrapLines(true),};
    private String testSix;
    private String testSeven;

    @Test
    public void testReformattedFilesParseCleanly() throws Throwable {
        for (AntlrFormatterSettings s : allSettings) {
            testOneFormatter("nestedMapGrammar", nestedMapGrammar, s);
            testOneFormatter("rustMinimal", rustMinimal, s);
//            testOneFormatter("antlrLexer", antlrLexer, s);
            testOneFormatter("antlrGrammar", antlrGrammar, s);
            testOneFormatter("java", java, s);
        }
    }

    public void testOneFormatter(String name, String code, AntlrFormatterSettings settings) throws Throwable {
        String text = formatOne(code, settings);
//        System.out.println("FORMATTED: \n" + text);

        ANTLRv4Lexer lexer = new ANTLRv4Lexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        V v = new V(name, settings);
        lexer.addErrorListener(v);

        ANTLRv4Parser parser = new ANTLRv4Parser(new CommonTokenStream(lexer, 0));
        parser.grammarFile().accept(v);

        v.assertNoErrors(text);
        // Try to force the output window to catch up with the output before
        // closing the stream
        System.out.flush();
        Thread.sleep(500);
    }

    @Test
    public void testFormattingUnchanged() throws Throwable {
        List<String> errors = new ArrayList<>();
        int failureCount = 0;
        Set<String> failedFiles = new TreeSet<>();
        for (int i = 0; i < allSettings.length; i++) {
            for (Map.Entry<String, String> e : textForName.entrySet()) {
                String nm = e.getKey() + "-" + i;
                System.out.println(nm);
                String formatted = formatOne(e.getValue(), allSettings[i]);
                if (e.getValue().equals(formatted)) {
                    fail("Reformat of " + e.getKey() + " did not change the text at all");
                }
                String msgs = compareGolden(e.getKey(), i, formatted);
                if (msgs != null) {
                    failedFiles.add(nm);
                    failureCount++;
                    errors.add("\n-------------------- " + e.getKey() + ":" + i + "----------------");
                    errors.add("For " + e.getKey() + " with " + allSettings[i]);
                    errors.add(msgs);
                }
                System.out.println("");
            }
        }
        if (!errors.isEmpty()) {
            for (String s : errors) {
                System.out.println(s);
            }
            fail("Formatting behavior has changed for " + failureCount + " files: " + failedFiles);
        }
    }

    @Test
    public void testDebug() throws Throwable {
        String rust = formatOne(rustMinimal, allSettings[0]
                .copy().setWrapPoint(90).setWrapLines(false)
                .setBlankLineAfterRule(NewlineStyle.IF_COMPLEX));
        System.out.println("\nRUST-a:\n\n---------------\n" + rust + "\n-------------------------");
        String firstA = formatOne(testOne, allSettings[0]);
        System.out.println("\nFIRST-a:\n\n---------------\n" + firstA + "\n-------------------------");
        String firstB = formatOne(testOne, allSettings[1]);
        System.out.println("\nFIRST-b:\n\n---------------\n" + firstB + "\n-------------------------");
        String secondA = formatOne(testTwo, allSettings[0]);
        System.out.println("\nSECOND-a:\n\n---------------\n" + secondA + "\n-------------------------");
        String secondB = formatOne(testTwo, allSettings[1]);
        System.out.println("\nSECOND-b:\n\n---------------\n" + secondB + "\n-------------------------");
        String thirdA = formatOne(testThree, allSettings[0]);
        System.out.println("\nTHIRD-a:\n\n---------------\n" + thirdA + "\n-------------------------");
        String thirdB = formatOne(testThree, allSettings[1]);
        System.out.println("\nTHIRD-b:\n\n---------------\n" + thirdB + "\n-------------------------");
        String fourthA = formatOne(testFour, allSettings[0]);
        System.out.println("\nFOURTH-a:\n\n---------------\n" + fourthA + "\n-------------------------");
        String fourthB = formatOne(testFour, allSettings[1]);
        System.out.println("\nFOURTH-b:\n\n---------------\n" + fourthB + "\n-------------------------");
        String fifthA = formatOne(testFive, allSettings[0]);
        System.out.println("\nFIFTH-a:\n\n---------------\n" + fifthA + "\n" + allSettings[0] + "\n-------------------------");
        String fifthB = formatOne(testFive, allSettings[1]);
        System.out.println("\nFIFTH-b:\n\n---------------\n" + fifthB + "\n" + allSettings[1] + "\n-------------------------");
        String sixthA = formatOne(testSix, allSettings[0]);
        System.out.println("\nSIXTH-a:\n\n---------------\n" + sixthA + "\n" + allSettings[0] + "\n-------------------------");
        String sixthB = formatOne(testSix, allSettings[1].copy().setWrapLines(true));
        System.out.println("\nSIXTH-b:\n\n---------------\n" + sixthB + "\n" + allSettings[1].copy()
                .setWrapLines(false).setBlankLineAfterRule(NewlineStyle.IF_COMPLEX) + "\n-------------------------");
        String seventhA = formatOne(testSeven, allSettings[0]);
        System.out.println("\nSEVENTH-a:\n\n---------------\n" + seventhA + "\n" + allSettings[0] + "\n-------------------------");
        String seventhB = formatOne(testSeven, allSettings[1].copy().setWrapLines(true));
        System.out.println("\nSEVENTH-b:\n\n---------------\n" + seventhB + "\n" + allSettings[1].copy()
                .setWrapLines(true)
                .setWrapPoint(50)
                .setBlankLineAfterRule(NewlineStyle.IF_COMPLEX) + "\n-------------------------");

        System.out.flush();
        Thread.sleep(1000);
    }

    private String formatOne(String text, AntlrFormatterSettings settings) throws Throwable {
        ANTLRv4Lexer lexer = new ANTLRv4Lexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();

        AntlrGrammarLanguageFormatter prov = new AntlrGrammarLanguageFormatter(settings);
        String formatted = prov.reformat(text, 0, text.length(), settings).text();
        return formatted;
    }

    static final class V extends ANTLRv4BaseVisitor<Void> implements ANTLRErrorListener {

        private boolean hasErrors;
        private final String name;
        private final AntlrFormatterSettings settings;

        V(String name, AntlrFormatterSettings settings) {
            this.name = name;
            this.settings = settings;
        }

        void assertNoErrors(String text) {
            if (hasErrors) {
                textWithLineNumbers(text, System.out::println);
            }
            assertFalse(name + "(" + settings + "): Formatted code has errors", hasErrors);
        }

        private static void textWithLineNumbers(String txt, Consumer<String> cons) {
            String[] lines = txt.split("\n");
            for (int i = 0; i < lines.length; i++) {
                cons.accept((i + 1) + "\t" + lines[i]);
            }
        }

        @Override
        public Void visitErrorNode(ErrorNode node) {
            System.out.println(name + "(" + settings + ") ERROR NODE: " + node);
            hasErrors = true;
            return null;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            System.out.println(name + "(" + settings + ") SYNTAX ERROR: '" + offendingSymbol + " " + line + ":" + charPositionInLine);
            if (e != null) {
                e.printStackTrace();
            }
            hasErrors = true;
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

    private final Map<String, String> textForName = new HashMap<>();

    private String loadToMap(String name) throws IOException {
        return loadToMap(name, AntlrGrammarLanguageFormatterTest.class);
    }

    private String loadToMap(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
            String text = readString(in);
            textForName.put(file.getFileName().toString(), text);
            return text;
        }
    }

    private String loadToMap(String name, Class<?> relativeTo) throws IOException {
        try (InputStream in = relativeTo.getResourceAsStream(name)) {
            assertNotNull(in);
            String text = readString(in);
            textForName.put(name, text);
            return text;
        }
    }

    private String extraneous(String e, String g) {
        String shorter = e.length() < g.length() ? e : g;
        String longer = e.length() > g.length() ? e : g;
        return longer.substring(shorter.length());
    }

    private String charDiff(int line, String e, String g) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(e.length(), g.length()); i++) {
            char ec = e.charAt(i);
            char gc = g.charAt(i);
            if (ec != gc) {
                sb.append("Lines first differ at ").append(i)
                        .append(": '").append(ec)
                        .append("' got '").append(gc).append("'");
                sb.append('\n');
                sb.append('\'').append(e).append("'|'").append(g).append('\'').append('\n');
                break;
            }
        }
        if (e.length() != g.length()) {
            sb.append("\nExpected length ").append(e.length())
                    .append(" but was ").append(g.length()).append('\n');
            sb.append("Extraneous characters: '").append(extraneous(e, g));
        }
        return sb.toString();
    }

    private List<String> toLines(String text) {
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isEmpty() && lines[i].charAt(lines[i].length() - 1) == '\r') {
                lines[i] = lines[i].substring(0, lines[i].length() - 1);
            }
        }
        return Arrays.asList(lines);
    }

    private List<String> splitBySemi(String text) {
        String[] rules = text.split(";");
        return Arrays.asList(rules);
    }

    private String compare(String name, int index, String expected, String got) throws DiffException {
        List<String> expectedRules = splitBySemi(expected);
        List<String> gotRules = splitBySemi(got);
        StringBuilder sb = new StringBuilder();
        assertEquals(expectedRules.size(), gotRules.size());
        Iterator<String> origRules = expectedRules.iterator();
        Iterator<String> revRules = gotRules.iterator();
        int deltaIndex = 0;
        int ruleIndex=0;
        while (origRules.hasNext()) {
            String ot = origRules.next();
            String rt = revRules.next();
            List<String> origLines = toLines(ot);
            List<String> revLines = toLines(rt);
            Patch<String> patch = DiffUtils.diff(origLines, revLines, new MyersDiff<>());
            List<AbstractDelta<String>> deltas = patch.getDeltas();
            if (!deltas.isEmpty()) {
                if (sb.length() == 0) {
                    sb.append("\n************************* ").append(name).append("-").append(index).append(" ******************\n");
                }
                if (!ot.equals(rt)) {
                    System.out.println(ruleIndex + "a: " + ot);
                    System.out.println(ruleIndex + "b: " + rt);
                }
                int ix = 0;
                for (AbstractDelta<String> d : deltas) {
                    sb.append(ix++).append(": ")
                            .append('\n');
                    Chunk<String> orig = d.getSource();
                    Chunk<String> rev = d.getTarget();
                    List<String> ol = orig.getLines();
                    List<String> rl = rev.getLines();
                    for (int i = 0; i < Math.max(ol.size(), rl.size()); i++) {
                        String o = i < ol.size() ? ol.get(i) : "-------------------------";
                        String r = i < rl.size() ? rl.get(i) : "-------------------------";
                        sb.append("  ");
                        sb.append(deltaIndex++).append("a: ");
                        sb.append(o).append('\n');
                        sb.append("  ").append(deltaIndex - 1).append("b: ").append(r).append('\n');
                    }
                }
            }
            ruleIndex++;
        }
        if (deltaIndex > 0) {
            return sb.toString();
        }
        return null;
    }

    private String compareGolden(String name, int configIndex, String formattedText) throws IOException, URISyntaxException, DiffException {
        String goldenContent = loadGolden(name, configIndex);
        if (goldenContent != null) {
            return compare(name, configIndex, goldenContent, formattedText);
        }
        String fileName = goldenFileName(name, configIndex);
        Path path = TestDir.testResourcePath(AntlrGrammarLanguageFormatterTest.class, fileName);
        System.out.println("writing golden file to " + path);
        try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            out.write(formattedText.getBytes(UTF_8));
        }
        return "Generated " + path;
    }

    private String loadGolden(String name, int configIndex) throws IOException, URISyntaxException {
        String fileName = goldenFileName(name, configIndex);
        Path path = TestDir.testResourcePath(AntlrGrammarLanguageFormatterTest.class, fileName);
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
                return readString(in);
            }
        }
        return null;
    }

    private String goldenFileName(String name, int index) {
        int ix = name.lastIndexOf('.');
        if (ix > 0) {
            name = name.substring(0, ix);
        }
        return "golden/" + name + "-" + index + "-golden.g4";
    }

    @Before
    public void load() throws IOException, URISyntaxException {
        try (InputStream in = AntlrGrammarLanguageFormatterTest.class.getResourceAsStream("NestedMapGrammar.g4")) {
            assertNotNull(in);
            nestedMapGrammar = readString(in);
        }
        try (InputStream in = AntlrGrammarLanguageFormatterTest.class.getResourceAsStream("Java.g4")) {
            assertNotNull(in);
            java = readString(in);
        }
        rustMinimal = loadToMap("Rust-Minimal._g4", ErrorHandlingTest.class);
        testOne = loadToMap("TestOne.g4");
        testTwo = loadToMap("TestTwo.g4");
        testThree = loadToMap("TestThree.g4");
        testFour = loadToMap("TestFour.g4");
        testFive = loadToMap("TestFive.g4");
        testSix = loadToMap("TestSix.g4");
        testSeven = loadToMap("TestSeven.g4");
        Path baseDir = projectBaseDir();
        Path lexer = baseDir.resolve("src/main/antlr4/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4Lexer.g4");
        Path grammar = baseDir.resolve("src/main/antlr4/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4.g4");

        antlrGrammar = loadToMap(grammar);
        try (InputStream in = Files.newInputStream(lexer, StandardOpenOption.READ)) {
            antlrLexer = readString(in);
        }
    }

    private String readString(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileUtil.copy(in, out);
        return new String(out.toByteArray(), UTF_8);
    }
}
