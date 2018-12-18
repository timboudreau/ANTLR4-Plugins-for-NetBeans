package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.ReferenceExtractorInfo.ExtractorReturnType;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedRegionReferenceSets;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegionsBuilder;
import org.nemesis.antlr.v4.netbeans.v8.util.reflection.ReflectionPath;
import org.nemesis.antlr.v4.netbeans.v8.util.reflection.ReflectionPath.ResolutionContext;

/**
 *
 * @author Tim Boudreau
 */
public final class NamedRegionExtractorBuilder<T extends Enum<T>, Ret> {

    private final Class<T> keyType;
    private NamedRegionKey<T> namePositionKey;
    private NamedRegionKey<T> ruleRegionKey;
    private Set<NameExtractorInfo<?, T>> nameExtractors = new HashSet<>();
    private final Set<ReferenceExtractorPair<T>> referenceExtractors = new HashSet<>();
    private final Function<NameExtractors, Ret> buildFunction;
    private final ResolutionContext ctx = new ResolutionContext();

    NamedRegionExtractorBuilder(Class<T> keyType, Function<NameExtractors, Ret> buildFunction) {
        this.keyType = keyType;
        this.buildFunction = buildFunction;
    }

    public NamedRegionExtractorBuilderWithNameKey<T, Ret> recordingNamePositionUnder(NamedRegionKey<T> key) {
        assert keyType == key.type;
        namePositionKey = key;
        return new NamedRegionExtractorBuilderWithNameKey<>(this);
    }

    public NamedRegionExtractorBuilderWithRuleKey<T, Ret> recordingRuleRegionUnder(NamedRegionKey<T> key) {
        assert keyType == key.type;
        ruleRegionKey = key;
        return new NamedRegionExtractorBuilderWithRuleKey<>(this);
    }

    void addReferenceExtractorPair(ReferenceExtractorPair<T> pair) {
        this.referenceExtractors.add(pair);
    }

    Ret _build() {
        return buildFunction.apply(new NameExtractors<>(keyType, namePositionKey, ruleRegionKey, nameExtractors, referenceExtractors));
    }

    static final class NamedRegionExtractorBuilderWithNameKey<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        public NamedRegionExtractorBuilderWithNameKey(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        public <R extends ParserRuleContext> NameExtractorBuilder<R, T, Ret> whereRuleIs(Class<R> type) {
            return new NameExtractorBuilder<>(bldr, type);
        }

        public NamedRegionExtractorBuilderWithBothKeys<T, Ret> recordingNamePositionUnder(NamedRegionKey<T> key) {
            assert bldr.keyType == key.type;
            bldr.ruleRegionKey = key;
            return new NamedRegionExtractorBuilderWithBothKeys<>(bldr);
        }
    }

    static final class NamedRegionExtractorBuilderWithRuleKey<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        NamedRegionExtractorBuilderWithRuleKey(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        public <R extends ParserRuleContext> NameExtractorBuilder<R, T, Ret> whereRuleIs(Class<R> type) {
            return new NameExtractorBuilder<>(bldr, type);
        }

        public NamedRegionExtractorBuilderWithBothKeys<T, Ret> recordingNamePositionUnder(NamedRegionKey<T> key) {
            assert bldr.keyType == key.type;
            bldr.namePositionKey = key;
            return new NamedRegionExtractorBuilderWithBothKeys<>(bldr);
        }
    }

    static final class NamedRegionExtractorBuilderWithBothKeys<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        NamedRegionExtractorBuilderWithBothKeys(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        public <R extends ParserRuleContext> NameExtractorBuilder<R, T, Ret> whereRuleIs(Class<R> type) {
            return new NameExtractorBuilder<>(bldr, type);
        }
    }

