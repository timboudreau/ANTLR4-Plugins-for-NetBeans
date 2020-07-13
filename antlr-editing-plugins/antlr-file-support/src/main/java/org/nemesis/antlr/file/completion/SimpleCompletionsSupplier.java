/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.file.completion;

import com.mastfrog.antlr.code.completion.spi.CaretToken;
import com.mastfrog.antlr.code.completion.spi.Completer;
import com.mastfrog.antlr.code.completion.spi.CompletionItems;
import com.mastfrog.antlr.code.completion.spi.CompletionsSupplier;
import com.mastfrog.util.collections.IntList;
import com.mastfrog.util.strings.LevenshteinDistance;
import com.mastfrog.util.strings.Strings;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_lexComMode;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_lexComPushMode;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.GrammarType;
import org.nemesis.antlr.common.extractiontypes.LexerModes;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.file.impl.GrammarDeclaration;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = CompletionsSupplier.class, position = 1)
public class SimpleCompletionsSupplier extends CompletionsSupplier {

    @Override
    public Completer forDocument(Document document) {
        if (ANTLR_MIME_TYPE.equals(NbEditorUtilities.getMimeType(document))) {
            return AntlrCompleter.find(document);
        }
        return noop();
    }

    @SuppressWarnings("unchecked")
    static Extraction extractionFor(Document doc) {
        WeakReference<Extraction> cached = (WeakReference<Extraction>) doc.getProperty("_ext");
        Extraction result = null;
        if (cached != null) {
            result = cached.get();
            if (result != null) {
                if (!result.isSourceProbablyModifiedSinceCreation()) {
                    return result;
                }
            }
        }
        try {
            result = NbAntlrUtils.parseImmediately(doc);
            if (result != null) {
                doc.putProperty("_ext", new WeakReference<>(result));
            }

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        return result;
    }

    static class AntlrCompleter implements Completer {

        private final Document doc;

        public AntlrCompleter(Document doc) {
            this.doc = doc;
        }

        static AntlrCompleter find(Document doc) {
            AntlrCompleter result = (AntlrCompleter) doc.getProperty(AntlrCompleter.class);
            if (result == null) {
                System.out.println("CREATE AN ANTLR COMPLETER");
                result = new AntlrCompleter(doc);
                doc.putProperty(AntlrCompleter.class, result);
            }
            return result;
        }

        protected void collectSetsOfRegions(int parserRuleId, Extraction ext, Set<NamedSemanticRegions<RuleTypes>> use, Set<RuleTypes> types) {
            SingletonEncounters<GrammarDeclaration> grammarType = ext.singletons(AntlrKeys.GRAMMAR_TYPE);
            SingletonEncounters.SingletonEncounter<GrammarDeclaration> gd = grammarType.first();
            GrammarType type = GrammarType.COMBINED;
            if (gd != null) {
                type = gd.get().type();
            }
            switch (parserRuleId) {
                case ANTLRv4Parser.RULE_block:
                    use.add(ext.namedRegions(AntlrKeys.RULE_NAMES));
                    switch (type) {
                        case COMBINED:
                            types.add(RuleTypes.PARSER);
                            types.add(RuleTypes.LEXER);
                            types.add(RuleTypes.FRAGMENT);
                            break;
                        case LEXER:
                            types.add(RuleTypes.LEXER);
                            types.add(RuleTypes.FRAGMENT);
                            break;
                        case PARSER:
                            types.add(RuleTypes.LEXER);
                            types.add(RuleTypes.PARSER);
                            break;
                    }
//                case ANTLRv4Parser.RULE_labeledParserRuleElement:
                case ANTLRv4Parser.RULE_parserRuleAtom:
                    use.add(ext.namedRegions(AntlrKeys.RULE_NAMES));
                    types.add(RuleTypes.PARSER);
                    types.add(RuleTypes.LEXER);
                    break;
                case ANTLRv4Parser.RULE_lexerRuleAtom:
                case ANTLRv4Parser.RULE_lexerRuleElement:
                case ANTLRv4Parser.RULE_lexerRuleElementBlock:
                case ANTLRv4Parser.RULE_tokenRuleIdentifier:
                    types.add(RuleTypes.LEXER);
                    types.add(RuleTypes.FRAGMENT);
                    use.add(ext.namedRegions(AntlrKeys.RULE_NAMES));
                    break;
                case ANTLRv4Parser.RULE_fragmentRuleIdentifier:
                    types.add(RuleTypes.FRAGMENT);
                    use.add(ext.namedRegions(AntlrKeys.RULE_NAMES));

            }
        }

        private String nullBlanks(String txt) {
            return Strings.isBlank(txt) ? null : txt;
        }

        @Override
        public void apply(int parserRuleId, CaretToken token, int maxResultsPerKey, IntList rulePath, CompletionItems addTo) throws Exception {
            String optionalPrefix = nullBlanks(token.leadingTokenText());
            String optionalSuffix = nullBlanks(token.trailingTokenText());
            System.out.println("NAMES FOR RULE " + ANTLRv4Parser.ruleNames[parserRuleId]
                    + " pfx '" + optionalPrefix + "' sfx '" + optionalSuffix + "'");
            findNames(parserRuleId, nullBlanks(optionalPrefix), maxResultsPerKey,
                    nullBlanks(optionalSuffix), rulePath, addTo);

        }


        private void recursivelyFindRegionSets(int parserRuleId, Extraction ext, Set<NamedSemanticRegions<RuleTypes>> use, Set<RuleTypes> types) {
            recursivelyFindRegionSets(parserRuleId, ext, new HashSet<>(), use, types);
        }

        private void recursivelyFindRegionSets(int parserRuleId, Extraction ext, Set<GrammarSource<?>> seen, Set<NamedSemanticRegions<RuleTypes>> use, Set<RuleTypes> types) {
            if (seen.contains(ext.source())) {
                return;
            }
            seen.add(ext.source());
            collectSetsOfRegions(parserRuleId, ext, use, types);
            Attributions<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolved = ext.resolveAll(AntlrKeys.RULE_NAME_REFERENCES);
            for (Map.Entry<GrammarSource<?>, Extraction> e : resolved.dependencies().entrySet()) {
                if (!e.getKey().equals(ext.source()) && !seen.contains(e.getKey())) {
                    recursivelyFindRegionSets(parserRuleId, e.getValue(), seen, use, types);
                }
            }
        }

        public void findNamesForModes(int parserRuleId, String optionalPrefix, int maxResultsPerKey,
                String optionalSuffix, IntList rulePath, Extraction ext, CompletionItems names) {
            if (optionalPrefix != null && optionalPrefix.trim().equals("(")) {
                optionalPrefix = null;
            }
            if (optionalSuffix != null && optionalSuffix.trim().equals("(")) {
                optionalSuffix = null;
            }
            NamedSemanticRegions<LexerModes> regions = ext.namedRegions(AntlrKeys.MODES);
            Consumer<NamedSemanticRegion<LexerModes>> c = mode -> {
                names.add(mode.name(), mode.kind());
            };
            if (optionalPrefix != null) {
                regions.matchingPrefix(optionalPrefix, c);
            } else if (optionalSuffix != null && !regions.contains(optionalSuffix)) {
                regions.matchingSuffix(optionalSuffix, c);
            } else {
                regions.forEach(c);
            }
        }

        public void findNames(int parserRuleId, String optionalPrefix, int maxResultsPerKey,
                String optionalSuffix, IntList rulePath, CompletionItems names) {
            Extraction ext = extractionFor(doc);
            if (ext != null) {
                switch (parserRuleId) {
                    case RULE_lexComPushMode:
                    case RULE_lexComMode:
                        findNamesForModes(parserRuleId, optionalPrefix, maxResultsPerKey, optionalSuffix, rulePath, ext, names);
                        return;
                }
                Set<NamedSemanticRegions<RuleTypes>> use = new HashSet<>();
                Set<RuleTypes> types = EnumSet.noneOf(RuleTypes.class);
                recursivelyFindRegionSets(parserRuleId, ext, use, types);

                Set<CompEntry> items = new HashSet<>();
                for (NamedSemanticRegions<RuleTypes> set : use) {
                    if (optionalSuffix != null && set.contains(optionalSuffix) && optionalPrefix == null) {
                        // we are positioned before a word - offer the entire set, and prepend space
                        set.forEach(item -> {
                            if (types.contains(item.kind())) {
                                items.add(new CompEntry(item.name() + " ", null, item.kind()));
                            }
                        });
                        continue;
                    }
                    if (optionalPrefix != null) {
                        set.matchingPrefix(optionalPrefix, item -> {
                            if (types.contains(item.kind())) {
                                items.add(new CompEntry(item.name(), optionalPrefix, item.kind()));
                            }
                        });
                    } else if (optionalSuffix != null) {
                        set.matchingSuffix(optionalSuffix, item -> {
                            if (types.contains(item.kind())) {
                                items.add(new CompEntry(item.name(), optionalSuffix, item.kind()));
                            }
                        });
                    } else {
                        set.forEach(item -> {
                            if (types.contains(item.kind())) {
                                items.add(new CompEntry(item.name(), null, item.kind()));
                            }
                        });
                    }
                }
                List<CompEntry> all = new ArrayList<>(items);
                Collections.sort(all);
                if (all.size() > maxResultsPerKey) {
//                    all = all.subList(0, maxResultsPerKey);
                }
                float minScore = Float.MAX_VALUE;
                float maxScore = Float.MIN_VALUE;
                for (CompEntry ce : all) {
                    minScore = Math.min(minScore, ce.score());
                    maxScore = Math.max(maxScore, ce.score());
                }
                for (CompEntry ce : all) {
                    names.add(ce.name, ce.type, ce.contextualScore(minScore, maxScore));
                }
            }
        }
    }

    static class CompEntry implements Comparable<CompEntry> {

        private final String name;
        private final String prefixOrSuffix;
        private final RuleTypes type;

        public CompEntry(String name, String prefix, RuleTypes type) {
            this.name = name;
            this.prefixOrSuffix = prefix;
            this.type = type;
        }

        float contextualScore(float min, float max) {
            float val = score();
            if (max == val) {
                return 1;
            }
            if (min == val) {
                return 0;
            }
            float range = max - min;
            float pos = val - min;
            return pos / range;
        }

        int score() {
            if (prefixOrSuffix == null) {
                return 0;
            }
            return LevenshteinDistance.levenshteinDistance(prefixOrSuffix, name, false);
        }

        @Override
        public int compareTo(CompEntry o) {
            if (o.name.length() == name.length() || prefixOrSuffix == null) {
                return name.compareToIgnoreCase(o.name);
            }
            return -Integer.compare(score(), o.score());
        }

        public boolean equals(Object o) {
            return o == null ? false : o == this ? true
                    : !(o instanceof CompEntry) ? false
                            : ((CompEntry) o).name.equals(name);
        }

        @Override
        public int hashCode() {
            return 73 * name.hashCode();
        }
    }
}
