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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.common.extractiontypes.EbnfProperty;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrKeys;
import org.nemesis.extraction.Extraction;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorSettings;

/**
 *
 * @author Timimport org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
 Boudreau
 */
final class AntlrEbnfHighlighter extends AbstractAntlrHighlighter.DocumentOriented<Void, ANTLRv4ParserResult, Extraction> {

    public AntlrEbnfHighlighter(Document doc) {
        super(doc, ANTLRv4ParserResult.class, findExtraction());
    }

    public void refresh(Document doc, Void argument, Extraction ext, ANTLRv4ParserResult result) {
        SemanticRegions<Set<EbnfProperty>> ebnfs = ext.regions(AntlrKeys.EBNFS);
        Map<String, AttributeSet> ebnfColorings = colorings();
        for (SemanticRegion<Set<EbnfProperty>> e : ebnfs) {
            bag.addHighlight(e.start(), e.end(), ebnfColorings.get(coloringName(e.key())));
        }
    }

    private static final String[] COLORING_NAMES = {"plus_block", "wildcard_block"};

    public String coloringName(Set<EbnfProperty> props) {
        return props.contains(EbnfProperty.PLUS) ? COLORING_NAMES[0] : COLORING_NAMES[1];
    }

    public static Map<String, AttributeSet> colorings() {
        // Do not cache - user can edit these
        MimePath mimePath = MimePath.parse(ANTLR_MIME_TYPE);
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        Map<String, AttributeSet> result = new HashMap<>(3);
        for (String kind : COLORING_NAMES) {
            AttributeSet attrs = fcs.getTokenFontColors(kind);

            assert attrs != null : kind + " missing from fonts and colors for text/x-g4";
            result.put(kind, attrs);
        }
        return result;
    }
}
