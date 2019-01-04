package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.ReferenceExtractionStrategy.ExtractorReturnType;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.NameReferenceSetKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.NamedRegionKey;
import org.nemesis.antlr.v4.netbeans.v8.util.reflection.ReflectionPath;
import org.nemesis.antlr.v4.netbeans.v8.util.reflection.ReflectionPath.ResolutionContext;

/**
 * Builder for extracting &quot;named regions&quot; during a parse. You supply:
 * <ul>
 * <li>A <b>key</b> the resulting names will be retrievd from an
 * {@link Extraction} with</li>
 * <li>One or more rule node classes which should trigger inspection</li>
 * <li>A function (or consumer) which accepts the node and returns a name or
 * null</li>
 * <li>Optionally, a second key which records the bounds of an outer node
 * containing the named item (for example, recording both the position of the
 * name of a method and the bounds of its signature and body)</li>
 * <li>Optional qualifying predicates to limit triggering to only when the rule
 * node matched is also a child of one or more particular types of ancestor rule
 * nodes</li>
 * <li>Optionally, you may also specify similar parameters for finding all
 * <i>references to</i> a named region (such as uses of a variable)</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public final class NamedRegionExtractorBuilder<T extends Enum<T>, Ret> {

    private final Class<T> keyType;
    private NamedRegionKey<T> namePositionKey;
    private NamedRegionKey<T> ruleRegionKey;
    private Set<NameExtractionStrategy<?, T>> nameExtractors = new HashSet<>();
    private final Set<ReferenceExtractorPair<T>> referenceExtractors = new HashSet<>();
    private final Function<NamesAndReferencesExtractionStrategy, Ret> buildFunction;
    private final ResolutionContext ctx = new ResolutionContext();

    NamedRegionExtractorBuilder(Class<T> keyType, Function<NamesAndReferencesExtractionStrategy, Ret> buildFunction) {
        this.keyType = keyType;
        this.buildFunction = buildFunction;
    }

    /**
     * Provide a key the data collected will be retrievable under from an
     * {@link Extraction} for <i>name positions</i>.
     *
     * @param key A key
     * @return a builder
     */
    public NamedRegionExtractorBuilderWithNameKey<T, Ret> recordingNamePositionUnder(NamedRegionKey<T> key) {
        assert keyType == key.type();
        namePositionKey = key;
        return new NamedRegionExtractorBuilderWithNameKey<>(this);
    }

    /**
     * Provide a key the data collected will be retrievable under from an
     * {@link Extraction} for <i>parent node of the name-containing node</i>.
     *
     * @param key A key
     * @return a builder
     */
    public NamedRegionExtractorBuilderWithRuleKey<T, Ret> recordingRuleRegionUnder(NamedRegionKey<T> key) {
        assert keyType == key.type();
        ruleRegionKey = key;
        return new NamedRegionExtractorBuilderWithRuleKey<>(this);
    }

    void addReferenceExtractorPair(ReferenceExtractorPair<T> pair) {
        this.referenceExtractors.add(pair);
    }

    Ret _build() {
        return buildFunction.apply(new NamesAndReferencesExtractionStrategy<>(keyType, namePositionKey, ruleRegionKey, nameExtractors, referenceExtractors));
    }

    public static final class NamedRegionExtractorBuilderWithNameKey<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        public NamedRegionExtractorBuilderWithNameKey(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        /**
         * Supply one rule type which, when encountered, should be examined for
         * potential name extraction.
         *
         * @param <R> The rule type
         * @param type The rule type
         * @return A sub-builder
         */
        public <R extends ParserRuleContext> NameExtractorBuilder<R, T, Ret> whereRuleIs(Class<R> type) {
            return new NameExtractorBuilder<>(bldr, type);
        }

        /**
         * Provide a key the data collected will be retrievable under from an
         * {@link Extraction} for <i>parent node of the name-containing
         * node</i>.
         *
         * @param key A key
         * @return a sub-builder
         */
        public NamedRegionExtractorBuilderWithBothKeys<T, Ret> recordingRulePositionUnder(NamedRegionKey<T> key) {
            assert bldr.keyType == key.type();
            bldr.ruleRegionKey = key;
            return new NamedRegionExtractorBuilderWithBothKeys<>(bldr);
        }
    }

    public static final class NamedRegionExtractorBuilderWithRuleKey<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        NamedRegionExtractorBuilderWithRuleKey(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        /**
         * Supply one rule type which, when encountered, should be examined for
         * potential name extraction.
         *
         * @param <R> The rule type
         * @param type The rule type
         * @return A sub-builder
         */
        public <R extends ParserRuleContext> NameExtractorBuilder<R, T, Ret> whereRuleIs(Class<R> type) {
            return new NameExtractorBuilder<>(bldr, type);
        }

        /**
         * Provide a key the data collected will be retrievable under from an
         * {@link Extraction} for <i>node representing the name</i>.
         *
         * @param key A key
         * @return a builder
         */
        public NamedRegionExtractorBuilderWithBothKeys<T, Ret> recordingNamePositionUnder(NamedRegionKey<T> key) {
            assert bldr.keyType == key.type();
            bldr.namePositionKey = key;
            return new NamedRegionExtractorBuilderWithBothKeys<>(bldr);
        }
    }

    public static final class NamedRegionExtractorBuilderWithBothKeys<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        NamedRegionExtractorBuilderWithBothKeys(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        /**
         * Supply one rule type which, when encountered, should be examined for
         * potential name extraction.
         *
         * @param <R> The rule type
         * @param type The rule type
         * @return A sub-builder
         */
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

        /**
         * Limit rule detection triggering to only when the current AST node is
         * a child of the passed ancestor tree node type.
         *
         * @param qualifyingAncestorType A rule node type which must be present
         * for this name extraction strategy to be used.
         *
         * @return this
         */
        public NameExtractorBuilder<R, T, Ret> whenInAncestorRule(Class<? extends RuleNode> qualifyingAncestorType) {
            return whenAncestorMatches(new QualifierPredicate(qualifyingAncestorType));
        }

        /**
         * Limit rule detection triggering to only when the current AST node
         * matches the passed predicate. Note: If Extractions are cached, it
         * is wise for the passed RuleNode to implement Hashable if it is
         * not a lambda to a static method.
         *
         * @param ancestorTest
         * @return this
         */
        public NameExtractorBuilder<R, T, Ret> whenAncestorMatches(Predicate<RuleNode> ancestorTest) {
            if (qualifiers == null) {
                qualifiers = ancestorTest;
            } else {
                qualifiers = qualifiers.or(ancestorTest);
            }
            return this;
        }

        /**
         * Pass a reflection path, such as <code>identifier().ID()</code>
         * which will be used at runtime to resolve the object which
         * supplies the name.  The object such a path returns must be a
         * ParserRuleContext, TerminalNode or Token or an exception will be
         * thrown at runtime.
         *
         * @param reflectionPath A reflection invocation path - methods (which must
         * take no arguments and return non-void) are referenced as a name followed by
         * parentheses; fields have no parentheses.  E.g. <code>someField.someMethod().otherMethod()</code>.
         * @param kind The enum kind to use for names found using this strategy
         * @see ReflectionPath
         * @return A builder
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameWith(String reflectionPath, T kind) {
            return derivingNameWith(func(kind, new ReflectionPath<Object>(reflectionPath, Object.class), bldr.ctx));
        }

        /**
         * Use the passed function to derive a name.  If it returns null, no name is recorded.
         *
         * @param extractor A function
         * @return A builder
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameWith(Function<R, NamedRegionData<T>> extractor) {
//            bldr.nameExtractors.add(new NameExtractionStrategy<R, T>(type, extractor, qualifiers);
            NameExtractionStrategy<R, T> info = new NameExtractionStrategy<>(type, extractor, qualifiers);
            bldr.nameExtractors.add(info);
            return new FinishableNamedRegionExtractorBuilder<>(bldr);
        }

        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameFromTokenWith(T kind, Function<R, Token> extractor) {
            return derivingNameWith(rule -> {
                Token tok = extractor.apply(rule);
                if (tok == null) {
                    return null;
                }
                return new NamedRegionData<>(tok.getText(), kind, tok.getStartIndex(), tok.getStopIndex() + 1);
            });
        }

        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameFromTerminalNodeWith(T kind, Function<R, TerminalNode> extractor) {
            return derivingNameWith(rule -> {
                TerminalNode tn = extractor.apply(rule);
                if (tn != null) {
                    Token tok = tn.getSymbol();
                    if (tok != null) {
                        return new NamedRegionData<>(tok.getText(), kind, tok.getStartIndex(), tok.getStopIndex() + 1);
                    }
                }
                return null;
            });
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
            NameExtractionStrategy<R, T> info = new NameExtractionStrategy<>(type, qualifiers, argType, extractor);
            bldr.nameExtractors.add(info);
            return new FinishableNamedRegionExtractorBuilder<>(bldr);
        }
    }

    public static final class FinishableNamedRegionExtractorBuilder<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        FinishableNamedRegionExtractorBuilder(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        /**
         * Add another strategy for derinving names which should be included under the
         * key passed earlier.
         *
         * @param <R> A rule type
         * @param type A rule type
         * @return A builder
         */
        public <R extends ParserRuleContext> NameExtractorBuilder<R, T, Ret> whereRuleIs(Class<R> type) {
            return new NameExtractorBuilder<>(bldr, type);
        }

        /**
         * Provide a key for collecting <i>references to</i> the names you are providing
         * strategies for collecting.
         *
         * @param refSetKey A key for looking up references in an {@link Extraction}.
         * @return A reference collection builder
         */
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
        private final Set<ReferenceExtractionStrategy<?, ?>> referenceExtractors = new HashSet<>();

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

        private NameReferenceCollectorBuilder<T, Ret> finish(ReferenceExtractionStrategy<R, T> info) {
            bldr.referenceExtractors.add(info);
            return bldr;
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsExplicitlyWith(Function<R, NameAndOffsets> offsetsExtractor) {
            return finish(new ReferenceExtractionStrategy<>(ruleType, offsetsExtractor, ExtractorReturnType.NAME_AND_OFFSETS, ancestorQualifier));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromRuleWith(Function<R, ParserRuleContext> offsetsExtractor) {
            return finish(new ReferenceExtractionStrategy<>(ruleType, offsetsExtractor, ExtractorReturnType.PARSER_RULE_CONTEXT, ancestorQualifier));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromTokenWith(Function<R, Token> offsetsExtractor) {
            return finish(new ReferenceExtractionStrategy<>(ruleType, offsetsExtractor, ExtractorReturnType.TOKEN, ancestorQualifier));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromTerminalNodeWith(Function<R, TerminalNode> offsetsExtractor) {
            return finish(new ReferenceExtractionStrategy<>(ruleType, offsetsExtractor, ExtractorReturnType.TERMINAL_NODE, ancestorQualifier));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsExplicitlyWith(T typeHint, Function<R, NameAndOffsets> offsetsExtractor) {
            return finish(new ReferenceExtractionStrategy<>(ruleType, offsetsExtractor, ExtractorReturnType.NAME_AND_OFFSETS, ancestorQualifier, typeHint));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromRuleWith(T typeHint, Function<R, ParserRuleContext> offsetsExtractor) {
            return finish(new ReferenceExtractionStrategy<>(ruleType, offsetsExtractor, ExtractorReturnType.PARSER_RULE_CONTEXT, ancestorQualifier, typeHint));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromTokenWith(T typeHint, Function<R, Token> offsetsExtractor) {
            return finish(new ReferenceExtractionStrategy<>(ruleType, offsetsExtractor, ExtractorReturnType.TOKEN, ancestorQualifier, typeHint));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromTerminalNodeWith(T typeHint, Function<R, TerminalNode> offsetsExtractor) {
            return finish(new ReferenceExtractionStrategy<>(ruleType, offsetsExtractor, ExtractorReturnType.TERMINAL_NODE, ancestorQualifier, typeHint));
        }
    }
}
