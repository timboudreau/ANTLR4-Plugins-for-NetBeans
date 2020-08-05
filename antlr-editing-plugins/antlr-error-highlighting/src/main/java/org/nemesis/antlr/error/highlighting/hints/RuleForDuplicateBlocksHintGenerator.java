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
package org.nemesis.antlr.error.highlighting.hints;

import org.nemesis.antlr.error.highlighting.spi.AntlrHintGenerator;
import org.nemesis.antlr.error.highlighting.spi.NonHighlightingHintGenerator;
import com.mastfrog.range.Range;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.common.extractiontypes.GrammarType;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.error.highlighting.HintsAndErrorsExtractors;
import static org.nemesis.antlr.error.highlighting.hints.RemoveSuperfluousParenthesesHintGenerator.elidedContent;
import org.nemesis.antlr.error.highlighting.hints.util.ExtractRule;
import org.nemesis.antlr.error.highlighting.hints.util.RuleNamingConvention;
import static org.nemesis.antlr.error.highlighting.hints.util.RuleNamingConvention.findGrammarType;
import static org.nemesis.antlr.file.AntlrKeys.BLOCKS;
import static org.nemesis.antlr.file.AntlrKeys.RULE_BOUNDS;
import static org.nemesis.antlr.file.AntlrKeys.RULE_NAMES;
import static org.nemesis.antlr.file.AntlrKeys.RULE_NAME_REFERENCES;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.graph.hetero.BitSetHeteroObjectGraph;
import org.nemesis.data.named.ContentsChecksums;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.UnknownNameReference;
import org.openide.text.PositionBounds;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({
    "# {0} - occurrences",
    "# {1} - text",
    "dup_hints_detail=The block ''{0}'' occurs {1} times in the grammar. Extract into a new rule?",
    "# {0} - occurrences",
    "dup_block=The same parenthesized expression occurs at {0}.  Extract into a new rule?"
})
@ServiceProvider(service = AntlrHintGenerator.class)
public final class RuleForDuplicateBlocksHintGenerator extends NonHighlightingHintGenerator {

