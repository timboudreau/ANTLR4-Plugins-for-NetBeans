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
        if (ParserManager.canBeParsed(mimeType)) {
            try {
                return parse(mimeType, src, type);
            } catch (IOException | ParseException ex) {
                LOG.log(Level.SEVERE, "Failed parsing " + src + " of "
                        + mimeType + " with " + type, ex);
            }
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
                    if (pr instanceof ExtractionParserResult) {
                        result[0] = ((ExtractionParserResult) pr).extraction();
                    }
                }
            });
            return result[0];
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
