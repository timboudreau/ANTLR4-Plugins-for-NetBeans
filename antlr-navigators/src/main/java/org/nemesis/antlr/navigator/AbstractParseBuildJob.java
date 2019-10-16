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
package org.nemesis.antlr.navigator;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.text.Document;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
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

    protected abstract void onReplaceExtraction(Extraction semantics);

    @Override
    public void run() {
        try {
            ParserManager.parseWhenScanFinished(Collections.singleton(Source.create(document)), this);
        } catch (ParseException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public void run(ResultIterator ri) throws Exception {
        if (forChange != changeCount.get()) {
            onNoModel();
            return;
        }
        Parser.Result res = ri.getParserResult();
        if (res instanceof ExtractionParserResult) {
            Extraction extraction = ((ExtractionParserResult) res).extraction();
            if (extraction == null) {
                // Happens currently if the file is outside the source
                // folders
                onParseFailed();
                return;
            }
            onReplaceExtraction(extraction);
        } else {
            onNoModel();
        }
    }
}
