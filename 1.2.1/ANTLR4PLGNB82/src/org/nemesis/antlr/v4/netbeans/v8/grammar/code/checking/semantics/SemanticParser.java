package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4BaseVisitor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.Item;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.OffsetsBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.ReferenceSets;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.Usages;

/**
 *
 * @author Tim Boudreau
 */
public class SemanticParser {

    static final String MISSING_TOKEN_ID = "<missing TOKEN_ID>";
    static final String MISSING_ID = "<missing ID>";
    private final String grammarToParse;
    private final CharStreamSource streamSupplier;

    SemanticParser(String grammarToParse, CharStreamSource streamSupplier) {
        this.grammarToParse = grammarToParse;
        this.streamSupplier = streamSupplier;
    }

    interface CharStreamSource {

        <T> T charStream(String name, CharStreamConsumer<T> cons) throws IOException;
    }

    interface CharStreamConsumer<T> {

        T consume(long lastModified, CharStreamSupplier stream) throws IOException;
    }

    interface CharStreamSupplier {

        CharStream get() throws IOException;
    }

    public RulesInfo parse() throws IOException {
        return parse(grammarToParse);
    }

    public RulesInfo parse(String grammar) throws IOException {
        return streamSupplier.charStream(grammar, (lastModified, streamSupplier) -> {
            return parse(streamSupplier.get(), lastModified);
        });
    }

    public RulesInfo parse(CharStream stream, long lastModified) throws IOException {
        ANTLRv4Lexer lexer = new ANTLRv4Lexer(stream);
        ANTLRv4Parser parser = new ANTLRv4Parser(new CommonTokenStream(lexer, 0));
        return extract(parser.grammarFile());
    }

    public RulesInfo extract(ANTLRv4Parser.GrammarFileContext file) throws IOException {
        return extract(file, new HashMap<>());
    }

    public RulesInfo extract(ANTLRv4Parser.GrammarFileContext file, Map<String, RulesInfo> infoForGrammar) throws IOException {
        String grammarName = file.grammarSpec().identifier().getText(); // XXX NPE
        System.out.println("GRAMMAR NAME: " + grammarName);

        Set<String> delegatedGrammars = file.accept(new ImportFinder());
        for (String del : delegatedGrammars) {
            if (!infoForGrammar.containsKey(del)) {
                RulesInfo info = streamSupplier.charStream(del, (lastModified, streamSupplier) ->{
                    return parse(streamSupplier.get(), lastModified);
                });
                System.out.println("PARSED DELEGATE '" + del + "'");
                infoForGrammar.put(del, info);
            }
        }

        System.out.println("DELEGATED GRAMMARS: " + delegatedGrammars);

        // Read dependent grammars first
        // Need a RulesInfo implementation for a missing file
        // Move all this to a different package
        Offsets<AntlrRuleKind> names = file.accept(new RuleNamesCollector()).build();

        RuleAlternativeCollector coll = new RuleAlternativeCollector();
        file.accept(coll);
        Offsets<AntlrRuleKind> labels = coll.labels.build();
        Offsets<AntlrRuleKind> alternativesForLabels = coll.alternativesForLabels.build();

        RulesInfo info = file.accept(new ReferenceCollector(names))
                .setLabelsAndAlternatives(labels, alternativesForLabels)
                .build();

        infoForGrammar.put(grammarName, info);

        // Collect EBNFs and other interesting things
        return info;
    }

    static class ImportFinder extends ANTLRv4BaseVisitor<Set<String>> {

        final Set<String> grammars = new LinkedHashSet<>();
        boolean delegatesVisited;

        @Override
        protected Set<String> defaultResult() {
            return grammars;
        }

        @Override
        protected Set<String> aggregateResult(Set<String> aggregate, Set<String> nextResult) {
            if ((aggregate == null) != (nextResult == null)) {
                return aggregate == null ? nextResult : aggregate;
            }
            if (nextResult != null) {
                aggregate.addAll(nextResult);
            }
            return aggregate;
        }

        @Override
        public Set<String> visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
            return null;
        }

