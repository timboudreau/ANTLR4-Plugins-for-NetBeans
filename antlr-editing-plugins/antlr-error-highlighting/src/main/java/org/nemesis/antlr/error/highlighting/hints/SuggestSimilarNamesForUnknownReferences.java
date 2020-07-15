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
import java.util.Iterator;
import java.util.logging.Level;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.source.api.GrammarSource;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@Messages({
    "# {0} - oldRule",
    "# {1} - newRule",
    "replace=Replace {0} with {1}",
    "# {0} - uknownRuleName",
    "unknown_rule_referenced=Unknown rule referenced: {0}"
})
@ServiceProvider(service = AntlrHintGenerator.class)
public final class SuggestSimilarNamesForUnknownReferences extends NonHighlightingHintGenerator {

    @Override
    protected void generate(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate, Fixes fixes,
            Document doc, PositionFactory positions) throws BadLocationException {
        SemanticRegions<UnknownNameReference<RuleTypes>> unknowns = extraction.unknowns(AntlrKeys.RULE_NAME_REFERENCES);
        if (!unknowns.isEmpty()) {
            Attributions<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolved
                    = extraction.resolveAll(AntlrKeys.RULE_NAME_REFERENCES);

            SemanticRegions<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes>> attributed
                    = resolved == null ? SemanticRegions.empty() : resolved.attributed();

            Iterator<SemanticRegion<UnknownNameReference<RuleTypes>>> it = unknowns.iterator();
            while (it.hasNext()) {
                SemanticRegion<UnknownNameReference<RuleTypes>> unk = it.next();
                String text = unk.key().name();
                if ("EOF".equals(text)) {
                    continue;
                }
                if (attributed.at(unk.start()) != null) {
                    continue;
                }
                PositionRange rng = positions.range(unk);
                try {
                    String hint = Bundle.unknown_rule_referenced(text);
                    String errId = "unk-" + text + "-" + unk.start() + "-" + unk.end();
                    fixes.ifUnusedErrorId(errId, () -> {
                        fixes.addError(errId, unk, hint, fixen -> {
                            NamedSemanticRegions<RuleTypes> nameRegions = extraction.namedRegions(AntlrKeys.RULE_NAMES);
                            for (String sim : nameRegions.topSimilarNames(text, 5)) {
                                String msg = Bundle.replace(text, sim);
                                fixen.addFix(msg, bag -> {
                                    bag.replace(rng, sim);
                                });
                            }
                        });
                    });
                } catch (BadLocationException ex) {
                    LOG.log(Level.WARNING, "Exception adding hint for unknown rule: " + unk, ex);
                }
            }
        }
    }
}
