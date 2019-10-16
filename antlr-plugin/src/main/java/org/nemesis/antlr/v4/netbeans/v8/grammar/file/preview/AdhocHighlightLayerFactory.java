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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.awt.EventQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting.AbstractAntlrHighlighter;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting.AntlrHighlightingLayerFactory.highlighter;
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

    public AbstractAntlrHighlighter<?, ?, ?> getRulesHighlighter(Document doc) {
        return highlighter(doc, AdhocRuleHighlighter.class, () -> new AdhocRuleHighlighter(doc, mimeType));
    }

    public AbstractAntlrHighlighter<?, ?, ?> getErrorHighlighter(Document doc) {
        return highlighter(doc, AdhocErrorHighlighter.class, () -> new AdhocErrorHighlighter(doc, mimeType));
    }

    public static final class Trigger implements Runnable {

        private final Context context;
        private final AbstractAntlrHighlighter<?,?,?>[] highlighters;
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
                for (AbstractAntlrHighlighter<?,?,?> h : highlighters) {
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
