package org.nemesis.antlr.live.language;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocHighlightLayerFactory implements HighlightsLayerFactory {

    private final String mimeType;
    static final Logger LOG = Logger.getLogger(AdhocHighlightLayerFactory.class.getName());
    private static final String DOC_PROP_HIGHLIGHTS_LAYERS = "adhoc-highlight-layers";

    public AdhocHighlightLayerFactory(String mimeType) {
        this.mimeType = mimeType;
        LOG.log(Level.INFO, "Create highlight layer factory for {0}", mimeType);
    }

    public static final class Trigger implements Runnable {

        private final AdhocHighlighterManager mgr;
        private final RequestProcessor.Task task = RequestProcessor.getDefault().create(this);

        public Trigger(AdhocHighlighterManager mgr) {
            this.mgr = mgr;
        }

        @Override
        public void run() {
            LOG.log(Level.FINER, "Run highlighters from trigger for {0}", mgr.document());
            mgr.scheduleReparse();
        }
    }

    @Override
    public HighlightsLayer[] createLayers(Context context) {
        Document doc = context.getDocument();
        HighlightsLayer[] result = (HighlightsLayer[]) doc.getProperty(DOC_PROP_HIGHLIGHTS_LAYERS);
        if (result == null) {
            AdhocHighlighterManager mgr = new AdhocHighlighterManager(mimeType, context);
            AbstractAntlrHighlighter[] hls = mgr.highlighters();
            result = new HighlightsLayer[hls.length];
            for (int i = 0; i < hls.length; i++) {
                result[i] = HighlightsLayer.create(hls[i].getClass().getName(),
                        hls[i].zorder(), true, hls[i].getHighlightsBag());
            }
            LOG.log(Level.FINE, "Instantiated highlight layers for {0}", mimeType);
            doc.putProperty(DOC_PROP_HIGHLIGHTS_LAYERS, result);
            // Allow the preview component to proactively trigger re-highlighting
            // if something relevant to highlighting is changed interactively
            context.getComponent().putClientProperty("trigger", new Trigger(mgr));
        }
        return result;
    }
}