        @Override
        public Set<String> visitTokenRuleDeclaration(ANTLRv4Parser.TokenRuleDeclarationContext ctx) {
            return null;
        }

        @Override
        public Set<String> visitFragmentRuleDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx) {
            return null;
        }

        @Override
        public Set<String> visitDelegateGrammar(ANTLRv4Parser.DelegateGrammarContext ctx) {
            ANTLRv4Parser.GrammarIdentifierContext gic = ctx.grammarIdentifier();
            if (gic != null) {
                ANTLRv4Parser.IdentifierContext ic = gic.identifier();
                if (ic != null) {
                    TerminalNode idTN = ic.ID();
                    if (idTN != null) {
                        Token idToken = idTN.getSymbol();
                        if (idToken != null) {
                            String importedGrammarName = idToken.getText();
                            if (!importedGrammarName.equals(MISSING_ID)) {
                                grammars.add(importedGrammarName);
                            }
                        }
                    }
                }

            }
            return super.visitDelegateGrammar(ctx);
        }

        @Override
        public Set<String> visitOptionSpec(ANTLRv4Parser.OptionSpecContext ctx) {
            return super.visitOptionSpec(ctx); //To change body of generated methods, choose Tools | Templates.
        }
    }

    static enum AntlrRuleKind {
        PARSER_RULE,
        LEXER_RULE,
        FRAGMENT_RULE,
        PARSER_RULE_LABEL
    }

    private static class RuleAlternativeCollector extends ANTLRv4BaseVisitor<OffsetsBuilder<AntlrRuleKind>> {

        private final OffsetsBuilder<AntlrRuleKind> labels
                = Offsets.builder(AntlrRuleKind.class);

        private final OffsetsBuilder<AntlrRuleKind> alternativesForLabels
                = Offsets.builder(AntlrRuleKind.class);

        @Override
        protected OffsetsBuilder<AntlrRuleKind> defaultResult() {
            return labels;
        }

        @Override
        public OffsetsBuilder<AntlrRuleKind> visitParserRuleLabeledAlternative(ANTLRv4Parser.ParserRuleLabeledAlternativeContext ctx) {
            ANTLRv4Parser.IdentifierContext idc = ctx.identifier();
            if (idc != null) {
                TerminalNode idTN = idc.ID();
                if (idTN != null) {
                    Token labelToken = idTN.getSymbol();
                    if (labelToken != null) {
                        labels.add(labelToken.getText(), AntlrRuleKind.PARSER_RULE_LABEL, labelToken.getStartIndex(), labelToken.getStopIndex() + 1);
                        alternativesForLabels.add(labelToken.getText(), AntlrRuleKind.PARSER_RULE_LABEL, ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex() + 1);
                    }
                }
            }
            return super.visitParserRuleLabeledAlternative(ctx);
        }

        @Override
        public OffsetsBuilder<AntlrRuleKind> visitTokenRuleDeclaration(ANTLRv4Parser.TokenRuleDeclarationContext ctx) {
            return labels;
        }

        @Override
        public OffsetsBuilder<AntlrRuleKind> visitFragmentRuleDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx) {
            return labels;
        }
    }

    private static class RuleNamesCollector extends ANTLRv4BaseVisitor<OffsetsBuilder<AntlrRuleKind>> {

//        private final Set<String> names = new TreeSet<>();
        private final OffsetsBuilder<AntlrRuleKind> names
                = Offsets.builder(AntlrRuleKind.class);

        @Override
        protected OffsetsBuilder<AntlrRuleKind> defaultResult() {
            return names;
        }

        @Override
        public OffsetsBuilder<AntlrRuleKind> visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
            ANTLRv4Parser.ParserRuleDeclarationContext decl = ctx.parserRuleDeclaration();
            if (decl != null) {
                ANTLRv4Parser.ParserRuleIdentifierContext ident = decl.parserRuleIdentifier();
                if (ident != null) {
                    TerminalNode tn = ident.PARSER_RULE_ID();
                    if (tn != null) {
                        Token id = tn.getSymbol();
                        if (id != null) {
                            String ruleId = id.getText();
                            if (!MISSING_ID.equals(ruleId)) {
                                names.add(ruleId, AntlrRuleKind.PARSER_RULE, id.getStartIndex(), id.getStopIndex() + 1);
                            }
                        }
                    }
                }
            }
            return super.visitParserRuleSpec(ctx);
        }

        @Override
        public OffsetsBuilder<AntlrRuleKind> visitParserRuleIdentifier(ANTLRv4Parser.ParserRuleIdentifierContext ctx) {
            TerminalNode pridTN = ctx.PARSER_RULE_ID();
            if (pridTN != null) {
                Token tok = pridTN.getSymbol();
                if (tok != null && MISSING_ID.equals(tok.getText())) {
                    names.add(pridTN.getText(), AntlrRuleKind.PARSER_RULE, tok.getStartIndex(), tok.getStopIndex() + 1);
                }
            }
            return super.visitParserRuleIdentifier(ctx);
        }

        @Override
        public OffsetsBuilder<AntlrRuleKind> visitTokenRuleDeclaration(ANTLRv4Parser.TokenRuleDeclarationContext ctx) {
            TerminalNode tid = ctx.TOKEN_ID();
            if (tid != null) {
                Token tok = tid.getSymbol();
                if (tok != null && !MISSING_ID.equals(tok.getText())) {
                    names.add(tid.getText(), AntlrRuleKind.LEXER_RULE, tok.getStartIndex(), tok.getStopIndex() + 1);
                }
            }
            return super.visitTokenRuleDeclaration(ctx);
        }

        @Override
        public OffsetsBuilder<AntlrRuleKind> visitFragmentRuleDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx) {
            TerminalNode idTN = ctx.TOKEN_ID();
            if (idTN != null) {
                Token idToken = idTN.getSymbol();
                if (idToken != null && !MISSING_TOKEN_ID.equals(idToken.getText())) {
                    names.add(idToken.getText(), AntlrRuleKind.FRAGMENT_RULE, idToken.getStartIndex(), idToken.getStopIndex() + 1);
                }
            }
            return super.visitFragmentRuleDeclaration(ctx);
        }

