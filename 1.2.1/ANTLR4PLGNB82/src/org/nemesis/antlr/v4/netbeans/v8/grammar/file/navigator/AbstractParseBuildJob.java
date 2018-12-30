package org.nemesis.antlr.v4.netbeans.v8.grammar.file.navigator;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.Extraction;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.cookies.EditorCookie;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractParseBuildJob extends UserTask implements Runnable {

    protected final Document document;
    protected final int forChange;
    protected final EditorCookie ck;
    protected final AtomicInteger changeCount;

    public AbstractParseBuildJob(Document document, int forChange, EditorCookie ck, AtomicInteger changeCount) {
        this.document = document;
        this.forChange = forChange;
        this.ck = ck;
        this.changeCount = changeCount;
    }

    protected abstract void onNoModel();

    protected void onParseFailed() {
        onNoModel();
    }

    protected abstract void onNewModel(Extraction semantics);

    @Override
    public void run() {
        try {
            ParserManager.parseWhenScanFinished(Collections.singleton(Source.create(document)), this);
        } catch (ParseException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public void run(ResultIterator ri) throws Exception {
        if (forChange != changeCount.get()) {
            onNoModel();
            return;
        }
        Parser.Result res = ri.getParserResult();
        if (res instanceof NBANTLRv4Parser.ANTLRv4ParserResult) {
            NBANTLRv4Parser.ANTLRv4ParserResult pr = (NBANTLRv4Parser.ANTLRv4ParserResult) res;
            ANTLRv4SemanticParser semantics = pr.semanticParser();
            if (semantics == null || semantics.extraction() == null) {
                // Happens currently if the file is outside the source
                // folders
                onParseFailed();
                return;
            }
            onNewModel(semantics.extraction());
        } else {
            onNoModel();
        }
    }

}
