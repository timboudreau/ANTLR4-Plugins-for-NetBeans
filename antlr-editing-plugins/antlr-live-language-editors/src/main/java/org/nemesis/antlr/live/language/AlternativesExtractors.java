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
import com.mastfrog.util.collections.IntMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleAlternativeContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleDeclarationContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleIdentifierContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleLabeledAlternativeContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.extraction.ExtractionRegistration;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.localizers.annotations.Localize;

/**
 *
 * @author Tim Boudreau
 */
public class AlternativesExtractors {

    @Localize(displayName = "Outer Alternatives With Siblings")
    static final RegionsKey<AlternativeKey> OUTER_ALTERNATIVES_WITH_SIBLINGS
            = RegionsKey.create(AlternativeKey.class, "oalts");

    private static ThreadLocal<Map<ParserRuleAlternativeContext, AlternativeKey>> MAP_CACHE = new ThreadLocal<>();

    @ExtractionRegistration(mimeType = ANTLR_MIME_TYPE,
            entryPoint = ANTLRv4Parser.GrammarFileContext.class)
    public static void populateBuilder(ExtractorBuilder<? super ANTLRv4Parser.GrammarFileContext> bldr) {
        bldr.wrappingExtractionWith(runner -> {
            try {
                // Set up the cache before each extraction
                MAP_CACHE.set(new HashMap<>());
                return runner.get();
            } finally {
                // And tear it down
                MAP_CACHE.remove();
            }
        }).extractingRegionsUnder(OUTER_ALTERNATIVES_WITH_SIBLINGS).whenRuleType(ParserRuleAlternativeContext.class)
                .extractingBoundsFromRuleAndKeyWith((ParserRuleAlternativeContext alt) -> {
                    Map<ParserRuleAlternativeContext, AlternativeKey> info = MAP_CACHE.get();
                    assert info != null : "Wrapper not called";
                    if (info.isEmpty()) {
                        GrammarFileContext top = TreeUtils.ancestor(alt, ANTLRv4Parser.GrammarFileContext.class);
                        top.accept(new V(info));
                    }
                    return info.get(alt);
                }).finishRegionExtractor();
    }

    static class V extends ANTLRv4BaseVisitor<Map<ParserRuleAlternativeContext, AlternativeKey>> {

        private final Map<ParserRuleAlternativeContext, AlternativeKey> m;
        private int counter = -1;
        private String currentRuleName = null;
        private String currentLabel = null;
        private boolean inAlternative;

        public V(Map<ParserRuleAlternativeContext, AlternativeKey> m) {
            this.m = m;
        }

        @Override
        protected Map<ParserRuleAlternativeContext, AlternativeKey> defaultResult() {
            return m;
        }

        @Override
        public Map<ParserRuleAlternativeContext, AlternativeKey> visitParserRuleLabeledAlternative(ParserRuleLabeledAlternativeContext labeledAlt) {
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
        public Map<ParserRuleAlternativeContext, AlternativeKey> visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext spec) {
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
        public Map<ParserRuleAlternativeContext, AlternativeKey> visitParserRuleAlternative(ParserRuleAlternativeContext ctx) {
            if (counter < 0) { // broken source
                return super.visitParserRuleAlternative(ctx);
            }
            boolean wasInAlternative = inAlternative;
            if (wasInAlternative) {
                return super.visitParserRuleAlternative(ctx);
            }
            try {
                int currIndex = counter++;
                Map<ParserRuleAlternativeContext, AlternativeKey> info = MAP_CACHE.get();
                // if we're in a nested alternative, it can't have a label - only top level
                // ones do
                info.put(ctx, new AlternativeKey(currentRuleName, currIndex,
                        inAlternative ? null : currentLabel));
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
                result = Integer.compare(alternativeInParseSequence, o.alternativeInParseSequence);
            }
            return result;
        }
    }

    static Map<String, IntMap<AlternativeKey>> toKeys(Map<String, Set<AlternativeStub>> m) {
        Map<String, IntMap<AlternativeKey>> map = new HashMap<>(m.size());
        for (Map.Entry<String, Set<AlternativeStub>> e : m.entrySet()) {
            List<AlternativeStub> stubs = new ArrayList<>(e.getValue());
            Collections.sort(stubs);
            IntMap<AlternativeKey> result = IntMap.create(stubs.size());
            for (int i = 0; i < stubs.size(); i++) {
                AlternativeStub stub = stubs.get(i);
                result.put(i+1, new AlternativeKey(stub.ruleName, i, stub.label));
            }
            map.put(e.getKey(), result);
        }
        return map;
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
            return ruleName + ":" + alternativeIndex + (label.length() > 0 ? ":" + label : "");
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
