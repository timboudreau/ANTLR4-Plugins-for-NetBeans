package org.nemesis.antlr.live.language;

import java.util.logging.Level;
import javax.swing.text.Document;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import static org.nemesis.adhoc.mime.types.AdhocMimeTypes.loggableMimeType;
import org.nemesis.antlr.live.language.AdhocHighlighterManager.HighlightingInfo;
import org.nemesis.antlr.live.language.coloring.AdhocHighlightsContainer;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public final class AdhocRuleHighlighter extends AbstractAntlrHighlighter {

    private final AdhocHighlightsContainer bag;

    @SuppressWarnings("LeakingThisInConstructor")
    public AdhocRuleHighlighter(AdhocHighlighterManager mgr) {
        super(mgr, ZOrder.SYNTAX_RACK.forPosition(2000));
        bag = new AdhocHighlightsContainer();
//        parser = EmbeddedAntlrParsers.forGrammar("rule-higlighter "
//                + logNameOf(ctx, mimeType), FileUtil.toFileObject(
//                AdhocMimeTypes.grammarFilePathForMimeType(mimeType)
//                        .toFile()));

    }

    static final String logNameOf(Context ctx, String mimeType) {
        Document doc = ctx.getDocument();
        if (doc != null) {
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            if (fo != null) {
                return fo.getNameExt() + ":" + AdhocMimeTypes.loggableMimeType(mimeType);
            } else {
                return doc.toString() + ":" + AdhocMimeTypes.loggableMimeType(mimeType);
            }
        }
        return "unknown";
    }

    @Override
    public String toString() {
        return AdhocRuleHighlighter.class.getSimpleName() + " for " + loggableMimeType(mgr.mimeType());
    }

    @Override
    protected void refresh(HighlightingInfo info) {
        LOG.log(Level.FINEST, "Refresh {0} with {1}", new Object[]{this, info});
        if (info.semantics.isUnparsed() || info.semantics.text() == null || info.semantics.text().length() == 0) {
            LOG.log(Level.INFO, "Unusable parse", new Object[]{info.semantics.loggingInfo()});
            return;
        }
        bag.update(mgr.colorings(), info.semantics, info.semantics.text().length());
    }

    public final HighlightsContainer getHighlightsBag() {
        return bag;
    }
}
