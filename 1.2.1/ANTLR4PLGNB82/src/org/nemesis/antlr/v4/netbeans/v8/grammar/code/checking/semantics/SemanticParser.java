package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4BaseVisitor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.BlockContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.IdentifierContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.ForeignItem;
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

//    static final class ParseCache {
//        TimedCache<String, RulesInfo, IOException> inMemory = TimedCache.createThrowing(60000, new TimedCache.Answerer<String, RulesInfo,IOException>(){
//            @Override
//            public RulesInfo answer(String request) throws IOException {
//                Path pth = Paths.get(request).normalize();
//                if (!Files.exists(pth)) {
//                    throw new IOException("File does not exist: " + request);
//                }
//                Path parent = pth.getParent();
//
//            }
//        });
//    }
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
                RulesInfo info = streamSupplier.charStream(del, (lastModified, streamSupplier) -> {
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

//        SemanticRegions<Void> blocks = file.accept(new BlockFinder());
        SemanticRegions<Void> blocks = file.accept(new GenericBlockFinder<>(BlockContext.class));

        System.out.println("BLOCKS: \n" + blocks);

        SemanticRegions<Set<EbnfProperty>> ebnfs = file.accept(new EbnfFinder());

        System.out.println("EBNFS: \n" + ebnfs);

        RulesInfo info = file.accept(new ReferenceCollector(grammarName, names, infoForGrammar))
                .setLabelsAndAlternatives(labels, alternativesForLabels)
                .setEBNFs(ebnfs)
                .setBlocks(blocks)
                .build();

        infoForGrammar.put(grammarName, info);

        // Collect EBNFs and other interesting things
        return info;
    }

    enum EbnfProperty {
        STAR,
        QUESTION,
        PLUS
    }

    static class BlockFinder extends ANTLRv4BaseVisitor<SemanticRegions<Void>> {

        private final SemanticRegions<Void> regions = new SemanticRegions<>(Void.class);

        @Override
        protected SemanticRegions<Void> defaultResult() {
            return regions;
        }

        @Override
        public SemanticRegions<Void> visitBlock(ANTLRv4Parser.BlockContext ctx) {
            int start = ctx.start.getStartIndex();
            int end = ctx.stop.getStopIndex() + 1;
            regions.add(null, start, end);
            return super.visitBlock(ctx);
        }
    }

    static class GenericBlockFinder<T extends ParserRuleContext> extends AbstractParseTreeVisitor<SemanticRegions<Void>> {

        private final SemanticRegions<Void> regions = new SemanticRegions<>(Void.class);
        private final Class<T> type;

        GenericBlockFinder(Class<T> type) {
            this.type = type;
        }

        @Override
        protected SemanticRegions<Void> defaultResult() {
            return regions;
        }

        @Override
        public SemanticRegions<Void> visitChildren(RuleNode ctx) {
            if (type.isInstance(ctx)) {
                ParserRuleContext p = type.cast(ctx);
                int start = p.start.getStartIndex();
                int end = p.stop.getStopIndex() + 1;
                regions.add(null, start, end);
            }
            return super.visitChildren(ctx);
        }
    }

    static class EbnfFinder extends ANTLRv4BaseVisitor<SemanticRegions<Set<EbnfProperty>>> {

        @SuppressWarnings({"rawtypes", "unchecked"})
        private final SemanticRegions<Set<EbnfProperty>> regions = new SemanticRegions<>((Class) Set.class);

        @Override
        protected SemanticRegions<Set<EbnfProperty>> defaultResult() {
            return regions;
        }

        private void addEbnf(ParserRuleContext repeated, ANTLRv4Parser.EbnfSuffixContext suffix) {
            if (suffix == null || repeated == null) {
                return;
            }
            String ebnfString = suffix.getText();
            if (!ebnfString.isEmpty()) {
                Set<EbnfProperty> props = EnumSet.noneOf(EbnfProperty.class);
                if (suffix.STAR() != null) {
                    props.add(EbnfProperty.STAR);
                }
                if (suffix.QUESTION() != null) {
                    props.add(EbnfProperty.QUESTION);
                }
                if (suffix.PLUS() != null) {
                    props.add(EbnfProperty.PLUS);
                }
                int start = repeated.getStart().getStartIndex();
                int end = suffix.getStop().getStopIndex() + 1;
                regions.add(props, start, end);
            }
        }

        @Override
        public SemanticRegions<Set<EbnfProperty>> visitEbnf(ANTLRv4Parser.EbnfContext ctx) {
            addEbnf(ctx.block(), ctx.ebnfSuffix());
            return super.visitEbnf(ctx);
        }

        @Override
        public SemanticRegions<Set<EbnfProperty>> visitParserRuleElement(ANTLRv4Parser.ParserRuleElementContext ctx) {
            if (ctx.parserRuleAtom() != null) {
                addEbnf(ctx.parserRuleAtom(), ctx.ebnfSuffix());
            } else if (ctx.labeledParserRuleElement() != null) {
                addEbnf(ctx.labeledParserRuleElement(), ctx.ebnfSuffix());
            }
            return super.visitParserRuleElement(ctx);
        }

        @Override
        public SemanticRegions<Set<EbnfProperty>> visitLexerRuleElement(ANTLRv4Parser.LexerRuleElementContext ctx) {
            if (ctx.lexerRuleAtom() != null) {
                addEbnf(ctx.lexerRuleAtom(), ctx.ebnfSuffix());
            } else if (ctx.lexerRuleElementBlock() != null) {
                addEbnf(ctx.lexerRuleElementBlock(), ctx.ebnfSuffix());
            }
            return super.visitLexerRuleElement(ctx);
        }
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
            return null; // do not descend
        }

        @Override
        public Set<String> visitTokenRuleDeclaration(ANTLRv4Parser.TokenRuleDeclarationContext ctx) {
            return null; // do not descend
        }

        @Override
        public Set<String> visitFragmentRuleDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx) {
            return null; // do not descend
        }

        @Override
        public Set<String> visitTokenVocabSpec(ANTLRv4Parser.TokenVocabSpecContext ctx) {
            IdentifierContext idctx = ctx.identifier();
            if (idctx != null) {
                TerminalNode tn = idctx.ID();
                if (tn != null) {
                    Token tok = tn.getSymbol();
                    if (tok != null) {
                        grammars.add(tok.getText());
                    }
                }
            }
//            return super.visitTokenVocabSpec(ctx); //To change body of generated methods, choose Tools | Templates.
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

        @Override
        public OffsetsBuilder<AntlrRuleKind> visitTokenList(ANTLRv4Parser.TokenListContext ctx) {
            List<TerminalNode> toks = ctx.TOKEN_ID();
            if (toks != null) {
                for (TerminalNode t : toks) {
                    Token tok = t.getSymbol();
                    if (tok != null) {
                        String nm = t.getText();
                        if (!MISSING_TOKEN_ID.equals(nm)) {
                            // XXX fragment rule?  Could be a lexer rule...we don't know.
                            names.add(nm, AntlrRuleKind.FRAGMENT_RULE, tok.getStartIndex(), tok.getStopIndex() + 1);
                        }
                    }
                }
            }
            return super.visitTokenList(ctx);
        }
    }

    private static class ReferenceCollector extends ANTLRv4BaseVisitor<ReferencesInfoBuilder> {

        private final ReferencesInfoBuilder names;

        ReferenceCollector(String grammarName, Offsets<AntlrRuleKind> offsets, Map<String, RulesInfo> dependencies) {
            this.names = new ReferencesInfoBuilder(grammarName, offsets, dependencies);
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
        public ReferencesInfoBuilder visitTokenList(ANTLRv4Parser.TokenListContext ctx) {
            List<TerminalNode> toks = ctx.TOKEN_ID();
            if (toks != null) {
                for (TerminalNode t : toks) {
                    Token tok = t.getSymbol();
                    if (tok != null) {
                        String nm = t.getText();
                        if (!MISSING_TOKEN_ID.equals(nm)) {
                            names.enterRule(nm, tok.getStartIndex(), tok.getStopIndex() + 1, () -> {
                                super.visitTokenList(ctx);
                            });
                            return names;
                        }
                    }
                }
            }
            return names;
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

        private final Offsets<AntlrRuleKind> ruleNames;
        private final ReferenceSets<AntlrRuleKind> refs;
        private final BitSetStringGraph usageGraph;
        private final Offsets<AntlrRuleKind> ruleBounds;
        private final Map<String, List<int[]>> unknownReferences;
        private final Offsets<AntlrRuleKind> alternativesForLabels;
        private final Offsets<AntlrRuleKind> labels;
        private final List<ForeignItem<AntlrRuleKind, String>> foreignReferences;
        private final String grammarName;
        private final SemanticRegions<Set<EbnfProperty>> ebnfs;
        private final SemanticRegions<Void> blocks;

        public RulesInfo(String grammarName, Offsets<AntlrRuleKind> offsets, ReferenceSets<AntlrRuleKind> refs, Usages usages, Offsets<AntlrRuleKind> ruleBounds, Map<String, List<int[]>> unknownReferences, Offsets<AntlrRuleKind> labels, Offsets<AntlrRuleKind> alternativesForLabels, List<ForeignItem<AntlrRuleKind, String>> foreignReferences,
                SemanticRegions<Set<EbnfProperty>> ebnfs, SemanticRegions<Void> blocks) {
            this(grammarName, offsets, refs, new BitSetStringGraph(usages.toBitSetTree(), offsets.nameArray()), ruleBounds, unknownReferences, labels, alternativesForLabels, foreignReferences, ebnfs, blocks);
        }

        public RulesInfo(String grammarName, Offsets<AntlrRuleKind> offsets, ReferenceSets<AntlrRuleKind> refs, BitSetStringGraph usageGraph, Offsets<AntlrRuleKind> ruleBounds, Map<String, List<int[]>> unknownReferences, Offsets<AntlrRuleKind> labels, Offsets<AntlrRuleKind> alternativesForLabels, List<ForeignItem<AntlrRuleKind, String>> foreignReferences, SemanticRegions<Set<EbnfProperty>> ebnfs, SemanticRegions<Void> blocks) {
            assert labels != null : "Null labels";
            assert alternativesForLabels != null : "Null alternativesForLabels";
            this.grammarName = grammarName;
            this.ruleNames = offsets;
            this.refs = refs;
            this.usageGraph = usageGraph;
            this.ruleBounds = ruleBounds;
            this.unknownReferences = unknownReferences;
            this.labels = labels;
            this.alternativesForLabels = alternativesForLabels;
            this.foreignReferences = foreignReferences;
            this.ebnfs = ebnfs;
            this.blocks = blocks;
        }

        public RulesInfo() { // serialization
            ruleNames = null;
            refs = null;
            usageGraph = null;
            ruleBounds = null;
            unknownReferences = null;
            foreignReferences = null;
            alternativesForLabels = null;
            grammarName = null;
            labels = null;
            ebnfs = null;
            blocks = null;
        }

        public SemanticRegions<Set<EbnfProperty>> ebnfs() {
            return ebnfs;
        }

        public SemanticRegions<Void> blocks() {
            return blocks;
        }

        public Item<AntlrRuleKind> itemAtPosition(int pos) {
            return itemAtPosition(pos, true);
        }

        public Item<AntlrRuleKind> itemAtPosition(int pos, boolean includeRuleBodies) {
            Item<AntlrRuleKind> result = refs.itemAt(pos);
            if (result != null) {
                return result;
            }
            for (ForeignItem<AntlrRuleKind, String> f : foreignReferences) {
                if (f.containsPosition(pos)) {
                    return f;
                }
            }
            result = labels.index().atOffset(pos);
            if (result != null) {
                return result;
            }
            result = ruleNames.index().atOffset(pos);
            if (result != null) {
                return result;
            }
            if (includeRuleBodies) {
                result = ruleBounds.index().atOffset(pos);
            }
            return result;
        }

        public List<ForeignItem<AntlrRuleKind, String>> foreignItems() {
            return foreignReferences;
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
                Field f = RulesInfo.class.getDeclaredField("ruleNames");
                f.setAccessible(true);
                f.set(this, stub.ruleNames);
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
                f = RulesInfo.class.getDeclaredField("foreignReferences");
                f.setAccessible(true);
                f.set(this, stub.foreignReferences);
                f = RulesInfo.class.getDeclaredField("ebnfs");
                f.setAccessible(true);
                f.set(this, stub.ebnfs);
                f = RulesInfo.class.getDeclaredField("blocks");
                f.setAccessible(true);
                f.set(this, stub.blocks);
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
                throw new IOException(ex);
            }
        }

        static class SerializationStub implements Externalizable {

            private String grammarName;
            private Offsets<AntlrRuleKind> ruleNames;
            private ReferenceSets<AntlrRuleKind> refs;
            private BitSetStringGraph usageGraph;
            private Offsets<AntlrRuleKind> ruleBounds;
            private Map<String, List<int[]>> unknownReferences;
            private Offsets<AntlrRuleKind> labels;
            private Offsets<AntlrRuleKind> alternativesForLabels;
            private List<ForeignItem<AntlrRuleKind, String>> foreignReferences;
            private SemanticRegions<Set<EbnfProperty>> ebnfs;
            private SemanticRegions<Void> blocks;

            SerializationStub(RulesInfo info) {
                this.grammarName = info.grammarName;
                this.foreignReferences = info.foreignReferences;
                this.ruleNames = info.ruleNames;
                this.refs = info.refs;
                this.usageGraph = info.usageGraph;
                this.ruleBounds = info.ruleBounds;
                this.unknownReferences = info.unknownReferences;
                this.labels = info.labels;
                this.alternativesForLabels = info.alternativesForLabels;
                this.ebnfs = info.ebnfs;
                this.blocks = info.blocks;
            }

            public SerializationStub() {

            }

            RulesInfo toRulesInfo() {
                return new RulesInfo(grammarName, ruleNames, refs, usageGraph, ruleBounds, unknownReferences, labels, alternativesForLabels, foreignReferences, ebnfs, blocks);
            }

            @Override
            public void writeExternal(ObjectOutput out) throws IOException {
                out.writeInt(1);
                Set<Offsets<?>> all = new HashSet<>(Arrays.asList(ruleNames, ruleBounds, alternativesForLabels, labels));
                for (ForeignItem<?, ?> i : foreignReferences) {
                    all.add(i.originOffsets());
                }
                Offsets.SerializationContext ctx = Offsets.createSerializationContext(all);
                out.writeObject(ctx);
                try {
                    Offsets.withSerializationContext(ctx, () -> {
                        out.writeUTF(grammarName);
                        out.writeObject(ruleNames);
                        out.writeObject(refs);
                        out.writeObject(ruleBounds);
                        out.writeObject(unknownReferences);
                        out.writeObject(labels);
                        out.writeObject(alternativesForLabels);
                        out.writeObject(foreignReferences);
                        out.writeObject(ebnfs);
                        out.writeObject(blocks);
                        usageGraph.save(out);
                        return null;
                    });
                } catch (Exception ex) {
                    throw new IOException(ex);
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
                int v = in.readInt();
                if (v != 1) {
                    throw new IOException("Unsupported version " + v);
                }
                Offsets.SerializationContext ctx = (Offsets.SerializationContext) in.readObject();
                try {
                    Offsets.withSerializationContext(ctx, () -> {
                        grammarName = in.readUTF();
                        ruleNames = (Offsets<AntlrRuleKind>) in.readObject();
                        refs = (ReferenceSets<AntlrRuleKind>) in.readObject();
                        ruleBounds = (Offsets<AntlrRuleKind>) in.readObject();
                        unknownReferences = (Map<String, List<int[]>>) in.readObject();
                        labels = (Offsets<AntlrRuleKind>) in.readObject();
                        alternativesForLabels = (Offsets<AntlrRuleKind>) in.readObject();
                        foreignReferences = (List<ForeignItem<AntlrRuleKind, String>>) in.readObject();
                        ebnfs = (SemanticRegions<Set<EbnfProperty>>) in.readObject();
                        blocks = (SemanticRegions<Void>) in.readObject();
                        usageGraph = BitSetStringGraph.load(in);
                        return null;
                    });
                } catch (Exception ex) {
                    throw new IOException(ex);
                }
            }
        }

        public List<Item<AntlrRuleKind>> allItems() {
            List<Item<AntlrRuleKind>> all = new ArrayList<>();
            ruleNames.collectItems(all);
            refs.collectItems(all);
            all.addAll(foreignReferences);
            Collections.sort(all);
            return all;
        }

        public BitSetStringGraph usageGraph() {
            return usageGraph;
        }

        public Set<String> unknownReferenceNames() {
            return unknownReferences.keySet();
        }

        public Map<String, List<int[]>> unknownReferences() {
            return unknownReferences;
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
        private SemanticRegions<Void> blocks;
        private SemanticRegions<Set<EbnfProperty>> ebnfs;
        private final Map<String, RulesInfo> dependencies;
        private final List<ForeignItem<AntlrRuleKind, String>> foreignReferences = new ArrayList<>();
        private final String grammarName;

        public ReferencesInfoBuilder(String grammarName, Offsets<AntlrRuleKind> offsets, Map<String, RulesInfo> dependencies) {
            this.offsets = offsets;
            this.refs = offsets.newReferenceSets();
            this.usages = offsets.newUsages();
            this.ruleBounds = offsets.secondary();
            this.dependencies = dependencies;
            this.grammarName = grammarName;
        }

        public ReferencesInfoBuilder setLabelsAndAlternatives(Offsets<AntlrRuleKind> labels, Offsets<AntlrRuleKind> alternativesForLabels) {
            this.labels = labels;
            this.alternativesForLabels = alternativesForLabels;
            return this;
        }

        public ReferencesInfoBuilder setBlocks(SemanticRegions<Void> blocks) {
            this.blocks = blocks;
            return this;
        }

        public ReferencesInfoBuilder setEBNFs(SemanticRegions<Set<EbnfProperty>> ebnfs) {
            this.ebnfs = ebnfs;
            return this;
        }

        RulesInfo build() {
            Set<String> origWithoutOffsets = offsets.itemsWithNoOffsets();
            System.out.println("ORIG NO OFFSETS: " + origWithoutOffsets);
            ruleBounds.removeAll(origWithoutOffsets);
            Set<String> rulesNoOffsets = ruleBounds.removeItemsWithNoOffsets();
            System.out.println("RULES NO OFFSETS: " + rulesNoOffsets);
            List<Item<AntlrRuleKind>> refItems = new ArrayList<>();
            refs.collectItems(refItems);
            Collections.sort(refItems);
            System.out.println("BUILD WITH FOREIGN: " + unknownReferences);
//            ReferenceSets<AntlrRuleKind> rebuilt = ruleBounds.newReferenceSets();
//            // XXX things are getting removed after the offsets is created
//            for (Item<AntlrRuleKind> i : refItems) {
//                if (ruleBounds.contains(i.name())) {
//                    rebuilt.addReference(i.name(), i.start(), i.end());
//                }
//            }
            return new RulesInfo(grammarName, offsets, refs, usages, ruleBounds, unknownReferences, labels, alternativesForLabels, foreignReferences,
                    ebnfs.trim(), blocks.trim());
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
            } else {
                addForeignReference(name, start, end);
            }
        }

        void addForeignReference(String name, int start, int end) {
            if ("EOF".equals(name)) {
                // EOF is a keyword, but gets parsed by our grammar as if it
                // were a token in some other grammar
                return;
            }
            for (Map.Entry<String, RulesInfo> e : dependencies.entrySet()) {
                if (!grammarName.equals(e.getKey())) {
                    ForeignItem<AntlrRuleKind, String> foreign = e.getValue().ruleNames.newForeignItem(name, e.getKey(), start, end);
                    if (foreign != null) {
                        foreignReferences.add(foreign);
                        return;
                    }
                }
            }
            List<int[]> refs = unknownReferences.get(name);
            if (refs == null) {
                refs = new ArrayList<>(3);
                unknownReferences.put(name, refs);
            }
            refs.add(new int[]{start, end});
        }
    }

}
