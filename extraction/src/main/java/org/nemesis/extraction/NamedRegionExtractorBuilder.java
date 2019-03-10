package org.nemesis.extraction;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.data.Hashable;
import org.nemesis.extraction.ReferenceExtractionStrategy.ExtractorReturnType;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.misc.utils.reflection.ReflectionPath;
import org.nemesis.misc.utils.reflection.ReflectionPath.ResolutionContext;

/**
 * Builder for extracting &quot;named regions&quot; during a parse. You supply:
 * <ul>
 * <li>A <b>key</b> the resulting names will be retrievd from an
 * {@link Extraction} with, when using extraction results</li>
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
 * <i>references to</i> a named region (such as uses of a variable)</li> - i.e.
 * if one kind of rule <b>defines</b> names (such as a rule detecting Java&trade;
 * methods) and another rule defines possible <b>calls</b> to those methods,
 * you can scan for both here, and have your extraction contain keys for both.
 * </ul>
 *
 * @author Tim Boudreau
 */
public final class NamedRegionExtractorBuilder<T extends Enum<T>, Ret> {

    private final Class<T> keyType;
    private NamedRegionKey<T> namePositionKey;
    private NamedRegionKey<T> ruleRegionKey;
    private final Set<NameExtractionStrategy<?, T>> nameExtractors = new HashSet<>();
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
        return buildFunction.apply(new NamesAndReferencesExtractionStrategy<>(keyType, namePositionKey, ruleRegionKey, nameExtractors, referenceExtractors, null));
    }

    private Ret setScopingDelimiter(String delim) {
        return buildFunction.apply(new NamesAndReferencesExtractionStrategy<>(keyType, namePositionKey, ruleRegionKey, nameExtractors, referenceExtractors, delim));
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
        return new ReflectionPathFunction<>(ctx, path, kind);
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
         * matches the passed predicate. Note: If Extractions are cached, it is
         * wise for the passed RuleNode to implement Hashable if it is not a
         * lambda to a static method.
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
         * Pass a reflection path, such as <code>identifier().ID()</code> which
         * will be used at runtime to resolve the object which supplies the
         * name. The object such a path returns must be a ParserRuleContext,
         * TerminalNode or Token or an exception will be thrown at runtime.
         *
         * @param reflectionPath A reflection invocation path - methods (which
         * must take no arguments and return non-void) are referenced as a name
         * followed by parentheses; fields have no parentheses. E.g.
         * <code>someField.someMethod().otherMethod()</code>.
         * @param kind The enum kind to use for names found using this strategy
         * @see ReflectionPath
         * @return A builder
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameWith(String reflectionPath, T kind) {
            return derivingNameWith(func(kind, new ReflectionPath<>(reflectionPath, Object.class), bldr.ctx));
        }

        /**
         * Use the passed function to derive a name. If it returns null, no name
         * is recorded.
         *
         * @param extractor A function
         * @return A builder
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameWith(Function<R, NamedRegionData<T>> extractor) {
            NameExtractionStrategy<R, T> info = new NameExtractionStrategy<>(type, extractor, qualifiers);
            bldr.nameExtractors.add(info);
            return new FinishableNamedRegionExtractorBuilder<>(bldr);
        }

        /**
         * Extract one or more names out of the passed rule by calling the
         * {@link NameRegionConsumer} that will be passed to the callback.
         *
         * @param cons A consumer
         * @return a builder
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameWith(BiConsumer<R, NameRegionConsumer<T>> cons) {
            Function<R, NamedRegionData<T>> res = new BiConsumerFunction(cons);
            return derivingNameWith(res);
        }

        /**
         * Extract one or more names out of the passed rule by calling the
         * consumer that will be passed to the callback, deriving the name and
         * bounds from the token you find in the rule.
         *
         * @param cons A consumer
         * @return a builder
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameFromTokenWith(BiConsumer<R, BiConsumer<T, Token>> cons) {
            return derivingNameWith(new TokenAndKindConvertFunction<>(cons));
        }

        /**
         * Extract one or more named regions from tokens by passing them to the
         * BiConsumer passed to the callback.
         *
         * @param kind The kind all extracted names should have
         * @param cons The consumer that will receive the rule and provide
         * tokens to use for adding named regions.
         * @return a builder
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameFromTokenWith(T kind, BiConsumer<R, Consumer<Token>> cons) {
            return derivingNameWith(new TokenConvertFunction<>(kind, cons));
        }

        /**
         * Extract one or more named regions from tokens using the passed
         * function to locate a token in the rule.
         *
         * @param kind The kind all extracted names should have
         * @param cons a function that takes a rule and returns a token that
         * should be used
         * @return a builder
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameFromTokenWith(T kind, Function<R, Token> extractor) {
            return derivingNameWith(rule -> {
                Token tok = extractor.apply(rule);
                if (tok == null) {
                    return null;
                }
                return new NamedRegionData<>(tok.getText(), kind, tok.getStartIndex(), tok.getStopIndex() + 1);
            });
        }

        /**
         * Extract one or more named regions from terminal nodes using the
         * passed function to locate a terminal node in the rule.
         *
         * @param kind The kind all extracted names should have
         * @param cons a function that takes a rule and returns a token that
         * should be used
         * @return a builder
         */
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
         * Extract one or more named regions from terminal nodes by calling the
         * passed consumer with each terminal node.
         *
         * @param kind The kind all extracted names should have
         * @param cons a callback for adding terminal nodes as named region. The
         * terminal node's <code>getSymbol().getText()</code> will be used to
         * derive the name
         * @return a builder
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameFromTerminalNodeWith(T kind, BiConsumer<R, Consumer<TerminalNode>> cons) {
            return derivingNameWith(new TerminalNodeConvertFunction<>(kind, cons));
        }

        /**
         * Extract one or more named regions from terminal nodes by calling the
         * passed consumer with each terminal node and the type that node should
         * get.
         *
         * @param kind The kind all extracted names should have
         * @param cons a callback for adding terminal nodes as named region. The
         * terminal node's <code>getSymbol().getText()</code> will be used to
         * derive the name
         * @return a builder
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameFromTerminalNodeWith(BiConsumer<R, BiConsumer<T, TerminalNode>> cons) {
            return derivingNameWith(new TerminalNodeAndKindConvertFunction<>(cons));
        }

        /**
         * Derive names from a terminal node list, which some kinds of rule can
         * return if there is no parser rule for the name definition itself. In
         * this case, the offset for both the name and bounds will be set to the
         * bounds of the terminal node, and the terminal node's
         * getSymbol().getText() value is the name.
         *
         * @param argType The type to use for all returned names
         * @param extractor A consumer which takes a consumer that can be passed
         * the relevant list of terminal nodes and will create named regions for
         * all of them using the terminal node's token's text.
         * @return a builder which can be finished
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameFromTerminalNodes(T argType, BiConsumer<R, Consumer<List<? extends TerminalNode>>> extractor) {
            Function<R, List<? extends TerminalNode>> f = new Function<R, List<? extends TerminalNode>>() {
                @Override
                public List<? extends TerminalNode> apply(R t) {
                    Ref<List<? extends TerminalNode>> ref = new Ref<>();
                    extractor.accept(t, ref);
                    if (ref.get() != null) {
                        return ref.get();
                    }
                    return Collections.emptyList();
                }
            };
            return derivingNameFromTerminalNodes(argType, f);
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

    // Some would-be lambda implementations so they can implement Hashable
    // correctly
    private static final class BiConsumerFunction<T extends Enum<T>, R> implements Function<R, NamedRegionData<T>>, Hashable {

        private final BiConsumer<R, NameRegionConsumer<T>> cons;

        BiConsumerFunction(BiConsumer<R, NameRegionConsumer<T>> cons) {
            this.cons = cons;
        }

        @Override
        public NamedRegionData<T> apply(R r) {
            Ref<NamedRegionData<T>> ref = new Ref<>();
            cons.accept(r, (NameRegionConsumer<T>) (int start, int end, String name, T kind) -> {
                ref.set(new NamedRegionData<>(name, kind, start, end));
            });
            return ref.get();
        }

        @Override
        public void hashInto(Hashable.Hasher hasher) {
            hasher.hashObject(cons);
        }

    }

    static final class TokenConvertFunction<R, T extends Enum<T>> implements Function<R, NamedRegionData<T>>, Hashable {

        private final T kind;
        private final BiConsumer<R, Consumer<Token>> cons;

        TokenConvertFunction(T kind, BiConsumer<R, Consumer<Token>> cons) {
            this.kind = kind;
            this.cons = cons;
        }

        public String toString() {
            return "TokenConvert<" + kind + "->" + cons + ">";
        }

        @Override
        public NamedRegionData<T> apply(R t) {
            Ref<Token> ref = new Ref<>();
            cons.accept(t, ref);
            Token tok = ref.get();
            if (tok != null) {
                String name = tok.getText();
                return new NamedRegionData<>(name, kind, tok.getStartIndex(), tok.getStopIndex() + 1);
            }
            return null;
        }

        @Override
        public void hashInto(Hashable.Hasher hasher) {
            hasher.writeInt(kind.ordinal());
            hasher.hashObject(cons);
        }
    }

    static final class TokenAndKindConvertFunction<R, T extends Enum<T>> implements Function<R, NamedRegionData<T>>, Hashable {

        private final BiConsumer<R, BiConsumer<T, Token>> cons;

        TokenAndKindConvertFunction(BiConsumer<R, BiConsumer<T, Token>> cons) {
            this.cons = cons;
        }

        @Override
        public NamedRegionData<T> apply(R t) {
            BiRef<T, Token> ref = new BiRef<>();
            cons.accept(t, ref);
            Token tok = ref.kind();
            T kind = ref.get();
            if (tok != null) {
                String name = tok.getText();
                return new NamedRegionData<>(name, kind, tok.getStartIndex(), tok.getStopIndex() + 1);
            }
            return null;
        }

        @Override
        public void hashInto(Hashable.Hasher hasher) {
            hasher.hashObject(cons);
        }
    }

    static final class TerminalNodeAndKindConvertFunction<R, T extends Enum<T>> implements Function<R, NamedRegionData<T>>, Hashable {

        private final BiConsumer<R, BiConsumer<T, TerminalNode>> cons;

        TerminalNodeAndKindConvertFunction(BiConsumer<R, BiConsumer<T, TerminalNode>> cons) {
            this.cons = cons;
        }

        @Override
        public NamedRegionData<T> apply(R t) {
            BiRef<T, TerminalNode> ref = new BiRef<>();
            cons.accept(t, ref);
            TerminalNode nd = ref.kind();
            T kind = ref.get();
            if (nd != null) {
                Token tok = nd.getSymbol();
                if (tok != null) {
                    String name = tok.getText();
                    return new NamedRegionData<>(name, kind, tok.getStartIndex(), tok.getStopIndex() + 1);
                }
            }
            return null;
        }

        @Override
        public void hashInto(Hashable.Hasher hasher) {
            hasher.hashObject(cons);
        }
    }

    static final class TerminalNodeConvertFunction<R, T extends Enum<T>> implements Function<R, NamedRegionData<T>>, Hashable {

        private final T kind;
        private final BiConsumer<R, Consumer<TerminalNode>> cons;

        TerminalNodeConvertFunction(T kind, BiConsumer<R, Consumer<TerminalNode>> cons) {
            this.kind = kind;
            this.cons = cons;
        }

        @Override
        public NamedRegionData<T> apply(R t) {
            Ref<TerminalNode> ref = new Ref<>();
            cons.accept(t, ref);
            TerminalNode nd = ref.get();
            if (nd != null) {
                Token tok = nd.getSymbol();
                if (tok != null) {
                    String name = tok.getText();
                    return new NamedRegionData<>(name, kind, tok.getStartIndex(), tok.getStopIndex() + 1);
                }
            }
            return null;
        }

        @Override
        public void hashInto(Hashable.Hasher hasher) {
            hasher.writeInt(kind.ordinal());
            hasher.hashObject(cons);
        }
    }

    /**
     * Builder for named regions which has at least one extraction strategy and
     * so can be completed.
     *
     * @param <T> The kind type for names
     * @param <Ret> The type returned by the finish method
     */
    public static final class FinishableNamedRegionExtractorBuilder<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        FinishableNamedRegionExtractorBuilder(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        /**
         * Add another strategy for derinving names which should be included
         * under the key passed earlier.
         *
         * @param <R> A rule type
         * @param type A rule type
         * @return A builder
         */
        public <R extends ParserRuleContext> NameExtractorBuilder<R, T, Ret> whereRuleIs(Class<R> type) {
            return new NameExtractorBuilder<>(bldr, type);
        }

        /**
         * Provide a key for collecting <i>references to</i> the names you are
         * providing strategies for collecting.
         *
         * @param refSetKey A key for looking up references in an
         * {@link Extraction}.
         * @return A reference collection builder
         */
        public NameReferenceCollectorBuilder<T, Ret> collectingReferencesUnder(NameReferenceSetKey<T> refSetKey) {
            return new NameReferenceCollectorBuilder<>(this, refSetKey);
        }

        FinishableNamedRegionExtractorBuilder<T, Ret> addReferenceExtractorPair(ReferenceExtractorPair<T> pair) {
            bldr.addReferenceExtractorPair(pair);
            return this;
        }

        /**
         * Build this region extractor, and specifying that nested names
         * will be scoped with the name they occur inside, with the
         * parent names separated by the passed delimiter.  This results
         * in names being derived where, if you had the structure
         * <pre>
         * foo
         *   bar
         *   baz
         *      foo
         * </pre>
         *
         * you would get the names
         * <pre>
         * foo
         * foo.bar
         * foo.baz
         * foo.baz.foo
         * </pre>
         *
         * which makes it possible to have nested names, rather than detecting
         * one occurrence of <code>foo</code> and one duplicate name.  Region
         * detection will then expect names to be similarly scoped when they
         * are encountered in the source, so a detected reference to "bar"
         * would be an unknown reference, but a reference to "foo.bar" would
         * not.
         * <p>
         * It should also be possible to go the other direction (e.g., mapping
         * a raw class name to a Java&trade; import statement), but this is not yet
         * implemented.
         * </p>
         * <p>
         * If you are using the <code>antlr-navigators</code> project to
         * generate navigator panels, the default <code>Appearance</code>
         * will strip the prefix of a name, and indent by the number of
         * delimiters found, to create the appearance of nesting without
         * lengthy names.
         * </p>
         *
         * @param delim A string delimiter.
         * @return
         */
        public Ret scopingNestedNamesDelimitedBy(String delim) {
            return bldr.setScopingDelimiter(delim);
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

    private static final class ReflectionPathFunction<R extends ParserRuleContext, T extends Enum<T>>
            implements Function<R, NamedRegionData<T>>, Hashable {

        private final ResolutionContext ctx;
        private final ReflectionPath<?> path;
        private final T kind;

        ReflectionPathFunction(ResolutionContext ctx, ReflectionPath<?> path, T kind) {
            this.ctx = ctx;
            this.path = path;
            this.kind = kind;
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeInt(kind.ordinal());
            hasher.writeString(path.path());
        }

        @Override
        public NamedRegionData<T> apply(R rule) {
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
                return new NamedRegionData<>(prc.getText(), kind, prc.getStart().getStartIndex(), prc.getStop().getStopIndex() + 1);
            } else if (result instanceof TerminalNode) {
                TerminalNode tn = (TerminalNode) result;
                return new NamedRegionData<>(tn.getText(), kind, tn.getSymbol().getStartIndex(), tn.getSymbol().getStopIndex() + 1);
            } else if (result instanceof Token) {
                Token tok = (Token) result;
                return new NamedRegionData<>(tok.getText(), kind, tok.getStartIndex(), tok.getStopIndex() + 1);
            } else {
                throw new IllegalStateException("Don't know how to convert " + result.getClass().getName()
                        + " to a NamedRegionData");
            }
        }
    }

    // Convenience classes for having a variable set within a lambda
    static final class Ref<T> implements Consumer<T> {

        private T value;

        public void set(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        @Override
        public void accept(T t) {
            set(t);
        }
    }

    static final class BiRef<T, R> implements BiConsumer<T, R> {

        private T value;
        private R kind;

        public void set(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        R kind() {
            return kind;
        }

        @Override
        public void accept(T t, R kind) {
            set(t);
            this.kind = kind;
        }
    }
}
