package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.util.function.Supplier;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;

/**
 *
 * @author Tim Boudreau
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/x-g4", service = HighlightsLayerFactory.class)
})
public class AntlrHighlightingLayerFactory implements HighlightsLayerFactory {

    public static <T extends AbstractAntlrHighlighter<?, ?, ?>> T highlighter(Document doc, Class<T> type, Supplier<T> ifNone) {
        T highlighter = existingHighlighter(doc, type);
        if (highlighter == null) {
            highlighter = ifNone.get();
            doc.putProperty(type, highlighter);
        }
        return highlighter;
    }

    public static <T extends AbstractAntlrHighlighter<?, ?, ?>> T existingHighlighter(Document doc, Class<T> type) {
        Object result = doc.getProperty(type);
        return result == null ? null : type.cast(result);
    }

    public static AbstractAntlrHighlighter<?, ?, ?> getMarkOccurrencesHighlighter(Document doc) {
        return highlighter(doc, AntlrMarkOccurrencesHighlighter.class, () -> new AntlrMarkOccurrencesHighlighter(doc));
    }

    public static AbstractAntlrHighlighter<?, ?, ?> getDeclarationHighlighter(Document doc) {
        return highlighter(doc, AntlrDeclarationHighlighter.class, () -> new AntlrDeclarationHighlighter(doc));
    }

    public static AbstractAntlrHighlighter<?, ?, ?> getFragmentHighlighter(Document doc) {
        return highlighter(doc, AntlrFragmentHighlighter.class, () -> new AntlrFragmentHighlighter(doc));
    }

    public static AbstractAntlrHighlighter<?, ?, ?> getEbnfHighlighter(Document doc) {
        return highlighter(doc, AntlrEbnfHighlighter.class, () -> new AntlrEbnfHighlighter(doc));
    }

    public static AbstractAntlrHighlighter<?, ?, ?> getNestingDepthHighlighter(Document doc) {
        return highlighter(doc, AntlrNestingDepthHighlighter.class, () -> new AntlrNestingDepthHighlighter(doc));
    }

    public static AbstractAntlrHighlighter<?, ?, ?> getHeaderMatterHighlighter(Document doc) {
        return highlighter(doc, AntlrHeaderMatterHighlighter.class, () -> new AntlrHeaderMatterHighlighter(doc));
    }

    @Override
    public HighlightsLayer[] createLayers(Context context) {
        HighlightsLayer semantic = HighlightsLayer.create(AntlrDeclarationHighlighter.class.getName(),
                ZOrder.SYNTAX_RACK.forPosition(1997),
                true,
                getDeclarationHighlighter(context.getDocument()).getHighlightsBag());
        HighlightsLayer fragments = HighlightsLayer.create(
                AntlrFragmentHighlighter.class.getName(),
                ZOrder.SYNTAX_RACK.forPosition(1998),
                true,
                getFragmentHighlighter(context.getDocument()).getHighlightsBag());
        HighlightsLayer ebnfs = HighlightsLayer.create(
                AntlrEbnfHighlighter.class.getName(),
                ZOrder.SYNTAX_RACK.forPosition(2052),
                true,
                getEbnfHighlighter(context.getDocument()).getHighlightsBag());
        HighlightsLayer hdr = HighlightsLayer.create(
                AntlrHeaderMatterHighlighter.class.getName(),
                ZOrder.SYNTAX_RACK.forPosition(2052),
                true,
                getHeaderMatterHighlighter(context.getDocument()).getHighlightsBag());
        HighlightsLayer nesting = HighlightsLayer.create(
                AntlrNestingDepthHighlighter.class.getName(),
                ZOrder.SYNTAX_RACK.forPosition(2051),
                true,
                getNestingDepthHighlighter(context.getDocument()).getHighlightsBag());

//        HighlightsLayer markOccurrences = HighlightsLayer.create(
//                AntlrMarkOccurrencesHighlighter.class.getName(),
//                ZOrder.SYNTAX_RACK.forPosition(2000),
//                true,
//                getMarkOccurrencesHighlighter(context.getDocument()).getHighlightsBag());
        return new HighlightsLayer[]{semantic, fragments, ebnfs, nesting, hdr/*, markOccurrences*/};
    }
}
