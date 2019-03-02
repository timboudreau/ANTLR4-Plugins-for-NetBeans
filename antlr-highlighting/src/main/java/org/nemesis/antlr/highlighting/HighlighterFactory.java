package org.nemesis.antlr.highlighting;

import org.nemesis.antlr.spi.language.highlighting.semantic.HighlightRefreshTrigger;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.text.Document;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.ZOrder;

/**
 *
 * @author Tim Boudreau
 */
final class HighlighterFactory {

    private final ZOrder zorder;
    private final boolean fixedSize;
    private final Function<Context, GeneralHighlighter<?>> factory;
    private final int positionInZOrder;
    private final IdKey id;
    private static final String BASE = HighlighterFactory.class.getName().replace('.', '-');

    HighlighterFactory(String id, ZOrder zorder, boolean fixedSize, int positionInZOrder, Function<Context, GeneralHighlighter<?>> factory) {
        this.zorder = zorder;
        this.fixedSize = fixedSize;
        this.positionInZOrder = positionInZOrder;
        this.factory = factory;
        this.id = new IdKey(id);
    }

    static HighlighterFactory forRefreshTrigger(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
            int positionInZOrder, String id, Supplier<AntlrHighlighter> supp) {

        Function<Context, GeneralHighlighter<?>> f = ctx -> {
            AntlrHighlighter impl = supp.get();
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
            if (result != null) {
                doc.putProperty(id, result);
            }
        }
        return result;
    }

    public HighlightsLayer createLayer(Context ctx) {
        GeneralHighlighter<?> highlighter = createHighlighter(ctx);
        return highlighter == null ? null
                : HighlightsLayer.create(id.toString(), zorder.forPosition(positionInZOrder),
                        fixedSize, highlighter.getHighlightsBag());
    }

    /**
     * Gives us a key type that is guaranteed not to be used by anything else,
     * since we're storing the highlighter as a document property.
     */
    private static final class IdKey {

        private final String id;

        private IdKey(String id) {
            assert id != null : "null id";
            this.id = id;
        }

        @Override
        public String toString() {
            return BASE + '-' + id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof IdKey && id.equals(((IdKey) o).id);
        }

        @Override
        public int hashCode() {
            return 51 * id.hashCode();
        }
    }
}
