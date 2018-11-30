package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui;

import static com.google.common.math.DoubleMath.log2;
import java.awt.EventQueue;
import java.io.IOException;
import static java.lang.Math.floor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.text.BadLocationException;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.ANTLRv4GrammarChecker;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleDeclaration;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementKind.LEXER_RULE_DECLARATION;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.AntlrRunOption;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.InMemoryAntlrSourceGenerationBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeElement;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ParseProxyBuilder;
import org.nemesis.antlr.v4.netbeans.v8.project.ProjectType;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.ProjectHelper;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
public class CulpritFinder {

    private final ParseTreeProxy origProxy;

    private final ANTLRv4SemanticParser parseInfo;

    private final List<ImportedGrammar> imports = new ArrayList<>();

    public CulpritFinder(ParseTreeProxy origProxy, ANTLRv4ParserResult parseInfo) throws IOException, BadLocationException {
        this.origProxy = origProxy;
        loadRules(parseInfo.semanticParser());
        this.parseInfo = parseInfo.semanticParser();
        List<String> imported = parseInfo.semanticParser().summary().getImportedGrammars();

        Optional<Project> project = ProjectHelper.getProject(origProxy.grammarPath());
        ProjectType type = project == null ? ProjectType.UNDEFINED : ProjectHelper.getProjectType(project.get());
        Optional<Path> imports = type.antlrArtifactFolder(origProxy.grammarPath(), AntlrFolders.IMPORT);
        Path parent = origProxy.grammarPath().getParent();
        int offset = (int) Files.size(origProxy.grammarPath());
        imp:
        for (String s : imported) {
            for (String ext : new String[]{".g", ".g4"}) {
                Path imp = parent.resolve(s + ext);
                if (Files.exists(imp)) {
                    offset = processImportedGrammar(offset, s, imp);
                    continue imp;
                }
                if (imports.isPresent()) {
                    imp = imports.get().resolve(s + ext);
                    if (Files.exists(imp)) {
                        offset = processImportedGrammar(offset, s, imp);
                        continue imp;
                    }
                }
            }
        }
    }

    private int processImportedGrammar(int offset, String s, Path imp) throws IOException, BadLocationException {
        ANTLRv4GrammarChecker res = NBANTLRv4Parser.parse(imp);
        imports.add(new ImportedGrammar(res, offset));
        return offset + (int) Files.size(imp);
    }

    private Map<String, String> ruleTextForRule = new HashMap<>();

    private void loadRules(ANTLRv4SemanticParser parse) throws IOException {
        String text = FileUtil.toFileObject(parse.grammarFilePath().get().toFile()).asText();
        for (RuleDeclaration d : parse.allDeclarations()) {
            ruleTextForRule.put(d.getRuleID(), text.substring(d.getRuleStartOffset(), d.getRuleEndOffset()));
        }
    }

    private final class ImportedGrammar {

        private final ANTLRv4GrammarChecker parse;
        private final int locationOffset;

        public ImportedGrammar(ANTLRv4GrammarChecker parse, int locationOffset) throws IOException {
            this.parse = parse;
            this.locationOffset = locationOffset;
            loadRules(parse.getSemanticParser());
        }

        public List<RuleDeclaration> declarations() {
            // We need to maintain the sort order as Antlr would parse it -
            // imports are affectively appended to the thing that imports them
            List<RuleDeclaration> all = new ArrayList<>(parse.getSemanticParser().allDeclarations().size());
            for (RuleDeclaration d : parse.getSemanticParser().allDeclarations()) {
                all.add(d.offsetBy(locationOffset));
            }
            return all;
        }

        public void appendRules(StringBuilder into) throws IOException {
            System.out.println("Append rules for imported: " + parse.getSemanticParser().grammarFilePath().get());
            String text = FileUtil.toFileObject(parse.getSemanticParser().grammarFilePath().get().toFile()).asText();
            for (RuleDeclaration rule : parse.getSemanticParser().allDeclarations()) {
                String ruleDef = text.substring(rule.getRuleStartOffset(), rule.getRuleEndOffset());
                into.append("\n// import ").append(parse.getSemanticParser().grammarFilePath());
                into.append(ruleDef).append(";\n");
            }
        }
    }

    public interface Monitor {

        void onAttempt(Set<RuleDeclaration> omitted, long attempt, long of);

        void onCompleted(boolean success, Set<RuleDeclaration> omitted, GenerateBuildAndRunGrammarResult proxy, Runnable runNext);

        void onStatus(String status);

        default Monitor replan() {
            return new Monitor() {
                @Override
                public void onAttempt(Set<RuleDeclaration> omitted, long attempt, long of) {
                    EventQueue.invokeLater(() -> {
                        Monitor.this.onAttempt(omitted, attempt, of);
                    });
                }

                @Override
                public Monitor replan() {
                    return this;
                }

                @Override
                public void onCompleted(boolean success, Set<RuleDeclaration> omitted, GenerateBuildAndRunGrammarResult proxy, Runnable runNext) {
                    EventQueue.invokeLater(() -> {
                        Monitor.this.onCompleted(success, omitted, proxy, runNext);
                    });
                }

                @Override
                public void onStatus(String status) {
                    if (!EventQueue.isDispatchThread()) {
                        EventQueue.invokeLater(() -> {
                            Monitor.this.onStatus(status);
                        });
                    } else {
                        Monitor.this.onStatus(status);
                    }
                }
            };
        }
    }

