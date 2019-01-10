package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui;

import static com.google.common.math.DoubleMath.log2;
import java.awt.EventQueue;
import java.io.IOException;
import static java.lang.Math.floor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.ANTLRv4GrammarChecker;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleTypes;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
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
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 * Thing which, in the most brute force way imaginable, attempts to answer the
 * eternal Antlr user's question: "This rule worked an hour ago! All I did was
 * make some innocuous changes to five or ten unrelated rules! What the heck did
 * I do to break this one?". It does so by attempting (poorly) to extract a list
 * of rules that are reasonable candidates to cause the syntax errors in the
 * passed ParseTreeProxy generated from trying to run the grammar in question
 * against some sample text in the preview window. It will then iterate through
 * the cartesian product of all possible sets of candidate rules, generating a
 * new grammar in memory with that each set of candidate rules omitted, compile
 * it to an in-memory filesystem, then run it against the text in an isolating
 * classloader, and extract a new ParseTreeProxy with the result (or say what
 * went wrong). Eventually, either the sun supernovas, or you have your answer.
 *
 * Plus, it was just fun to write a class called CombinatoricHellscape.
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

    private String loadGrammarText(Path grammarFilePath) throws IOException {
        // The parse we are working with may have offsets from a modified document,
        // so prefer the document's text to the FileObject's if available
        FileObject fo = FileUtil.toFileObject(grammarFilePath.toFile());
        EditorCookie ec = DataObject.find(fo).getLookup().lookup(EditorCookie.class);
        if (ec != null) {
            Document d = ec.getDocument();
            if (d != null) {
                try {
                    return d.getText(0, d.getLength());
                } catch (BadLocationException ex) {
                    throw new IOException(ex);
                }
            }
        }
        return fo.asText();
    }

    private void loadRules(ANTLRv4SemanticParser parse) throws IOException {
        String text = loadGrammarText(parse.grammarFilePath().get());
        NamedSemanticRegions<RuleTypes> decls = parse.extraction().namedRegions(AntlrExtractor.RULE_BOUNDS);
        for (NamedSemanticRegion<RuleTypes> d : decls) {
            ruleTextForRule.put(d.name(), text.substring(d.start(), d.end()));
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

        public List<NamedSemanticRegion<RuleTypes>> declarations() {
            NamedSemanticRegions<RuleTypes> decls = parse.getSemanticParser().extraction().namedRegions(AntlrExtractor.RULE_BOUNDS);
            List<NamedSemanticRegion<RuleTypes>> result = new ArrayList<>(decls.size());
            for (NamedSemanticRegion<RuleTypes> rule : decls) {
                result.add(new OffsetNamedSemanticRegion(rule, locationOffset));
            }
            return result;
        }

        public void appendRules(StringBuilder into) throws IOException {
            System.out.println("Append rules for imported: " + parse.getSemanticParser().grammarFilePath().get());
            String text = FileUtil.toFileObject(parse.getSemanticParser().grammarFilePath().get().toFile()).asText();
            NamedSemanticRegions<RuleTypes> decls = parse.getSemanticParser().extraction().namedRegions(AntlrExtractor.RULE_BOUNDS);
            for (NamedSemanticRegion<RuleTypes> rule : decls) {
                String ruleDef = text.substring(rule.start(), rule.end());
                into.append("\n// import ").append(parse.getSemanticParser().grammarFilePath());
                into.append(ruleDef).append(";\n");
            }
        }

    }

    private static final class OffsetNamedSemanticRegion implements NamedSemanticRegion<RuleTypes> {

        private final NamedSemanticRegion<RuleTypes> orig;
        private final int offset;

        public OffsetNamedSemanticRegion(NamedSemanticRegion<RuleTypes> orig, int offset) {
            this.orig = orig;
            this.offset = offset;
        }

        @Override
        public RuleTypes kind() {
            return orig.kind();
        }

        @Override
        public int ordering() {
            return orig.ordering();
        }

        @Override
        public boolean isReference() {
            return orig.isReference();
        }

        @Override
        public int start() {
            return orig.start() + offset;
        }

        @Override
        public int end() {
            return orig.end() + offset;
        }

        @Override
        public int index() {
            return orig.index();
        }

        @Override
        public String name() {
            return orig.name();
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof NamedSemanticRegion<?>) {
                NamedSemanticRegion<?> other = (NamedSemanticRegion<?>) o;
                return other.start() == start() && other.end() == end()
                        && other.kind() == kind() && name().equals(other.name());
            }
            return false;
        }

        public int hashCode() {
            return orig.hashCode();
        }

    }

    /**
     * Interface for monitoring the progress of a CulpritFinder as it chugs
     * through combinations of rules to eliminate.
     */
    public interface Monitor {

        void onAttempt(Set<NamedSemanticRegion<RuleTypes>> omitted, long attempt, long of);

        void onCompleted(boolean success, Set<NamedSemanticRegion<RuleTypes>> omitted, GenerateBuildAndRunGrammarResult proxy, Runnable runNext);

        void onStatus(String status);

        default Monitor replan() {
            return new Monitor() {
                @Override
                public void onAttempt(Set<NamedSemanticRegion<RuleTypes>> omitted, long attempt, long of) {
                    EventQueue.invokeLater(() -> {
                        Monitor.this.onAttempt(omitted, attempt, of);
                    });
                }

                @Override
                public Monitor replan() {
                    return this;
                }

                @Override
                public void onCompleted(boolean success, Set<NamedSemanticRegion<RuleTypes>> omitted, GenerateBuildAndRunGrammarResult proxy, Runnable runNext) {
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
            AtomicReference<Set<NamedSemanticRegion<RuleTypes>>> ref = new AtomicReference<>();
            try {
                String grammar = hellscape.nextGrammar(l -> {
                    ref.set(l);
                });
                List<String> ruleNames = new ArrayList<>();
                for (NamedSemanticRegion<RuleTypes> r : ref.get()) {
                    ruleNames.add(r.name());
                }
                System.out.println("--------------------- GEN-GRAMMAR -" + ruleNames + " --------------");
                monitor.onAttempt(ref.get(), hellscape.product.cursors.calls(),
                        hellscape.product.cursors.maxCalls());
                x.replacingAntlrGrammarWith(grammar);
                GenerateBuildAndRunGrammarResult res = runner.parse(text);
//            System.out.println("GOT RESULT " + res);
                boolean success = res.isUsable();
                monitor.onCompleted(success, ref.get(), res, this);
                return res;
            } catch (Throwable ex) {
                GenerateBuildAndRunGrammarResult res = GenerateBuildAndRunGrammarResult.
                        forThrown(ex, text);
                return res;
            }
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
        NamedSemanticRegions<RuleTypes> rules = parseInfo.extraction().namedRegions(AntlrExtractor.RULE_BOUNDS);
        if (candidateLexerRules.size() < 3) {
            for (NamedSemanticRegion<RuleTypes> d : rules) {
                switch (d.kind()) {
                    case LEXER:
                        candidateLexerRules.add(d.name());
                }
            }
        }
        System.out.println("CANDIDATE LEXER RULES: " + candidateLexerRules);
        List<NamedSemanticRegion<RuleTypes>> declarations = new ArrayList<>();
        for (NamedSemanticRegion<RuleTypes> r : rules) {
            declarations.add(r);
        }
        Set<NamedSemanticRegion<RuleTypes>> touched = new HashSet<>();
        List<NamedSemanticRegion<RuleTypes>> lexerRules = new ArrayList<>();
        for (String lexerRuleName : candidateLexerRules) {
            if (rules.contains(lexerRuleName)) {
                NamedSemanticRegion<RuleTypes> rule = rules.regionFor(lexerRuleName);
                touched.add(rule);
                if (rule.kind() == RuleTypes.LEXER) {
                    lexerRules.add(rule);
                }
            }
        }
        for (String parserRuleName : candidateParserRules) {
            if (rules.contains(parserRuleName)) {
                NamedSemanticRegion<RuleTypes> rule = rules.regionFor(parserRuleName);
                if (rule != null) {
                    touched.add(rule);
                }
            }
        }
        for (ImportedGrammar grammar : this.imports) {
            List<NamedSemanticRegion<RuleTypes>> imported = grammar.declarations();
            declarations.addAll(imported);
            for (NamedSemanticRegion<RuleTypes> d : imported) {
                switch (d.kind()) {
                    case LEXER:
                        lexerRules.add(d);
                        break;
                }
            }
        }
        Collections.sort(declarations);
        List<NamedSemanticRegion<RuleTypes>> touchedSorted = new ArrayList<>(touched);
//        if (true || touchedSorted.size() <= 2) {
//            System.out.println("too few touched - try lexer rules - " + lexerRules.size());
//            touchedSorted = lexerRules;
//        }
        touchedSorted.removeAll(lexerRules);
        touchedSorted.addAll(lexerRules);
        Collections.sort(touchedSorted);
        System.out.println("WILL try combinations of " + touchedSorted.size() + " with "
                + " " + declarations.size() + " declarations");
        return new CombinatoricHellscape(declarations, touchedSorted);
    }

    public final class CombinatoricHellscape {

        private final List<NamedSemanticRegion<RuleTypes>> allDeclarations;
        private final List<NamedSemanticRegion<RuleTypes>> touched;
        private CartesianProductizer product;

        public CombinatoricHellscape(List<NamedSemanticRegion<RuleTypes>> allDeclarations, List<NamedSemanticRegion<RuleTypes>> touched) throws IOException {
            this.allDeclarations = new ArrayList<>(allDeclarations);
            this.touched = new ArrayList<>(touched);
            product = new CartesianProductizer();
        }

        public String nextGrammar(Consumer<Set<NamedSemanticRegion<RuleTypes>>> c) {
            List<NamedSemanticRegion<RuleTypes>> all = product.next(c);
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
                for (NamedSemanticRegion<RuleTypes> rd : all) {
//                    String sub = orig.substring(rd.getRuleStartOffset(), rd.getRuleEndOffset());
                    String sub = ruleTextForRule.get(rd.name());
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

            private final Cursors3 cursors;
            private final Set<NamedSemanticRegion<RuleTypes>> latestOmitted = new LinkedHashSet<>();

            public CartesianProductizer() {
                cursors = new Cursors3(touched.size());
            }

            private void pruneDependencies(Set<NamedSemanticRegion<RuleTypes>> omitted, List<NamedSemanticRegion<RuleTypes>> kept) {
                if (true) {
                    // Reverse closure we're getting is larger than it should be
                    // hold this for a rewrite
                    return;
                }
                Set<NamedSemanticRegion<RuleTypes>> alsoRemoved = new LinkedHashSet<>(omitted);
                for (NamedSemanticRegion<RuleTypes> om : omitted) {
                    Set<String> closure = parseInfo.ruleTree().reverseClosureOf(om.name());
                    for (String s : closure) {
                        for (NamedSemanticRegion<RuleTypes> rd : kept) {
                            if (s.equals(rd.name())) {
                                alsoRemoved.add(rd);
                            }
                        }
                    }
                }
                System.out.println("--remove reverse dependencies: " + alsoRemoved);
                omitted.addAll(alsoRemoved);
                kept.removeAll(alsoRemoved);
            }

            private BitShiftArray last;

            public List<NamedSemanticRegion<RuleTypes>> next(Consumer<Set<NamedSemanticRegion<RuleTypes>>> c) {
                for (;;) {
                    latestOmitted.clear();
                    BitShiftArray set = cursors.next();
                    if (set == null) {
                        System.out.println("NO SET - DONE");
                        return null;
                    }
                    if (set.cardinality() == 0) {
                        continue;
                    }
                    if (last != null && last.equals(set)) {
                        continue;
                    }
                    last = set.copy();
                    List<NamedSemanticRegion<RuleTypes>> nue = new LinkedList<>(allDeclarations);
                    latestOmitted.addAll(nue);
                    set.pruneList(nue);
                    latestOmitted.removeAll(nue);
                    pruneDependencies(latestOmitted, nue);
                    System.out.println("OMITTING " + latestOmitted);
                    if (c != null) {
                        c.accept(new LinkedHashSet<>(latestOmitted));
                    }
                    return nue;
                }
            }
        }
    }

    public static int bitsRequiredForNumber(long n) {
        return (int) floor(log2(n / 2) + 1);
    }

    public static final class Cursors3 {

        private final int max;
        private BitShiftArray arr;
        private BitCursor cur;
        private final Set<Integer> duplicates;
        private final long maxCalls;
        private volatile long calls;

        public Cursors3(int max) {
            this.max = max;
            arr = new BitShiftArray(max);
            cur = new BitCursor(0, null);
            if (arr.sizeInBits() <= 32) {
                duplicates = new HashSet<>();
            } else {
                duplicates = null;
            }
            maxCalls = (long) Math.pow(2, max);
        }

        public long maxCalls() {
            return maxCalls;
        }

        public long calls() {
            return calls;
        }

        public BitShiftArray next() {
            calls++;
            cur = cur.next();
            if (cur == null) {
                return null;
            }
            if (duplicates != null) {
                while (duplicates.contains((int) arr.firstLong())) {
                    cur = cur.next();
                    if (cur == null) {
                        return null;
                    }
                }
                duplicates.add((int) arr.firstLong());
            }
            return arr;
        }

        public String toString() {
            return cur.toString();
        }

        class BitCursor {

            int pos;
            private int initialPos;
            private final BitCursor parent;
            private long iterations;

            BitCursor(int pos, BitCursor parent) {
                this.pos = initialPos = pos;
                this.parent = parent;
            }

            public int bitsInMotion() {
                return parent == null ? 1 : 1 + parent.bitsInMotion();
            }

            public String toString() {
                return "{" + pos + "@" + iterations + " initial: " + initialPos
                        + (parent == null ? "" : parent) + "}";
            }

            BitCursor child;

            private boolean isOwned(int loc) {
                if (loc == -1) {
                    return false;
                }
                if (loc == initialPos) {
                    return true;
                }
                if (parent != null) {
                    return parent.isOwned(loc);
                }
                return isChildOwned(loc);
            }

            private boolean isChildOwned(int loc) {
                if (child != null) {
                    boolean result = child.initialPos == loc;
                    if (!result) {
                        return child.isChildOwned(loc);
                    }
                }
                return false;
            }

            private int resetPos() {
                if (parent == null) {
                    return initialPos;
                }
                return arr.firstUnsetBit(parent.pos);
            }

            private boolean move() {
                int newPos;
                // If we have two bits moving, there are two possible
                // ways we can get an identical bit set - this avoids
                // those
                if (parent != null && parent.pos == pos) {
                    pos = arr.firstUnsetBit(pos);
                }
                do {
                    newPos = arr.moveBitLeft(pos);
                } while (isOwned(newPos));
                if (newPos == -1) {
                    arr.clear(pos);
//                    int ip = parent == null ? initialPos : arr.firstUnsetBit(parent.pos);
                    int ip = resetPos();
                    arr.set(pos = ip);
                    return parent == null ? false : parent.move();
                }
                pos = newPos;
                return true;
            }

            private boolean isInUse(int iniPos) {
                return initialPos == iniPos || (parent != null && parent.isInUse(iniPos));
            }

            private int findUnusedInitialPos() {
                int start = initialPos + 1;
                while (isInUse(start)) {
                    start++;
                    if (start == max) {
                        start = 0;
                    }
                    if (start == initialPos + 1) {
                        return -1;
                    }
                }
                int realStart = arr.firstUnsetBit(start);
                if (!isInUse(realStart)) {
                    start = realStart;
                }
                return start;
            }

            BitCursor next() {
                int lastPos = pos;
                if (!move()) {
                    if (child == null) {
                        if (arr.cardinality() == max) {
                            return null;
                        }
//                        if (bitsInMotion() > max * 2) {
//                            return null;
//                        }
//                        int childPos = arr.setFirstUnsetBitAtOrAfter(initialPos);
                        int childPos = findUnusedInitialPos();
//                        if (childPos == initialPos) {
//                            childPos++;
//                            if (childPos == max) {
//                                childPos = 0;
//                            }
//                        }
                        child = new BitCursor(childPos, this);
                        arr.set(childPos);
//                        System.out.println("New cursor " + child + " for lastPos " + lastPos);
                        return child;
                    }
                }
                return this;
            }
        }

        int bitsInMotion() {
            if (cur == null) {
                return max;
            }
            return cur.bitsInMotion();
        }
    }
}