    private static <R extends ParserRuleContext, T extends Enum<T>> Function<R, NamedRegionData<T>> func(T kind, ReflectionPath<?> path, ResolutionContext ctx) {
        return rule -> {
            ReflectionPath.ResolutionResult rr = ctx.getResult(path, rule);
            if (rr.thrown() != null) {
                Logger.getLogger(NamedRegionExtractorBuilder.class.getName()).log(Level.WARNING,
                        "Exception invoking " + path + " reflectively on " + rule, rr.thrown());
                return null;
            }
            Object result = rr.value();
            if (result == null) {
                return null;
            } else if (result instanceof ParserRuleContext) {
                ParserRuleContext prc = (ParserRuleContext) result;
                return new NamedRegionData<T>(prc.getText(), kind, prc.getStart().getStartIndex(), prc.getStop().getStopIndex() + 1);
            } else if (result instanceof TerminalNode) {
                TerminalNode tn = (TerminalNode) result;
                return new NamedRegionData<T>(tn.getText(), kind, tn.getSymbol().getStartIndex(), tn.getSymbol().getStopIndex() + 1);
            } else if (result instanceof Token) {
                Token tok = (Token) result;
                return new NamedRegionData<T>(tok.getText(), kind, tok.getStartIndex(), tok.getStopIndex() + 1);
            } else {
                throw new IllegalStateException("Don't know how to convert " + result.getClass().getName()
                        + " to a NamedRegionData");
            }
        };
    }

    public static final class NameExtractorBuilder<R extends ParserRuleContext, T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;
        private final Class<R> type;
        private Predicate<RuleNode> qualifiers;

        NameExtractorBuilder(NamedRegionExtractorBuilder<T, Ret> bldr, Class<R> type) {
            this.bldr = bldr;
            this.type = type;
        }

        public NameExtractorBuilder<R, T, Ret> whenInAncestorRule(Class<? extends RuleNode> qualifyingAncestorType) {
            return whenAncestorMatches(new QualifierPredicate(qualifyingAncestorType));
        }

        public NameExtractorBuilder<R, T, Ret> whenAncestorMatches(Predicate<RuleNode> ancestorTest) {
            if (qualifiers == null) {
                qualifiers = ancestorTest;
            } else {
                qualifiers = qualifiers.or(ancestorTest);
            }
            return this;
        }

