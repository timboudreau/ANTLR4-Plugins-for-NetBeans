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
package org.nemesis.antlr.error.highlighting;

import java.util.logging.Level;
import javax.swing.text.Document;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.openide.filesystems.FileObject;

@MimeRegistrations({
    @MimeRegistration(mimeType = ANTLR_MIME_TYPE, service = HighlightsLayerFactory.class)
})
public class AntlrRuntimeHighlightLayerFactory implements HighlightsLayerFactory {

    public static final String ANTLR_RUNTIME_ERRORS = "antlr-runtime-errors";
    private static final HighlightsLayer[] EMPTY = new HighlightsLayer[0];

    @Override
    public HighlightsLayer[] createLayers(Context ctx) {
        Document doc = ctx.getDocument();
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        if (fo == null) { // preview pane, etc.
            return EMPTY;
        }
        AntlrRuntimeErrorsHighlighter.LOG.log(Level.FINE, "Create layers for {0}", fo);
        AntlrRuntimeErrorsHighlighter hl = new AntlrRuntimeErrorsHighlighter(ctx);
        return new HighlightsLayer[]{
            HighlightsLayer.create(ANTLR_RUNTIME_ERRORS, ZOrder.TOP_RACK,
            true, hl.bag())
        };
    }
}
