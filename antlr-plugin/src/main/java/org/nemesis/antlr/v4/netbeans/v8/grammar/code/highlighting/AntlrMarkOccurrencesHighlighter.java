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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrKeys;
import org.nemesis.extraction.Extraction;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedRegionReferenceSet;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorNames;
import org.netbeans.api.editor.settings.FontColorSettings;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrMarkOccurrencesHighlighter extends AbstractAntlrHighlighter.CaretOriented<ANTLRv4ParserResult, Extraction> {

    public AntlrMarkOccurrencesHighlighter(Document doc) {
        super(doc, ANTLRv4ParserResult.class, findExtraction());
    }

    private AttributeSet markOccurrencesColoring() {
        MimePath mimePath = MimePath.parse("text/x-java");
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        return fcs.getFontColors(FontColorNames.INC_SEARCH_COLORING);
    }

    @Override
    protected void refresh(Document doc, Integer caretPosition, Extraction ext, ANTLRv4ParserResult result) {
        NamedSemanticRegions<RuleTypes> ruleBounds = ext.namedRegions(AntlrKeys.RULE_BOUNDS);
        NamedSemanticRegion<RuleTypes> ruleCaretIsIn = ruleBounds.at(caretPosition);
        if (ruleCaretIsIn != null) {

            NamedSemanticRegions<RuleTypes> names = ext.namedRegions(AntlrKeys.RULE_NAMES);

            NamedSemanticRegion<RuleTypes> curr = names.at(caretPosition);

            NamedRegionReferenceSets<RuleTypes> nameRefs = ext.nameReferences(AntlrKeys.RULE_NAME_REFERENCES);
            if (curr == null) {
                curr = nameRefs.at(caretPosition);
            }
            if (curr != null) {
                AttributeSet markColoring = markOccurrencesColoring();
                if (curr.isReference() && names.contains(curr.name())) {
                    NamedSemanticRegion<RuleTypes> decl = names.regionFor(curr.name());
                    bag.addHighlight(decl.start(), decl.end(), markColoring);
                }
//                bag.addHighlight(curr.start(), curr.end(), markColoring);
                NamedRegionReferenceSet<RuleTypes> refs = nameRefs.references(curr.name());
                if (refs != null && refs.size() > 0) {
                    for (NamedSemanticRegionReference<RuleTypes> ref : refs) {
                        bag.addHighlight(ref.start(), ref.end(), markColoring);
                    }
                }
            }
        }
    }
}
