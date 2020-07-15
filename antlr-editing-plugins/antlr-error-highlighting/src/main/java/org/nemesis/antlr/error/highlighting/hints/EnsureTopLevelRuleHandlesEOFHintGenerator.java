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
import org.nemesis.antlr.error.highlighting.hints.util.EOFInsertionStringGenerator;
import java.io.IOException;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Position;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import static org.nemesis.antlr.file.AntlrKeys.GRAMMAR_TYPE;
import org.nemesis.antlr.file.impl.GrammarDeclaration;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.UnknownNameReference;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({
    "# {0} - grammarName",
    "# {1} - firstRuleName",
    "eofUnhandled=End-of-file (EOF) is not handled in {0}."
    + " Append it to {1}?",
    "detailsNoEof=A grammar which does not handle EOF may not parse all of "
    + "the input it is given, leading to unexpected results.",
    "# {0} - insertionText",
    "# {1} - ruleName",
    "insertEof=Insert {0} at end of rule {1}"
})
@ServiceProvider(service=AntlrHintGenerator.class)
public final class EnsureTopLevelRuleHandlesEOFHintGenerator extends NonHighlightingHintGenerator {

    @Override
    protected void generate(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes, Document doc, PositionFactory positions) throws BadLocationException {
        fixes.ifUnusedErrorId("no-eof", () -> {
            NamedSemanticRegions<RuleTypes> regions = extraction.namedRegions(AntlrKeys.RULE_BOUNDS);
            SingletonEncounters<GrammarDeclaration> gt = extraction.singletons(GRAMMAR_TYPE);
            if (!regions.isEmpty() && !gt.isEmpty()) {
                SingletonEncounters.SingletonEncounter<GrammarDeclaration> grammarType = gt.first();
                GrammarDeclaration gg = grammarType.get();
                SemanticRegions<UnknownNameReference<RuleTypes>> unks = extraction.unknowns(AntlrKeys.RULE_NAME_REFERENCES);

                switch (gg.type()) {
                    case COMBINED:
                        NamedSemanticRegion<RuleTypes> firstRuleBounds = regions.index().first();
                        List<? extends SemanticRegion<UnknownNameReference<RuleTypes>>> eofMentions
                                = unks.collect((unk) -> "EOF".equals(unk.name()));
                        if (eofMentions.isEmpty()) {
                            Position targetPosition = positions
                                    .createPosition(firstRuleBounds.stop(), Position.Bias.Backward);

                            String msg = Bundle.eofUnhandled(extraction.source().name(), firstRuleBounds.name());

                            fixes.addWarning("no-eof", firstRuleBounds, msg, Bundle::detailsNoEof, fixen -> {
                                try {
                                    String toInsert = EOFInsertionStringGenerator.getEofInsertionString(doc);
                                    String imsg = Bundle.insertEof(toInsert, firstRuleBounds.name());
                                    fixen.addFix(imsg, bag -> {
                                        bag.insert(targetPosition, toInsert);
                                    });
                                } catch (IOException ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            });
                        }
                        break;
                    case PARSER:
                    // XXX find implicit grammar and scan it
                }
            }
        });
    }
}
