package org.nemesis.test.fixtures.support;

import java.util.Collections;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
public class SimulatedEditorReparse extends UserTask implements Runnable, DocumentListener {

    private final RequestProcessor.Task task = RequestProcessor.getDefault().create(this);
    private final Document doc;

    public SimulatedEditorReparse(Document doc) {
        this.doc = doc;
        doc.addDocumentListener(this);
    }

    public void disable() {
        task.cancel();
        doc.removeDocumentListener(this);
    }

    public void touch() {
        task.schedule(100);
    }

    @Override
    public void run() {
        try {
            ParserManager.parse(Collections.singleton(Source.create(doc)), this);
        } catch (ParseException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        touch();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        touch();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        touch();
    }

    @Override
    public void run(ResultIterator resultIterator) throws Exception {
        resultIterator.getParserResult();
    }

}