    // XXX pending:  Should insert the rule at the most sensible place if
    // rules are grouped into parser, then lexer, then fragment, as most
    // grammars are
    @Override
    protected void generate(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes, Document doc, PositionFactory positions) throws BadLocationException {
        GrammarType type = findGrammarType(extraction);
        NamedSemanticRegions<RuleTypes> allNames = extraction.namedRegions(RULE_NAMES);
        NamedRegionReferenceSets<RuleTypes> refs = extraction.nameReferences(RULE_NAME_REFERENCES);
        Set<RuleTypes> ruleReferenceTypesInDuplicateRegion = EnumSet.noneOf(RuleTypes.class);

        SemanticRegions<UnknownNameReference<RuleTypes>> unks = extraction.unknowns(RULE_NAME_REFERENCES);
        // We collect checksums of the set of (non comment, non whitespace0 tokens
        // inside of lexer and parser parenthesized blocks, so we can do a very fast lookup
        // of all cases where more than one block has the same checksum
        ContentsChecksums<SemanticRegion<Void>> checksums = extraction.checksums(BLOCKS);
        if (!checksums.isEmpty()) {
            // Figure out, in this grammar, what the naming convention is - this varies
            // by grammar author, and I've seen in the wild, parser rules that are all
            // lower case with underscores, all fragments upper case and _ delimited,
            // all lexer rules that way while fragments are bi-capitalized.  So
            // RuleNamingConvention figures that out and tries to do similarly;  given
            // sufficient information, it can also tell us what kind of rule a random name
            // is most likely to be depending on its use of capitalization and underscores
            RuleNamingConvention convention = RuleNamingConvention.forExtraction(extraction);
            int[] index = new int[1];
            Map<List<? extends SemanticRegion<Void>>, String> exactMatchRuleNameForRegion
                    = new IdentityHashMap<>();
            Map<SemanticRegion<Void>, List<? extends SemanticRegion<Void>>> listForDuplicate
                    = new IdentityHashMap<>();
            checksums.visitRegionGroups((List<? extends SemanticRegion<Void>> duplicateSet) -> {
                // XXX, why does visitRegionGroups not return a SemanticRegions in the first place?
                // That is what it should do.
                SemanticRegions<Void> duplicates = toSemanticRegions(duplicateSet);
                index[0]++;
                StringBuilder occurrences = new StringBuilder(duplicateSet.size() * 4);
                String text = null;
                List<String> namesInBlock = new ArrayList<String>();
                Map<SemanticRegion<Void>, PositionRange> rangeForRange = new IdentityHashMap<>();

                for (SemanticRegion<Void> tokensWithSameChecksum : duplicateSet) {
                    listForDuplicate.put(tokensWithSameChecksum, duplicateSet);
                    try {
                        // Convert to a Position-backed range that will adapt to changes
                        // in the editor
                        PositionRange pbr = positions.range(tokensWithSameChecksum);
                        rangeForRange.put(tokensWithSameChecksum, pbr);
                        if (text == null) {
                            try {
                                PositionBounds pb = PositionFactory.toPositionBounds(pbr);
                                if (pb != null) {
                                    text = Strings.dequote(pb.getText().trim(), '(', ')');
                                } else {
                                    text = textOf(tokensWithSameChecksum, doc).toString();
                                }
                                // XXX this is pretty imperfect, since a comment will
                                // screw up text matching
                                String match = textMatchRuleBody(tree, text);
                                exactMatchRuleNameForRegion.put(duplicateSet, match);
                            } catch (BadLocationException | IOException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                        if (ruleReferenceTypesInDuplicateRegion.isEmpty()) {
                            // The cheapest way to deal with this is to get a graph
                            // cross referncing the regions in question and the
                            // rule and uunknown name references where the references are
                            // children of the region that contains them.
                            //
                            // Then it is a matter of a couple of bit-set intersections to
                            // find all the rules referenced within a duplicate block
                            BitSetHeteroObjectGraph<SemanticRegion<Void>, NamedSemanticRegionReference<RuleTypes>, ?, NamedRegionReferenceSets<RuleTypes>> graph = duplicates.crossReference(refs);
                            Set<NamedSemanticRegionReference<RuleTypes>> targets = graph.leftSlice().children(duplicates.at(tokensWithSameChecksum.start()));
                            BitSetHeteroObjectGraph<SemanticRegion<Void>, SemanticRegion<UnknownNameReference<RuleTypes>>, ?, SemanticRegions<UnknownNameReference<RuleTypes>>> unkGraph = duplicates.crossReference(unks);
                            Set<SemanticRegion<UnknownNameReference<RuleTypes>>> unknownTargets = unkGraph.leftSlice().children(duplicates.at(tokensWithSameChecksum.start()));

                            targets.forEach(targ -> {
                                ruleReferenceTypesInDuplicateRegion.add(targ.kind());
                                namesInBlock.add(targ.name());
                            });
                            unknownTargets.forEach(targ -> {
                                RuleTypes expectedKind = targ.key().expectedKind();
                                // Will be null for lexer rules, because detection
                                // for unknowns does not try to infer from the other
                                // rule names - it doesn't have the information needed,
                                // because extraction is still generating it at the time
                                // it runs
                                if (expectedKind == null) {
                                    expectedKind = convention.identify(targ.key().name());
                                }
                                ruleReferenceTypesInDuplicateRegion.add(expectedKind);
                                namesInBlock.add(targ.key().name());
                            });
                        }
                        if (occurrences.length() > 0) {
                            occurrences.append(',');
                        }
                        occurrences.append(tokensWithSameChecksum.start()).append(':').append(tokensWithSameChecksum.stop());
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
                if (text == null || text.isEmpty()) {
                    // Exception thrown extracting the text, or the grammar contains
                    // empty () blocks - nothing useful we can do here
                    return;
                }
                String finalText = text;
                Supplier<CharSequence> det = () -> Bundle.dup_hints_detail(finalText, duplicateSet.size());
                String msg = Bundle.dup_block(duplicateSet.size());

                // Generate a rule name to propose
                // This will use the naming convention and the type of grammar to constrain
                // the generated name to be one legal in this grammar and which can legally
                // contain the types of rule names it references
                NameAndKind rn = createRuleName(namesInBlock, ruleReferenceTypesInDuplicateRegion,
                        convention, allNames, type);
                List<PositionRange> allRegions = new ArrayList<>(rangeForRange.values());
                Collections.sort(allRegions, Comparator.<Range<?>>naturalOrder().reversed());
                for (Map.Entry<SemanticRegion<Void>, PositionRange> e : rangeForRange.entrySet()) {
                    List<? extends SemanticRegion<Void>> set = listForDuplicate.get(e.getKey());
                    // If an existing rule was matched, offer to use it rather than generate
                    // a new rule
                    String existingMatchingRule = exactMatchRuleNameForRegion.get(set);
                    try {
                        PositionRange r = e.getValue();
                        if (existingMatchingRule != null) {
                            String elided = elidedContent(text);
                            String id = "ux-" + index[0] + "-" + e.getKey().start() + "-" + e.getKey().end();
                            fixes.ifUnusedErrorId(id, () -> {
                                String ms = Bundle.matchesExistingRule(elided, existingMatchingRule);
                                fixes.addHint(id, r, ms, fixen -> {
                                    fixen.addFix(ms, bag -> {
                                        bag.replace(r, existingMatchingRule);
                                    });
                                });
                            });
                        } else {
                            ExtractRule extract = new ExtractRule(allRegions, extraction, r,
                                    e.getKey().start(),
                                    text, rn.name, positions, rn.type);
                            String id = "bx-" + index[0] + "-" + e.getKey().start() + "-" + e.getKey().end();
                            fixes.ifUnusedErrorId(id, () -> {
                                fixes.addHint(id, r, msg, det, fixen -> {
                                    fixen.addFix(extract, det, extract);
                                });
                            });
                        }
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            });
        }
    }

    @Messages({
        "# {0} - block",
        "# {1} - ruleName",
        "matchesExistingRule=''{0}'' matches existing rule ''{1}'. Replace it?"
    })
    private static CharSequence textOf(SemanticRegion<?> region, Document doc) throws BadLocationException {
        Segment seg = new Segment();
        doc.getText(region.start(), region.size());
        return seg;
    }

    private static SemanticRegions<Void> toSemanticRegions(List<? extends SemanticRegion<Void>> vds) {
        SemanticRegions.SemanticRegionsBuilder<Void> sb = SemanticRegions.builder(Void.class);
        for (SemanticRegion<Void> s : vds) {
            sb.add(s.start(), s.end());
        }
        return sb.build();
    }

    private static class NameAndKind {

        final RuleTypes type;
        final String name;

        public NameAndKind(RuleTypes type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    private int startOfFirst(RuleTypes type, Extraction ext) {
        NamedSemanticRegions<RuleTypes> ruleBounds = ext.namedRegions(RULE_BOUNDS);
        for (NamedSemanticRegion<RuleTypes> item : ruleBounds.index()) {
            if (item.kind() == type) {
                return item.start();
            }
        }
        return -1;
    }

    private static NameAndKind createRuleName(List<String> rulesReferenced, Set<RuleTypes> ruleTypes,
            RuleNamingConvention namingConvention, NamedSemanticRegions<RuleTypes> allNames,
            GrammarType inGrammarType) {

        RuleTypes typeToGenerate;
        boolean generateParserRuleName = inGrammarType == GrammarType.PARSER;
        if (!generateParserRuleName) {
            if (inGrammarType != GrammarType.LEXER) {
                generateParserRuleName = ruleTypes.isEmpty() || ruleTypes.contains(RuleTypes.PARSER);
            }
        }
        if (generateParserRuleName) {
            typeToGenerate = RuleTypes.PARSER;
        } else {
            if (EnumSet.of(RuleTypes.FRAGMENT).equals(ruleTypes)) {
                typeToGenerate = RuleTypes.FRAGMENT;
            } else {
                typeToGenerate = RuleTypes.LEXER;
            }
        }
        if (rulesReferenced.isEmpty()) {
            String result = generateParserRuleName ? "new_rule" : "NewRule";
            if (!generateParserRuleName) {
                result = namingConvention.adjustName(result, typeToGenerate);
            }
            return new NameAndKind(typeToGenerate, result);
        }
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = rulesReferenced.iterator(); it.hasNext();) {
            String name = it.next();
            if (generateParserRuleName) {
                name = name.toLowerCase();
            } else {
                switch (namingConvention) {
                    case LEXER_RULES_UPPER_CASE:
                        name = name.toUpperCase();
                }
            }
            sb.append(name);
            if (sb.length() > 32 && !allNames.contains(sb.toString())) {
                break;
            }
            if (it.hasNext() && (generateParserRuleName
                    || namingConvention == RuleNamingConvention.LEXER_RULES_UPPER_CASE)) {
                sb.append('_');
            }
        }
        String result = sb.toString();
        if (allNames.contains(result)) {
            for (int i = 1;; i++) {
                String test = result + "_" + i;
                if (!allNames.contains(test)) {
                    result = test;
                    break;
                }
            }
        }
        return new NameAndKind(typeToGenerate, result);
    }

    private String textMatchRuleBody(GrammarFileContext ctx, String ruleBodyText) {
        MatchingRuleBodyFinder finder = new MatchingRuleBodyFinder(ruleBodyText);
        ctx.accept(finder);
        return finder.targetName;
    }

    static class MatchingRuleBodyFinder extends ANTLRv4BaseVisitor<Void> {

        // XXX we really should be testing against the exact set of
        // tokens under the tree of the doohickey
        private final String ruleBody;
        private final String ruleBodyNoParens;
        private String targetName;

        public MatchingRuleBodyFinder(String ruleBody) {
            this.ruleBody = ruleBody.replaceAll("\\s+", "");
            if (ruleBody.charAt(0) == '(' && ruleBody.charAt(ruleBody.length() - 1) == ')') {
                ruleBodyNoParens = ruleBody.substring(1, ruleBody.length() - 1);
            } else {
                ruleBodyNoParens = null;
            }
        }

        private void match(ParserRuleContext ctx) {
            String txt = ctx.getText();
            if (txt == null || txt.isEmpty()) { // broken source
                return;
            }
            if (txt.charAt(txt.length() - 1) == ';') {
                txt = txt.substring(0, txt.length() - 1);
            }
            boolean match = ruleBody.equals(txt) || (ruleBodyNoParens != null && ruleBodyNoParens.equals(txt));
            if (match) {
                targetName = HintsAndErrorsExtractors.ruleName(ctx);
            }
        }

        @Override
        public Void visitChildren(RuleNode node) {
            if (targetName != null) {
                return null;
            }
            return super.visitChildren(node); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Void visitParserRuleDefinition(ANTLRv4Parser.ParserRuleDefinitionContext ctx) {
            match(ctx);
            return super.visitParserRuleDefinition(ctx); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Void visitFragmentRuleDefinition(ANTLRv4Parser.FragmentRuleDefinitionContext ctx) {
            match(ctx);
            return super.visitFragmentRuleDefinition(ctx); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Void visitTokenRuleDefinition(ANTLRv4Parser.TokenRuleDefinitionContext ctx) {
            match(ctx);
            return super.visitTokenRuleDefinition(ctx); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
