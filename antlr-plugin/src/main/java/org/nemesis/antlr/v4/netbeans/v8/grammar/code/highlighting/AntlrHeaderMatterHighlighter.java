package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.util.HashMap;
import java.util.Map;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.HeaderMatter;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.Extraction;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting.AbstractAntlrHighlighter.GET_SEMANTICS;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorSettings;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrHeaderMatterHighlighter extends AbstractAntlrHighlighter.DocumentOriented<Void, NBANTLRv4Parser.ANTLRv4ParserResult, ANTLRv4SemanticParser> {

    public AntlrHeaderMatterHighlighter(Document doc) {
        super(doc, NBANTLRv4Parser.ANTLRv4ParserResult.class, GET_SEMANTICS);
    }

    static final String[] COLORING_NAMES = new String[] {"header_matter"};
    public static Map<String, AttributeSet> colorings() {
        // Do not cache - user can edit these
        MimePath mimePath = MimePath.parse("text/x-g4");
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
    protected void refresh(Document doc, Void argument, ANTLRv4SemanticParser semantics, NBANTLRv4Parser.ANTLRv4ParserResult result) {
        Extraction ext = semantics.extraction();
        SemanticRegions<HeaderMatter> hms = ext.regions(AntlrExtractor.HEADER_MATTER);
        AttributeSet attrs = colorings().get(COLORING_NAMES[0]);
        for (SemanticRegion<HeaderMatter> e : hms.outermostElements()) {
            bag.addHighlight(e.start(), e.end(), attrs);
        }
    }
}
