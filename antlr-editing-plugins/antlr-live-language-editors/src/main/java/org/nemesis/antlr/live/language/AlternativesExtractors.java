/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.live.language;

import com.mastfrog.antlr.utils.TreeUtils;
import com.mastfrog.function.state.Bool;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.strings.Strings;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleAlternativeContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleAtomContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleDeclarationContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleDefinitionContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleElementContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleIdentifierContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleLabeledAlternativeContext;
import org.nemesis.antlr.ANTLRv4Parser.TerminalContext;
import org.nemesis.antlr.ANTLRv4Parser.TokenRuleIdentifierContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.extraction.ExtractionRegistration;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.localizers.annotations.Localize;

/**
 * This little nightmare extracts <u>exactly</u> the same alternatives and
 * alternative numbers as Antlr's parser does, so the contents of the BitSet
 * from <code>ANTLRErrorListener.reportAmbiguity()</code> can be correctly
 * mapped back to the code region that is that alternative. Getting these out of
 * Antlr itself is difficult and error-prone, because anything containing
 * left-recursion gets its tree rewritten with garbage for token indices and
 * offsets; the tests for this check that we at least get the correct set of
 * alternatives in the same general places in the code.
 *
 * @author Tim Boudreau
 */
public class AlternativesExtractors {

    @Localize(displayName = "Outer Alternatives With Siblings")
    static final RegionsKey<AlternativeKey> OUTER_ALTERNATIVES_WITH_SIBLINGS
            = RegionsKey.create(AlternativeKey.class, "oalts");

    private static final ThreadLocal<Map<ParserRuleAlternativeContext, AlternativeKeyWithOffsets>> ALT_CACHE = new ThreadLocal<>();
    private static final ThreadLocal<Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets>> SET_CACHE = new ThreadLocal<>();
    private static final ThreadLocal<Bool> SCANNED = new ThreadLocal<>();

    @ExtractionRegistration(mimeType = ANTLR_MIME_TYPE,
            entryPoint = ANTLRv4Parser.GrammarFileContext.class)
    public static void populateBuilder(ExtractorBuilder<? super ANTLRv4Parser.GrammarFileContext> bldr) {
        // We need to do a real tree walk, not just capture items, so we set up some
        // threadlocals that will be set on the beginning of an extraction and cleared on
        // exit so nothing leaks;  on first invocation, we send some visitors off to chew
        // on the whole file and figure things out, then store the elements we want to return
        // in the cache mapped to the elements they correspond to
        bldr.wrappingExtractionWith(runner -> {
            try {
                // Set up the cache before each extraction
                ALT_CACHE.set(new HashMap<>(32));
                SET_CACHE.set(new HashMap<>(32));
                SCANNED.set(Bool.create());
                return runner.get();
            } finally {
                // And tear it down
                ALT_CACHE.remove();
                SET_CACHE.remove();
                SCANNED.remove();
            }
        }).extractingRegionsUnder(OUTER_ALTERNATIVES_WITH_SIBLINGS)
                .whenRuleType(ParserRuleDefinitionContext.class)
                .extractingKeyAndBoundsWith((ParserRuleDefinitionContext alt, BiPredicate<AlternativeKey, int[]> bip) -> {

                    Bool scanned = SCANNED.get();
                    assert scanned != null : "Wrapper not called";
                    Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets> altsForRules = SET_CACHE.get();
                    if (!scanned.getAsBoolean()) {
                        // if the first run, shoot off our visitors and collect
                        // what we need to know
                        try {
                            Map<ParserRuleAlternativeContext, AlternativeKeyWithOffsets> altsForAlts = ALT_CACHE.get();
                            assert altsForAlts != null : "Wrapper not called";
                            GrammarFileContext top = TreeUtils.ancestor(alt, ANTLRv4Parser.GrammarFileContext.class);
                            SetFinder setFinder = new SetFinder();
                            Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets> overarchingAlts = top.accept(setFinder);
                            AlternativeFinder v = new AlternativeFinder(overarchingAlts);
                            Map<ParserRuleAlternativeContext, AlternativeStub> keys = top.accept(v);
                            altsForAlts.putAll(toKeys(keys));
                            altsForRules.putAll(overarchingAlts);
                        } finally {
                            scanned.set();
                        }
                    }
                    // Get the item for this *entire* rule - our grammar treats
                    // as alternatives some elements that Antlr treats as a single
                    // unit - specifically, a rule which contains only or-separated
                    // token rules is a SET, not a collection of individual alternatives.
                    // So these get a single item for the entire rule, and nothing for
                    // any ParserRuleAlternativeContexts they contain
                    AlternativeKeyWithOffsets ak = altsForRules.get(alt);
                    if (ak != null) {
                        return bip.test(ak.key, new int[]{ak.start, ak.stop + 1});
                    }
                    return false;
                })
                .whenRuleType(ParserRuleAlternativeContext.class)
                .extractingKeyAndBoundsWith((ParserRuleAlternativeContext alt, BiPredicate<AlternativeKey, int[]> bip) -> {
                    // Get the item for an alternative down inside a rule
                    ParserRuleLabeledAlternativeContext lab = TreeUtils.ancestor(alt, ParserRuleLabeledAlternativeContext.class);
                    Map<ParserRuleAlternativeContext, AlternativeKeyWithOffsets> info = ALT_CACHE.get();
                    assert info != null : "Wrapper not called";
                    AlternativeKeyWithOffsets altern = info.get(alt);
                    if (altern != null) {
                        return bip.test(altern.key, new int[]{altern.start, altern.stop});
                    }
                    return false;
                }).finishRegionExtractor();
    }

