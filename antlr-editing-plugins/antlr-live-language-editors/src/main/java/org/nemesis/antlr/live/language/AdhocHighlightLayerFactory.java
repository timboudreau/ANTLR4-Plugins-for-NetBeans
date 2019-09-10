package org.nemesis.antlr.live.language;

import java.awt.EventQueue;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocHighlightLayerFactory implements HighlightsLayerFactory {

    private final String mimeType;
    static final Logger LOG = Logger.getLogger(AdhocHighlightLayerFactory.class.getName());

    public AdhocHighlightLayerFactory(String mimeType) {
        this.mimeType = mimeType;
        LOG.log(Level.INFO, "Create highlight layer factory for {0}", mimeType);
    }

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

    public AbstractAntlrHighlighter<?, ?, ?> getRulesHighlighter(Document doc) {
        return highlighter(doc, AdhocRuleHighlighter.class, () -> new AdhocRuleHighlighter(doc, mimeType));
    }

    public AbstractAntlrHighlighter<?, ?, ?> getErrorHighlighter(Document doc) {
        return highlighter(doc, AdhocErrorHighlighter.class, () -> new AdhocErrorHighlighter(doc, mimeType));
    }

    public static final class Trigger implements Runnable {

        private final Context context;
        private final AbstractAntlrHighlighter<?, ?, ?>[] highlighters;
        private final RequestProcessor.Task task = RequestProcessor.getDefault().create(this);

        public Trigger(Context context, AbstractAntlrHighlighter<?, ?, ?>... highlighters) {
            this.context = context;
            this.highlighters = highlighters;
        }

        @Override
        public void run() {
            if (EventQueue.isDispatchThread()) {
                task.schedule(250);
            } else {
                LOG.log(Level.FINER, "Run highlighters from trigger");
                for (AbstractAntlrHighlighter<?, ?, ?> h : highlighters) {
                    h.run();
                }
            }
        }
    }

    @Override
    public HighlightsLayer[] createLayers(Context context) {

        AbstractAntlrHighlighter<?, ?, ?> ruleHighlighter = getRulesHighlighter(context.getDocument());
        HighlightsLayer rules = HighlightsLayer.create(AdhocRuleHighlighter.class.getName(),
                ZOrder.SYNTAX_RACK.forPosition(2000),
                true,
                ruleHighlighter.getHighlightsBag());

//        AbstractAntlrHighlighter<?, ?, ?> errorHighlighter
//                = getErrorHighlighter(context.getDocument());
//        HighlightsLayer errors = HighlightsLayer.create(AdhocErrorHighlighter.class.getName(),
//                ZOrder.BOTTOM_RACK.forPosition(2001),
//                true,
//                errorHighlighter.getHighlightsBag());
        context.getComponent().putClientProperty("trigger", new Trigger(context, ruleHighlighter));

        LOG.log(Level.FINE, "Instantiated highlight layers for {0}", mimeType);
        return new HighlightsLayer[]{rules};
    }
}
