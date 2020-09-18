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
import static com.mastfrog.util.strings.Strings.deSingleQuote;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.extractiontypes.GrammarType;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.error.highlighting.HintsAndErrorsExtractors;
import org.nemesis.antlr.error.highlighting.hints.util.ExtractRule;
import static org.nemesis.antlr.error.highlighting.hints.util.NameUtils.generateLexerRuleName;
import org.nemesis.antlr.error.highlighting.hints.util.RuleNamingConvention;
import static org.nemesis.antlr.error.highlighting.hints.util.RuleNamingConvention.findGrammarType;
import static org.nemesis.antlr.file.AntlrKeys.RULE_NAMES;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({
    "# {0} - count",
    "# {1} - literal",
    "literalInParserRule=String literal {1} encountered in parser rule {0} times. Replace with lexer rule?",
    "# {0} - literal",
    "replaceWithLexerRule=Replace all occurrences of {0} with a lexer rule?",
    "# {0} - literal",
    "# {1} - existingRule",
    "replaceWithExistingRule=Replace {0} with reference to identical rule {1}"
})
@ServiceProvider(service=AntlrHintGenerator.class)
public class CreateRuleFromStringLiteralsInParserRulesHintGenerator extends NonHighlightingHintGenerator {

    @Override
    protected void generate(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate, Fixes fixes,
            Document doc, PositionFactory positions) throws BadLocationException {
        SemanticRegions<HintsAndErrorsExtractors.TermCtx> terms = extraction.regions(HintsAndErrorsExtractors.LONELY_STRING_LITERALS);
        SemanticRegions<HintsAndErrorsExtractors.SingleTermCtx> matchingRules = extraction.regions(HintsAndErrorsExtractors.SOLO_STRING_LITERALS);
        Map<String, String> index = new HashMap<>();
        for (SemanticRegion<HintsAndErrorsExtractors.SingleTermCtx> ctx : matchingRules) {
            index.put(ctx.key().text, ctx.key().ruleName);
        }
        terms.forEach(region -> {
            try {
                String id = region.key().text + "-pbr-" + region.index();
                fixes.ifUnusedErrorId(id, () -> {
                    PositionRange stringLiteralRegion = positions.range(region);
                    if (stringLiteralRegion == null) {
                        return;
                    }
                    List<PositionRange> ranges = new ArrayList<>(terms.size());
                    int[] count = new int[1];
                    terms.forEach(region2 -> {
                        if (region.key().text.equals(region2.key().text)) {
                            PositionRange pbr;
                            try {
                                pbr = positions.range(region2);
                                if (pbr == null) {
                                    return;
                                }
                                ranges.add(pbr);
                                count[0]++;
                            } catch (BadLocationException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                    });
                    String literal = deSingleQuote(region.key().text);
                    String targetRule = index.get(literal);
                    try {
                        if (targetRule != null) {
                            String msg = Bundle.replaceWithExistingRule(region.key().text, targetRule);
//                        PositionBoundsRange rng = PositionBoundsRange.create(extraction.source(), region);
                            fixes.addHint(id, stringLiteralRegion, msg, fixen -> {
//                            fixen.addReplacement(msg, rng.start(), rng.end(), targetRule);
                                fixen.addFix(false, msg, bag -> {
                                    bag.replace(stringLiteralRegion, targetRule);
                                });
                            });
                        } else {
                            String msg = Bundle.literalInParserRule(count[0], region.key().text);
                            RuleNamingConvention namingConvention = RuleNamingConvention.forExtraction(extraction);
                            fixes.addHint(id, region, msg, fixen -> {
                                GrammarType type = findGrammarType(extraction);
                                RuleTypes generatedRuleType;
                                switch(type) {
                                    case COMBINED :
                                        generatedRuleType = RuleTypes.LEXER;
                                        break;
                                    case LEXER :
                                        generatedRuleType = RuleTypes.FRAGMENT;
                                        break;
                                    case PARSER :
                                        generatedRuleType = RuleTypes.PARSER;
                                        break;
                                    case UNDEFINED :
                                        generatedRuleType = RuleTypes.LEXER;
                                        break;
                                    default :
                                        throw new AssertionError(type);
                                }
                                String generatedName = namingConvention.adjustName(generateLexerRuleName(region.key().text), RuleTypes.LEXER);
                                NamedSemanticRegions<RuleTypes> existingNames = extraction.namedRegions(RULE_NAMES);
                                int ix = 0;
                                String uniqueName = generatedName;
                                while (existingNames.contains(uniqueName)) {
                                    uniqueName = generatedName + "_" + ++ix;
                                }
                                ExtractRule extract = new ExtractRule(ranges, extraction,
                                        stringLiteralRegion, region.start(), region.key().text, uniqueName, 
                                        positions, generatedRuleType);
                                fixen.addFix(Bundle.replaceWithLexerRule(region.key().text), extract);
                            });
                        }
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                });
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
    }
}
