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
package org.nemesis.antlr.live.language;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import static javax.swing.text.Document.StreamDescriptionProperty;
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
        LOG.log(Level.FINE, "Create highlight layer factory for {0}", mimeType);
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
            mgr.scheduleSampleTextReparse();
        }
    }

    @Override
    public HighlightsLayer[] createLayers(Context context) {
        Document doc = context.getDocument();
        // We can be called before the document is set on an editor pane, and
        // we do NOT want to wind up with a zombie AdhocHighlighterManager triggering
        // reparses in the real one because they share a component
        if (doc.getProperty(StreamDescriptionProperty) == null) {
            return new HighlightsLayer[0];
        }
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