        /**
         * Pass a reflection path, such as 
         * 
         * @param reflectionPath
         * @param kind
         * @see ReflectionPath
         * @return 
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameWith(String reflectionPath, T kind) {
            return derivingNameWith(func(kind, new ReflectionPath<Object>(reflectionPath, Object.class), bldr.ctx));
        }

        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameWith(Function<R, NamedRegionData<T>> extractor) {
//            bldr.nameExtractors.add(new NameExtractorInfo<R, T>(type, extractor, qualifiers);
            NameExtractorInfo<R, T> info = new NameExtractorInfo<>(type, extractor, qualifiers);
            bldr.nameExtractors.add(info);
            return new FinishableNamedRegionExtractorBuilder<>(bldr);
        }

        /**
         * Derive names from a terminal node list, which some kinds of rule can
         * return if there is no parser rule for the name definition itself. In
         * this case, the offset for both the name and bounds will be set to the
         * bounds of the terminal node, and the terminal node's
         * getSymbol().getText() value is the name.
         *
         * @param argType The type to use for all returned names
         * @param extractor A function which finds the list of terminal nodes in
         * the rule
         * @return a builder which can be finished
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameFromTerminalNodes(T argType, Function<R, List<? extends TerminalNode>> extractor) {
            NameExtractorInfo<R, T> info = new NameExtractorInfo<>(type, qualifiers, argType, extractor);
            bldr.nameExtractors.add(info);
            return new FinishableNamedRegionExtractorBuilder<>(bldr);
        }
    }

    private static final class QualifierPredicate implements Predicate<RuleNode> {

        private final Class<? extends RuleNode> qualifyingType;

        public QualifierPredicate(Class<? extends RuleNode> qualifyingType) {
            this.qualifyingType = qualifyingType;
        }

        @Override
        public boolean test(RuleNode t) {
            return qualifyingType.isInstance(t);
        }

    }

    static final class NameExtractorInfo<R extends ParserRuleContext, T extends Enum<T>> {

        private final Class<R> type;
        private final Function<R, NamedRegionData<T>> extractor;
        private final Predicate<RuleNode> ancestorQualifier;
        private final T argType;
        private final Function<R, List<? extends TerminalNode>> terminalFinder;

        NameExtractorInfo(Class<R> type, Function<R, NamedRegionData<T>> extractor, Predicate<RuleNode> ancestorQualifier) {
            this.type = type;
            this.extractor = extractor;
            this.ancestorQualifier = ancestorQualifier;
            this.terminalFinder = null;
            this.argType = null;
        }

        NameExtractorInfo(Class<R> type, Predicate<RuleNode> ancestorQualifier, T argType, Function<R, List<? extends TerminalNode>> terminalFinder) {
            this.type = type;
            this.extractor = null;
            this.ancestorQualifier = ancestorQualifier;
            this.argType = argType;
            this.terminalFinder = terminalFinder;
        }

        public void find(R node, BiConsumer<NamedRegionData<T>, TerminalNode> cons) {
            if (extractor != null) {
                cons.accept(extractor.apply(node), null);
            } else {
                List<? extends TerminalNode> nds = terminalFinder.apply(node);
                if (nds != null) {
                    for (TerminalNode tn : nds) {
                        Token tok = tn.getSymbol();
                        if (tok != null) {
                            cons.accept(new NamedRegionData<>(tn.getText(), argType, tok.getStartIndex(), tok.getStopIndex() + 1), tn);
                        }
                    }
                }
            }
        }
    }

    public static final class FinishableNamedRegionExtractorBuilder<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        FinishableNamedRegionExtractorBuilder(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        public <R extends ParserRuleContext> NameExtractorBuilder<R, T, Ret> whereRuleIs(Class<R> type) {
            return new NameExtractorBuilder<>(bldr, type);
        }

        public NameReferenceCollectorBuilder<T, Ret> collectingReferencesUnder(NameReferenceSetKey<T> refSetKey) {
            return new NameReferenceCollectorBuilder<>(this, refSetKey);
        }

        FinishableNamedRegionExtractorBuilder<T, Ret> addReferenceExtractorPair(ReferenceExtractorPair<T> pair) {
            bldr.addReferenceExtractorPair(pair);
            return this;
        }

        public Ret finishNamedRegions() {
            return bldr._build();
        }
    }

    public static final class NameReferenceCollectorBuilder<T extends Enum<T>, Ret> {

        private final FinishableNamedRegionExtractorBuilder<T, Ret> bldr;
        private final NameReferenceSetKey<T> refSetKey;
        private final Set<ReferenceExtractorInfo<?>> referenceExtractors = new HashSet<>();

        public NameReferenceCollectorBuilder(FinishableNamedRegionExtractorBuilder<T, Ret> bldr, NameReferenceSetKey<T> refSetKey) {
            this.bldr = bldr;
            this.refSetKey = refSetKey;
        }

        public <R extends ParserRuleContext> ReferenceExtractorBuilder<R, T, Ret> whereReferenceContainingRuleIs(Class<R> ruleType) {
            return new ReferenceExtractorBuilder<>(this, ruleType);
        }

        public FinishableNamedRegionExtractorBuilder<T, Ret> finishReferenceCollector() {
            ReferenceExtractorPair<T> extractorPair = new ReferenceExtractorPair<>(referenceExtractors, refSetKey);
            bldr.addReferenceExtractorPair(extractorPair);
            return bldr;
        }
    }

    static class ReferenceExtractorPair<T extends Enum<T>> {

        private final Set<ReferenceExtractorInfo<?>> referenceExtractors;
        private final NameReferenceSetKey<T> refSetKey;

        ReferenceExtractorPair(Set<ReferenceExtractorInfo<?>> referenceExtractors, NameReferenceSetKey<T> refSetKey) {
            this.refSetKey = refSetKey;
            this.referenceExtractors = new HashSet<>(referenceExtractors);
        }
    }

    public static final class ReferenceExtractorBuilder<R extends ParserRuleContext, T extends Enum<T>, Ret> {

        private final NameReferenceCollectorBuilder<T, Ret> bldr;
        private final Class<R> ruleType;
        private Predicate<RuleNode> ancestorQualifier;

        ReferenceExtractorBuilder(NameReferenceCollectorBuilder<T, Ret> bldr, Class<R> ruleType) {
            this.bldr = bldr;
            this.ruleType = ruleType;
        }

        public <A extends RuleNode> ReferenceExtractorBuilder<R, T, Ret> whenAncestorRuleOf(Class<A> ancestorRuleType) {
            if (ancestorQualifier == null) {
                ancestorQualifier = new QualifierPredicate(ancestorRuleType);
            } else {
                ancestorQualifier = ancestorQualifier.or(new QualifierPredicate(ancestorRuleType));
            }
            return this;
        }

        private NameReferenceCollectorBuilder<T, Ret> finish(ReferenceExtractorInfo<R> info) {
            bldr.referenceExtractors.add(info);
            return bldr;
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsExplicitlyWith(Function<R, NameAndOffsets> offsetsExtractor) {
            return finish(new ReferenceExtractorInfo<>(ruleType, offsetsExtractor, ExtractorReturnType.NAME_AND_OFFSETS, ancestorQualifier));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromRuleWith(Function<R, ParserRuleContext> offsetsExtractor) {
            return finish(new ReferenceExtractorInfo<>(ruleType, offsetsExtractor, ExtractorReturnType.PARSER_RULE_CONTEXT, ancestorQualifier));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromTokenWith(Function<R, Token> offsetsExtractor) {
            return finish(new ReferenceExtractorInfo<>(ruleType, offsetsExtractor, ExtractorReturnType.TOKEN, ancestorQualifier));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromTerminalNodeWith(Function<R, TerminalNode> offsetsExtractor) {
            return finish(new ReferenceExtractorInfo<>(ruleType, offsetsExtractor, ExtractorReturnType.TERMINAL_NODE, ancestorQualifier));
        }
    }

    public static class NameAndOffsets {

        final String name;
        final int start;
        final int end;

        public NameAndOffsets(String name, int start, int end) {
            assert name != null;
            assert start >= 0;
            assert end >= 0;
            this.name = name;
            this.start = start;
            this.end = end;
        }

        public String toString() {
            return name + "@" + start + ":" + end;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 41 * hash + Objects.hashCode(this.name);
            hash = 41 * hash + this.start;
            hash = 41 * hash + this.end;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final NameAndOffsets other = (NameAndOffsets) obj;
            if (this.start != other.start) {
                return false;
            }
            if (this.end != other.end) {
                return false;
            }
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return true;
        }
    }

    public static class NamedRegionData<T extends Enum<T>> {

        final String string;
        final T kind;
        final int start;
        final int end;

        public NamedRegionData(String string, T kind, int start, int end) {
            this.string = string;
            this.kind = kind;
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return string + ":" + kind + ":" + start + ":" + end;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + Objects.hashCode(this.string);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final NamedRegionData<?> other = (NamedRegionData<?>) obj;
            if (!Objects.equals(this.string, other.string)) {
                return false;
            }
            return true;
        }
    }

    static final class ReferenceExtractorInfo<R extends ParserRuleContext> {

        final Class<R> ruleType;
        final Function<R, NameAndOffsets> offsetsExtractor;
        private final Predicate<RuleNode> ancestorQualifier;

        public ReferenceExtractorInfo(Class<R> ruleType, Function<R, ?> offsetsExtractor, ExtractorReturnType rtype, Predicate<RuleNode> ancestorQualifier) {
            this.ruleType = ruleType;
            this.offsetsExtractor = offsetsExtractor.andThen(rtype);
            this.ancestorQualifier = ancestorQualifier;
        }

        enum ExtractorReturnType implements Function<Object, NameAndOffsets> {
            NAME_AND_OFFSETS,
            PARSER_RULE_CONTEXT,
            TOKEN,
            TERMINAL_NODE;

            @Override
            public NameAndOffsets apply(Object t) {
                if (t == null) {
                    return null;
                }
                NameAndOffsets result = null;
                switch (this) {
                    case NAME_AND_OFFSETS:
                        result = (NameAndOffsets) t;
                        break;
                    case PARSER_RULE_CONTEXT:
                        ParserRuleContext c = (ParserRuleContext) t;
                        result = new NameAndOffsets(c.getText(), c.start.getStartIndex(), c.stop.getStopIndex() + 1);
                        break;
                    case TOKEN:
                        Token tok = (Token) t;
                        result = new NameAndOffsets(tok.getText(), tok.getStartIndex(), tok.getStopIndex() + 1);
                        break;
                    case TERMINAL_NODE:
                        TerminalNode tn = (TerminalNode) t;
                        tok = tn.getSymbol();
                        if (tok != null) {
                            result = new NameAndOffsets(tok.getText(), tok.getStartIndex(), tok.getStopIndex() + 1);
                        }
                        break;
                    default:
                        throw new AssertionError(this);
                }
                return result;
            }
        }

    }

    public static final class NamedRegionKey<T extends Enum<T>> {

        private final String name;
        private final Class<T> type;

        private NamedRegionKey(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }

        public static <T extends Enum<T>> NamedRegionKey create(Class<T> type) {
            return new NamedRegionKey(type.getSimpleName(), type);
        }

        public static <T extends Enum<T>> NamedRegionKey create(String name, Class<T> type) {
            return new NamedRegionKey(name, type);
        }

        public String toString() {
            return name + ":" + type.getSimpleName();
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.name);
            hash = 37 * hash + Objects.hashCode(this.type);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final NamedRegionKey<?> other = (NamedRegionKey<?>) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            return true;
        }
    }

    public static final class NameReferenceSetKey<T extends Enum<T>> {

        private final String name;
        private final Class<T> type;

        private NameReferenceSetKey(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }

        public static final <T extends Enum<T>> NameReferenceSetKey<T> create(Class<T> type) {
            return new NameReferenceSetKey(type.getSimpleName(), type);
        }

        public static final <T extends Enum<T>> NameReferenceSetKey<T> create(String name, Class<T> type) {
            return new NameReferenceSetKey(name, type);
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 37 * hash + Objects.hashCode(this.name);
            hash = 37 * hash + Objects.hashCode(this.type);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final NameReferenceSetKey<?> other = (NameReferenceSetKey<?>) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            return true;
        }

    }

    static class NameExtractors<T extends Enum<T>> {

        private final Class<T> keyType;
        private final NamedRegionKey<T> namePositionKey;
        private final NamedRegionKey<T> ruleRegionKey;
        private final NameExtractorInfo<?, T>[] nameExtractors;
        private final ReferenceExtractorPair<?>[] referenceExtractors;

        public NameExtractors(Class<T> keyType, NamedRegionKey<T> namePositionKey, NamedRegionKey<T> ruleRegionKey, Set<NameExtractorInfo<?, T>> nameExtractors, Set<ReferenceExtractorPair<T>> referenceExtractors) {
            this.keyType = keyType;
            assert namePositionKey != null || ruleRegionKey != null;
            this.namePositionKey = namePositionKey;
            this.ruleRegionKey = ruleRegionKey;
            this.nameExtractors = nameExtractors.toArray((NameExtractorInfo<?, T>[]) new NameExtractorInfo<?, ?>[nameExtractors.size()]);
            this.referenceExtractors = referenceExtractors.toArray(new ReferenceExtractorPair<?>[referenceExtractors.size()]);
        }

        void invoke(ParserRuleContext ctx, NameInfoStore store) {
            RuleNameAndBoundsVisitor v = new RuleNameAndBoundsVisitor();
            ctx.accept(v);
            NamedSemanticRegions<T> names = v.namesBuilder == null ? null : v.namesBuilder.build();
            NamedSemanticRegions<T> ruleBounds = v.ruleBoundsBuilder.build();

            if (namePositionKey != null) {
                store.addNamedRegions(namePositionKey, names);
            }
            if (ruleRegionKey != null) {
                store.addNamedRegions(ruleRegionKey, ruleBounds);
            }

            ReferenceExtractorVisitor v1 = new ReferenceExtractorVisitor(ruleBounds);
            ctx.accept(v1);
            v1.conclude(store);
        }

        interface NameInfoStore {

            <T extends Enum<T>> void addNamedRegions(NamedRegionKey<T> key, NamedSemanticRegions<T> regions);

            <T extends Enum<T>> void addReferences(NameReferenceSetKey<T> key, NamedSemanticRegions.NamedRegionReferenceSets<T> regions);

            <T extends Enum<T>> void addReferenceGraph(NameReferenceSetKey<T> refSetKey, BitSetStringGraph stringGraph);
        }

        class ReferenceExtractorVisitor extends AbstractParseTreeVisitor<Void> {

            int[] lengths;
            int[][] activations;
            Set<NameAndOffsets> unknown = new HashSet<>();

            ReferenceExtractorInfo<?>[][] infos;
            private final NamedSemanticRegions<T> regions;
            NamedSemanticRegions.NamedRegionReferenceSets<T>[] refs;
            private final BitSet[][] references, reverseReferences;

            ReferenceExtractorVisitor(NamedSemanticRegions<T> regions) {
                activations = new int[referenceExtractors.length][];
                lengths = new int[referenceExtractors.length];
                references = new BitSet[referenceExtractors.length][regions.size()];
                reverseReferences = new BitSet[referenceExtractors.length][regions.size()];

                infos = new ReferenceExtractorInfo[referenceExtractors.length][];
                refs = (NamedRegionReferenceSets<T>[]) new NamedRegionReferenceSets<?>[referenceExtractors.length];

                for (int i = 0; i < referenceExtractors.length; i++) {
                    ReferenceExtractorInfo[] ex = referenceExtractors[i].referenceExtractors.toArray(new ReferenceExtractorInfo[referenceExtractors[i].referenceExtractors.size()]);
                    lengths[i] = ex.length;
                    infos[i] = ex;
                    activations[i] = new int[ex.length];
                    refs[i] = regions.newReferenceSets();
                    for (int j = 0; j < ex.length; j++) {
                        if (ex[i].ancestorQualifier == null) {
                            activations[i][j] = 1;
                        }
                    }
                    for (int j = 0; j < regions.size(); j++) {
                        references[i][j] = new BitSet(regions.size());
                        reverseReferences[i][j] = new BitSet(regions.size());
                    }
                }
                this.regions = regions;
            }

            void conclude(NameInfoStore store) {
                for (int i = 0; i < referenceExtractors.length; i++) {
                    ReferenceExtractorPair r = referenceExtractors[i];
                    store.addReferences(r.refSetKey, refs[i]);
                    BitSetTree graph = new BitSetTree(reverseReferences[i], references[i]);
                    BitSetStringGraph stringGraph = new BitSetStringGraph(graph, regions.nameArray());
                    store.addReferenceGraph(r.refSetKey, stringGraph);
                }
            }

            private <L extends ParserRuleContext> NameAndOffsets doRunOne(ReferenceExtractorInfo<L> ext, L nd) {
                return ext.offsetsExtractor.apply(nd);
            }

            private <L extends ParserRuleContext> NameAndOffsets runOne(ReferenceExtractorInfo<L> ext, RuleNode nd) {
                return doRunOne(ext, ext.ruleType.cast(nd));
            }

            @Override
            public Void visitChildren(RuleNode node) {
                boolean[][] activeScratch = new boolean[referenceExtractors.length][];
                for (int i = 0; i < lengths.length; i++) {
                    activeScratch[i] = new boolean[lengths[i]];
                    for (int j = 0; j < lengths[i]; j++) {
                        ReferenceExtractorInfo<?> info = infos[i][j];
                        if (info.ancestorQualifier != null) {
                            if (info.ancestorQualifier.test(node)) {
                                activeScratch[i][j] = true;
                                activations[i][j]++;
                            }
                        }
                        if (activations[i][j] > 0 && info.ruleType.isInstance(node)) {
                            NameAndOffsets referenceOffsets = runOne(info, node);
                            if (referenceOffsets != null) {
                                if (regions.contains(referenceOffsets.name)) {
                                    refs[i].addReference(referenceOffsets.name, referenceOffsets.start, referenceOffsets.end);
                                    NamedSemanticRegions.NamedSemanticRegion<T> containedBy = regions.index().regionAt(referenceOffsets.start);
                                    int referencedIndex = regions.indexOf(referenceOffsets.name);
                                    if (containedBy != null && referencedIndex != -1) {
                                        System.out.println("REGION AT " + referenceOffsets.start + " IS " + containedBy.start() + ":" + containedBy.end() + " - " + containedBy.name());
                                        assert containedBy.containsPosition(referenceOffsets.start) :
                                                "Index returned bogus result for position " + referenceOffsets.start + ": " + containedBy + " from " + regions.index() + "; code:\n" + regions.toCode();
                                        System.out.println("ENCOUNTERED " + referenceOffsets + " index " + referencedIndex + " inside " + containedBy.name() + " index " + containedBy.index() + " in " + regions);
                                        int referenceIndex = containedBy.index();

                                        references[i][referencedIndex].set(referenceIndex);
                                        reverseReferences[i][referenceIndex].set(referencedIndex);
                                    }
                                } else {
                                    System.out.println("ADD UNKNOWN: " + referenceOffsets);
                                    unknown.add(referenceOffsets);
                                }
                            }
                        }
                    }
                }
                super.visitChildren(node);
                for (int i = 0; i < lengths.length; i++) {
                    for (int j = 0; j < lengths[i]; j++) {
                        if (activeScratch[i][j]) {
                            activations[i][j]--;
                        }
                    }
                }
                return null;
            }

        }

        class RuleNameAndBoundsVisitor extends AbstractParseTreeVisitor<Void> {

            private final NamedSemanticRegionsBuilder<T> namesBuilder;
            private final NamedSemanticRegionsBuilder<T> ruleBoundsBuilder;
            private final int[] activations;

            public RuleNameAndBoundsVisitor() {
                activations = new int[nameExtractors.length];
                for (int i = 0; i < activations.length; i++) {
                    if (nameExtractors[i].ancestorQualifier == null) {
                        activations[i] = 1;
                    }
                }
                if (namePositionKey != null) {
                    namesBuilder = NamedSemanticRegions.builder(keyType);
                } else {
                    namesBuilder = null;
                }
                ruleBoundsBuilder = NamedSemanticRegions.builder(keyType);
                assert namesBuilder != null || ruleBoundsBuilder != null;
            }

            @Override
            public Void visitChildren(RuleNode node) {
                if (node instanceof ParserRuleContext) {
                    onVisit((ParserRuleContext) node);
                } else {
                    super.visitChildren(node);
                }
                return null;
            }

            private void onVisit(ParserRuleContext node) {
                boolean activationScratch[] = new boolean[nameExtractors.length];
                for (int i = 0; i < nameExtractors.length; i++) {
                    if (nameExtractors[i].ancestorQualifier != null) {
                        activationScratch[i] = nameExtractors[i].ancestorQualifier.test(node);
                        activations[i]++;
                    }
                }
                try {
                    for (int i = 0; i < nameExtractors.length; i++) {
                        if (activations[i] > 0) {
                            runOne(node, nameExtractors[i]);
                        }
                    }
                    super.visitChildren(node);
                } finally {
                    for (int i = 0; i < activationScratch.length; i++) {
                        if (activationScratch[i]) {
                            activations[i]--;
                        }
                    }
                }
            }

            private <R extends ParserRuleContext> void runOne(ParserRuleContext node, NameExtractorInfo<R, T> nameExtractor) {
                if (nameExtractor.type.isInstance(node)) {
                    doRunOne(nameExtractor.type.cast(node), nameExtractor);
                }
            }

            private <R extends ParserRuleContext> void doRunOne(R node, NameExtractorInfo<R, T> e) {
                e.find(node, (NamedRegionData<T> nm, TerminalNode tn) -> {
                    // If we are iterating TerminalNodes, tn will be non-null; otherwise
                    // it will be null and we are doing single extraction - this is so we can,
                    // for example, in an ANTLR grammar for ANTLR, create token names and
                    // references from an import tokens statement where there is no rule
                    // definition, but we should not point the definition position for all
                    // of the names to the same spot
                    if (nm != null) {
                        if (namesBuilder != null) {
                            // XXX, the names extractor actually needs to return the name AND the offsets of the name
                            // use the same code we use for finding the reference
                            namesBuilder.add(nm.string, nm.kind, nm.start, nm.end);
                        }
                        if (ruleBoundsBuilder != null) {
                            if (tn == null) {
                                ruleBoundsBuilder.add(nm.string, nm.kind, node.start.getStartIndex(), node.stop.getStopIndex() + 1);
                            } else {
                                Token tok = tn.getSymbol();
                                ruleBoundsBuilder.add(nm.string, nm.kind, tok.getStartIndex(), tok.getStopIndex() + 1);
                            }
                        }
                    }
                });
            }
        }
    }
}