    public Runnable createCombinatoricRunner(Monitor monitor, String toParse) throws IOException {
        Runner runner = new Runner(toParse, createCombinatoricHellscape(), monitor);
        return runner;
    }

    public final class Runner implements Runnable {

        private final String text;

        private final CombinatoricHellscape hellscape;
        private final ParseProxyBuilder runner;
        InMemoryAntlrSourceGenerationBuilder x;
        private final Monitor monitor;
        private final RequestProcessor proc = new RequestProcessor("hellscape", 1, true);

        public Runner(String text, CombinatoricHellscape hellscape, Monitor monitor) {
            this.text = text;
            this.hellscape = hellscape;
            x = InMemoryAntlrSourceGenerationBuilder.forAntlrSource(origProxy.grammarPath())
                    .withRunOptions(AntlrRunOption.GENERATE_LEXER, AntlrRunOption.GENERATE_VISITOR);
            runner = x.toParseAndRunBuilder();
            this.monitor = monitor;
        }

        public GenerateBuildAndRunGrammarResult next() throws IOException {
            AtomicReference<Set<RuleDeclaration>> ref = new AtomicReference<>();
            String grammar = hellscape.nextGrammar(l -> {
                ref.set(l);
            });
            List<String> ruleNames = new ArrayList<>();
            for (RuleDeclaration r : ref.get()) {
                ruleNames.add(r.getRuleID());
            }
            System.out.println("--------------------- GEN-GRAMMAR -" + ruleNames + " -----------:\n"
                    + grammar + "\n----------------------------------");
            monitor.onAttempt(ref.get(), hellscape.product.cursors.calls(),
                    hellscape.product.cursors.maxCalls());
            x.replacingAntlrGrammarWith(grammar);
            GenerateBuildAndRunGrammarResult res = runner.parse(text);
            System.out.println("GOT RESULT " + res);
            boolean success = res.isUsable();
            monitor.onCompleted(success, ref.get(), res, this);
            return res;
        }