//        @Override
//        public Set<String> visitSingleTypeImportDeclaration(ANTLRv4Parser.SingleTypeImportDeclarationContext ctx) {
//            return super.visitSingleTypeImportDeclaration(ctx); //To change body of generated methods, choose Tools | Templates.
//        }
//        @Override
//        public OffsetsBuilder<OffsetKind> visitTokenList(ANTLRv4Parser.TokenListContext ctx) {
//            List<TerminalNode> toks = ctx.TOKEN_ID();
//            if (toks != null) {
//                for (TerminalNode t : toks) {
//                    String nm = t.getText();
//                    if (!"<missing-id>".equals(nm)) {
////                        names.add(nm);
//                    }
//                }
//            }
//            return super.visitTokenList(ctx);
//        }
    }

    private static class ReferenceCollector extends ANTLRv4BaseVisitor<ReferencesInfoBuilder> {

        private final ReferencesInfoBuilder names;

        ReferenceCollector(Offsets<AntlrRuleKind> offsets) {
            this.names = new ReferencesInfoBuilder(offsets);
        }

        @Override
        protected ReferencesInfoBuilder defaultResult() {
            return names;
        }

        @Override
        protected ReferencesInfoBuilder aggregateResult(ReferencesInfoBuilder a, ReferencesInfoBuilder b) {
            return defaultResult();
        }

        @Override
        public ReferencesInfoBuilder visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
            ANTLRv4Parser.ParserRuleDeclarationContext decl = ctx.parserRuleDeclaration();
            if (decl != null) {
                ANTLRv4Parser.ParserRuleIdentifierContext ident = decl.parserRuleIdentifier();
                if (ident != null) {
                    TerminalNode tn = ident.PARSER_RULE_ID();
                    if (tn != null) {
                        Token id = tn.getSymbol();
                        if (id != null) {
                            String ruleId = id.getText();
                            if (!MISSING_ID.equals(ruleId)) {
                                names.enterRule(ruleId, ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex() + 1, () -> {
                                    super.visitParserRuleSpec(ctx);
                                });
                            }
                        }
                    }
                }
            }
            return names;
        }

        @Override
        public ReferencesInfoBuilder visitTokenRuleDeclaration(ANTLRv4Parser.TokenRuleDeclarationContext ctx) {
            TerminalNode tn = ctx.TOKEN_ID();
            if (tn != null) {
                Token tok = tn.getSymbol();
                if (tok != null && !MISSING_ID.equals(tok.getText())) {
                    names.enterRule(tok.getText(), ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex(), () -> {
                        super.visitTokenRuleDeclaration(ctx);
                    });
                }
            }
            return null;
        }

        @Override
        public ReferencesInfoBuilder visitFragmentRuleDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx) {
            TerminalNode tn = ctx.TOKEN_ID();
            if (tn != null) {
                Token tok = tn.getSymbol();
                if (tok != null && !MISSING_TOKEN_ID.equals(tok.getText())) {
                    names.enterRule(tok.getText(), ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex(), () -> {
                        super.visitFragmentRuleDeclaration(ctx);
                    });
                }
            }
            return null;
        }

        @Override
        public ReferencesInfoBuilder visitTerminal(ANTLRv4Parser.TerminalContext ctx) {
            TerminalNode idTN = ctx.TOKEN_ID();
            if (idTN != null) {
                Token idToken = idTN.getSymbol();
                if (idToken != null) {
                    String id = idToken.getText();
                    if (!MISSING_ID.equals(id) && !MISSING_TOKEN_ID.equals(id)) {
                        names.onReference(id, idToken.getStartIndex(), idToken.getStopIndex() + 1);
                    }
                }
            }
            return super.visitTerminal(ctx);
        }

        @Override
        public ReferencesInfoBuilder visitParserRuleReference(ANTLRv4Parser.ParserRuleReferenceContext ctx) {
            ANTLRv4Parser.ParserRuleIdentifierContext pric = ctx.parserRuleIdentifier();
            if (pric != null) {
                TerminalNode pridTN = pric.PARSER_RULE_ID();
                if (pridTN != null) {
                    Token tok = pridTN.getSymbol();
                    if (tok != null && !MISSING_ID.equals(tok.getText())) {
                        names.onReference(tok.getText(), tok.getStartIndex(), tok.getStopIndex() + 1);
                    }
                }
            }
            return super.visitParserRuleReference(ctx);
        }
    }

    public static final class RulesInfo implements Externalizable {

        private final Offsets<AntlrRuleKind> offsets;
        private final ReferenceSets<AntlrRuleKind> refs;
        private final BitSetStringGraph usageGraph;
        private final Offsets<AntlrRuleKind> ruleBounds;
        private final Map<String, List<int[]>> unknownReferences;
        private Offsets<AntlrRuleKind> alternativesForLabels;
        private Offsets<AntlrRuleKind> labels;

        public RulesInfo(Offsets<AntlrRuleKind> offsets, ReferenceSets<AntlrRuleKind> refs, Usages usages, Offsets<AntlrRuleKind> ruleBounds, Map<String, List<int[]>> unknownReferences, Offsets<AntlrRuleKind> labels, Offsets<AntlrRuleKind> alternativesForLabels) {
            this(offsets, refs, new BitSetStringGraph(usages.toBitSetTree(), offsets.nameArray()), ruleBounds, unknownReferences, labels, alternativesForLabels);
        }

        public RulesInfo(Offsets<AntlrRuleKind> offsets, ReferenceSets<AntlrRuleKind> refs, BitSetStringGraph usageGraph, Offsets<AntlrRuleKind> ruleBounds, Map<String, List<int[]>> unknownReferences, Offsets<AntlrRuleKind> labels, Offsets<AntlrRuleKind> alternativesForLabels) {
            assert labels != null : "Null labels";
            assert alternativesForLabels != null : "Null alternativesForLabels";
            this.offsets = offsets;
            this.refs = refs;
            this.usageGraph = usageGraph;
            this.ruleBounds = ruleBounds;
            this.unknownReferences = unknownReferences;
            this.labels = labels;
            this.alternativesForLabels = alternativesForLabels;
        }

        public RulesInfo() {
            offsets = null;
            refs = null;
            usageGraph = null;
            ruleBounds = null;
            unknownReferences = null;
        }

        public List<Item<AntlrRuleKind>> labels() {
            List<Item<AntlrRuleKind>> result = new ArrayList<>(labels.size());
            labels.collectItems(result);
            Collections.sort(result);
            return result;
        }

        public List<Item<AntlrRuleKind>> labelClauses() {
            List<Item<AntlrRuleKind>> result = new ArrayList<>(labels.size());
            alternativesForLabels.collectItems(result);
            Collections.sort(result);
            return result;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(new SerializationStub(this));
        }

        public static RulesInfo load(ObjectInputStream input) throws IOException, ClassNotFoundException {
            SerializationStub stub = (SerializationStub) input.readObject();
            return stub.toRulesInfo();
        }

        @Override
        @SuppressWarnings("unchecked")
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            SerializationStub stub = (SerializationStub) in.readObject();
            try {
                Field f = RulesInfo.class.getDeclaredField("offsets");
                f.setAccessible(true);
                f.set(this, stub.offsets);
                f = RulesInfo.class.getDeclaredField("refs");
                f.setAccessible(true);
                f.set(this, stub.refs);
                f = RulesInfo.class.getDeclaredField("usageGraph");
                f.setAccessible(true);
                f.set(this, stub.usageGraph);
                f = RulesInfo.class.getDeclaredField("ruleBounds");
                f.setAccessible(true);
                f.set(this, stub.ruleBounds);
                f = RulesInfo.class.getDeclaredField("unknownReferences");
                f.setAccessible(true);
                f.set(this, stub.unknownReferences);
                f = RulesInfo.class.getDeclaredField("labels");
                f.setAccessible(true);
                f.set(this, stub.labels);
                f = RulesInfo.class.getDeclaredField("alternativesForLabels");
                f.setAccessible(true);
                f.set(this, stub.alternativesForLabels);
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
                throw new IOException(ex);
            }
        }

        static class SerializationStub implements Externalizable {

            private Offsets<AntlrRuleKind> offsets;
            private ReferenceSets<AntlrRuleKind> refs;
            private BitSetStringGraph usageGraph;
            private Offsets<AntlrRuleKind> ruleBounds;
            private Map<String, List<int[]>> unknownReferences;
            private Offsets<AntlrRuleKind> labels;
            private Offsets<AntlrRuleKind> alternativesForLabels;

            SerializationStub(RulesInfo info) {
                this.offsets = info.offsets;
                this.refs = info.refs;
                this.usageGraph = info.usageGraph;
                this.ruleBounds = info.ruleBounds;
                this.unknownReferences = info.unknownReferences;
                this.labels = info.labels;
                this.alternativesForLabels = info.alternativesForLabels;
            }

            public SerializationStub() {

            }

            RulesInfo toRulesInfo() {
                return new RulesInfo(offsets, refs, usageGraph, ruleBounds, unknownReferences, labels, alternativesForLabels);
            }

            @Override
            public void writeExternal(ObjectOutput out) throws IOException {
                out.writeInt(1);
                out.writeObject(offsets);
                out.writeObject(refs);
                out.writeObject(ruleBounds);
                out.writeObject(unknownReferences);
                out.writeObject(labels);
                out.writeObject(alternativesForLabels);
                usageGraph.save(out);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
                int v = in.readInt();
                if (v != 1) {
                    throw new IOException("Unsupported version " + v);
                }
                offsets = (Offsets<AntlrRuleKind>) in.readObject();
                refs = (ReferenceSets<AntlrRuleKind>) in.readObject();
                ruleBounds = (Offsets<AntlrRuleKind>) in.readObject();
                unknownReferences = (Map<String, List<int[]>>) in.readObject();
                labels = (Offsets<AntlrRuleKind>) in.readObject();
                alternativesForLabels = (Offsets<AntlrRuleKind>) in.readObject();
                usageGraph = BitSetStringGraph.load(in);
            }
        }

        public List<Item<AntlrRuleKind>> allItems() {
            List<Item<AntlrRuleKind>> all = new ArrayList<>();
            offsets.collectItems(all);
            refs.collectItems(all);
            Collections.sort(all);
            return all;
        }

        public BitSetStringGraph usageGraph() {
            return usageGraph;
        }

        public Set<String> unknownReferenceNames() {
            return unknownReferences.keySet();
        }

        public Iterable<Item<AntlrRuleKind>> allRules() {
            return ruleBounds;
        }

        public Item<AntlrRuleKind> boundsForRule(String rule) {
            return ruleBounds.item(rule);
        }

        public List<Item<AntlrRuleKind>> referencesTo(String rule) {
            List<Item<AntlrRuleKind>> items = new LinkedList<>();
            refs.references(rule).collectItems(items);
            return items;
        }
    }

    private static final class ReferencesInfoBuilder {

        private final Offsets<AntlrRuleKind> offsets;
        private final ReferenceSets<AntlrRuleKind> refs;
        private final Offsets<AntlrRuleKind>.UsagesImpl usages;
        private final Offsets<AntlrRuleKind> ruleBounds;
        private final Map<String, List<int[]>> unknownReferences = new TreeMap<>();
        private Offsets<AntlrRuleKind> labels;
        private Offsets<AntlrRuleKind> alternativesForLabels;

        public ReferencesInfoBuilder(Offsets<AntlrRuleKind> offsets) {
            this.offsets = offsets;
            this.refs = offsets.newReferenceSets();
            this.usages = offsets.newUsages();
            this.ruleBounds = offsets.secondary();
        }

        public ReferencesInfoBuilder setLabelsAndAlternatives(Offsets<AntlrRuleKind> labels, Offsets<AntlrRuleKind> alternativesForLabels) {
            this.labels = labels;
            this.alternativesForLabels = alternativesForLabels;
            return this;
        }

        RulesInfo build() {
            Set<String> origWithoutOffsets = offsets.itemsWithNoOffsets();
            ruleBounds.removeAll(origWithoutOffsets);
            Set<String> rulesNoOffsets = ruleBounds.removeItemsWithNoOffsets();
            System.out.println("RULES NO OFFSETS: " + rulesNoOffsets);
            List<Item<AntlrRuleKind>> refItems = new ArrayList<>();
            refs.collectItems(refItems);
            Collections.sort(refItems);
//            ReferenceSets<AntlrRuleKind> rebuilt = ruleBounds.newReferenceSets();
//            // XXX things are getting removed after the offsets is created
//            for (Item<AntlrRuleKind> i : refItems) {
//                if (ruleBounds.contains(i.name())) {
//                    rebuilt.addReference(i.name(), i.start(), i.end());
//                }
//            }
            return new RulesInfo(offsets, refs, usages, ruleBounds, unknownReferences, labels, alternativesForLabels);
        }

        private Item<AntlrRuleKind> currentRule;

        void enterRule(String ruleName, int start, int end, Runnable r) {
            if (currentRule != null) {
                throw new IllegalStateException("EnterRule should not be reentrant: " + currentRule);
            }
            ruleBounds.setOffsets(ruleName, start, end);
            currentRule = offsets.item(ruleName);
            try {
                r.run();
            } finally {
                currentRule = null;
            }
        }

        void onReference(String name, int start, int end) {
            if (currentRule == null) {
                throw new IllegalStateException("Adding ref with no current "
                        + "rule: " + name + "@" + start + ":" + end);
            }
            if (offsets.contains(name)) {
                refs.addReference(name, start, end);
                usages.add(currentRule.index(), offsets.indexOf(name));
//                System.out.println("addUsage " + currentRule.name() + " -> " + name);
            } else {
                System.out.println("add foreign reference " + name);
                addForeignReference(name, start, end);
            }
        }

        void addForeignReference(String name, int start, int end) {
            List<int[]> refs = unknownReferences.get(name);
            if (refs == null) {
                refs = new ArrayList<>(3);
            }
            refs.add(new int[]{start, end});
        }
    }

}
