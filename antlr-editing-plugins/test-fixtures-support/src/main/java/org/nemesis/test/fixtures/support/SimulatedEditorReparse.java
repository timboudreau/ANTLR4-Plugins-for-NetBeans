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
        task.schedule(1);
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
