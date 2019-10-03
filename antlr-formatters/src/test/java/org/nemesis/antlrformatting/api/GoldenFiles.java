package org.nemesis.antlrformatting.api;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import static org.junit.jupiter.api.Assertions.fail;
import org.nemesis.antlrformatting.spi.AntlrFormatterStub;
import org.nemesis.simple.SampleFile;

/**
 *
 * @author Tim Boudreau
 */
public class GoldenFiles<E extends Enum<E>, T extends AntlrFormatterStub<E, L>, L extends Lexer, C> {

    private final Path goldenFilesDir;

    private final Class<T> stubClass;
    private final int[] whitespaceTokens;

    public GoldenFiles(Path goldenFilesDir, Class<T> stubClass, int... whitespaceTokens) {
        this.goldenFilesDir = goldenFilesDir;
        this.stubClass = stubClass;
        this.whitespaceTokens = whitespaceTokens;
    }

    AntlrFormattingHarness<C, E> harness(SampleFile<L, ?> file) {
        try {
            return new AntlrFormattingHarness<>(file, stubClass, whitespaceTokens);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalArgumentException
                | IllegalAccessException | InstantiationException
                | NoSuchMethodException ex) {
            throw new AssertionError(ex);
        }
    }

    List<SyntaxError> testLex(String reformatted, Lexer lexer) {
        ErrL errors = new ErrL();
        lexer.addErrorListener(errors);
        for (Token tok = lexer.nextToken(); tok.getType() != -1; tok = lexer.nextToken());
        return errors.errors;
    }

    static void syntaxErrors(List<SyntaxError> errors, StringBuilder sb) {
        if (!errors.isEmpty()) {
            sb.append("\nReparse had syntax errors\n");
            for (SyntaxError e : errors) {
                sb.append(e).append('\n');
            }
        }
    }

    public void go(SampleFile<L, ?> file, C config, String name, boolean update) throws IOException, DiffException {
        Path goldenFile = goldenFilesDir.resolve(name);
        AntlrFormattingHarness<C, E> harn = harness(file);
        String reformatted = harn.reformat(config);
        Lexer lexer = harn.lex(reformatted);
        List<SyntaxError> errors = testLex(reformatted, lexer);
        if (Files.exists(goldenFile) && !update) {
            String expectedText = FileUtils.readUTF8String(goldenFile);
            if (!expectedText.equals(reformatted)) {
                String diff = diff(expectedText, reformatted);
                if (diff != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Reformatted text for ").append(name).append(" does not match golden file.  Diff: \n").append(diff);
                    syntaxErrors(errors, sb);
                    fail(sb.toString());
                    return;
                }
            } else if (!errors.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                syntaxErrors(errors, sb);
                fail(sb.toString());
                return;
            }
        } else {
            if (errors.isEmpty()) {
                Path dir = goldenFile.getParent();
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
                Files.write(goldenFile, reformatted.getBytes(UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
            } else {
                System.err.println("Reformatted text for " + name + " lexed with errors."
                        + " Will not update golden file.");
            }
        }
    }

    static class SyntaxError {

        private final Object offendingSymbol;
        private final int line;
        private final int charPositionInLine;
        private final String msg;
        private final RecognitionException e;

        private SyntaxError(Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            this.offendingSymbol = offendingSymbol;
            this.line = line;
            this.charPositionInLine = charPositionInLine;
            this.msg = msg;
            this.e = e;
        }

        public String message(List<String> lines) {
            String sym = "";
            if (offendingSymbol != null && offendingSymbol instanceof Token) {
                Token t = (Token) offendingSymbol;
                sym = " '" + Strings.escape(t.getText(), Escaper.CONTROL_CHARACTERS)
                        + "' at token " + t.getTokenIndex();
            }
            StringBuilder sb = new StringBuilder("Syntax error at ").append(line).append(':')
                    .append(charPositionInLine)
                    .append(sym)
                    .append(":\n");
            if (line >= 0 && line < lines.size()) {
                String line = lines.get(this.line);
                char[] indent = new char[this.charPositionInLine < 0 ? 0 : this.charPositionInLine];
                Arrays.fill(indent, ' ');
                sb.append(line).append('\n').append(indent).append('^').append("\n\n");
            }
            if (msg != null) {
                sb.append(msg).append('\n');
            }
            if (e != null) {
                sb.append(e.getMessage()).append('\n');
            }
            return sb.toString();
        }

    }

    static class ErrL implements ANTLRErrorListener {

        private final List<SyntaxError> errors = new ArrayList<>(5);

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            errors.add(new SyntaxError(offendingSymbol, line, charPositionInLine, msg, e));
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

    public static String diff(String origText, String reformatted) throws DiffException {
        List<String> reformattedLines = Arrays.asList(reformatted.split("\n"));
        Patch<String> patch = DiffUtils.diff(Arrays.asList(origText.split("\n")), reformattedLines);
        List<AbstractDelta<String>> deltas = patch.getDeltas();
        StringBuilder sb = new StringBuilder();
        if (!deltas.isEmpty()) {
            for (AbstractDelta<String> d : deltas) {
                if (d.getType() == DeltaType.EQUAL) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(d.getType()).append('\n');
                List<String> origLines = d.getSource().getLines();
                List<String> modLines = d.getTarget().getLines();
                int max = Math.max(origLines.size(), modLines.size());
                int sp = d.getSource().getPosition();
                int tp = d.getTarget().getPosition();
                for (int i = 0; i < max; i++) {
                    String orig = i < origLines.size() ? origLines.get(i) : null;
                    String nue = i < modLines.size() ? modLines.get(i) : null;
                    sb.append(i + sp).append(". orig: '");
                    if (orig != null) {
                        sb.append(orig);
                    }
                    sb.append("'\n");
                    sb.append(i + tp).append(".  new: '");
                    if (nue != null) {
                        sb.append(nue);
                    }
                    sb.append("'\n\n");
                }
                for (int i = Math.max(0, tp - 6); i < Math.min(reformattedLines.size() - 1, tp + max + 3); i++) {
                    sb.append(i).append(".").append(reformattedLines.get(i)).append('\n');
                }
                sb.append("\n");
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /*
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
     */
}
