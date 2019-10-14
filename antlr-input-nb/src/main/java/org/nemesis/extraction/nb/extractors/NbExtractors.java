package org.nemesis.extraction.nb.extractors;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.Extractors;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Extractors.class)
public class NbExtractors extends Extractors {

    private static final Logger LOG = Logger.getLogger(NbExtractors.class
            .getName());

    @Override
    public <P extends ParserRuleContext> Extraction extract(String mimeType,
            GrammarSource<?> src, Class<P> type) {
        if (mimeType == null || mimeType.indexOf('/') <= 0) {
            throw new IllegalArgumentException("'" + mimeType
                    + "' does not look like a mime type.");
        }
        System.out.println("NBExtrators extract " + src + " for " + mimeType + " and " + type);
        if (ParserManager.canBeParsed(mimeType)) {
            try {
                return parse(mimeType, src, type);
            } catch (IOException | ParseException ex) {
                LOG.log(Level.SEVERE, "Failed parsing " + src + " of "
                        + mimeType + " with " + type, ex);
            }
        } else {
            System.out.println("ParserManager says " + mimeType + " cannot be parsed");
        }
        return null;
    }

    private <P extends ParserRuleContext> Extraction parse(String mimeType,
            GrammarSource<?> src, Class<P> type) throws IOException, ParseException {
        Source source = source(src);
        if (source != null) {
            Extraction[] result = new Extraction[1];
            ParserManager.parse(Collections.singleton(source), new UserTask() {
                @Override
                public void run(ResultIterator resultIterator) throws Exception {
                    Parser.Result pr = resultIterator.getParserResult();
                    System.out.println("     PARSE GOT " + pr);
                    if (pr instanceof ExtractionParserResult) {
                        result[0] = ((ExtractionParserResult) pr).extraction();
                    }
                }
            });
            System.out.println("  returning pr " + result[0]);
            return result[0];
        } else {
            System.out.println("could not create a Source over " + src);
        }
        return null;
    }

    private Source source(GrammarSource<?> src) throws IOException {
        Object o = src.source();
        if (o instanceof Snapshot) {
            return ((Snapshot) o).getSource();
        } else if (o instanceof Source) {
            return (Source) o;
        } else if (o instanceof Document) {
            Document d = (Document) o;
            return Source.create(d);
        } else if (o instanceof FileObject) {
            FileObject fo = (FileObject) o;
            return Source.create(fo);
        }
        Optional<Document> doc = src.lookup(Document.class);
        if (doc.isPresent()) {
            return Source.create(doc.get());
        }
        Optional<FileObject> fo = src.lookup(FileObject.class);
        if (fo.isPresent()) {
            return Source.create(fo.get());
        }
        return null;
    }
}
