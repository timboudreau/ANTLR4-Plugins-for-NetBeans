package org.nemesis.antlr.live.language;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.ParserFactory;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.WeakSet;

/**
 *
 * @author Tim Boudreau
 */
final class AdhocParserFactory extends ParserFactory implements Runnable {

    private final Set<AdhocParser> liveParsers = new WeakSet<>();
    private static final Logger LOG = Logger.getLogger(AdhocParserFactory.class.getName());
    private final String mimeType;
//    private final EmbeddedAntlrParser embeddedParser;

    @SuppressWarnings("LeakingThisInConstructor")
    public AdhocParserFactory(String mimeType) {
        this.mimeType = mimeType;
        Path grammarFilePath = AdhocMimeTypes.grammarFilePathForMimeType(mimeType);
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(grammarFilePath.toFile()));
//        embeddedParser = EmbeddedAntlrParsers.forGrammar("parser-factory:" + AdhocMimeTypes.loggableMimeType(mimeType), fo);
//        embeddedParser.listen(this);
        AdhocLanguageHierarchy.parserFor(mimeType).listen(this);
    }

    private boolean updating;
    private int idAtLastUpdate = -1;

    void updated() {
        if (updating) {
            return;
        }
        try {
            updating = true;
            LOG.log(Level.FINE, "Received update notification from embedded parser "
                    + "{0} with {1} live parsers for underlying grammar change",
                    new Object[]{AdhocLanguageHierarchy.parserFor(mimeType), liveParsers.size()});
            for (AdhocParser p : liveParsers) {
                p.updated();
            }
            DynamicLanguages.mimeData().updateMimeType(mimeType);
        } finally {
            updating = false;
        }
    }

    @Override
    public Parser createParser(Collection<Snapshot> clctn) {
        if (!clctn.isEmpty()) {
            Snapshot snap = clctn.iterator().next();
            String m = snap.getMimeType();
            if (!mimeType.equals(m)) {
                LOG.log(Level.SEVERE, "AdhocParserFactory for " + mimeType + " asked to create a parser for " + m,
                        new Exception("AdhocParserFactory for " + mimeType + " asked to create a parser for " + m));
            }
        }
        AdhocParser parser = new AdhocParser(mimeType);
        liveParsers.add(parser);
        LOG.log(Level.FINER, "Created a parser {0} over {1} grammar {2}",
                new Object[]{parser, clctn, mimeType});
        return parser;
    }

    @Override
    public void run() {
        updated();
    }
}