    /**
     * Finds elements where the entire rule consists of a set of tokens - in
     * other wrods, 'SomeToken | OtherToken | OtherOtherToken' - our grammar
     * treats these as alternative nodes; Antlr's Tool calls them a SET and does
     * not break them out, and maps the rule definition element to the data to
     * put into the extraction for it.
     */
    private static class SetFinder extends ANTLRv4BaseVisitor<Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets>> {

        Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets> collectedResults = new HashMap<>();
        Set<Class<?>> currentContainedTypes = new HashSet<>();
        private static final Set<Class<?>> targetTypes
                = CollectionUtils.immutableSetOf(
                        ParserRuleDefinitionContext.class,
                        ParserRuleAlternativeContext.class,
                        TokenRuleIdentifierContext.class,
                        TerminalContext.class,
                        ParserRuleLabeledAlternativeContext.class, ParserRuleElementContext.class,
                        ParserRuleAtomContext.class);
        private boolean inDefinition;
        private int atomsSeen;
        private int terminalsSeen;
        private String currentRule;

        @Override
        public Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets> visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
            ParserRuleDeclarationContext decl = ctx.parserRuleDeclaration();
            if (decl != null && decl.parserRuleIdentifier() != null) {
                currentRule = decl.parserRuleIdentifier().getText();
            }
            try {
                return super.visitParserRuleSpec(ctx);
            } finally {
                currentRule = null;
            }
        }

        @Override
        public Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets> visitTerminal(TerminalContext ctx) {
            terminalsSeen++;
            return super.visitTerminal(ctx);
        }

        private String setToString(Set<Class<?>> c) {
            return Strings.join(',', c, Class::getSimpleName);
        }

        @Override
        public Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets> visitParserRuleDefinition(ParserRuleDefinitionContext ctx) {
            inDefinition = true;
            currentContainedTypes.clear();
            atomsSeen = 0;
            try {
                return super.visitParserRuleDefinition(ctx);
            } finally {
                inDefinition = false;
                switch (currentRule) {
                    case "intrinsic_type":
                    case "signed_int_subtype":
                    case "unsigned_int_subtype":
//                        System.out.println("RULE " + currentRule
//                                + " atoms seen " + atomsSeen + " seen types \n" + setToString(CollectionUtils.immutableSet(currentContainedTypes))
//                                + " expecting \n" + setToString(targetTypes) + " match? " + targetTypes.equals(currentContainedTypes) + "\n\n");

                }
                // If all of the elements were single terminal nodes we are a go
                if (atomsSeen > 0 && terminalsSeen >= atomsSeen && currentRule != null
                        && targetTypes.equals(currentContainedTypes)) {
                    AlternativeKeyWithOffsets kwo = new AlternativeKeyWithOffsets(new AlternativeKey(currentRule, 1, null), ctx.getStart().getStartIndex(),
                            ctx.getStop().getStopIndex());
                    collectedResults.put(ctx, kwo);
                }
            }
        }

