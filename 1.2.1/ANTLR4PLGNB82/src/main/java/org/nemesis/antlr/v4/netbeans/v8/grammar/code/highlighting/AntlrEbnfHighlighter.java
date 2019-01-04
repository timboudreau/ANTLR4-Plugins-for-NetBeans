package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.EbnfProperty;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.Extraction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.SemanticRegions;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorSettings;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrEbnfHighlighter extends AbstractAntlrHighlighter.DocumentOriented<Void, ANTLRv4ParserResult, ANTLRv4SemanticParser> {

    public AntlrEbnfHighlighter(Document doc) {
        super(doc, ANTLRv4ParserResult.class, GET_SEMANTICS);
    }

    public void refresh(Document doc, Void argument, ANTLRv4SemanticParser semantics, ANTLRv4ParserResult result) {
        Extraction ext = semantics.extraction();
        SemanticRegions<Set<EbnfProperty>> ebnfs = ext.regions(AntlrExtractor.EBNFS);
        Map<String, AttributeSet> ebnfColorings = colorings();
        for (SemanticRegions.SemanticRegion<Set<EbnfProperty>> e : ebnfs) {
            bag.addHighlight(e.start(), e.end(), ebnfColorings.get(coloringName(e.key())));
        }
    }

    private static final String[] COLORING_NAMES = {"plus_block", "wildcard_block"};

    public String coloringName(Set<EbnfProperty> props) {
        return props.contains(EbnfProperty.PLUS) ? COLORING_NAMES[0] : COLORING_NAMES[1];
    }

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
}
