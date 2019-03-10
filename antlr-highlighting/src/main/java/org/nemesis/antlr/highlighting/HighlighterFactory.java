package org.nemesis.antlr.highlighting;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlightRefreshTrigger;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.ZOrder;

/**
 *
 * @author Tim Boudreau
 */
final class HighlighterFactory implements Comparable<HighlighterFactory> {

    private final ZOrder zorder;
    private final boolean fixedSize;
    private final Function<Context, GeneralHighlighter<?>> factory;
    private final int positionInZOrder;
    private final IdKey id;
    private static final String BASE = HighlighterFactory.class.getName().replace('.', '-');
    private static final Logger LOG = Logger.getLogger(HighlighterFactory.class.getName());

    HighlighterFactory(String id, ZOrder zorder, boolean fixedSize, int positionInZOrder, Function<Context, GeneralHighlighter<?>> factory) {
        this.zorder = zorder;
        this.fixedSize = fixedSize;
        this.positionInZOrder = positionInZOrder;
        this.factory = factory;
        this.id = new IdKey(id);
        LOG.log(Level.FINE, "Created a HighlighterFactory {0} fixed {1} z {2} pos {3}", new Object[] {id, fixedSize,
            zorder, positionInZOrder});
    }

    @Override
    public int compareTo(HighlighterFactory o) {
        int a = zorderPosition();
        int b = o.zorderPosition();
        if (a == b) {
            int pa = positionInZOrder;
            int pb = o.positionInZOrder;
            return pa > pb ? 1 : pa < pb ? -1 : 0;
        }
        return a > b ? 1 : a < b ? -1 : 0;
    }

    private int zorderPosition() {
        String s = zorder.toString();
        System.out.println("ZORDER '" + s + "'");
        int open = s.indexOf('(');
        int close = s.indexOf(')');
        if (open > 0 && close > 0 && close > open) {
            return Integer.parseInt(s.substring(open+1, close));
        }
        return 0;
    }


    static HighlighterFactory forRefreshTrigger(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
            int positionInZOrder, String id, Supplier<AntlrHighlighter> supp) {

        Function<Context, GeneralHighlighter<?>> f = ctx -> {
            AntlrHighlighter impl = supp.get();
            if (trigger == HighlightRefreshTrigger.CARET_MOVED && impl instanceof SimpleNamedRegionReferenceAntlrHighlighter<?>) {
                SimpleNamedRegionReferenceAntlrHighlighter<?> s
                        = (SimpleNamedRegionReferenceAntlrHighlighter<?>) impl;
                s.highlightReferencesUnderCaret();
            }
            int refreshDelay = trigger.refreshDelay();
            return createHighlighter(ctx, refreshDelay, impl, trigger);
        };
        return new HighlighterFactory(id, zorder, fixedSize, positionInZOrder, f);
    }

    static GeneralHighlighter<?> createHighlighter(Context ctx, int refreshDelay, AntlrHighlighter implementation, HighlightRefreshTrigger trigger) {
        switch (trigger) {
            case DOCUMENT_CHANGED:
                return new GeneralHighlighter.DocumentOriented<Void>(ctx, refreshDelay, implementation);
            case CARET_MOVED:
                return new GeneralHighlighter.CaretOriented(ctx, refreshDelay, implementation);
            default:
                throw new AssertionError(trigger);
        }
    }

    protected GeneralHighlighter<?> existingHighlighter(Context ctx) {
        Document doc = ctx.getDocument();
        Object result = doc.getProperty(id);
        return result == null ? null
                : result instanceof GeneralHighlighter<?> ? (GeneralHighlighter<?>) result
                        : null;
    }

    private GeneralHighlighter<?> createHighlighter(Context ctx) {
        assert ctx != null : "null context";
        Document doc = ctx.getDocument();
        GeneralHighlighter<?> result = existingHighlighter(ctx);
        if (result == null) {
            result = factory.apply(ctx);
            LOG.log(Level.FINEST, "Create a GeneralHighlighter for {0}: {1}",
                    new Object[] { this, result });
            if (result != null) {
                doc.putProperty(id, result);
            }
        } else {
            LOG.log(Level.FINEST, "Use existing GeneralHighlighter for {0}: {1}",
                    new Object[] { this, result });
        }
        return result;
    }

    public HighlightsLayer createLayer(Context ctx) {
        GeneralHighlighter<?> highlighter = createHighlighter(ctx);
        LOG.log(Level.FINER, "Create highlights layer for {0}", highlighter);
        System.out.println("CREATE LAYER " + highlighter);
        return highlighter == null ? null
                : HighlightsLayer.create(id.toString(), zorder.forPosition(positionInZOrder),
                        fixedSize, highlighter.getHighlightsBag());
    }

    /**
     * Gives us a key type that is guaranteed not to be used by anything else,
     * since we're storing the highlighter as a document property.
     */
    private static int intIds = 0;

    private static final class IdKey {

        private final String id;
        private final int intId = intIds++;

        private IdKey(String id) {
            assert id != null : "null id";
            this.id = id;
        }

        @Override
        public String toString() {
            return BASE + '-' + id + "-" + intId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof IdKey
                    && intId == (((IdKey) o).intId)
                    && id.equals(((IdKey) o).id);
        }

        @Override
        public int hashCode() {
            return (intId + 1) * (51 * id.hashCode());
        }
    }
}