        @Override
        public void run() {
            if (!proc.isRequestProcessorThread()) {
                proc.submit(this);
            } else {
                try {
                    next();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

    public CombinatoricHellscape createCombinatoricHellscape() throws IOException {
        Set<String> candidateParserRules = new HashSet<>();
        Set<String> candidateLexerRules = new HashSet<>();
        for (ProxyToken tok : origProxy.tokens()) {
            String name = origProxy.tokenTypeForInt(tok.getType()).symbolicName;
            if (name != null) {
                candidateLexerRules.add(name);
            }
            List<ParseTreeElement> rules = tok.referencedBy();
            for (ParseTreeElement rule : rules) {
                switch (rule.kind()) {
                    case RULE:
                        candidateParserRules.add(rule.name());
                        break;
                }
            }
        }
        if (candidateLexerRules.size() < 3) {
            for (RuleDeclaration d : CulpritFinder.this.parseInfo.allDeclarations()) {
                switch(d.kind()) {
                    case LEXER_RULE_DECLARATION :
                        candidateLexerRules.add(d.getRuleID());
                }
            }
        }
        System.out.println("CANDIDATE LEXER RULES: " + candidateLexerRules);
        List<RuleDeclaration> declarations = new ArrayList<>(parseInfo.allDeclarations());
        Set<RuleDeclaration> touched = new HashSet<>();
        List<RuleDeclaration> lexerRules = new ArrayList<>();
        for (String lexerRuleName : candidateLexerRules) {
            RuleDeclaration rule = parseInfo.getLexerRuleDeclaration(lexerRuleName);
            if (rule != null) {
                touched.add(rule);
                if (rule.kind() == LEXER_RULE_DECLARATION) {
                    lexerRules.add(rule);
                }
            }
        }
        for (String parserRuleName : candidateParserRules) {
            RuleDeclaration rule = parseInfo.getParserRuleDeclaration(parserRuleName);
            if (rule != null) {
                touched.add(rule);
            }
        }
        for (ImportedGrammar grammar : this.imports) {
            List<RuleDeclaration> imported = grammar.declarations();
            declarations.addAll(imported);
            for (RuleDeclaration d : imported) {
                switch (d.kind()) {
                    case LEXER_RULE_DECLARATION:
                        lexerRules.add(d);
                        break;
                }
            }
        }
        Collections.sort(declarations);
        List<RuleDeclaration> touchedSorted = new ArrayList<>(touched);
//        if (true || touchedSorted.size() <= 2) {
//            System.out.println("too few touched - try lexer rules - " + lexerRules.size());
//            touchedSorted = lexerRules;
//        }
        touchedSorted.addAll(lexerRules);
        Collections.sort(touchedSorted);
        System.out.println("WILL try combinations of " + touchedSorted.size() + " with "
                + " " + declarations.size() + " declarations");
        return new CombinatoricHellscape(declarations, touchedSorted);
    }

    public final class CombinatoricHellscape {

        private final List<RuleDeclaration> allDeclarations;
        private final List<RuleDeclaration> touched;
        private CartesianProductizer product;

        public CombinatoricHellscape(List<RuleDeclaration> allDeclarations, List<RuleDeclaration> touched) throws IOException {
            this.allDeclarations = new ArrayList<>(allDeclarations);
            this.touched = new ArrayList<>(touched);
            product = new CartesianProductizer();
        }

        public String nextGrammar(Consumer<Set<RuleDeclaration>> c) {
            List<RuleDeclaration> all = product.next(c);
            System.out.println("NEXT GRAMMAR with " + all.size());
            StringBuilder sb = new StringBuilder();
            try {
                switch (parseInfo.getGrammarType()) {
                    case COMBINED:
                    case UNDEFINED:
                        sb.append("grammar ");
                        break;
                    case LEXER:
                        sb.append("lexer grammar ");
                        break;
                    case PARSER:
                        sb.append("parser grammar ");
                        break;
                }
                sb.append(parseInfo.getGrammarName()).append(";\n\n");
                for (RuleDeclaration rd : all) {
//                    String sub = orig.substring(rd.getRuleStartOffset(), rd.getRuleEndOffset());
                    String sub = ruleTextForRule.get(rd.getRuleID());
                    sb.append(sub).append('\n');
                }
                System.out.println("GENERATED GRAMMAR " + sb.length());
            } catch (Exception e) {
                e.printStackTrace();
                Exceptions.printStackTrace(e);
            }
            return sb.toString();
        }

        private class CartesianProductizer {

            private final Cursors cursors;
            private Set<RuleDeclaration> latestOmitted = new LinkedHashSet<>();

            public CartesianProductizer() {
                cursors = new Cursors(touched.size());
            }

            public List<RuleDeclaration> next(Consumer<Set<RuleDeclaration>> c) {
                latestOmitted.clear();
                List<RuleDeclaration> nue = new LinkedList<>(allDeclarations);
                BitSet set = cursors.next();
                if (set == null) {
                    System.out.println("NO SET - DONE");
                    return null;
                }
                while (set.cardinality() == 0) {
                    set = cursors.next(); // XXX
                }
                for (int bit = set.previousSetBit(touched.size()); bit >= 0; bit = set.previousSetBit(bit - 1)) {
                    RuleDeclaration d = nue.remove(bit);
                    latestOmitted.add(d);
                }
                if (c != null) {
                    c.accept(new LinkedHashSet<>(latestOmitted));
                }
                return nue;
            }
        }
    }

    public static int bitsRequiredForNumber(long n) {
        return (int) floor(log2(n / 2) + 1);
    }

    static final class Cursors {

        private final long[] longs;
        private final long rotateThreshold;
        private final long addBitThreshold;
        private final long maxValue;

        Cursors(int max) {
            BitSet bits = new BitSet(max);
            for (int i = 0; i < max; i++) {
                bits.set(i);
            }
            longs = bits.toLongArray();
            Arrays.fill(longs, 0);
            longs[0] = 1;
            rotateThreshold = longs.length * 64;
            maxValue = (long) Math.pow(2, max);
            addBitThreshold = bitsRequiredForNumber(maxValue);
            System.out.println("LONGS: " + longs.length);
            System.out.println("ROTATE THRESHOLD: " + rotateThreshold);
            System.out.println("ADD BIT THRESHOLD: " + addBitThreshold);
        }

        public long calls() {
            return calls;
        }

        public long maxCalls() {
            return maxValue;
        }

        private long calls = 0;

        private boolean seenFullCardinality;

        public BitSet next() {
            if (seenFullCardinality) {
                return null;
            }
            calls++;
            int curr = (int) (calls % longs.length);
            longs[curr] = (longs[curr] >>> 1) | (longs[curr] << (Long.SIZE - 1));
            if (curr % rotateThreshold == 0) {
                rotate();
            }
            if (calls % addBitThreshold == 0) {
//                System.out.println("ADD BIT: " + calls);
                BitSet bits = BitSet.valueOf(longs);
                int ix = bits.nextClearBit(0);
                if (ix >= 0) {
                    bits.set(ix);
                    long[] nue = bits.toLongArray();
                    System.arraycopy(nue, 0, longs, 0, longs.length);
                    seenFullCardinality = bits.cardinality() == addBitThreshold * 2;
                } else {
                    return null;
                }
            }
            return BitSet.valueOf(longs);
        }

        private void rotate() {
//            System.out.println("ROTATE " + calls);
            if (longs.length == 1) {
                return;
            }
            long hold = longs[0];
            for (int i = 1; i < longs.length; i++) {
                longs[i - 1] = longs[i];
            }
            longs[longs.length - 1] = hold;
        }
    }
}
