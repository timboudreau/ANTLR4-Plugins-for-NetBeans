package org.nemesis.antlr.error.highlighting;

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
        AntlrRuntimeErrorsHighlighter hl = new AntlrRuntimeErrorsHighlighter(ctx);
        return new HighlightsLayer[]{
            HighlightsLayer.create(ANTLR_RUNTIME_ERRORS, ZOrder.SHOW_OFF_RACK,
            true, hl.bag())
        };
    }

}
