package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.util.HashMap;
import java.util.Map;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.common.extractiontypes.HeaderMatter;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrKeys;
import org.nemesis.extraction.Extraction;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorSettings;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrHeaderMatterHighlighter extends AbstractAntlrHighlighter.DocumentOriented<Void, ANTLRv4ParserResult, Extraction> {

    public AntlrHeaderMatterHighlighter(Document doc) {
        super(doc, NBANTLRv4Parser.ANTLRv4ParserResult.class, findExtraction());
    }

    static final String[] COLORING_NAMES = new String[] {"header_matter"};
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

    @Override
    protected void refresh(Document doc, Void argument, Extraction ext, NBANTLRv4Parser.ANTLRv4ParserResult result) {
        SemanticRegions<HeaderMatter> hms = ext.regions(AntlrKeys.HEADER_MATTER);
        AttributeSet attrs = colorings().get(COLORING_NAMES[0]);
        for (SemanticRegion<HeaderMatter> e : hms.outermostElements()) {
            bag.addHighlight(e.start(), e.end(), attrs);
        }
    }
}
