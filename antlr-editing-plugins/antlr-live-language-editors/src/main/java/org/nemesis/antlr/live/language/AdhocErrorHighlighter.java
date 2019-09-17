package org.nemesis.antlr.live.language;

import java.awt.Color;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocErrorHighlighter extends AbstractAntlrHighlighter {

    public AdhocErrorHighlighter(Document doc) {
        super(doc);
    }

    static AttributeSet errorColoring;

    public static AttributeSet coloring() {
        // Do not cache - user can edit these
//        MimePath mimePath = MimePath.parse(ANTLR_MIME_TYPE);
//        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
//        AttributeSet result = fcs.getFontColors("nested_blocks");
//        assert result != null : "nested_block missing from colors";
//        return result;
        if (errorColoring != null) {
            return errorColoring;
        }
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setUnderline(set, true);
        StyleConstants.setForeground(set, Color.red);
        return errorColoring = set;
    }

    @Override
    protected void refresh(HighlightingInfo info) {
        Document doc = info.doc;
        AntlrProxies.ParseTreeProxy semantics = info.semantics;
        LOG.log(Level.FINER, "Refresh errors with {0} errors", semantics.syntaxErrors().size());
        bag.clear();
        if (!semantics.syntaxErrors().isEmpty()) {
            AttributeSet attrs = coloring();
            for (AntlrProxies.ProxySyntaxError e : semantics.syntaxErrors()) {
                if (e instanceof AntlrProxies.ProxyDetailedSyntaxError) {
                    AntlrProxies.ProxyDetailedSyntaxError d = (AntlrProxies.ProxyDetailedSyntaxError) e;
                    int start = d.startIndex();
                    int end = Math.min(doc.getLength() - 1, d.stopIndex());
                    bag.addHighlight(Math.min(start, end), Math.max(start, end) + 1, attrs);
                } else {
                    if (doc instanceof LineDocument) {
                        LineDocument ld = (LineDocument) doc;
                        try {
                            int start = LineDocumentUtils.getLineStart(ld, e.line())
                                    + e.charPositionInLine();
                            int end = LineDocumentUtils.getLineEnd(ld, e.line());
                            bag.addHighlight(Math.min(start, end), Math.max(start, end), attrs);
                        } catch (BadLocationException ble) {
                            Logger.getLogger(AdhocErrorHighlighter.class.getName()).log(Level.INFO, "BLE parsing " + e, ble);
                        }
                    }
                }
            }
        }
    }
}