        @Override
        public Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets> visitParserRuleAtom(ParserRuleAtomContext ctx) {
            atomsSeen++;
            return super.visitParserRuleAtom(ctx);
        }

        @Override
        protected Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets> defaultResult() {
            return collectedResults;
        }

        @Override
        public Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets> visitChildren(RuleNode node) {
            if (inDefinition) {
                currentContainedTypes.add(node.getClass());
            }
            return super.visitChildren(node);
        }
    }

    /**
     * Finds alternatives within rules that do not have an overarching
     * alternative already set, and creates the data that will be fed into the
     * extraction for that case.
     */
    static class AlternativeFinder extends ANTLRv4BaseVisitor<Map<ParserRuleAlternativeContext, AlternativeStub>> {

        private final Map<ParserRuleAlternativeContext, AlternativeStub> m = new HashMap<>();
        private int counter = -1;
        private String currentRuleName = null;
        private String currentLabel = null;
        private boolean inAlternative;
        private final Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets> definitionResults;

        public AlternativeFinder(Map<ParserRuleDefinitionContext, AlternativeKeyWithOffsets> definitionResults) {
            this.definitionResults = definitionResults;
        }

        @Override
        protected Map<ParserRuleAlternativeContext, AlternativeStub> defaultResult() {
            return m;
        }

        @Override
        public Map<ParserRuleAlternativeContext, AlternativeStub> visitParserRuleDefinition(ParserRuleDefinitionContext ctx) {
            if (definitionResults.containsKey(ctx)) {
                // Don't traverse if we have an overarching alt that spans the
                // whole rule
                return defaultResult();
            }
            return super.visitParserRuleDefinition(ctx);
        }

        @Override
        public Map<ParserRuleAlternativeContext, AlternativeStub> visitParserRuleLabeledAlternative(ParserRuleLabeledAlternativeContext labeledAlt) {
            if (labeledAlt.identifier() != null) {
                currentLabel = labeledAlt.identifier().getText();
                try {
                    return super.visitParserRuleLabeledAlternative(labeledAlt);
                } finally {
                    currentLabel = null;
                }
            }
            return super.visitParserRuleLabeledAlternative(labeledAlt);
        }

        @Override
        public Map<ParserRuleAlternativeContext, AlternativeStub> visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext spec) {
            ParserRuleDeclarationContext decl = spec.parserRuleDeclaration();
            if (decl != null) {
                if (decl != null) {
                    ParserRuleIdentifierContext id = decl.parserRuleIdentifier();
                    currentRuleName = id.getText();
                    counter = 1;
                    try {
                        return super.visitParserRuleSpec(spec);
                    } finally {
                        currentRuleName = null;
                        counter = -1;
                    }
                }
            }
            return super.visitParserRuleSpec(spec);
        }

        @Override
        public Map<ParserRuleAlternativeContext, AlternativeStub> visitParserRuleAlternative(ParserRuleAlternativeContext ctx) {
            if (counter < 0) { // broken source
                return super.visitParserRuleAlternative(ctx);
            }
            boolean wasInAlternative = inAlternative;
            if (wasInAlternative) {
                return super.visitParserRuleAlternative(ctx);
            }
            try {
                int currIndex = counter++;
                // if we're in a nested alternative, it can't have a label - only top level
                // ones do
                int start = ctx.getStart().getStartIndex();
                int stop = ctx.getStop().getStopIndex();
                if (ctx.getParent() instanceof ParserRuleLabeledAlternativeContext) {
                    ParserRuleLabeledAlternativeContext lab = (ParserRuleLabeledAlternativeContext) ctx.getParent();
                    if (lab.identifier() != null) {
                        stop = lab.identifier().getStop().getStopIndex();
                    }
                }
                if (!m.containsKey(ctx)) {
                    m.put(ctx, new AlternativeStub(start, stop + 1, currentRuleName, currIndex,
                            inAlternative ? null : currentLabel));
                }
                inAlternative = true;
                return super.visitParserRuleAlternative(ctx);
            } finally {
                inAlternative = wasInAlternative;
            }
        }
    }

    static final class AlternativeStub implements Comparable<AlternativeStub> {

        public final int startTokenIndex;
        public final int stopTokenIndex;
        public final String ruleName;
        public final String label;
        public final int alternativeInParseSequence;

        public AlternativeStub(int startTokenIndex, int stopTokenIndex, String ruleName, int alternativeInParseSequence, String label) {
            this.startTokenIndex = startTokenIndex;
            this.stopTokenIndex = stopTokenIndex;
            this.ruleName = ruleName;
            this.alternativeInParseSequence = alternativeInParseSequence;
            this.label = label;
        }

        @Override
        public int compareTo(AlternativeStub o) {
            int result = Integer.compare(startTokenIndex, o.startTokenIndex);
            if (result == 0) {
                result = -Integer.compare(stopTokenIndex, o.stopTokenIndex);
            }
            if (result == 0) {
                result = Integer.compare(alternativeInParseSequence, o.alternativeInParseSequence);
            }
            return result;
        }

        AlternativeKey toKey(int index) {
            return new AlternativeKey(ruleName, index, label == null ? Integer.toString(index) : label);
        }
    }

    static Map<ParserRuleAlternativeContext, AlternativeKeyWithOffsets> toKeys(Map<ParserRuleAlternativeContext, AlternativeStub> m) {
        // This gets us the alternatives sorted in lexical order
        Map<String, Set<AlternativeStub>> map = CollectionUtils.supplierMap(TreeSet::new);
        Map<AlternativeStub, ParserRuleAlternativeContext> inverse = new HashMap<>();
        for (Map.Entry<ParserRuleAlternativeContext, AlternativeStub> e : m.entrySet()) {
            map.get(e.getValue().ruleName).add(e.getValue());
            inverse.put(e.getValue(), e.getKey());
        }
        Map<ParserRuleAlternativeContext, AlternativeKeyWithOffsets> result = new HashMap<>(m.size());
        for (Map.Entry<String, Set<AlternativeStub>> e : map.entrySet()) {
            int cursor = 1;
            for (AlternativeStub stub : e.getValue()) {
                ParserRuleAlternativeContext ctx = inverse.get(stub);
                AlternativeKey key = stub.toKey(cursor++);
                AlternativeKeyWithOffsets withOffsets = new AlternativeKeyWithOffsets(key, stub.startTokenIndex, stub.stopTokenIndex);
                result.put(ctx, withOffsets);
            }
        }
        return result;
    }

    static final class AlternativeKeyWithOffsets {

        private final AlternativeKey key;
        private final int start;
        private final int stop;

        public AlternativeKeyWithOffsets(AlternativeKey key, int start, int stop) {
            this.key = key;
            this.start = start;
            this.stop = stop;
        }
    }

    static final class AlternativeKey implements Comparable<AlternativeKey> {

        private final String ruleName;
        private final short alternativeIndex;
        private final String label;

        AlternativeKey(String ruleName, int alternativeIndex, String label) {
            this.ruleName = ruleName;
            this.alternativeIndex = (short) alternativeIndex;
            this.label = label == null ? Integer.toString(alternativeIndex) : label;
        }

        public String rule() {
            return ruleName;
        }

        public int alternativeIndex() {
            return alternativeIndex;
        }

        public String label() {
            return label.isEmpty() ? Integer.toString(alternativeIndex) : label;
        }

        @Override
        public int hashCode() {
            return ruleName.hashCode() * 101 * alternativeIndex;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final AlternativeKey other = (AlternativeKey) obj;
            if (this.alternativeIndex != other.alternativeIndex) {
                return false;
            }
            return Objects.equals(this.ruleName, other.ruleName);
        }

        @Override
        public String toString() {
            String result = ruleName + ":" + alternativeIndex;
            if (label.length() > 0 && !label.equals(Integer.toString(alternativeIndex))) {
                result += "(" + label + ")";
            }
            return result;
        }

        @Override
        public int compareTo(AlternativeKey o) {
            int result = ruleName.compareTo(o.ruleName);
            if (result == 0) {
                result = Short.compare(alternativeIndex, o.alternativeIndex);
            }
            return result;
        }
    }
}
